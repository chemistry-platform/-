package com.example.data.repository

import android.util.Log
import androidx.core.text.HtmlCompat
import com.example.core.AudioPlayerManager
import com.example.core.NetworkMonitor
import com.example.core.TranslationManager
import com.example.data.local.ApiCacheEntity
import com.example.data.local.FavoriteWordEntity
import com.example.data.local.LexiconDao
import com.example.data.local.OfflinePackWordEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.WordIndexEntity
import com.example.data.remote.DatamuseApi
import com.example.data.remote.DictionaryEntryDto
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.TatoebaApi
import com.example.data.remote.WiktionaryApi
import com.example.domain.model.ConfusableCandidate
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.PosSenseGroup
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SenseItem
import com.example.domain.model.SenseSource
import com.example.domain.model.TatoebaSentence
import com.example.domain.model.WordEntry
import com.example.domain.model.WordFamilyItem
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import retrofit2.HttpException
import java.util.Locale

private const val TAG = "LexiconRepository"
private const val CACHE_STALE_MS = 7L * 24L * 60L * 60L * 1000L
private const val SOURCE_TIMEOUT_MS = 8_000L

private const val SRC_FREEDICT = "freedict"
private const val SRC_WIKTIONARY = "wiktionary"
private const val SRC_DATAMUSE = "datamuse"
private const val SRC_TATOEBA = "tatoeba"
private const val SRC_CONFUSABLE_ENTRY = "confusable_entry"

private val EXACT_PARTICLES = setOf(
    "at", "after", "for", "up", "down", "out", "in", "on", "off", "over",
    "into", "with", "about", "through", "back", "away", "around", "by", "to",
    "from", "of", "against", "along", "across", "behind", "between", "beyond",
    "under", "onto", "towards", "within", "without"
)

sealed interface WordLoadState {
    data object Loading : WordLoadState
    data class Success(
        val entry: WordEntry,
        val arabicQuery: String? = null,
        val isUpdating: Boolean = false
    ) : WordLoadState
    data class Error(
        val message: String,
        val isOffline: Boolean = false,
        val needsLanguagePack: Boolean = false
    ) : WordLoadState
}

@Serializable
private data class FreeDictCachedPayload(
    val notFound: Boolean = false,
    val usIpa: String = "",
    val ukIpa: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val senses: List<SenseItem> = emptyList(),
    val phrases: List<String> = emptyList()
)

@Serializable
private data class WiktionaryCachedPayload(
    val notFound: Boolean = false,
    val senses: List<SenseItem> = emptyList(),
    val phrases: List<String> = emptyList(),
    val derivedWords: List<String> = emptyList()
)

@Serializable
private data class DatamuseCachedPayload(
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    val prepositions: List<String> = emptyList(),
    val familyCandidates: List<WordFamilyItem> = emptyList(),
    val confusableCandidates: List<ConfusableCandidate> = emptyList(),
    val generalTerms: List<String> = emptyList(),
    val specificTerms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList()
)

@Serializable
private data class TatoebaCachedPayload(
    val sentences: List<TatoebaSentence> = emptyList()
)

@Serializable
private data class ConfusableEntryCachePayload(
    val hasRealEntry: Boolean = false,
    val detail: ConfusableWordDetail? = null
)

