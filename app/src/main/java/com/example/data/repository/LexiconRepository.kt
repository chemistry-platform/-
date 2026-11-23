package com.example.data.repository

import android.util.Log
import com.example.core.NetworkMonitor
import com.example.data.local.CachedWordEntity
import com.example.data.local.FavoriteWordEntity
import com.example.data.local.LexiconDao
import com.example.data.local.SearchHistoryEntity
import com.example.data.remote.DatamuseApi
import com.example.data.remote.DatamuseWordDto
import com.example.data.remote.DictionaryEntryDto
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.GeminiLexiconService
import com.example.data.remote.MlKitTranslationService
import com.example.data.remote.OfflineModelStatus
import com.example.data.remote.RawSenseInput
import com.example.domain.model.ArabicSearchResolution
import com.example.domain.model.CollocationItem
import com.example.domain.model.LanguageDirection
import com.example.domain.model.PhraseItem
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SenseItem
import com.example.domain.model.SynonymGroup
import com.example.domain.model.WordEntry
import com.example.domain.model.WordFamilyItem
import com.example.domain.model.WordNetSynset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.util.Locale

private const val TAG = "LexiconRepository"

sealed interface WordLoadResult {
    data class Success(val entry: WordEntry) : WordLoadResult
    data class ArabicResolved(val resolution: ArabicSearchResolution, val primaryEntry: WordEntry?) : WordLoadResult
    data class Error(val message: String, val isOffline: Boolean = false) : WordLoadResult
}

class LexiconRepository(
    private val dao: LexiconDao,
    private val dictionaryApi: FreeDictionaryApi,
    private val datamuseApi: DatamuseApi,
    private val mlKitService: MlKitTranslationService,
    private val geminiService: GeminiLexiconService,
    private val networkMonitor: NetworkMonitor
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val recentHistory: Flow<List<SearchHistoryEntity>> = dao.observeRecentHistory()
    val favorites: Flow<List<FavoriteWordEntity>> = dao.observeFavorites()
    val cachedCount: Flow<Int> = dao.observeCachedCount()
    val mlKitModelStatus: StateFlow<OfflineModelStatus> = mlKitService.modelStatus
    val isOnlineFlow: Flow<Boolean> = networkMonitor.isOnlineFlow

    fun observeIsFavorite(word: String): Flow<Boolean> =
        dao.observeIsFavorite(word.trim().lowercase(Locale.US))

    suspend fun toggleFavorite(entry: WordEntry, currentlyFavorite: Boolean) {
        val key = entry.word.trim().lowercase(Locale.US)
        if (currentlyFavorite) {
            dao.deleteFavorite(key)
        } else {
            dao.upsertFavorite(
                FavoriteWordEntity(
                    word = key,
                    arabicSummary = entry.arabicMeaningsSummary.joinToString("؛ "),
                    usIpa = entry.usIpa,
                    partOfSpeechArabic = entry.partsOfSpeech.firstOrNull().orEmpty()
                )
            )
        }
    }

    suspend fun toggleFavoriteByWord(word: String) {
        dao.deleteFavorite(word.trim().lowercase(Locale.US))
    }

    suspend fun deleteHistoryItem(queryKey: String) {
        dao.deleteHistoryItem(queryKey)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun clearCache() {
        dao.clearCachedWords()
    }

    suspend fun refreshMlKitStatus() {
        mlKitService.refreshModelStatus()
    }

    suspend fun downloadOfflineModels(): Result<Unit> = mlKitService.ensureModelsDownloaded()

    suspend fun deleteOfflineModels(): Result<Unit> = mlKitService.deleteOfflineModels()

    suspend fun translateSentence(
        text: String,
        direction: LanguageDirection
    ): Result<String> {
        return when (direction) {
            LanguageDirection.EN_TO_AR -> mlKitService.translateEnToAr(text)
            LanguageDirection.AR_TO_EN -> mlKitService.translateArToEn(text)
        }
    }

    suspend fun getLiveSuggestions(
        query: String,
        direction: LanguageDirection
    ): List<SearchSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val isArabicInput = containsArabic(trimmed)
        if (isArabicInput) {
            val cachedMatches = dao.searchCachedWordsByArabic(trimmed).map {
                SearchSuggestion(
                    englishWord = it.word,
                    arabicMeaning = it.arabicSummary,
                    partOfSpeechArabic = it.partOfSpeechSummary,
                    isFromCache = true
                )
            }
            val translatedEn = mlKitService.translateArToEn(trimmed).getOrNull()
                ?.trim()
                ?.lowercase(Locale.US)
                ?.replace(Regex("[^a-zA-Z\\s-]"), "")
                ?.trim()
                .orEmpty()

            val primaryWord = translatedEn.split(" ").firstOrNull().orEmpty()
            val dynamicMatches = buildList {
                if (primaryWord.isNotBlank()) {
                    add(
                        SearchSuggestion(
                            englishWord = primaryWord,
                            arabicMeaning = trimmed,
                            partOfSpeechArabic = "ترجمة فورية"
                        )
                    )
                    if (networkMonitor.isOnline()) {
                        val related = try {
                            datamuseApi.getRelatedWords(primaryWord, max = 5)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            emptyList()
                        }
                        for (dto in related) {
                            currentCoroutineContext().ensureActive()
                            val w = dto.word?.trim().orEmpty()
                            if (w.isNotBlank() && !w.equals(primaryWord, ignoreCase = true) && !w.contains(" ")) {
                                val ar = mlKitService.translateEnToAr(w).getOrNull().orEmpty()
                                add(
                                    SearchSuggestion(
                                        englishWord = w,
                                        arabicMeaning = ar.ifBlank { trimmed },
                                        partOfSpeechArabic = mapDatamusePos(dto.tags)
                                    )
                                )
                            }
                        }
                    }
                }
            }
            return (dynamicMatches + cachedMatches)
                .distinctBy { it.englishWord.lowercase(Locale.US) }
                .take(12)
        }

        // English input
        val normalized = trimmed.lowercase(Locale.US)
        val cached = dao.searchCachedWordsByPrefix(normalized).map {
            SearchSuggestion(
                englishWord = it.word,
                arabicMeaning = it.arabicSummary,
                partOfSpeechArabic = it.partOfSpeechSummary,
                isFromCache = true
            )
        }

        if (normalized.length < 2 || !networkMonitor.isOnline() || trimmed.contains(" ")) {
            if (cached.isNotEmpty()) return cached
            val fallbackAr = mlKitService.translateEnToAr(trimmed).getOrNull().orEmpty()
            return if (fallbackAr.isNotBlank()) {
                listOf(
                    SearchSuggestion(
                        englishWord = normalized.split(" ").firstOrNull() ?: normalized,
                        arabicMeaning = fallbackAr
                    )
                )
            } else {
                emptyList()
            }
        }

        val apiSuggestions = try {
            datamuseApi.getPrefixSuggestions("$normalized*", max = 8)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        val candidates = apiSuggestions
            .mapNotNull { dto ->
                val word = dto.word?.trim() ?: return@mapNotNull null
                if (word.contains(" ")) return@mapNotNull null
                word to mapDatamusePos(dto.tags)
            }
            .filter { (w, _) -> cached.none { it.englishWord.equals(w, ignoreCase = true) } }
            .take(5)

        val translatedCandidates = buildList {
            for ((word, posAr) in candidates) {
                currentCoroutineContext().ensureActive()
                val cachedRow = dao.getCachedWord(word.lowercase(Locale.US))
                val arabic = cachedRow?.arabicSummary
                    ?: mlKitService.translateEnToAr(word).getOrNull().orEmpty()
                add(
                    SearchSuggestion(
                        englishWord = word,
                        arabicMeaning = arabic.ifBlank { "اضغط لعرض المعاني" },
                        partOfSpeechArabic = posAr,
                        isFromCache = cachedRow != null
                    )
                )
            }
        }

        val exactCachedOrDirect = if (cached.none { it.englishWord.equals(normalized, ignoreCase = true) } &&
            translatedCandidates.none { it.englishWord.equals(normalized, ignoreCase = true) }
        ) {
            currentCoroutineContext().ensureActive()
            val directAr = mlKitService.translateEnToAr(normalized).getOrNull().orEmpty()
            listOf(
                SearchSuggestion(
                    englishWord = normalized,
                    arabicMeaning = directAr.ifBlank { "بحث مباشر في القاموس" }
                )
            )
        } else {
            emptyList()
        }

        return (cached + exactCachedOrDirect + translatedCandidates)
            .distinctBy { it.englishWord.lowercase(Locale.US) }
            .take(12)
    }

    suspend fun lookupQuery(rawQuery: String, forceRefresh: Boolean = false): WordLoadResult {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            return WordLoadResult.Error("يرجى إدخال كلمة للبحث")
        }

        if (containsArabic(trimmed)) {
            return lookupArabicWord(trimmed)
        }

        val normalizedWord = trimmed.lowercase(Locale.US)
        val cached = dao.getCachedWord(normalizedWord)
        if (cached != null && !forceRefresh && (cached.isAiComplete || !networkMonitor.isOnline())) {
            val decoded = runCatching { json.decodeFromString<WordEntry>(cached.payloadJson) }.getOrNull()
            if (decoded != null) {
                recordHistory(
                    queryKey = normalizedWord,
                    displayQuery = decoded.word,
                    resolvedEnglishWord = decoded.word,
                    arabicPreview = decoded.arabicMeaningsSummary.joinToString("؛ "),
                    direction = LanguageDirection.EN_TO_AR
                )
                return WordLoadResult.Success(decoded.copy(isFromCache = true))
            }
        }

        if (!networkMonitor.isOnline()) {
            // Even if not cached in Room, try ML Kit offline translation as basic fallback
            val offlineMeaning = mlKitService.translateEnToAr(normalizedWord).getOrNull()
            return if (!offlineMeaning.isNullOrBlank()) {
                val offlineEntry = WordEntry(
                    word = normalizedWord,
                    arabicMeaningsSummary = listOf(offlineMeaning),
                    senses = listOf(
                        SenseItem(
                            index = 1,
                            partOfSpeech = "word",
                            partOfSpeechArabic = "كلمة",
                            arabicMeaning = offlineMeaning,
                            englishDefinition = "Offline translation via ML Kit",
                            exampleEnglish = "",
                            exampleArabic = ""
                        )
                    ),
                    aiStatusNote = "أنت غير متصل بالإنترنت. تم عرض الترجمة الفورية المحفوظة على الجهاز."
                )
                WordLoadResult.Success(offlineEntry)
            } else {
                WordLoadResult.Error(
                    message = "لا يوجد اتصال بالإنترنت، وهذه الكلمة غير محفوظة محلياً بعد.",
                    isOffline = true
                )
            }
        }

        return fetchAndEnrichEnglishWord(normalizedWord)
    }

    private suspend fun lookupArabicWord(arabicQuery: String): WordLoadResult {
        val translatedEn = mlKitService.translateArToEn(arabicQuery).getOrNull()
            ?.trim()
            ?.lowercase(Locale.US)
            ?.replace(Regex("[^a-zA-Z\\s-]"), "")
            ?.trim()
            .orEmpty()

        val primaryEnglish = translatedEn.split(" ").firstOrNull().orEmpty()
        if (primaryEnglish.isBlank()) {
            return WordLoadResult.Error(
                message = if (networkMonitor.isOnline()) {
                    "تعذر ترجمة الكلمة العربية حالياً. تأكد من تحميل حزمة الترجمة من قائمة أكثر."
                } else {
                    "لا يوجد اتصال بالإنترنت وحزمة الترجمة العربية غير جاهزة بعد."
                },
                isOffline = !networkMonitor.isOnline()
            )
        }

        val candidates = mutableListOf(
            SearchSuggestion(
                englishWord = primaryEnglish,
                arabicMeaning = arabicQuery,
                partOfSpeechArabic = "المعنى الأساسي"
            )
        )

        if (networkMonitor.isOnline()) {
            val related = runCatching {
                datamuseApi.getRelatedWords(primaryEnglish, max = 5)
            }.getOrDefault(emptyList())
            related.forEach { dto ->
                val w = dto.word?.trim().orEmpty()
                if (w.isNotBlank() && !w.contains(" ") && !w.equals(primaryEnglish, ignoreCase = true)) {
                    val ar = mlKitService.translateEnToAr(w).getOrNull().orEmpty()
                    candidates.add(
                        SearchSuggestion(
                            englishWord = w,
                            arabicMeaning = ar.ifBlank { arabicQuery },
                            partOfSpeechArabic = mapDatamusePos(dto.tags)
                        )
                    )
                }
            }
        }

        val primaryResult = lookupQuery(primaryEnglish)
        val primaryEntry = (primaryResult as? WordLoadResult.Success)?.entry
        if (primaryEntry != null) {
            recordHistory(
                queryKey = arabicQuery,
                displayQuery = arabicQuery,
                resolvedEnglishWord = primaryEntry.word,
                arabicPreview = primaryEntry.arabicMeaningsSummary.joinToString("؛ "),
                direction = LanguageDirection.AR_TO_EN
            )
        }

        return WordLoadResult.ArabicResolved(
            resolution = ArabicSearchResolution(
                arabicQuery = arabicQuery,
                primaryEnglish = primaryEnglish,
                candidateWords = candidates.distinctBy { it.englishWord.lowercase(Locale.US) }
            ),
            primaryEntry = primaryEntry
        )
    }

    private suspend fun fetchAndEnrichEnglishWord(word: String): WordLoadResult = coroutineScope {
        val dictDeferred = async {
            runCatching { dictionaryApi.getWordDefinitions(word) }
        }
        val synDeferred = async {
            runCatching { datamuseApi.getSynonyms(word, max = 20) }.getOrDefault(emptyList())
        }
        val antDeferred = async {
            runCatching { datamuseApi.getAntonyms(word, max = 15) }.getOrDefault(emptyList())
        }
        val relDeferred = async {
            runCatching { datamuseApi.getRelatedWords(word, max = 16) }.getOrDefault(emptyList())
        }

        val dictResult = dictDeferred.await()
        val datamuseSynonyms = synDeferred.await()
        val datamuseAntonyms = antDeferred.await()
        val datamuseRelated = relDeferred.await()

        val entries = dictResult.getOrNull()
        if (entries.isNullOrEmpty()) {
            val throwable = dictResult.exceptionOrNull()
            if (throwable is HttpException && throwable.code() == 404) {
                // Fallback to ML Kit if word isn't in Free Dictionary API
                val fallbackAr = mlKitService.translateEnToAr(word).getOrNull()
                if (!fallbackAr.isNullOrBlank()) {
                    val basicEntry = WordEntry(
                        word = word,
                        arabicMeaningsSummary = listOf(fallbackAr),
                        allSynonyms = datamuseSynonyms.mapNotNull { it.word }.distinct(),
                        allAntonyms = datamuseAntonyms.mapNotNull { it.word }.distinct(),
                        senses = listOf(
                            SenseItem(
                                index = 1,
                                partOfSpeech = "word",
                                partOfSpeechArabic = "كلمة",
                                arabicMeaning = fallbackAr,
                                englishDefinition = "Direct translation result",
                                exampleEnglish = "",
                                exampleArabic = ""
                            )
                        ),
                        aiStatusNote = "لم يتم العثور على مداخل تفصيلية لهذه الكلمة في القاموس المفتوح، تم عرض الترجمة الفورية."
                    )
                    return@coroutineScope WordLoadResult.Success(basicEntry)
                }
                return@coroutineScope WordLoadResult.Error("لم يتم العثور على الكلمة \"$word\" في القاموس.")
            }
            return@coroutineScope WordLoadResult.Error(
                message = "تعذر الاتصال بالقاموس حالياً. تحقق من الشبكة وحاول مجدداً.",
                isOffline = !networkMonitor.isOnline()
            )
        }

        val primaryDto = entries.first()
        val headword = primaryDto.word?.trim().takeIf { !it.isNullOrBlank() } ?: word

        val (usIpa, ukIpa, usAudio, ukAudio) = extractPhonetics(entries)
        val originText = entries.firstNotNullOfOrNull { it.origin?.trim()?.takeIf { o -> o.isNotBlank() } }

        // Build full list of raw senses across ALL parts of speech & entries
        val rawSensesWithMeta = mutableListOf<Triple<RawSenseInput, List<String>, List<String>>>()
        var runningIndex = 1
        entries.forEach { entryDto ->
            entryDto.meanings.orEmpty().forEach { meaningDto ->
                val pos = meaningDto.partOfSpeech?.trim().orEmpty().ifBlank { "noun" }
                val meaningSyns = meaningDto.synonyms.orEmpty()
                val meaningAnts = meaningDto.antonyms.orEmpty()
                meaningDto.definitions.orEmpty().forEach { defDto ->
                    val defText = defDto.definition?.trim().orEmpty()
                    if (defText.isNotBlank() && runningIndex <= 24) {
                        rawSensesWithMeta.add(
                            Triple(
                                RawSenseInput(
                                    index = runningIndex,
                                    partOfSpeech = pos,
                                    englishDefinition = defText,
                                    existingExample = defDto.example?.trim()?.takeIf { it.isNotBlank() }
                                ),
                                (defDto.synonyms.orEmpty() + meaningSyns).distinct(),
                                (defDto.antonyms.orEmpty() + meaningAnts).distinct()
                            )
                        )
                        runningIndex++
                    }
                }
            }
        }

        if (rawSensesWithMeta.isEmpty()) {
            return@coroutineScope WordLoadResult.Error("لا توجد تعريفات متاحة لهذه الكلمة حالياً.")
        }

        val geminiResult = geminiService.enrichWord(
            word = headword,
            rawSenses = rawSensesWithMeta.map { it.first },
            originHint = originText
        )

        val enrichment = geminiResult.getOrNull()
        val aiFallbackNote = if (enrichment == null) {
            "تعذر توليد أمثلة الذكاء الاصطناعي حالياً (نفاد الحصة أو الشبكة). تم استخدام الترجمة المحلية وأمثلة القاموس المفتوح."
        } else {
            null
        }

        val enrichedByIndex = enrichment?.enrichedSenses?.associateBy { it.index }.orEmpty()

        // Build final SenseItems; if Gemini failed, translate definitions + examples via ML Kit
        val finalSenses = rawSensesWithMeta.map { (raw, syns, ants) ->
            val aiSense = enrichedByIndex[raw.index]
            val arabicMeaning = aiSense?.arabicMeaning?.takeIf { it.isNotBlank() }
                ?: mlKitService.translateEnToAr(raw.englishDefinition).getOrNull()
                    ?.takeIf { it.isNotBlank() }
                ?: mlKitService.translateEnToAr(headword).getOrNull()
                ?: headword

            val exampleEn = aiSense?.exampleEnglish?.takeIf { it.isNotBlank() }
                ?: raw.existingExample
                ?: "This word \"$headword\" is used in context."

            val exampleAr = aiSense?.exampleArabic?.takeIf { it.isNotBlank() }
                ?: mlKitService.translateEnToAr(exampleEn).getOrNull().orEmpty()

            SenseItem(
                index = raw.index,
                partOfSpeech = raw.partOfSpeech,
                partOfSpeechArabic = mapPosToArabic(raw.partOfSpeech),
                grammarLabel = aiSense?.grammarLabel.orEmpty(),
                registerTag = aiSense?.registerTag.orEmpty(),
                arabicMeaning = arabicMeaning,
                englishDefinition = raw.englishDefinition,
                exampleEnglish = exampleEn,
                exampleArabic = exampleAr,
                synonyms = syns,
                antonyms = ants
            )
        }

        val partsOfSpeechArabic = finalSenses.map { it.partOfSpeechArabic }.distinct()
        val summaryMeanings = enrichment?.arabicSummary?.takeIf { it.isNotEmpty() }
            ?: finalSenses.map { it.arabicMeaning }.distinct().take(8)

        val registerTags = (enrichment?.registerTags.orEmpty() + finalSenses.mapNotNull {
            it.registerTag.takeIf { tag -> tag.isNotBlank() }
        }).distinct()

        // Group synonyms by meaning/POS + merge Datamuse synonyms
        val synonymGroups = buildList {
            val groupedByPos = finalSenses.groupBy { it.partOfSpeechArabic }
            groupedByPos.forEach { (posAr, sensesForPos) ->
                val groupSyns = sensesForPos.flatMap { it.synonyms }.distinct()
                if (groupSyns.isNotEmpty()) {
                    add(SynonymGroup(meaningLabel = posAr, synonyms = groupSyns.take(12)))
                }
            }
            val dmSyns = datamuseSynonyms.mapNotNull { it.word?.trim()?.takeIf { w -> w.isNotBlank() } }
            val existingSyns = finalSenses.flatMap { it.synonyms }.toSet()
            val extraDmSyns = dmSyns.filter { it !in existingSyns }.take(12)
            if (extraDmSyns.isNotEmpty()) {
                add(SynonymGroup(meaningLabel = "مرادفات إضافية", synonyms = extraDmSyns))
            }
        }

        val allSynonyms = (finalSenses.flatMap { it.synonyms } +
            datamuseSynonyms.mapNotNull { it.word?.trim() })
            .filter { it.isNotBlank() && !it.equals(headword, ignoreCase = true) }
            .distinct()
            .take(24)

        val allAntonyms = (finalSenses.flatMap { it.antonyms } +
            datamuseAntonyms.mapNotNull { it.word?.trim() })
            .filter { it.isNotBlank() && !it.equals(headword, ignoreCase = true) }
            .distinct()
            .take(20)

        val wordFamily = enrichment?.wordFamily?.takeIf { it.isNotEmpty() }
            ?: buildFallbackWordFamily(headword, datamuseRelated)

        val wordNetSynsets = buildWordNetSynsets(finalSenses, allSynonyms, allAntonyms, datamuseRelated)

        val finalEntry = WordEntry(
            word = headword,
            usIpa = usIpa,
            ukIpa = ukIpa,
            usAudioUrl = usAudio,
            ukAudioUrl = ukAudio,
            arabicMeaningsSummary = summaryMeanings,
            partsOfSpeech = partsOfSpeechArabic,
            registerTags = registerTags,
            synonymGroups = synonymGroups,
            allSynonyms = allSynonyms,
            allAntonyms = allAntonyms,
            senses = finalSenses,
            phrases = enrichment?.phrases.orEmpty(),
            wordFamily = wordFamily,
            collocations = enrichment?.collocations.orEmpty(),
            wordNetSynsets = wordNetSynsets,
            origin = enrichment?.originArabic ?: originText,
            aiStatusNote = aiFallbackNote,
            isFromCache = false
        )

        // Save to Room offline cache + history
        runCatching {
            dao.upsertCachedWord(
                CachedWordEntity(
                    word = headword.lowercase(Locale.US),
                    arabicSummary = summaryMeanings.joinToString("؛ "),
                    partOfSpeechSummary = partsOfSpeechArabic.joinToString(" • "),
                    payloadJson = json.encodeToString(finalEntry),
                    isAiComplete = enrichment != null
                )
            )
            recordHistory(
                queryKey = headword.lowercase(Locale.US),
                displayQuery = headword,
                resolvedEnglishWord = headword,
                arabicPreview = summaryMeanings.joinToString("؛ "),
                direction = LanguageDirection.EN_TO_AR
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed saving word to Room cache", e)
        }

        WordLoadResult.Success(finalEntry)
    }

    private suspend fun recordHistory(
        queryKey: String,
        displayQuery: String,
        resolvedEnglishWord: String,
        arabicPreview: String,
        direction: LanguageDirection
    ) {
        dao.upsertHistory(
            SearchHistoryEntity(
                queryKey = queryKey,
                displayQuery = displayQuery,
                resolvedEnglishWord = resolvedEnglishWord,
                arabicPreview = arabicPreview,
                direction = direction.name,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private data class PhoneticsTuple(
        val usIpa: String,
        val ukIpa: String,
        val usAudio: String?,
        val ukAudio: String?
    )

    private fun extractPhonetics(entries: List<DictionaryEntryDto>): PhoneticsTuple {
        val phonetics = entries.flatMap { it.phonetics.orEmpty() }
        val fallbackIpa = entries.firstNotNullOfOrNull { it.phonetic?.trim()?.takeIf { p -> p.isNotBlank() } }
            ?: phonetics.firstNotNullOfOrNull { it.text?.trim()?.takeIf { t -> t.isNotBlank() } }
            ?: ""

        val usPhonetic = phonetics.firstOrNull {
            val audio = it.audio.orEmpty().lowercase(Locale.US)
            audio.contains("-us.") || audio.contains("_us") || audio.contains("/us")
        }
        val ukPhonetic = phonetics.firstOrNull {
            val audio = it.audio.orEmpty().lowercase(Locale.US)
            audio.contains("-uk.") || audio.contains("_uk") || audio.contains("/uk") || audio.contains("-gb.")
        }

        val anyAudio = phonetics.firstNotNullOfOrNull { it.audio?.trim()?.takeIf { a -> a.isNotBlank() } }

        return PhoneticsTuple(
            usIpa = usPhonetic?.text?.trim()?.takeIf { it.isNotBlank() } ?: fallbackIpa,
            ukIpa = ukPhonetic?.text?.trim()?.takeIf { it.isNotBlank() } ?: fallbackIpa,
            usAudio = usPhonetic?.audio?.trim()?.takeIf { it.isNotBlank() } ?: anyAudio,
            ukAudio = ukPhonetic?.audio?.trim()?.takeIf { it.isNotBlank() } ?: anyAudio
        )
    }

    private fun buildFallbackWordFamily(
        headword: String,
        related: List<DatamuseWordDto>
    ): List<WordFamilyItem> {
        val stem = headword.lowercase(Locale.US).take(4)
        return related
            .mapNotNull { dto ->
                val w = dto.word?.trim() ?: return@mapNotNull null
                if (w.equals(headword, ignoreCase = true) || w.contains(" ")) return@mapNotNull null
                if (stem.length >= 3 && w.lowercase(Locale.US).startsWith(stem)) {
                    val posAr = mapDatamusePos(dto.tags)
                    WordFamilyItem(
                        word = w,
                        partOfSpeech = dto.tags?.firstOrNull().orEmpty(),
                        partOfSpeechArabic = posAr.ifBlank { "مشتق" },
                        arabicMeaning = ""
                    )
                } else null
            }
            .distinctBy { it.word.lowercase(Locale.US) }
            .take(6)
    }

    private fun buildWordNetSynsets(
        senses: List<SenseItem>,
        globalSynonyms: List<String>,
        globalAntonyms: List<String>,
        related: List<DatamuseWordDto>
    ): List<WordNetSynset> {
        val relatedWords = related.mapNotNull { it.word?.trim() }.filter { it.isNotBlank() }.take(8)
        return senses.take(10).map { sense ->
            WordNetSynset(
                partOfSpeech = sense.partOfSpeech,
                partOfSpeechArabic = sense.partOfSpeechArabic,
                definition = sense.englishDefinition,
                arabicMeaning = sense.arabicMeaning,
                synonyms = sense.synonyms.ifEmpty { globalSynonyms.take(6) },
                antonyms = sense.antonyms.ifEmpty { globalAntonyms.take(4) },
                relatedTerms = relatedWords
            )
        }
    }

    private fun containsArabic(text: String): Boolean =
        text.any { ch -> ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' }

    private fun mapPosToArabic(pos: String): String = when (pos.lowercase(Locale.US)) {
        "noun" -> "اسم"
        "verb" -> "فعل"
        "adjective" -> "صفة"
        "adverb" -> "ظرف"
        "pronoun" -> "ضمير"
        "preposition" -> "حرف جر"
        "conjunction" -> "أداة ربط"
        "interjection" -> "تعجب"
        "determiner" -> "محدد"
        "exclamation" -> "صيغة تعجب"
        else -> pos.ifBlank { "كلمة" }
    }

    private fun mapDatamusePos(tags: List<String>?): String {
        val tag = tags?.firstOrNull()?.lowercase(Locale.US) ?: return ""
        return when (tag) {
            "n" -> "اسم"
            "v" -> "فعل"
            "adj" -> "صفة"
            "adv" -> "ظرف"
            else -> ""
        }
    }
}