class LexiconRepository(
    private val dao: LexiconDao,
    private val dictionaryApi: FreeDictionaryApi,
    private val datamuseApi: DatamuseApi,
    private val wiktionaryApi: WiktionaryApi,
    private val tatoebaApi: TatoebaApi,
    private val translationManager: TranslationManager,
    private val audioPlayerManager: AudioPlayerManager,
    private val networkMonitor: NetworkMonitor
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val recentHistory: Flow<List<SearchHistoryEntity>> = dao.observeRecentHistory()
    val favorites: Flow<List<FavoriteWordEntity>> = dao.observeFavorites()
    val cachedCount: Flow<Int> = dao.observeCachedCount()
    val offlinePackCount: Flow<Int> = dao.observeOfflinePackCount()
    val isOnlineFlow: Flow<Boolean> = networkMonitor.isOnlineFlow

    fun observeIsFavorite(word: String): Flow<Boolean> =
        dao.observeIsFavorite(word.trim().lowercase(Locale.US))

    suspend fun toggleFavorite(entry: WordEntry, currentlyFavorite: Boolean) {
        val key = entry.word.trim().lowercase(Locale.US)
        if (currentlyFavorite) {
            dao.deleteFavorite(key)
        } else {
            val firstSense = entry.posGroups.firstOrNull()?.senses?.firstOrNull()
            dao.upsertFavorite(
                FavoriteWordEntity(
                    word = key,
                    summaryText = firstSense?.definition.orEmpty().take(160),
                    usIpa = entry.usIpa,
                    partOfSpeech = entry.posGroups.firstOrNull()?.partOfSpeechArabic.orEmpty()
                )
            )
        }
    }

    suspend fun removeFavorite(word: String) {
        dao.deleteFavorite(word.trim().lowercase(Locale.US))
    }

    suspend fun deleteHistoryItem(queryKey: String) {
        dao.deleteHistoryItem(queryKey)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun clearApiCache() {
        dao.clearApiCache()
        dao.clearWordIndex()
    }

    // --- Section 6: Live Search Suggestions (Room instant first + Datamuse sug & sp) ---
    suspend fun getLiveSuggestions(query: String): List<SearchSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val normalized = trimmed.lowercase(Locale.US)

        val indexedMatches = dao.searchIndexByPrefix(normalized, limit = 10).map {
            SearchSuggestion(
                word = it.word,
                partOfSpeech = mapPosToArabic(it.partOfSpeech),
                summaryDefinition = it.summaryDefinition,
                usAudioUrl = it.usAudioUrl,
                ukAudioUrl = it.ukAudioUrl,
                isLocal = true
            )
        }

        val packMatches = dao.searchOfflinePackByPrefix(normalized, limit = 10).map {
            SearchSuggestion(
                word = it.word,
                partOfSpeech = "",
                summaryDefinition = it.summaryDefinition,
                isLocal = true
            )
        }

        val localMerged = (indexedMatches + packMatches)
            .distinctBy { it.word.lowercase(Locale.US) }

        if (containsArabic(trimmed) || !networkMonitor.isOnline()) {
            return@withContext localMerged
                .sortedWith(prefixFirstComparator(normalized))
                .take(15)
        }

        val remoteWords = coroutineScope {
            val sugDeferred = async {
                withTimeoutOrNull(4_000L) {
                    runCatching { datamuseApi.getAutocompleteSuggestions(normalized, max = 10) }
                        .getOrDefault(emptyList())
                }.orEmpty()
            }
            val spDeferred = async {
                withTimeoutOrNull(4_000L) {
                    runCatching { datamuseApi.getSpellingMatches("$normalized*", max = 10) }
                        .getOrDefault(emptyList())
                }.orEmpty()
            }
            val sugList = sugDeferred.await()
            val spList = spDeferred.await()
            (sugList + spList)
        }

        val remoteSuggestions = remoteWords.mapNotNull { dto ->
            val w = dto.word?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val cachedIndex = dao.getWordIndex(w.lowercase(Locale.US))
            SearchSuggestion(
                word = w,
                partOfSpeech = mapDatamusePos(dto.tags),
                summaryDefinition = cachedIndex?.summaryDefinition.orEmpty(),
                usAudioUrl = cachedIndex?.usAudioUrl,
                ukAudioUrl = cachedIndex?.ukAudioUrl,
                isLocal = cachedIndex != null
            )
        }

        (localMerged + remoteSuggestions)
            .distinctBy { it.word.lowercase(Locale.US) }
            .sortedWith(prefixFirstComparator(normalized))
            .take(15)
    }

    private fun prefixFirstComparator(prefix: String): Comparator<SearchSuggestion> =
        compareByDescending<SearchSuggestion> { it.word.lowercase(Locale.US) == prefix }
            .thenByDescending { it.word.lowercase(Locale.US).startsWith(prefix) }
            .thenByDescending { it.isLocal }

    // --- Section 4: Progressive Deep Word Lookup ---
    fun observeWordEntry(
        rawQuery: String,
        forceRefresh: Boolean = false
    ): Flow<WordLoadState> = channelFlow {
        send(WordLoadState.Loading)

        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            send(WordLoadState.Error("يرجى إدخال كلمة للبحث"))
            return@channelFlow
        }

        var arabicQuery: String? = null
        val targetEnglish: String = if (containsArabic(trimmed)) {
            arabicQuery = trimmed
            val translated = translationManager.translateShortIfReady(
                text = trimmed,
                sourceCode = TranslateLanguage.ARABIC,
                targetCode = TranslateLanguage.ENGLISH
            )
            val enWord = translated.getOrNull()
                ?.trim()
                ?.lowercase(Locale.US)
                ?.replace(Regex("[^a-zA-Z\\s-]"), "")
                ?.trim()
                .orEmpty()
            if (enWord.isBlank()) {
                val missingPack = !translationManager.isPairDownloaded(
                    TranslateLanguage.ARABIC,
                    TranslateLanguage.ENGLISH
                )
                send(
                    WordLoadState.Error(
                        message = if (missingPack) {
                            "للبحث بالعربية، قم بتحميل حزمتي العربية والإنجليزية من شاشة اللغات."
                        } else {
                            "تعذر العثور على مقابل إنجليزي لهذه الكلمة."
                        },
                        isOffline = !networkMonitor.isOnline(),
                        needsLanguagePack = missingPack
                    )
                )
                return@channelFlow
            }
            enWord
        } else {
            trimmed.replace(Regex("\\s+"), " ")
        }

        val normalized = targetEnglish.lowercase(Locale.US)
        val now = System.currentTimeMillis()

        // 1) Read Offline Pack & Room Caches immediately on IO
        var offlinePackEntry: WordEntry? = null
        var freeDictPayload: FreeDictCachedPayload? = null
        var wiktionaryPayload: WiktionaryCachedPayload? = null
        var datamusePayload: DatamuseCachedPayload? = null
        var tatoebaPayload: TatoebaCachedPayload? = null

        var needFreeDict = true
        var needWiktionary = true
        var needDatamuse = true
        var needTatoeba = true

        withContext(Dispatchers.IO) {
            dao.getOfflinePackWord(normalized)?.let { packRow ->
                offlinePackEntry = parseOfflinePackEntity(packRow)
            }
            val cachedRows = dao.getAllSourcesForWord(normalized).associateBy { it.source }

            cachedRows[SRC_FREEDICT]?.let { row ->
                freeDictPayload = decodeOrNull<FreeDictCachedPayload>(row.payloadJson)
                if (!forceRefresh && freeDictPayload != null && (now - row.timestampMs) <= CACHE_STALE_MS) {
                    needFreeDict = false
                }
            }
            cachedRows[SRC_WIKTIONARY]?.let { row ->
                wiktionaryPayload = decodeOrNull<WiktionaryCachedPayload>(row.payloadJson)
                if (!forceRefresh && wiktionaryPayload != null && (now - row.timestampMs) <= CACHE_STALE_MS) {
                    needWiktionary = false
                }
            }
            cachedRows[SRC_DATAMUSE]?.let { row ->
                datamusePayload = decodeOrNull<DatamuseCachedPayload>(row.payloadJson)
                if (!forceRefresh && datamusePayload != null && (now - row.timestampMs) <= CACHE_STALE_MS) {
                    needDatamuse = false
                }
            }
            cachedRows[SRC_TATOEBA]?.let { row ->
                tatoebaPayload = decodeOrNull<TatoebaCachedPayload>(row.payloadJson)
                if (!forceRefresh && tatoebaPayload != null && (now - row.timestampMs) <= CACHE_STALE_MS) {
                    needTatoeba = false
                }
            }
        }

        val online = networkMonitor.isOnline()
        val anyRemoteNeeded = online && (needFreeDict || needWiktionary || needDatamuse || needTatoeba)

        val initialEntry = mergeWordEntry(
            word = normalized,
            offlinePack = offlinePackEntry,
            freeDict = freeDictPayload,
            wiktionary = wiktionaryPayload,
            datamuse = datamusePayload,
            tatoeba = tatoebaPayload,
            isFromCache = (freeDictPayload != null || wiktionaryPayload != null)
        )

        if (initialEntry != null) {
            audioPlayerManager.prefetchWordAudio(
                word = initialEntry.word,
                usAudioUrl = initialEntry.usAudioUrl,
                ukAudioUrl = initialEntry.ukAudioUrl
            )
            send(
                WordLoadState.Success(
                    entry = initialEntry,
                    arabicQuery = arabicQuery,
                    isUpdating = anyRemoteNeeded
                )
            )
        } else if (!online) {
            send(
                WordLoadState.Error(
                    message = "لا يوجد اتصال بالإنترنت، وهذه الكلمة غير موجودة في الذاكرة المحلية أو حزمة الأوفلاين.",
                    isOffline = true
                )
            )
            return@channelFlow
        }

        if (anyRemoteNeeded) {
            val mergeMutex = Mutex()
            suspend fun emitUpdated(isFinal: Boolean = false) {
                val merged = mergeMutex.withLock {
                    mergeWordEntry(
                        word = normalized,
                        offlinePack = offlinePackEntry,
                        freeDict = freeDictPayload,
                        wiktionary = wiktionaryPayload,
                        datamuse = datamusePayload,
                        tatoeba = tatoebaPayload,
                        isFromCache = false
                    )
                }
                if (merged != null) {
                    audioPlayerManager.prefetchWordAudio(
                        word = merged.word,
                        usAudioUrl = merged.usAudioUrl,
                        ukAudioUrl = merged.ukAudioUrl
                    )
                    send(
                        WordLoadState.Success(
                            entry = merged,
                            arabicQuery = arabicQuery,
                            isUpdating = !isFinal
                        )
                    )
                }
            }

            coroutineScope {
                if (needFreeDict) {
                    launch(Dispatchers.IO) {
                        val fetched = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchFreeDictionarySource(normalized)
                        }
                        if (fetched != null) {
                            mergeMutex.withLock { freeDictPayload = fetched }
                            saveSourceCache(normalized, SRC_FREEDICT, json.encodeToString(fetched))
                            emitUpdated()
                        }
                    }
                }
                if (needWiktionary) {
                    launch(Dispatchers.IO) {
                        val fetched = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchWiktionarySource(normalized)
                        }
                        if (fetched != null) {
                            mergeMutex.withLock { wiktionaryPayload = fetched }
                            saveSourceCache(normalized, SRC_WIKTIONARY, json.encodeToString(fetched))
                            emitUpdated()
                        }
                    }
                }
                if (needDatamuse) {
                    launch(Dispatchers.IO) {
                        val fetched = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchDatamuseSource(normalized)
                        }
                        if (fetched != null) {
                            mergeMutex.withLock { datamusePayload = fetched }
                            saveSourceCache(normalized, SRC_DATAMUSE, json.encodeToString(fetched))
                            emitUpdated()
                        }
                    }
                }
                if (needTatoeba) {
                    launch(Dispatchers.IO) {
                        val fetched = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchTatoebaSource(normalized)
                        }
                        if (fetched != null) {
                            mergeMutex.withLock { tatoebaPayload = fetched }
                            saveSourceCache(normalized, SRC_TATOEBA, json.encodeToString(fetched))
                            emitUpdated()
                        }
                    }
                }
            }
        }

        val finalEntry = mergeWordEntry(
            word = normalized,
            offlinePack = offlinePackEntry,
            freeDict = freeDictPayload,
            wiktionary = wiktionaryPayload,
            datamuse = datamusePayload,
            tatoeba = tatoebaPayload,
            isFromCache = !anyRemoteNeeded
        )

        if (finalEntry == null ||
            (finalEntry.posGroups.isEmpty() &&
                finalEntry.synonyms.isEmpty() &&
                finalEntry.tatoebaSentences.isEmpty() &&
                finalEntry.relatedWords.isEmpty())
        ) {
            send(
                WordLoadState.Error(
                    message = "لم يتم العثور على مداخل قاموسية لـ \"$targetEnglish\".",
                    isOffline = !networkMonitor.isOnline()
                )
            )
            return@channelFlow
        }

        withContext(Dispatchers.IO) {
            val firstSense = finalEntry.posGroups.firstOrNull()?.senses?.firstOrNull()
            dao.upsertWordIndex(
                WordIndexEntity(
                    word = normalized,
                    summaryDefinition = firstSense?.definition.orEmpty().take(180),
                    partOfSpeech = firstSense?.partOfSpeech.orEmpty(),
                    usAudioUrl = finalEntry.usAudioUrl,
                    ukAudioUrl = finalEntry.ukAudioUrl,
                    updatedAt = System.currentTimeMillis()
                )
            )
            recordHistory(
                queryKey = (arabicQuery ?: normalized).lowercase(Locale.US),
                displayQuery = arabicQuery ?: normalized,
                resolvedEnglishWord = normalized,
                previewSummary = firstSense?.definition.orEmpty().take(120)
            )
        }

        send(
            WordLoadState.Success(
                entry = finalEntry,
                arabicQuery = arabicQuery,
                isUpdating = false
            )
        )
    }

    // --- Section 4.10: Lazy Easily Confused Words (max 4 concurrent, cached in Room) ---
    suspend fun loadConfusableDetails(
        candidates: List<ConfusableCandidate>
    ): Map<String, ConfusableWordDetail> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyMap()
        val semaphore = Semaphore(4)
        val results = coroutineScope {
            candidates.take(12).map { candidate ->
                async {
                    val keyWord = candidate.word.lowercase(Locale.US)
                    val cacheKey = "$keyWord|$SRC_CONFUSABLE_ENTRY"
                    val cached = dao.getApiCache(cacheKey)
                    val now = System.currentTimeMillis()
                    if (cached != null && (now - cached.timestampMs) <= CACHE_STALE_MS) {
                        val parsed = decodeOrNull<ConfusableEntryCachePayload>(cached.payloadJson)
                        if (parsed != null) {
                            return@async parsed.detail?.copy(labels = candidate.labels)
                        }
                    }

                    // Check offline pack before network
                    val pack = dao.getOfflinePackWord(keyWord)?.let { parseOfflinePackEntity(it) }
                    if (pack != null && pack.posGroups.isNotEmpty()) {
                        val firstSense = pack.posGroups.first().senses.first()
                        val detail = ConfusableWordDetail(
                            word = keyWord,
                            labels = candidate.labels,
                            partOfSpeech = firstSense.partOfSpeechArabic,
                            shortDefinition = firstSense.definition,
                            example = firstSense.examples.firstOrNull().orEmpty(),
                            usAudioUrl = null,
                            ukAudioUrl = null
                        )
                        saveSourceCache(
                            keyWord,
                            SRC_CONFUSABLE_ENTRY,
                            json.encodeToString(ConfusableEntryCachePayload(true, detail))
                        )
                        return@async detail
                    }

                    if (!networkMonitor.isOnline()) return@async null

                    semaphore.withPermit {
                        val freeDict = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchFreeDictionarySource(keyWord)
                        }
                        val detail = if (freeDict != null && !freeDict.notFound && freeDict.senses.isNotEmpty()) {
                            val s = freeDict.senses.first()
                            ConfusableWordDetail(
                                word = keyWord,
                                labels = candidate.labels,
                                partOfSpeech = s.partOfSpeechArabic,
                                shortDefinition = s.definition,
                                example = s.examples.firstOrNull().orEmpty(),
                                usAudioUrl = freeDict.usAudioUrl,
                                ukAudioUrl = freeDict.ukAudioUrl
                            )
                        } else {
                            val wik = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                                fetchWiktionarySource(keyWord)
                            }
                            if (wik != null && !wik.notFound && wik.senses.isNotEmpty()) {
                                val s = wik.senses.first()
                                ConfusableWordDetail(
                                    word = keyWord,
                                    labels = candidate.labels,
                                    partOfSpeech = s.partOfSpeechArabic,
                                    shortDefinition = s.definition,
                                    example = s.examples.firstOrNull().orEmpty(),
                                    usAudioUrl = null,
                                    ukAudioUrl = null
                                )
                            } else {
                                null
                            }
                        }

                        saveSourceCache(
                            keyWord,
                            SRC_CONFUSABLE_ENTRY,
                            json.encodeToString(
                                ConfusableEntryCachePayload(
                                    hasRealEntry = detail != null,
                                    detail = detail
                                )
                            )
                        )
                        detail
                    }
                }
            }.awaitAll()
        }
        results.filterNotNull().associateBy { it.word.lowercase(Locale.US) }
    }

    // --- Source A: Free Dictionary API ---
    private suspend fun fetchFreeDictionarySource(word: String): FreeDictCachedPayload? {
        return try {
            val entries = dictionaryApi.getWordDefinitions(word)
            if (entries.isEmpty()) {
                return FreeDictCachedPayload(notFound = true)
            }
            val (usIpa, ukIpa, usAudio, ukAudio) = extractPhonetics(entries)
            val senses = mutableListOf<SenseItem>()
            val phrases = mutableListOf<String>()
            var idx = 1
            entries.forEach { entry ->
                entry.meanings.orEmpty().forEach { meaning ->
                    val pos = meaning.partOfSpeech?.trim().orEmpty().ifBlank { "word" }
                    val posAr = mapPosToArabic(pos)
                    meaning.definitions.orEmpty().forEach { def ->
                        val defText = def.definition?.trim().orEmpty()
                        if (defText.isNotEmpty()) {
                            val examples = listOfNotNull(def.example?.trim()?.takeIf { it.isNotEmpty() })
                            val syns = def.synonyms.orEmpty().filter { it.isNotBlank() }.distinct()
                            val ants = def.antonyms.orEmpty().filter { it.isNotBlank() }.distinct()
                            senses.add(
                                SenseItem(
                                    index = idx++,
                                    partOfSpeech = pos,
                                    partOfSpeechArabic = posAr,
                                    definition = defText,
                                    examples = examples,
                                    synonyms = syns,
                                    antonyms = ants,
                                    source = SenseSource.FREE_DICTIONARY
                                )
                            )
                            extractPhrasalMentions(defText, word, phrases)
                        }
                    }
                }
            }
            FreeDictCachedPayload(
                notFound = senses.isEmpty(),
                usIpa = usIpa,
                ukIpa = ukIpa,
                usAudioUrl = usAudio,
                ukAudioUrl = ukAudio,
                senses = senses,
                phrases = phrases.distinct()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404) FreeDictCachedPayload(notFound = true) else null
        } catch (e: Exception) {
            Log.w(TAG, "FreeDictionary failed for $word", e)
            null
        }
    }

    // --- Source B: Wiktionary REST API ("en" block only, strip HTML cleanly) ---
    private suspend fun fetchWiktionarySource(word: String): WiktionaryCachedPayload? {
        return try {
            val slug = word.trim().replace(Regex("\\s+"), "_")
            val raw = wiktionaryApi.getDefinitionRaw(slug).string()
            val root = json.parseToJsonElement(raw).jsonObject
            val enArray = root["en"]?.jsonArray ?: return WiktionaryCachedPayload(notFound = true)
            val senses = mutableListOf<SenseItem>()
            val phrases = mutableListOf<String>()
            val derivedWords = mutableListOf<String>()
            var idx = 1

            for (secElem in enArray) {
                val sec = secElem.jsonObject
                val pos = sec["partOfSpeech"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "word" }
                val posAr = mapPosToArabic(pos)
                val defs = sec["definitions"]?.jsonArray ?: continue
                for (defElem in defs) {
                    val defObj = defElem.jsonObject
                    val defHtml = defObj["definition"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val cleanDef = stripHtml(defHtml)
                    if (cleanDef.isBlank()) continue

                    val exList = mutableListOf<String>()
                    defObj["examples"]?.jsonArray?.forEach { exEl ->
                        val cleaned = stripHtml(exEl.jsonPrimitive.contentOrNull.orEmpty())
                        if (cleaned.isNotBlank()) exList.add(cleaned)
                    }
                    defObj["parsedExamples"]?.jsonArray?.forEach { pEl ->
                        val exHtml = pEl.jsonObject["example"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val cleaned = stripHtml(exHtml)
                        if (cleaned.isNotBlank() && cleaned !in exList) exList.add(cleaned)
                    }

                    extractWikiLinksForPhrases(defHtml, word, phrases, derivedWords)

                    senses.add(
                        SenseItem(
                            index = idx++,
                            partOfSpeech = pos,
                            partOfSpeechArabic = posAr,
                            definition = cleanDef,
                            examples = exList.take(3),
                            synonyms = emptyList(),
                            antonyms = emptyList(),
                            source = SenseSource.WIKTIONARY
                        )
                    )
                }
            }
            WiktionaryCachedPayload(
                notFound = senses.isEmpty(),
                senses = senses,
                phrases = phrases.distinct().take(20),
                derivedWords = derivedWords.distinct().take(15)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 404) WiktionaryCachedPayload(notFound = true) else null
        } catch (e: Exception) {
            Log.w(TAG, "Wiktionary failed for $word", e)
            null
        }
    }

    // --- Source C: Datamuse API ---
    private suspend fun fetchDatamuseSource(word: String): DatamuseCachedPayload? {
        return try {
            coroutineScope {
                val synD = async { runCatching { datamuseApi.getSynonyms(word, 30) }.getOrDefault(emptyList()) }
                val antD = async { runCatching { datamuseApi.getAntonyms(word, 30) }.getOrDefault(emptyList()) }
                val mlD = async { runCatching { datamuseApi.getMeansLike(word, max = 30) }.getOrDefault(emptyList()) }
                val trgD = async { runCatching { datamuseApi.getTriggeredWords(word, max = 30) }.getOrDefault(emptyList()) }
                val jjbD = async { runCatching { datamuseApi.getAdjectiveCollocations(word, 30) }.getOrDefault(emptyList()) }
                val jjaD = async { runCatching { datamuseApi.getNounCollocations(word, 30) }.getOrDefault(emptyList()) }
                val bgaD = async { runCatching { datamuseApi.getFollowers(word, 30) }.getOrDefault(emptyList()) }
                val spcD = async { runCatching { datamuseApi.getMoreGeneralTerms(word, 30) }.getOrDefault(emptyList()) }
                val genD = async { runCatching { datamuseApi.getMoreSpecificTerms(word, 30) }.getOrDefault(emptyList()) }
                val homD = async { runCatching { datamuseApi.getHomophones(word, 15) }.getOrDefault(emptyList()) }
                val slD = async { runCatching { datamuseApi.getSoundsLike(word, 15) }.getOrDefault(emptyList()) }
                val spD = async { runCatching { datamuseApi.getSimilarSpelling(word, 15) }.getOrDefault(emptyList()) }
                val famD = async {
                    val stem = word.trim().lowercase(Locale.US)
                    if (stem.length >= 3 && !stem.contains(" ")) {
                        runCatching { datamuseApi.getSpellingMatches("$stem*", max = 20) }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }

                val synonyms = synD.await().mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(30)

                val antonyms = antD.await().mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(30)

                val collocations = (jjbD.await().mapNotNull { it.word?.trim()?.let { adj -> "$adj $word" } } +
                    jjaD.await().mapNotNull { it.word?.trim()?.let { n -> "$word $n" } })
                    .distinct()
                    .take(30)

                val prepositions = bgaD.await().mapNotNull { it.word?.trim()?.lowercase(Locale.US) }
                    .filter { it in EXACT_PARTICLES }
                    .distinct()
                    .map { "$word $it" }

                val generalTerms = spcD.await().mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(20)

                val specificTerms = genD.await().mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(20)

                val relatedWords = (mlD.await() + trgD.await())
                    .mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(30)

                val familyCandidates = famD.await()
                    .mapNotNull { dto ->
                        val w = dto.word?.trim() ?: return@mapNotNull null
                        if (w.equals(word, ignoreCase = true) || w.contains(" ")) return@mapNotNull null
                        val posAr = mapDatamusePos(dto.tags)
                        if (posAr.isBlank()) return@mapNotNull null
                        WordFamilyItem(
                            word = w,
                            partOfSpeech = dto.tags?.firstOrNull().orEmpty(),
                            partOfSpeechArabic = posAr
                        )
                    }
                    .distinctBy { it.word.lowercase(Locale.US) }
                    .take(12)

                val confusableCandidates = buildConfusableCandidates(
                    searchedWord = word,
                    homophones = homD.await().mapNotNull { it.word?.trim() },
                    soundsLike = slD.await().mapNotNull { it.word?.trim() },
                    similarSpelling = spD.await().mapNotNull { it.word?.trim() }
                )

                DatamuseCachedPayload(
                    synonyms = synonyms,
                    antonyms = antonyms,
                    collocations = collocations,
                    prepositions = prepositions,
                    familyCandidates = familyCandidates,
                    confusableCandidates = confusableCandidates,
                    generalTerms = generalTerms,
                    specificTerms = specificTerms,
                    relatedWords = relatedWords
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Datamuse failed for $word", e)
            null
        }
    }

    // --- Source D: Tatoeba API (English + Arabic translations, up to 15) ---
    private suspend fun fetchTatoebaSource(word: String): TatoebaCachedPayload? {
        return try {
            val raw = tatoebaApi.searchSentencesRaw(query = word).string()
            val root = json.parseToJsonElement(raw).jsonObject
            val results = root["results"]?.jsonArray ?: return TatoebaCachedPayload()
            val pairs = mutableListOf<TatoebaSentence>()
            for (elem in results) {
                val obj = elem.jsonObject
                val enText = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (enText.isBlank()) continue
                val arText = extractArabicTranslationFromTatoeba(obj["translations"] as? JsonArray)
                if (arText.isNotBlank()) {
                    pairs.add(TatoebaSentence(english = enText, arabic = arText))
                    if (pairs.size >= 15) break
                }
            }
            TatoebaCachedPayload(sentences = pairs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Tatoeba failed for $word", e)
            null
        }
    }

    private fun extractArabicTranslationFromTatoeba(translationsArray: JsonArray?): String {
        if (translationsArray == null) return ""
        for (outer in translationsArray) {
            when (outer) {
                is JsonArray -> {
                    for (inner in outer) {
                        val obj = inner as? JsonObject ?: continue
                        val lang = obj["lang"]?.jsonPrimitive?.contentOrNull
                        val txt = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        if ((lang == "ara" || lang == "ar" || containsArabic(txt)) && txt.isNotBlank()) {
                            return txt
                        }
                    }
                }
                is JsonObject -> {
                    val lang = outer["lang"]?.jsonPrimitive?.contentOrNull
                    val txt = outer["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if ((lang == "ara" || lang == "ar" || containsArabic(txt)) && txt.isNotBlank()) {
                        return txt
                    }
                }
                else -> Unit
            }
        }
        return ""
    }

    private fun buildConfusableCandidates(
        searchedWord: String,
        homophones: List<String>,
        soundsLike: List<String>,
        similarSpelling: List<String>
    ): List<ConfusableCandidate> {
        val base = searchedWord.trim().lowercase(Locale.US)
        val merged = LinkedHashMap<String, LinkedHashSet<String>>()

        fun addFrom(list: List<String>, label: String) {
            for (raw in list) {
                val candidate = raw.trim().lowercase(Locale.US)
                if (candidate.isBlank() || candidate.contains(" ")) continue
                if (candidate == base || isSimpleInflectionPair(base, candidate)) continue
                merged.getOrPut(candidate) { LinkedHashSet() }.add(label)
            }
        }

        addFrom(homophones, "نفس النطق")
        addFrom(soundsLike, "نطق مشابه")
        addFrom(similarSpelling, "كتابة مشابهة")

        return merged.entries
            .take(12)
            .map { (w, labels) -> ConfusableCandidate(word = w, labels = labels.toList()) }
    }

    private fun isSimpleInflectionPair(a: String, b: String): Boolean {
        return isInflectionOf(a, b) || isInflectionOf(b, a)
    }

    private fun isInflectionOf(base: String, candidate: String): Boolean {
        if (base.length < 3 || candidate.length < 3) return false
        val suffixes = listOf("s", "es", "ed", "d", "ing", "er", "est", "ly")
        if (suffixes.any { candidate == base + it }) return true
        if (base.endsWith("y") && candidate == base.dropLast(1) + "ies") return true
        if (base.endsWith("y") && candidate == base.dropLast(1) + "ied") return true
        if (base.endsWith("e") && candidate == base.dropLast(1) + "ing") return true
        val doubled = base + base.last()
        if (candidate == "${doubled}ed" || candidate == "${doubled}ing" || candidate == "${doubled}er") return true
        return false
    }

    private fun mergeWordEntry(
        word: String,
        offlinePack: WordEntry?,
        freeDict: FreeDictCachedPayload?,
        wiktionary: WiktionaryCachedPayload?,
        datamuse: DatamuseCachedPayload?,
        tatoeba: TatoebaCachedPayload?,
        isFromCache: Boolean
    ): WordEntry? {
        val fdSenses = freeDict?.takeIf { !it.notFound }?.senses.orEmpty()
        val wkSenses = wiktionary?.takeIf { !it.notFound }?.senses.orEmpty()
        val packSenses = offlinePack?.posGroups?.flatMap { it.senses }.orEmpty()

        val combinedSenses = if (fdSenses.isNotEmpty() || wkSenses.isNotEmpty() || packSenses.isNotEmpty()) {
            dedupSenses(fdSenses + wkSenses + packSenses)
        } else {
            emptyList()
        }

        val posGroups = combinedSenses
            .groupBy { it.partOfSpeech.lowercase(Locale.US) }
            .map { (posKey, sensesForPos) ->
                val renumbered = sensesForPos.mapIndexed { i, item -> item.copy(index = i + 1) }
                PosSenseGroup(
                    partOfSpeech = posKey,
                    partOfSpeechArabic = mapPosToArabic(posKey),
                    senses = renumbered
                )
            }

        val allSynonyms = (combinedSenses.flatMap { it.synonyms } +
            datamuse?.synonyms.orEmpty() +
            offlinePack?.synonyms.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val allAntonyms = (combinedSenses.flatMap { it.antonyms } +
            datamuse?.antonyms.orEmpty() +
            offlinePack?.antonyms.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val collocations = (datamuse?.collocations.orEmpty() + offlinePack?.collocations.orEmpty())
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val prepositionsAndPhrases = (datamuse?.prepositions.orEmpty() +
            freeDict?.phrases.orEmpty() +
            wiktionary?.phrases.orEmpty() +
            offlinePack?.prepositionsAndPhrases.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val wkFamily = wiktionary?.derivedWords.orEmpty().map {
            WordFamilyItem(word = it, partOfSpeech = "", partOfSpeechArabic = "مشتق")
        }
        val packFamily = offlinePack?.wordFamily.orEmpty()
        val dmFamily = datamuse?.familyCandidates.orEmpty()
        val wordFamily = (wkFamily + packFamily + dmFamily)
            .distinctBy { it.word.lowercase(Locale.US) }
            .take(12)

        val confusableCandidates = if (datamuse != null && datamuse.confusableCandidates.isNotEmpty()) {
            datamuse.confusableCandidates
        } else {
            offlinePack?.confusableCandidates.orEmpty()
        }

        val tatoebaList = tatoeba?.sentences.orEmpty().take(15)

        val hasAnyData = posGroups.isNotEmpty() ||
            allSynonyms.isNotEmpty() ||
            allAntonyms.isNotEmpty() ||
            tatoebaList.isNotEmpty() ||
            datamuse?.relatedWords?.isNotEmpty() == true

        if (!hasAnyData) return null

        return WordEntry(
            word = word,
            usIpa = freeDict?.usIpa.orEmpty().ifBlank { offlinePack?.usIpa.orEmpty() },
            ukIpa = freeDict?.ukIpa.orEmpty(),
            usAudioUrl = freeDict?.usAudioUrl,
            ukAudioUrl = freeDict?.ukAudioUrl,
            posGroups = posGroups,
            synonyms = allSynonyms,
            antonyms = allAntonyms,
            collocations = collocations,
            prepositionsAndPhrases = prepositionsAndPhrases,
            wordFamily = wordFamily,
            confusableCandidates = confusableCandidates,
            generalTerms = datamuse?.generalTerms.orEmpty(),
            specificTerms = datamuse?.specificTerms.orEmpty(),
            relatedWords = datamuse?.relatedWords.orEmpty(),
            tatoebaSentences = tatoebaList,
            hasWiktionaryAttribution = wkSenses.isNotEmpty(),
            hasTatoebaAttribution = tatoebaList.isNotEmpty(),
            isFromOfflinePack = offlinePack != null,
            isFromCache = isFromCache
        )
    }

    private fun dedupSenses(raw: List<SenseItem>): List<SenseItem> {
        val seenDefs = HashSet<String>()
        val result = ArrayList<SenseItem>(raw.size)
        for (sense in raw) {
            val normDef = sense.definition.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
            if (normDef.length < 3) continue
            val key = "${sense.partOfSpeech.lowercase(Locale.US)}|$normDef"
            if (seenDefs.add(key)) {
                result.add(sense)
            }
        }
        return result
    }

    private fun parseOfflinePackEntity(entity: OfflinePackWordEntity): WordEntry? {
        return try {
            val root = JSONObject(entity.payloadJson)
            val word = root.optString("word", entity.word).trim().lowercase(Locale.US)
            val phonetic = root.optString("phonetic", entity.phonetic).trim()
            val rawSenses = mutableListOf<SenseItem>()
            val syns = mutableListOf<String>()
            val ants = mutableListOf<String>()

            val sensesArr = root.optJSONArray("senses")
            if (sensesArr != null) {
                for (i in 0 until sensesArr.length()) {
                    val sObj = sensesArr.optJSONObject(i) ?: continue
                    val pos = sObj.optString("partOfSpeech", "word").trim().ifBlank { "word" }
                    val def = sObj.optString("definition", "").trim()
                    if (def.isBlank()) continue

                    val exList = readJsonStringList(sObj.optJSONArray("examples"))
                    val sSyns = readJsonStringList(sObj.optJSONArray("synonyms"))
                    val sAnts = readJsonStringList(sObj.optJSONArray("antonyms"))
                    syns.addAll(sSyns)
                    ants.addAll(sAnts)

                    rawSenses.add(
                        SenseItem(
                            index = rawSenses.size + 1,
                            partOfSpeech = pos,
                            partOfSpeechArabic = mapPosToArabic(pos),
                            definition = def,
                            examples = exList,
                            synonyms = sSyns,
                            antonyms = sAnts,
                            source = SenseSource.OFFLINE_PACK
                        )
                    )
                }
            }

            val posGroups = rawSenses
                .groupBy { it.partOfSpeech.lowercase(Locale.US) }
                .map { (posKey, list) ->
                    PosSenseGroup(
                        partOfSpeech = posKey,
                        partOfSpeechArabic = mapPosToArabic(posKey),
                        senses = list.mapIndexed { idx, s -> s.copy(index = idx + 1) }
                    )
                }

            val collocations = readJsonStringList(root.optJSONArray("collocations"))
            val phrases = readJsonStringList(root.optJSONArray("phrases"))
            val family = readJsonStringList(root.optJSONArray("family")).map {
                WordFamilyItem(word = it, partOfSpeech = "", partOfSpeechArabic = "")
            }
            val confusables = readJsonStringList(root.optJSONArray("confusables")).map {
                ConfusableCandidate(word = it, labels = listOf("كتابة/معنى متقارب"))
            }

            WordEntry(
                word = word,
                usIpa = phonetic,
                posGroups = posGroups,
                synonyms = syns.distinct().take(30),
                antonyms = ants.distinct().take(30),
                collocations = collocations,
                prepositionsAndPhrases = phrases,
                wordFamily = family,
                confusableCandidates = confusables,
                isFromOfflinePack = true,
                isFromCache = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing offline pack row for ${entity.word}", e)
            null
        }
    }

    private fun readJsonStringList(arr: org.json.JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim()
            if (s.isNotEmpty()) list.add(s)
        }
        return list
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

        // Section 3: STRICTLY match "-us" for US and "-uk" for UK, ignoring empty strings.
        val usPhonetic = phonetics.firstOrNull {
            val audio = it.audio?.trim().orEmpty()
            audio.isNotEmpty() && audio.lowercase(Locale.US).contains("-us")
        }
        val ukPhonetic = phonetics.firstOrNull {
            val audio = it.audio?.trim().orEmpty()
            audio.isNotEmpty() && audio.lowercase(Locale.US).contains("-uk")
        }

        return PhoneticsTuple(
            usIpa = usPhonetic?.text?.trim()?.takeIf { it.isNotBlank() } ?: fallbackIpa,
            ukIpa = ukPhonetic?.text?.trim()?.takeIf { it.isNotBlank() } ?: fallbackIpa,
            usAudio = usPhonetic?.audio?.trim()?.takeIf { it.isNotBlank() },
            ukAudio = ukPhonetic?.audio?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace('\uFFFC', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractPhrasalMentions(defText: String, baseWord: String, out: MutableList<String>) {
        val regex = Regex("\\b${Regex.escape(baseWord)}\\s+(${EXACT_PARTICLES.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
        regex.findAll(defText).forEach { m ->
            val phrase = m.value.lowercase(Locale.US).trim()
            if (phrase.isNotEmpty()) out.add(phrase)
        }
    }

    private fun extractWikiLinksForPhrases(
        html: String,
        baseWord: String,
        phrasesOut: MutableList<String>,
        derivedOut: MutableList<String>
    ) {
        val linkRegex = Regex("title=\"([^\"]+)\"")
        linkRegex.findAll(html).forEach { m ->
            val title = m.groupValues[1].trim()
            if (title.isBlank() || title.contains(":") || title.contains("#")) return@forEach
            val lower = title.lowercase(Locale.US)
            if (lower.startsWith("${baseWord.lowercase(Locale.US)} ") && lower.length <= 32) {
                phrasesOut.add(lower)
            } else if (!lower.contains(" ") &&
                lower != baseWord.lowercase(Locale.US) &&
                baseWord.length >= 4 &&
                lower.startsWith(baseWord.lowercase(Locale.US).take(4)) &&
                lower.all { it.isLetter() }
            ) {
                derivedOut.add(lower)
            }
        }
    }

    private suspend fun saveSourceCache(word: String, source: String, payloadJson: String) {
        runCatching {
            dao.upsertApiCache(
                ApiCacheEntity(
                    cacheKey = "$word|$source",
                    word = word,
                    source = source,
                    payloadJson = payloadJson,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun recordHistory(
        queryKey: String,
        displayQuery: String,
        resolvedEnglishWord: String,
        previewSummary: String
    ) {
        dao.upsertHistory(
            SearchHistoryEntity(
                queryKey = queryKey,
                displayQuery = displayQuery,
                resolvedEnglishWord = resolvedEnglishWord,
                previewSummary = previewSummary,
                timestamp = System.currentTimeMillis()
            )
        )
        dao.trimHistoryTo50()
    }

    private inline fun <reified T> decodeOrNull(raw: String): T? =
        runCatching { json.decodeFromString<T>(raw) }.getOrNull()

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
        "interjection" -> "صيغة تعجب"
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
