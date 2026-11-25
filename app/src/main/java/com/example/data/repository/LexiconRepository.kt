package com.example.data.repository

import android.os.Process
import android.util.Log
import android.util.LruCache
import com.example.core.Accent
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.ContextualTranslationAnalyzer
import com.example.core.LinguisticRanker
import com.example.core.NetworkMonitor
import com.example.core.TranslationManager
import com.example.data.local.ApiCacheEntity
import com.example.data.local.FavoriteWordEntity
import com.example.data.local.LexiconDao
import com.example.data.local.OfflinePackWordEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.TranslationCacheEntity
import com.example.data.local.WordIndexEntity
import com.example.data.remote.DatamuseApi
import com.example.data.remote.DatamuseWordDto
import com.example.data.remote.DictionaryEntryDto
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.TatoebaApi
import com.example.data.remote.WikimediaCommonsApi
import com.example.data.remote.WiktionaryApi
import com.example.domain.model.AwlWordItem
import com.example.domain.model.ConfusableCandidate
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.DetectedIdiom
import com.example.domain.model.InstantTranslationResult
import com.example.domain.model.PhraseDetailItem
import com.example.domain.model.PhraseSheetData
import com.example.domain.model.PosSenseGroup
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SenseItem
import com.example.domain.model.SenseSource
import com.example.domain.model.SentenceLookupResult
import com.example.domain.model.TatoebaSentence
import com.example.domain.model.WordEntry
import com.example.domain.model.WordFamilyItem
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
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
import java.security.MessageDigest
import java.util.Locale

private const val TAG = "LexiconRepository"
private const val CACHE_STALE_MS = 7L * 24L * 60L * 60L * 1000L
private const val SOURCE_TIMEOUT_MS = 8_000L

private const val SRC_FREEDICT = "freedict"
private const val SRC_WIKTIONARY = "wiktionary_v2"
private const val SRC_WIKTIONARY_AR = "wiktionary_ar_v2"
private const val SRC_DATAMUSE = "datamuse"
private const val SRC_TATOEBA = "tatoeba"
private const val SRC_TATOEBA_AR = "tatoeba_ar"
private const val SRC_CONFUSABLE_ENTRY = "confusable_entry"
private const val SRC_PHRASE_DETAIL = "phrase_detail_v2"
private const val SRC_WIKIMEDIA_AUDIO = "wikimedia_audio"

private val EXACT_PARTICLES = setOf(
    "at", "after", "for", "up", "down", "out", "in", "on", "off", "over",
    "into", "with", "about", "through", "back", "away", "around", "by", "to",
    "from", "of", "against", "along", "across", "behind", "between", "beyond",
    "under", "onto", "towards", "within", "without"
)

private val COMMON_STOPWORDS = setOf(
    "the", "a", "an", "and", "or", "but", "if", "in", "on", "at", "to", "for",
    "of", "with", "by", "from", "as", "is", "are", "was", "were", "be", "been",
    "being", "have", "has", "had", "do", "does", "did", "will", "would", "can",
    "could", "should", "may", "might", "that", "this", "these", "those", "it",
    "its", "he", "she", "they", "we", "you", "i", "me", "my", "his", "her",
    "their", "our", "your", "not", "no", "so", "than", "then", "very", "just"
)

sealed interface WordLoadState {
    data object Loading : WordLoadState
    data class Success(
        val entry: WordEntry,
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
private data class CachedArabicGloss(
    val senseGloss: String,
    val arabicTerms: List<String> = emptyList()
)

@Serializable
private data class WiktionaryCachedPayload(
    val notFound: Boolean = false,
    val senses: List<SenseItem> = emptyList(),
    val phrases: List<String> = emptyList(),
    val phrasalVerbs: List<String> = emptyList(),
    val idioms: List<String> = emptyList(),
    val compounds: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val derivedWords: List<String> = emptyList(),
    val relatedTerms: List<String> = emptyList(),
    val arabicGlosses: List<CachedArabicGloss> = emptyList(),
    val vocalizedHeadwords: List<String> = emptyList(),
    val transliteration: String = ""
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
    val relatedWords: List<String> = emptyList(),
    val wordNetSenses: List<SenseItem> = emptyList()
)

@Serializable
private data class WikimediaAudioCachedPayload(
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null
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
    private val networkMonitor: NetworkMonitor,
    private val wikimediaCommonsApi: WikimediaCommonsApi? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Section 18.4: Bounded 20-entry in-memory LruCache of ready-to-render WordEntry models
    private val readyEntryCache = object : LruCache<String, WordEntry>(20) {}
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val recentHistory: Flow<List<SearchHistoryEntity>> = dao.observeRecentHistory()
    val favorites: Flow<List<FavoriteWordEntity>> = dao.observeFavorites()
    val cachedCount: Flow<Int> = dao.observeCachedCount()
    val offlinePackCount: Flow<Int> = dao.observeOfflinePackCount()
    val isOnlineFlow: Flow<Boolean> = networkMonitor.isOnlineFlow

    fun observeIsFavorite(word: String): Flow<Boolean> =
        dao.observeIsFavorite(ArabicTextUtils.normalizeForKey(word))

    suspend fun toggleFavorite(entry: WordEntry, currentlyFavorite: Boolean) {
        val key = entry.word
        if (currentlyFavorite) {
            dao.deleteFavorite(key)
        } else {
            val firstSense = entry.posGroups.firstOrNull()?.senses?.firstOrNull()
            dao.upsertFavorite(
                FavoriteWordEntity(
                    word = entry.displayWord,
                    summaryText = firstSense?.definition.orEmpty()
                        .ifBlank { entry.machineTranslations.firstOrNull().orEmpty() }
                        .take(160),
                    usIpa = if (entry.isArabicWord) "" else entry.usIpa,
                    partOfSpeech = entry.posGroups.firstOrNull()?.partOfSpeechArabic.orEmpty()
                )
            )
        }
    }

    suspend fun removeFavorite(word: String) {
        dao.deleteFavorite(word.trim())
        dao.deleteFavorite(ArabicTextUtils.normalizeForKey(word))
    }

    suspend fun deleteHistoryItem(queryKey: String) {
        dao.deleteHistoryItem(queryKey)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    fun observeAwlWords(): Flow<List<AwlWordItem>> =
        dao.observeAwlOfflinePackWords().map { rows ->
            if (rows.isEmpty()) return@map emptyList()
            val indexMap = HashMap<String, WordIndexEntity>(rows.size)
            rows.map { it.normalizedWord }.chunked(500).forEach { chunk ->
                dao.getWordIndexesByNormalized(chunk).forEach { idx ->
                    indexMap[idx.normalizedWord] = idx
                }
            }
            rows.map { row ->
                val idx = indexMap[row.normalizedWord]
                AwlWordItem(
                    word = row.word,
                    phonetic = row.phonetic,
                    summaryDefinition = row.summaryDefinition,
                    usAudioUrl = idx?.usAudioUrl,
                    ukAudioUrl = idx?.ukAudioUrl
                )
            }
        }

    suspend fun clearApiCache() {
        synchronized(readyEntryCache) {
            readyEntryCache.evictAll()
        }
        dao.clearApiCache()
        dao.clearWordIndex()
        dao.clearTranslationCache()
    }

    // --- Section 18.3: Background Prefetch of Top Suggestion ---
    fun prefetchWord(rawWord: String) {
        val trimmed = rawWord.trim()
        if (trimmed.isEmpty() || ArabicTextUtils.isMultiWordQuery(trimmed)) return
        val isArabic = ArabicTextUtils.containsArabicLetters(trimmed)
        val normalized = if (isArabic) {
            ArabicTextUtils.normalizeArabicForMatching(trimmed)
        } else {
            trimmed.lowercase(Locale.US)
        }
        val memKey = if (isArabic) {
            if (ArabicTextUtils.hasArabicDiacritics(trimmed)) {
                "ar_v:${ArabicTextUtils.canonicalizeArabicWithDiacritics(trimmed)}"
            } else {
                "ar:$normalized"
            }
        } else {
            "en:$normalized"
        }
        synchronized(readyEntryCache) {
            if (readyEntryCache.get(memKey) != null) return
        }

        prefetchScope.launch {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
            try {
                if (isArabic) {
                    val wikKey = "$normalized|$SRC_WIKTIONARY_AR"
                    if (dao.getApiCache(wikKey) == null && networkMonitor.isOnline()) {
                        val slug = ArabicTextUtils.stripArabicDiacriticsKeepHamza(trimmed)
                        val wik = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchArabicWiktionarySource(slug, normalized, trimmed)
                        }
                        if (wik != null) {
                            saveSourceCache(normalized, SRC_WIKTIONARY_AR, json.encodeToString(wik))
                        }
                    }
                } else {
                    val fdKey = "$normalized|$SRC_FREEDICT"
                    if (dao.getApiCache(fdKey) == null && networkMonitor.isOnline()) {
                        val fd = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchFreeDictionarySource(normalized)
                        }
                        if (fd != null) {
                            saveSourceCache(normalized, SRC_FREEDICT, json.encodeToString(fd))
                            audioPlayerManager.prefetchWordAudio(normalized, fd.usAudioUrl, fd.ukAudioUrl)
                        }
                    }
                }
            } catch (_: Exception) {
                // Silent background prefetch
            }
        }
    }

    // --- Section 6 & 15.5: Live Search Suggestions (Arabic: Room only; English: Room + Datamuse) ---
    suspend fun getLiveSuggestions(query: String): List<SearchSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val isArabic = ArabicTextUtils.containsArabicLetters(trimmed)
        val normalized = if (isArabic) {
            ArabicTextUtils.normalizeArabicForMatching(trimmed)
        } else {
            trimmed.lowercase(Locale.US)
        }

        val indexedMatches = dao.searchIndexByPrefix(normalized, limit = 10).map {
            val itemIsAr = it.lang == "ar" || ArabicTextUtils.containsArabicLetters(it.word)
            SearchSuggestion(
                word = it.word,
                partOfSpeech = mapPosToArabic(it.partOfSpeech),
                summaryDefinition = it.summaryDefinition,
                usAudioUrl = if (itemIsAr) null else it.usAudioUrl,
                ukAudioUrl = if (itemIsAr) null else it.ukAudioUrl,
                isArabic = itemIsAr,
                isLocal = true
            )
        }

        val packMatches = dao.searchOfflinePackByPrefix(normalized, limit = 10).map {
            val itemIsAr = it.lang == "ar" || ArabicTextUtils.containsArabicLetters(it.word)
            SearchSuggestion(
                word = it.word,
                partOfSpeech = "",
                summaryDefinition = it.summaryDefinition,
                isArabic = itemIsAr,
                isLocal = true
            )
        }

        val historyMatches = if (isArabic) {
            dao.searchHistoryByPrefix(normalized, limit = 8).mapNotNull { h ->
                if (!ArabicTextUtils.containsArabicLetters(h.displayQuery)) return@mapNotNull null
                SearchSuggestion(
                    word = h.displayQuery,
                    partOfSpeech = "",
                    summaryDefinition = h.previewSummary,
                    isArabic = true,
                    isLocal = true
                )
            }
        } else {
            emptyList()
        }

        val localMerged = (indexedMatches + packMatches + historyMatches)
            .distinctBy { ArabicTextUtils.normalizeForKey(it.word) }

        // Section 15.5: Datamuse is English-only -> NEVER call it for Arabic input
        if (isArabic || !networkMonitor.isOnline()) {
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
                isArabic = false,
                isLocal = cachedIndex != null
            )
        }

        (localMerged + remoteSuggestions)
            .distinctBy { it.word.lowercase(Locale.US) }
            .sortedWith(prefixFirstComparator(normalized))
            .take(15)
    }

    private fun prefixFirstComparator(prefix: String): Comparator<SearchSuggestion> =
        compareByDescending<SearchSuggestion> { ArabicTextUtils.normalizeForKey(it.word) == prefix }
            .thenByDescending { ArabicTextUtils.normalizeForKey(it.word).startsWith(prefix) }
            .thenByDescending { it.isLocal }

    // --- BUG 6: Live Instant Translation under Search Bar (cached first, non-blocking) ---
    suspend fun instantTranslateForSearch(rawQuery: String): InstantTranslationResult {
        val cleaned = rawQuery.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) {
            return InstantTranslationResult(query = "", isLoading = false)
        }
        val isArabic = ArabicTextUtils.containsArabicLetters(cleaned)
        val srcLang = if (isArabic) TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH
        val tgtLang = if (isArabic) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC
        val normalized = ArabicTextUtils.normalizeForKey(cleaned)
        val isSingleWord = !ArabicTextUtils.isMultiWordQuery(cleaned)

        var usAudio: String? = null
        var ukAudio: String? = null
        var cachedTranslation: String? = null

        val literalMode = !isSingleWord && translationManager.isLiteralTranslationEnabled()
        val cacheScope = if (literalMode) "lit|$srcLang" else "whole|$srcLang"

        withContext(Dispatchers.IO) {
            if (isSingleWord) {
                val idx = dao.getWordIndex(normalized)
                if (idx != null) {
                    if (!isArabic) {
                        usAudio = idx.usAudioUrl
                        ukAudio = idx.ukAudioUrl
                    }
                    if (idx.summaryDefinition.isNotBlank() &&
                        ((!isArabic && ArabicTextUtils.containsArabicLetters(idx.summaryDefinition)) ||
                            (isArabic && !ArabicTextUtils.containsArabicLetters(idx.summaryDefinition)))
                    ) {
                        cachedTranslation = idx.summaryDefinition
                    }
                }
                if (cachedTranslation.isNullOrBlank()) {
                    val pack = dao.getOfflinePackWord(normalized)
                    if (pack != null) {
                        val parsedPack = parseOfflinePackEntity(pack)
                        val firstAr = parsedPack?.posGroups?.firstOrNull()?.senses?.firstOrNull()?.definitionArabic
                        if (!isArabic && !firstAr.isNullOrBlank()) {
                            cachedTranslation = firstAr
                        } else if (pack.summaryDefinition.isNotBlank() &&
                            ((!isArabic && ArabicTextUtils.containsArabicLetters(pack.summaryDefinition)) ||
                                (isArabic && !ArabicTextUtils.containsArabicLetters(pack.summaryDefinition)))
                        ) {
                            cachedTranslation = pack.summaryDefinition
                        }
                    }
                }
            }
            if (cachedTranslation.isNullOrBlank()) {
                val roomKey = translationCacheKey(cacheScope, tgtLang, cleaned)
                cachedTranslation = dao.getTranslationCacheBatch(listOf(roomKey))
                    .firstOrNull()?.translatedText?.takeIf { it.isNotBlank() }
            }
        }

        if (!cachedTranslation.isNullOrBlank()) {
            return InstantTranslationResult(
                query = cleaned,
                translation = cachedTranslation,
                isArabicSource = isArabic,
                isMachine = true,
                missingPack = false,
                usAudioUrl = usAudio,
                ukAudioUrl = ukAudio,
                isLoading = false
            )
        }

        val packReady = withContext(Dispatchers.IO) {
            translationManager.isPairDownloaded(srcLang, tgtLang)
        }
        if (!packReady) {
            return InstantTranslationResult(
                query = cleaned,
                translation = null,
                isArabicSource = isArabic,
                isMachine = true,
                missingPack = true,
                usAudioUrl = usAudio,
                ukAudioUrl = ukAudio,
                isLoading = false
            )
        }

        val mt = withContext(Dispatchers.Default) {
            translationManager.translateShortIfReady(
                text = cleaned,
                sourceCode = srcLang,
                targetCode = tgtLang
            ).getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        }

        if (!mt.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                val roomKey = translationCacheKey(cacheScope, tgtLang, cleaned)
                dao.upsertTranslationCacheBatch(
                    listOf(TranslationCacheEntity(roomKey, mt, System.currentTimeMillis()))
                )
            }
        }

        return InstantTranslationResult(
            query = cleaned,
            translation = mt,
            isArabicSource = isArabic,
            isMachine = true,
            missingPack = false,
            usAudioUrl = usAudio,
            ukAudioUrl = ukAudio,
            isLoading = false
        )
    }

    // --- BUG 3: Background & Cached Chip Meanings + Fallback Bottom Sheet ---
    suspend fun getCachedChipMeanings(phrases: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        val cleaned = phrases.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) return@withContext emptyMap()

        val result = HashMap<String, String>(cleaned.size)
        val remaining = ArrayList<String>()

        val normalizedList = cleaned.map { it.lowercase(Locale.US) }.distinct()
        val indexMap = HashMap<String, WordIndexEntity>()
        normalizedList.chunked(400).forEach { chunk ->
            dao.getWordIndexesByNormalized(chunk).forEach { idx ->
                indexMap[idx.normalizedWord] = idx
            }
        }

        for (phrase in cleaned) {
            val isAr = ArabicTextUtils.containsArabicLetters(phrase)
            val norm = phrase.lowercase(Locale.US)
            val idx = indexMap[norm]
            val idxDef = idx?.summaryDefinition?.trim().orEmpty()
            val matchesTarget = idxDef.isNotBlank() &&
                ((!isAr && ArabicTextUtils.containsArabicLetters(idxDef)) ||
                    (isAr && !ArabicTextUtils.containsArabicLetters(idxDef)))
            if (matchesTarget) {
                result[norm] = idxDef
            } else {
                remaining.add(phrase)
            }
        }

        if (remaining.isNotEmpty()) {
            val keyToPhrase = remaining.associateBy { phrase ->
                if (ArabicTextUtils.containsArabicLetters(phrase)) {
                    translationCacheKey("whole|${TranslateLanguage.ARABIC}", TranslateLanguage.ENGLISH, phrase)
                } else {
                    translationCacheKey("whole|${TranslateLanguage.ENGLISH}", TranslateLanguage.ARABIC, phrase)
                }
            }
            keyToPhrase.keys.chunked(400).forEach { chunk ->
                dao.getTranslationCacheBatch(chunk).forEach { row ->
                    val src = keyToPhrase[row.cacheKey] ?: return@forEach
                    if (row.translatedText.isNotBlank()) {
                        result[src.lowercase(Locale.US)] = row.translatedText
                    }
                }
            }
        }
        result
    }

    suspend fun fetchMissingChipMeanings(
        phrases: List<String>,
        alreadyKnown: Map<String, String>
    ): Map<String, String> {
        val missingAll = phrases.map { it.trim() }
            .filter { it.isNotBlank() && alreadyKnown[it.lowercase(Locale.US)].isNullOrBlank() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(80)
        if (missingAll.isEmpty()) return emptyMap()

        val enMissing = missingAll.filter { !ArabicTextUtils.containsArabicLetters(it) }
        val arMissing = missingAll.filter { ArabicTextUtils.containsArabicLetters(it) }

        val mergedOut = HashMap<String, String>()
        val cacheEntities = ArrayList<TranslationCacheEntity>()
        val now = System.currentTimeMillis()

        if (enMissing.isNotEmpty()) {
            val packReady = withContext(Dispatchers.IO) {
                translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
            }
            if (packReady) {
                val translated = translationManager.translateBatchIfReady(
                    texts = enMissing,
                    sourceCode = TranslateLanguage.ENGLISH,
                    targetCode = TranslateLanguage.ARABIC,
                    concurrency = 4
                )
                translated.forEach { (src, ar) ->
                    mergedOut[src.lowercase(Locale.US)] = ar
                    cacheEntities.add(
                        TranslationCacheEntity(
                            cacheKey = translationCacheKey("whole|${TranslateLanguage.ENGLISH}", TranslateLanguage.ARABIC, src),
                            translatedText = ar,
                            timestampMs = now
                        )
                    )
                }
            }
        }

        if (arMissing.isNotEmpty()) {
            val packReady = withContext(Dispatchers.IO) {
                translationManager.isPairDownloaded(TranslateLanguage.ARABIC, TranslateLanguage.ENGLISH)
            }
            if (packReady) {
                val translated = translationManager.translateBatchIfReady(
                    texts = arMissing,
                    sourceCode = TranslateLanguage.ARABIC,
                    targetCode = TranslateLanguage.ENGLISH,
                    concurrency = 4
                )
                translated.forEach { (src, en) ->
                    mergedOut[src.lowercase(Locale.US)] = en
                    cacheEntities.add(
                        TranslationCacheEntity(
                            cacheKey = translationCacheKey("whole|${TranslateLanguage.ARABIC}", TranslateLanguage.ENGLISH, src),
                            translatedText = en,
                            timestampMs = now
                        )
                    )
                }
            }
        }

        if (cacheEntities.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                cacheEntities.chunked(200).forEach { batch ->
                    dao.upsertTranslationCacheBatch(batch)
                }
            }
        }
        return mergedOut
    }

    suspend fun inspectChipTap(rawPhrase: String): Pair<Boolean, PhraseSheetData> {
        val phrase = rawPhrase.trim().replace(Regex("\\s+"), " ")
        val isArabic = ArabicTextUtils.containsArabicLetters(phrase)
        val hasEntry = checkKnownPhraseEntryExists(phrase, isArabic)
        if (hasEntry) {
            return true to PhraseSheetData(phrase = phrase, isLoading = false)
        }

        val srcLang = if (isArabic) TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH
        val tgtLang = if (isArabic) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC
        val normalized = ArabicTextUtils.normalizeForKey(phrase)

        var meaning: String? = null
        var missingPack = false
        var usAudio: String? = null
        var ukAudio: String? = null

        withContext(Dispatchers.IO) {
            val idx = dao.getWordIndex(normalized)
            if (!isArabic) {
                usAudio = idx?.usAudioUrl
                ukAudio = idx?.ukAudioUrl
            }
            val roomKey = translationCacheKey("whole|$srcLang", tgtLang, phrase)
            meaning = dao.getTranslationCacheBatch(listOf(roomKey))
                .firstOrNull()?.translatedText?.takeIf { it.isNotBlank() }
        }

        if (meaning.isNullOrBlank()) {
            val res = withContext(Dispatchers.Default) {
                translationManager.translateShortIfReady(phrase, srcLang, tgtLang)
            }
            if (res.isSuccess) {
                meaning = res.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                if (!meaning.isNullOrBlank()) {
                    withContext(Dispatchers.IO) {
                        val roomKey = translationCacheKey("whole|$srcLang", tgtLang, phrase)
                        dao.upsertTranslationCacheBatch(
                            listOf(TranslationCacheEntity(roomKey, meaning!!, System.currentTimeMillis()))
                        )
                    }
                }
            } else {
                missingPack = !withContext(Dispatchers.IO) {
                    translationManager.isPairDownloaded(srcLang, tgtLang)
                }
            }
        }

        // Fetch one real example sentence from Tatoeba if online
        val tatoebaExample = withContext(Dispatchers.IO) {
            if (!networkMonitor.isOnline()) null
            else {
                withTimeoutOrNull(4_500L) {
                    if (isArabic) {
                        fetchTatoebaArabicToEnglish(ArabicTextUtils.stripArabicDiacriticsKeepHamza(phrase))
                            ?.sentences?.firstOrNull()
                    } else {
                        fetchTatoebaSource(phrase)?.sentences?.firstOrNull()
                    }
                }
            }
        }

        return false to PhraseSheetData(
            phrase = phrase,
            arabicMeaning = meaning,
            isArabicMachine = true,
            missingArabicPack = missingPack,
            example = tatoebaExample?.english.orEmpty(),
            exampleArabic = tatoebaExample?.arabic,
            exampleIsMachine = false,
            exampleAudioUrl = tatoebaExample?.audioUrl,
            usAudioUrl = usAudio,
            ukAudioUrl = ukAudio,
            isLoading = false
        )
    }

    // --- Sections 4, 14 & BUG 4: Sentence & Idiom Recognition (Both Directions) ---
    fun observeSentenceLookup(rawText: String): Flow<SentenceLookupResult> = channelFlow {
        val cleaned = rawText.trim().replace(Regex("\\s+"), " ")
        val isArabic = ArabicTextUtils.containsArabicLetters(cleaned)
        val wordTokens = ArabicTextUtils.extractWordTokens(cleaned)
        val wordsCount = wordTokens.size

        send(
            SentenceLookupResult(
                originalText = cleaned,
                isArabicInput = isArabic,
                wordChips = wordTokens,
                isUpdating = true
            )
        )

        var machineTranslation: String? = null
        var missingPack = false
        var tatoebaList: List<TatoebaSentence> = emptyList()
        var directPhraseFound = false
        var detectedIdioms: List<DetectedIdiom> = emptyList()

        val srcLang = if (isArabic) TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH
        val tgtLang = if (isArabic) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC

        coroutineScope {
            val mtDeferred = async(Dispatchers.IO) {
                val res = translationManager.translateShortIfReady(
                    text = cleaned,
                    sourceCode = srcLang,
                    targetCode = tgtLang
                )
                if (res.isSuccess) {
                    res.getOrNull()?.takeIf { it.isNotBlank() } to false
                } else {
                    null to !translationManager.isPairDownloaded(srcLang, tgtLang)
                }
            }

            val phraseCheckDeferred = async(Dispatchers.IO) {
                if (wordsCount in 2..7) {
                    checkKnownPhraseEntryExists(cleaned, isArabic)
                } else {
                    false
                }
            }

            val idiomScanDeferred = async(Dispatchers.IO) {
                val localContextual = ContextualTranslationAnalyzer.detectKnownIdiomsInSentence(cleaned, isArabic)
                val remoteWiki = if (!isArabic && wordsCount >= 3) {
                    detectWiktionaryIdiomsInSentence(cleaned, wordTokens)
                } else {
                    emptyList()
                }
                (localContextual + remoteWiki)
                    .distinctBy { it.phrase.lowercase(Locale.US) }
                    .take(6)
            }

            val tatoebaDeferred = async(Dispatchers.IO) {
                if (!networkMonitor.isOnline()) return@async emptyList()
                withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    val queryForTatoeba = wordTokens.take(4).joinToString(" ")
                    if (isArabic) {
                        fetchTatoebaArabicToEnglish(queryForTatoeba)?.sentences.orEmpty().take(3)
                    } else {
                        fetchTatoebaSource(queryForTatoeba)?.sentences.orEmpty().take(3)
                    }
                }.orEmpty()
            }

            val (mt, missing) = mtDeferred.await()
            machineTranslation = mt
            missingPack = missing
            directPhraseFound = phraseCheckDeferred.await()
            detectedIdioms = if (directPhraseFound) emptyList() else idiomScanDeferred.await()
            tatoebaList = tatoebaDeferred.await()
        }

        send(
            SentenceLookupResult(
                originalText = cleaned,
                isArabicInput = isArabic,
                machineTranslation = machineTranslation,
                missingTranslationPack = missingPack,
                wordChips = wordTokens,
                detectedIdioms = detectedIdioms,
                tatoebaSentences = tatoebaList,
                isUpdating = false,
                directPhraseEntryAvailable = directPhraseFound
            )
        )
    }

    private suspend fun detectWiktionaryIdiomsInSentence(
        sentence: String,
        tokens: List<String>
    ): List<DetectedIdiom> {
        val rawTokens = sentence.trim().split(Regex("\\s+"))
            .map { it.trim('.', ',', '!', '?', '"', '\'', '(', ')', ':', ';') }
            .filter { it.isNotBlank() }
        if (rawTokens.size < 2) return emptyList()

        val candidates = LinkedHashSet<String>()
        for (len in minOf(5, rawTokens.size) downTo 2) {
            for (start in 0..(rawTokens.size - len)) {
                val slice = rawTokens.subList(start, start + len)
                if (slice.all { it.lowercase(Locale.US) in COMMON_STOPWORDS }) continue
                // Require at least one non-stopword
                if (slice.none { it.lowercase(Locale.US) !in COMMON_STOPWORDS }) continue
                val phrase = slice.joinToString(" ").lowercase(Locale.US)
                if (phrase.length in 5..45) {
                    candidates.add(phrase)
                }
            }
        }
        // Also prioritize candidates if any cached word entry lists them
        val prioritized = ArrayList<String>()
        for (tok in tokens.take(4)) {
            val cachedWik = dao.getApiCache("${tok.lowercase(Locale.US)}|$SRC_WIKTIONARY")
                ?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
            if (cachedWik != null) {
                val lowerSentence = sentence.lowercase(Locale.US)
                (cachedWik.idioms + cachedWik.phrasalVerbs + cachedWik.phrases).forEach { ph ->
                    if (ph.contains(" ") && lowerSentence.contains(ph.lowercase(Locale.US))) {
                        prioritized.add(ph.lowercase(Locale.US))
                    }
                }
            }
        }

        val scanList = (prioritized + candidates.toList())
            .distinct()
            .take(8)
        if (scanList.isEmpty()) return emptyList()

        val semaphore = Semaphore(4)
        val isOnline = networkMonitor.isOnline()
        val packReady = translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)

        return coroutineScope {
            scanList.map { phrase ->
                async {
                    semaphore.withPermit {
                        val wikKey = "$phrase|$SRC_WIKTIONARY"
                        var wik = dao.getApiCache(wikKey)?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
                        if (wik == null && isOnline) {
                            wik = withTimeoutOrNull(4_000L) { fetchWiktionarySource(phrase) }
                            if (wik != null) {
                                saveSourceCache(phrase, SRC_WIKTIONARY, json.encodeToString(wik))
                            }
                        }
                        val validWik = wik?.takeIf { !it.notFound && it.senses.isNotEmpty() } ?: return@async null
                        val firstSense = validWik.senses.first()
                        val arabicMeaning = firstSense.definitionArabic?.takeIf { it.isNotBlank() }
                            ?: if (packReady) {
                                val roomKey = translationCacheKey("whole|${TranslateLanguage.ENGLISH}", TranslateLanguage.ARABIC, phrase)
                                dao.getTranslationCacheBatch(listOf(roomKey)).firstOrNull()?.translatedText
                                    ?: translationManager.translateShortIfReady(
                                        "$phrase: ${firstSense.definition}",
                                        TranslateLanguage.ENGLISH,
                                        TranslateLanguage.ARABIC
                                    ).getOrNull()?.let { extractContextualSenseArabic(it, phrase) }
                            } else null
                        DetectedIdiom(
                            phrase = phrase,
                            englishMeaning = firstSense.definition,
                            arabicMeaning = arabicMeaning,
                            isArabicMachine = firstSense.definitionArabic.isNullOrBlank(),
                            example = firstSense.examples.firstOrNull().orEmpty()
                        )
                    }
                }
            }.awaitAll().filterNotNull().distinctBy { it.phrase.lowercase(Locale.US) }.take(4)
        }
    }

    private suspend fun checkKnownPhraseEntryExists(phrase: String, isArabic: Boolean): Boolean {
        val key = ArabicTextUtils.normalizeForKey(phrase)
        if (dao.getOfflinePackWord(key) != null) return true
        if (dao.getWordIndex(key) != null) return true

        val cachedWik = dao.getApiCache("$key|$SRC_WIKTIONARY")?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
        if (cachedWik != null && !cachedWik.notFound && cachedWik.senses.isNotEmpty()) return true
        val cachedFd = dao.getApiCache("$key|$SRC_FREEDICT")?.let { decodeOrNull<FreeDictCachedPayload>(it.payloadJson) }
        if (cachedFd != null && !cachedFd.notFound && cachedFd.senses.isNotEmpty()) return true

        if (!networkMonitor.isOnline()) return false

        return if (isArabic) {
            val slug = ArabicTextUtils.stripArabicDiacriticsKeepHamza(phrase)
            val wik = withTimeoutOrNull(4_500L) { fetchArabicWiktionarySource(slug, key, phrase) }
            wik != null && !wik.notFound && wik.senses.isNotEmpty()
        } else {
            val wik = withTimeoutOrNull(4_500L) { fetchWiktionarySource(key) }
            if (wik != null && !wik.notFound && wik.senses.isNotEmpty()) {
                saveSourceCache(key, SRC_WIKTIONARY, json.encodeToString(wik))
                return true
            }
            val fd = withTimeoutOrNull(4_500L) { fetchFreeDictionarySource(key) }
            if (fd != null && !fd.notFound && fd.senses.isNotEmpty()) {
                saveSourceCache(key, SRC_FREEDICT, json.encodeToString(fd))
                return true
            }
            false
        }
    }

    // --- Sections 4, 13, 15, 18: Bidirectional Deep Word Lookup ---
    fun observeWordEntry(
        rawQuery: String,
        forceRefresh: Boolean = false
    ): Flow<WordLoadState> = channelFlow {
        val trimmed = rawQuery.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) {
            send(WordLoadState.Error("يرجى إدخال كلمة للبحث"))
            return@channelFlow
        }

        val isArabic = ArabicTextUtils.containsArabicLetters(trimmed)
        if (isArabic) {
            observeArabicToEnglishWord(trimmed, forceRefresh, ::send)
        } else {
            observeEnglishToArabicWord(trimmed, forceRefresh, ::send)
        }
    }

    private suspend fun observeArabicToEnglishWord(
        displayWord: String,
        forceRefresh: Boolean,
        emitState: suspend (WordLoadState) -> Unit
    ) {
        val slugWithHamza = ArabicTextUtils.stripArabicDiacriticsKeepHamza(displayWord)
        val normalized = ArabicTextUtils.normalizeArabicForMatching(displayWord)
        val hasExplicitTashkeel = ArabicTextUtils.hasArabicDiacritics(displayWord)
        val canonicalExact = ArabicTextUtils.canonicalizeArabicWithDiacritics(displayWord)
        val memKey = if (hasExplicitTashkeel) "ar_v:$canonicalExact" else "ar:$normalized"

        if (!forceRefresh) {
            val memCached = synchronized(readyEntryCache) { readyEntryCache.get(memKey) }
            if (memCached != null) {
                emitState(WordLoadState.Success(memCached.copy(displayWord = displayWord, isFromCache = true), isUpdating = false))
                return
            }
        }

        emitState(WordLoadState.Loading)

        val now = System.currentTimeMillis()
        var offlinePackEntry: WordEntry? = null
        var wiktionaryPayload: WiktionaryCachedPayload? = null
        var tatoebaPayload: TatoebaCachedPayload? = null
        var needWiktionary = true
        var needTatoeba = true

        withContext(Dispatchers.IO) {
            val packRow = dao.getOfflinePackWordByLang(slugWithHamza, "ar")
                ?: dao.getOfflinePackWordByLang(normalized, "ar")
                ?: dao.getOfflinePackWord(normalized)
            if (packRow != null) {
                offlinePackEntry = parseOfflinePackEntity(packRow)
            }

            val cachedRows = dao.getAllSourcesForWord(normalized).associateBy { it.source }
            cachedRows[SRC_WIKTIONARY_AR]?.let { row ->
                wiktionaryPayload = decodeOrNull<WiktionaryCachedPayload>(row.payloadJson)
                if (!forceRefresh && wiktionaryPayload != null && (now - row.timestampMs) <= CACHE_STALE_MS) {
                    needWiktionary = false
                }
            }
            cachedRows[SRC_TATOEBA_AR]?.let { row ->
                tatoebaPayload = decodeOrNull<TatoebaCachedPayload>(row.payloadJson)
                if (!forceRefresh && tatoebaPayload != null && (now - row.timestampMs) <= CACHE_STALE_MS) {
                    needTatoeba = false
                }
            }
        }

        // ML Kit ar -> en quick translation (runs locally on-device)
        val mlKitResult = withContext(Dispatchers.Default) {
            val res = translationManager.translateShortIfReady(
                text = displayWord,
                sourceCode = TranslateLanguage.ARABIC,
                targetCode = TranslateLanguage.ENGLISH
            )
            if (res.isSuccess) {
                val rawMt = res.getOrNull().orEmpty().trim()
                val chips = rawMt.split(Regex("[,;/]"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !ArabicTextUtils.containsArabicLetters(it) }
                    .distinctBy { it.lowercase(Locale.US) }
                chips to false
            } else {
                emptyList<String>() to !translationManager.isPairDownloaded(
                    TranslateLanguage.ARABIC,
                    TranslateLanguage.ENGLISH
                )
            }
        }
        val machineChips = mlKitResult.first
        val missingPack = mlKitResult.second

        val online = networkMonitor.isOnline()
        val anyRemoteNeeded = online && (needWiktionary || needTatoeba)

        val initialEntry = mergeArabicWordEntry(
            normalizedKey = normalized,
            displayWord = displayWord,
            canonicalExact = canonicalExact,
            hasExplicitTashkeel = hasExplicitTashkeel,
            offlinePack = offlinePackEntry,
            wiktionary = wiktionaryPayload,
            tatoeba = tatoebaPayload,
            machineTranslations = machineChips,
            missingTranslationPack = missingPack,
            isFromCache = wiktionaryPayload != null
        )

        if (initialEntry != null) {
            emitState(WordLoadState.Success(initialEntry, isUpdating = anyRemoteNeeded))
        } else if (!online && missingPack && machineChips.isEmpty()) {
            emitState(
                WordLoadState.Error(
                    message = "لا يوجد اتصال بالإنترنت، وحزمة العربية/الإنجليزية غير محمّلة للترجمة المحلية.",
                    isOffline = true,
                    needsLanguagePack = true
                )
            )
            return
        }

        if (anyRemoteNeeded) {
            val mergeMutex = Mutex()
            suspend fun emitUpdated(isFinal: Boolean = false) {
                val merged = withContext(Dispatchers.Default) {
                    mergeMutex.withLock {
                        mergeArabicWordEntry(
                            normalizedKey = normalized,
                            displayWord = displayWord,
                            canonicalExact = canonicalExact,
                            hasExplicitTashkeel = hasExplicitTashkeel,
                            offlinePack = offlinePackEntry,
                            wiktionary = wiktionaryPayload,
                            tatoeba = tatoebaPayload,
                            machineTranslations = machineChips,
                            missingTranslationPack = missingPack,
                            isFromCache = false
                        )
                    }
                }
                if (merged != null) {
                    emitState(WordLoadState.Success(merged, isUpdating = !isFinal))
                }
            }

            coroutineScope {
                if (needWiktionary) {
                    launch(Dispatchers.IO) {
                        val fetched = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchArabicWiktionarySource(slugWithHamza, normalized, displayWord)
                        }
                        if (fetched != null) {
                            mergeMutex.withLock { wiktionaryPayload = fetched }
                            saveSourceCache(normalized, SRC_WIKTIONARY_AR, json.encodeToString(fetched))
                            emitUpdated()
                        }
                    }
                }
                if (needTatoeba) {
                    launch(Dispatchers.IO) {
                        val fetched = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchTatoebaArabicToEnglish(slugWithHamza)
                        }
                        if (fetched != null) {
                            mergeMutex.withLock { tatoebaPayload = fetched }
                            saveSourceCache(normalized, SRC_TATOEBA_AR, json.encodeToString(fetched))
                            emitUpdated()
                        }
                    }
                }
            }
        }

        val finalEntry = withContext(Dispatchers.Default) {
            mergeArabicWordEntry(
                normalizedKey = normalized,
                displayWord = displayWord,
                canonicalExact = canonicalExact,
                hasExplicitTashkeel = hasExplicitTashkeel,
                offlinePack = offlinePackEntry,
                wiktionary = wiktionaryPayload,
                tatoeba = tatoebaPayload,
                machineTranslations = machineChips,
                missingTranslationPack = missingPack,
                isFromCache = !anyRemoteNeeded
            )
        }

        if (finalEntry == null) {
            emitState(
                WordLoadState.Error(
                    message = if (missingPack) {
                        "لا توجد مداخل قاموسية لهذه الكلمة، كما أن حزمة العربية/الإنجليزية غير محمّلة."
                    } else {
                        "لم يتم العثور على مداخل قاموسية لـ \"$displayWord\"."
                    },
                    isOffline = !networkMonitor.isOnline(),
                    needsLanguagePack = missingPack
                )
            )
            return
        }

        synchronized(readyEntryCache) {
            readyEntryCache.put(memKey, finalEntry)
        }

        withContext(Dispatchers.IO) {
            val firstSense = finalEntry.posGroups.firstOrNull()?.senses?.firstOrNull()
            val summary = firstSense?.definitionArabic?.takeIf { it.isNotBlank() }
                ?: firstSense?.definition.orEmpty()
                    .ifBlank { finalEntry.machineTranslations.joinToString(" • ") }
                    .take(180)
            dao.upsertWordIndex(
                WordIndexEntity(
                    word = finalEntry.displayWord.ifBlank { slugWithHamza },
                    normalizedWord = normalized,
                    lang = "ar",
                    summaryDefinition = summary,
                    partOfSpeech = firstSense?.partOfSpeech.orEmpty(),
                    usAudioUrl = null,
                    ukAudioUrl = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            recordHistory(
                queryKey = if (hasExplicitTashkeel) canonicalExact else normalized,
                normalizedQuery = normalized,
                displayQuery = finalEntry.displayWord.ifBlank { displayWord },
                resolvedWord = finalEntry.displayWord.ifBlank { displayWord },
                previewSummary = summary.take(120)
            )
        }

        emitState(WordLoadState.Success(finalEntry, isUpdating = false))
    }

    private suspend fun observeEnglishToArabicWord(
        trimmed: String,
        forceRefresh: Boolean,
        emitState: suspend (WordLoadState) -> Unit
    ) {
        val normalized = trimmed.lowercase(Locale.US)
        val memKey = "en:$normalized"
        val isPackReadyNow = withContext(Dispatchers.IO) {
            translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
        }

        if (!forceRefresh) {
            val memCached = synchronized(readyEntryCache) { readyEntryCache.get(memKey) }
            if (memCached != null && (!memCached.missingTranslationPack || !isPackReadyNow)) {
                audioPlayerManager.prefetchWordAudio(memCached.word, memCached.usAudioUrl, memCached.ukAudioUrl)
                emitState(WordLoadState.Success(memCached.copy(isFromCache = true), isUpdating = false))
                return
            }
        }

        emitState(WordLoadState.Loading)

        val now = System.currentTimeMillis()
        var offlinePackEntry: WordEntry? = null
        var freeDictPayload: FreeDictCachedPayload? = null
        var wiktionaryPayload: WiktionaryCachedPayload? = null
        var datamusePayload: DatamuseCachedPayload? = null
        var tatoebaPayload: TatoebaCachedPayload? = null
        var wikimediaAudioPayload: WikimediaAudioCachedPayload? = null

        var needFreeDict = true
        var needWiktionary = true
        var needDatamuse = true
        var needTatoeba = true
        var needWikimediaAudio = wikimediaCommonsApi != null

        withContext(Dispatchers.IO) {
            val packRow = dao.getOfflinePackWordByLang(normalized, "en") ?: dao.getOfflinePackWord(normalized)
            packRow?.let { offlinePackEntry = parseOfflinePackEntity(it) }

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
            cachedRows[SRC_WIKIMEDIA_AUDIO]?.let { row ->
                wikimediaAudioPayload = decodeOrNull<WikimediaAudioCachedPayload>(row.payloadJson)
                if (!forceRefresh && wikimediaAudioPayload != null && (now - row.timestampMs) <= CACHE_STALE_MS) {
                    needWikimediaAudio = false
                }
            }
        }

        // Section 15.3 & 19: ML Kit quick en -> ar translation of headword when pack is downloaded
        val mtHeadwordResult = withContext(Dispatchers.Default) {
            val res = translationManager.translateShortIfReady(
                text = trimmed,
                sourceCode = TranslateLanguage.ENGLISH,
                targetCode = TranslateLanguage.ARABIC
            )
            if (res.isSuccess) {
                val ar = res.getOrNull().orEmpty().trim()
                (if (ar.isNotBlank()) listOf(ar) else emptyList()) to false
            } else {
                emptyList<String>() to !isPackReadyNow
            }
        }

        val online = networkMonitor.isOnline()
        val cachedMissingAudio = (freeDictPayload?.usAudioUrl.isNullOrBlank() || freeDictPayload?.ukAudioUrl.isNullOrBlank())
        val shouldTryWikimedia = online && wikimediaCommonsApi != null && needWikimediaAudio && (needFreeDict || cachedMissingAudio)
        val anyRemoteNeeded = online && (needFreeDict || needWiktionary || needDatamuse || needTatoeba || shouldTryWikimedia)

        val initialRaw = withContext(Dispatchers.Default) {
            mergeWordEntry(
                word = normalized,
                offlinePack = offlinePackEntry,
                freeDict = freeDictPayload,
                wiktionary = wiktionaryPayload,
                datamuse = datamusePayload,
                tatoeba = tatoebaPayload,
                wikimediaAudio = wikimediaAudioPayload,
                machineTranslations = mtHeadwordResult.first,
                missingTranslationPack = mtHeadwordResult.second,
                isFromCache = (freeDictPayload != null || wiktionaryPayload != null)
            )
        }
        val initialEntry = initialRaw?.let { applyRoomTranslationCache(it) }

        if (initialEntry != null) {
            audioPlayerManager.prefetchWordAudio(
                word = initialEntry.word,
                usAudioUrl = initialEntry.usAudioUrl,
                ukAudioUrl = initialEntry.ukAudioUrl
            )
            emitState(
                WordLoadState.Success(
                    entry = initialEntry,
                    isUpdating = anyRemoteNeeded || isPackReadyNow
                )
            )
        } else if (!online) {
            emitState(
                WordLoadState.Error(
                    message = "لا يوجد اتصال بالإنترنت، وهذه الكلمة غير موجودة في الذاكرة المحلية أو حزمة الأوفلاين.",
                    isOffline = true
                )
            )
            return
        }

        if (anyRemoteNeeded) {
            val mergeMutex = Mutex()
            suspend fun emitUpdated() {
                val merged = withContext(Dispatchers.Default) {
                    mergeMutex.withLock {
                        mergeWordEntry(
                            word = normalized,
                            offlinePack = offlinePackEntry,
                            freeDict = freeDictPayload,
                            wiktionary = wiktionaryPayload,
                            datamuse = datamusePayload,
                            tatoeba = tatoebaPayload,
                            wikimediaAudio = wikimediaAudioPayload,
                            machineTranslations = mtHeadwordResult.first,
                            missingTranslationPack = mtHeadwordResult.second,
                            isFromCache = false
                        )
                    }
                }
                if (merged != null) {
                    val cachedMerged = applyRoomTranslationCache(merged)
                    audioPlayerManager.prefetchWordAudio(
                        word = cachedMerged.word,
                        usAudioUrl = cachedMerged.usAudioUrl,
                        ukAudioUrl = cachedMerged.ukAudioUrl
                    )
                    emitState(
                        WordLoadState.Success(
                            entry = cachedMerged,
                            isUpdating = true
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
                        // Fallback to Wikimedia Commons for any missing US/UK human audio
                        if (wikimediaCommonsApi != null && needWikimediaAudio) {
                            val needUs = fetched?.usAudioUrl.isNullOrBlank()
                            val needUk = fetched?.ukAudioUrl.isNullOrBlank()
                            if (needUs || needUk) {
                                val wikiAudio = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                                    fetchWikimediaAudioForWord(normalized, needUs = needUs, needUk = needUk)
                                }
                                if (wikiAudio != null) {
                                    mergeMutex.withLock { wikimediaAudioPayload = wikiAudio }
                                    saveSourceCache(normalized, SRC_WIKIMEDIA_AUDIO, json.encodeToString(wikiAudio))
                                    emitUpdated()
                                }
                            }
                        }
                    }
                } else if (shouldTryWikimedia) {
                    launch(Dispatchers.IO) {
                        val needUs = freeDictPayload?.usAudioUrl.isNullOrBlank()
                        val needUk = freeDictPayload?.ukAudioUrl.isNullOrBlank()
                        if (needUs || needUk) {
                            val wikiAudio = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                                fetchWikimediaAudioForWord(normalized, needUs = needUs, needUk = needUk)
                            }
                            if (wikiAudio != null) {
                                mergeMutex.withLock { wikimediaAudioPayload = wikiAudio }
                                saveSourceCache(normalized, SRC_WIKIMEDIA_AUDIO, json.encodeToString(wikiAudio))
                                emitUpdated()
                            }
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

        val mergedFinal = withContext(Dispatchers.Default) {
            mergeWordEntry(
                word = normalized,
                offlinePack = offlinePackEntry,
                freeDict = freeDictPayload,
                wiktionary = wiktionaryPayload,
                datamuse = datamusePayload,
                tatoeba = tatoebaPayload,
                wikimediaAudio = wikimediaAudioPayload,
                machineTranslations = mtHeadwordResult.first,
                missingTranslationPack = mtHeadwordResult.second,
                isFromCache = !anyRemoteNeeded
            )
        }

        if (mergedFinal == null ||
            (mergedFinal.posGroups.isEmpty() &&
                mergedFinal.synonyms.isEmpty() &&
                mergedFinal.tatoebaSentences.isEmpty() &&
                mergedFinal.relatedWords.isEmpty() &&
                mergedFinal.machineTranslations.isEmpty())
        ) {
            emitState(
                WordLoadState.Error(
                    message = "لم يتم العثور على مداخل قاموسية لـ \"$trimmed\".",
                    isOffline = !networkMonitor.isOnline()
                )
            )
            return
        }

        val withCachedTranslations = applyRoomTranslationCache(mergedFinal)
        // Show dictionary content immediately before batch translating uncached senses/examples
        if (isPackReadyNow && needsBatchTranslation(withCachedTranslations)) {
            emitState(WordLoadState.Success(entry = withCachedTranslations, isUpdating = true))
        }

        val finalEntry = if (isPackReadyNow) {
            translateMissingSensesInBatch(withCachedTranslations)
        } else {
            withCachedTranslations
        }

        synchronized(readyEntryCache) {
            readyEntryCache.put(memKey, finalEntry)
        }

        withContext(Dispatchers.IO) {
            val firstSense = finalEntry.posGroups.firstOrNull()?.senses?.firstOrNull()
            dao.upsertWordIndex(
                WordIndexEntity(
                    word = normalized,
                    normalizedWord = normalized,
                    lang = "en",
                    summaryDefinition = firstSense?.definitionArabic?.takeIf { it.isNotBlank() }
                        ?: firstSense?.definition.orEmpty().take(180),
                    partOfSpeech = firstSense?.partOfSpeech.orEmpty(),
                    usAudioUrl = finalEntry.usAudioUrl,
                    ukAudioUrl = finalEntry.ukAudioUrl,
                    updatedAt = System.currentTimeMillis()
                )
            )
            recordHistory(
                queryKey = normalized,
                normalizedQuery = normalized,
                displayQuery = normalized,
                resolvedWord = normalized,
                previewSummary = firstSense?.definitionArabic?.takeIf { it.isNotBlank() }
                    ?: firstSense?.definition.orEmpty()
                        .ifBlank { finalEntry.machineTranslations.firstOrNull().orEmpty() }
                        .take(120)
            )
        }

        emitState(
            WordLoadState.Success(
                entry = finalEntry,
                isUpdating = false
            )
        )
    }

    private fun translationCacheKey(sourceLang: String, targetLang: String, text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return "$sourceLang|$targetLang|$digest"
    }

    private fun contextualDefPrompt(word: String, definition: String): String =
        "${word.trim()}: ${definition.trim()}"

    private fun contextualDefCacheKey(word: String, definition: String): String =
        translationCacheKey("ctx|${word.trim().lowercase(Locale.US)}|${TranslateLanguage.ENGLISH}", TranslateLanguage.ARABIC, definition)

    private fun extractContextualSenseArabic(rawTranslated: String, headword: String): String {
        val trimmed = rawTranslated.trim()
        val colonIdx = trimmed.indexOf(':').takeIf { it > 0 } ?: trimmed.indexOf('：')
        if (colonIdx in 1..32) {
            val prefix = trimmed.substring(0, colonIdx).trim()
            val after = trimmed.substring(colonIdx + 1).trim()
            // Strip prefix if it echoes the headword (or short single-word translation of headword)
            if (after.isNotBlank() && (prefix.equals(headword, ignoreCase = true) || prefix.split(Regex("\\s+")).size <= 2)) {
                return after
            }
        }
        return trimmed
    }

    private suspend fun applyRoomTranslationCache(entry: WordEntry): WordEntry {
        if (entry.isArabicWord || entry.posGroups.isEmpty()) return entry
        val defKeys = HashMap<String, String>() // cacheKey -> def
        val exKeys = HashMap<String, String>()  // cacheKey -> ex
        entry.posGroups.forEach { group ->
            group.senses.forEach { sense ->
                if (sense.definitionArabic.isNullOrBlank() &&
                    !ArabicTextUtils.containsArabicLetters(sense.definition)
                ) {
                    val def = sense.definition.trim()
                    defKeys[contextualDefCacheKey(entry.word, def)] = def
                }
                sense.examples.forEach { ex ->
                    if (!sense.exampleTranslations.containsKey(ex) &&
                        !ArabicTextUtils.containsArabicLetters(ex)
                    ) {
                        val trimmedEx = ex.trim()
                        exKeys[translationCacheKey(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC, trimmedEx)] = trimmedEx
                    }
                }
            }
        }
        if (defKeys.isEmpty() && exKeys.isEmpty()) return entry

        val cachedDefMap = HashMap<String, String>()
        val cachedExMap = HashMap<String, String>()
        withContext(Dispatchers.IO) {
            (defKeys.keys + exKeys.keys).distinct().chunked(400).forEach { chunk ->
                dao.getTranslationCacheBatch(chunk).forEach { entity ->
                    if (entity.translatedText.isNotBlank()) {
                        defKeys[entity.cacheKey]?.let { srcDef ->
                            cachedDefMap[srcDef] = entity.translatedText
                        }
                        exKeys[entity.cacheKey]?.let { srcEx ->
                            cachedExMap[srcEx] = entity.translatedText
                        }
                    }
                }
            }
        }
        if (cachedDefMap.isEmpty() && cachedExMap.isEmpty()) return entry

        return applyTranslationsMapToEntry(entry, cachedDefMap, cachedExMap)
    }

    private fun needsBatchTranslation(entry: WordEntry): Boolean {
        return entry.posGroups.any { group ->
            group.senses.any { sense ->
                (sense.definitionArabic.isNullOrBlank() &&
                    !ArabicTextUtils.containsArabicLetters(sense.definition)) ||
                    sense.examples.any { ex ->
                        !sense.exampleTranslations.containsKey(ex) &&
                            !ArabicTextUtils.containsArabicLetters(ex)
                    }
            }
        }
    }

    private suspend fun translateMissingSensesInBatch(entry: WordEntry): WordEntry {
        if (entry.isArabicWord || entry.posGroups.isEmpty()) return entry
        val missingDefs = LinkedHashSet<String>()
        val missingExamples = LinkedHashSet<String>()
        entry.posGroups.forEach { group ->
            group.senses.forEach { sense ->
                if (sense.definitionArabic.isNullOrBlank() &&
                    !ArabicTextUtils.containsArabicLetters(sense.definition)
                ) {
                    missingDefs.add(sense.definition.trim())
                }
                sense.examples.forEach { ex ->
                    if (!sense.exampleTranslations.containsKey(ex) &&
                        !ArabicTextUtils.containsArabicLetters(ex)
                    ) {
                        missingExamples.add(ex.trim())
                    }
                }
            }
        }
        if (missingDefs.isEmpty() && missingExamples.isEmpty()) return entry

        // BUG 5: Translate "word: definition" together for per-sense context, and WHOLE sentence as one unit
        val promptToDef = missingDefs.associateBy { contextualDefPrompt(entry.word, it) }
        val batchInputs = promptToDef.keys.toList() + missingExamples.toList()

        val rawBatchResults = translationManager.translateBatchIfReady(
            texts = batchInputs,
            sourceCode = TranslateLanguage.ENGLISH,
            targetCode = TranslateLanguage.ARABIC,
            concurrency = 4
        )
        if (rawBatchResults.isEmpty()) return entry

        val defMap = HashMap<String, String>()
        val exMap = HashMap<String, String>()
        val cacheEntities = ArrayList<TranslationCacheEntity>()
        val now = System.currentTimeMillis()

        promptToDef.forEach { (prompt, rawDef) ->
            val rawAr = rawBatchResults[prompt]?.trim()
            if (!rawAr.isNullOrBlank()) {
                val cleanedAr = extractContextualSenseArabic(rawAr, entry.word)
                defMap[rawDef] = cleanedAr
                cacheEntities.add(
                    TranslationCacheEntity(
                        cacheKey = contextualDefCacheKey(entry.word, rawDef),
                        translatedText = cleanedAr,
                        timestampMs = now
                    )
                )
            }
        }

        missingExamples.forEach { rawEx ->
            val rawAr = rawBatchResults[rawEx]?.trim()
            if (!rawAr.isNullOrBlank() && ArabicTextUtils.containsArabicLetters(rawAr)) {
                exMap[rawEx] = rawAr
                cacheEntities.add(
                    TranslationCacheEntity(
                        cacheKey = translationCacheKey(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC, rawEx),
                        translatedText = rawAr,
                        timestampMs = now
                    )
                )
            }
        }

        if (cacheEntities.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                cacheEntities.chunked(200).forEach { batch ->
                    dao.upsertTranslationCacheBatch(batch)
                }
            }
        }

        return applyTranslationsMapToEntry(entry, defMap, exMap).copy(missingTranslationPack = false)
    }

    private fun applyTranslationsMapToEntry(
        entry: WordEntry,
        defTranslatedMap: Map<String, String>,
        exTranslatedMap: Map<String, String>
    ): WordEntry {
        val updatedGroups = entry.posGroups.map { group ->
            val updatedSenses = group.senses.map { sense ->
                val defAr = if (!sense.definitionArabic.isNullOrBlank()) {
                    sense.definitionArabic
                } else {
                    defTranslatedMap[sense.definition.trim()]
                }
                val defIsMachine = if (!sense.definitionArabic.isNullOrBlank()) {
                    sense.definitionArabicIsMachine
                } else {
                    defAr != null
                }

                val newExTrans = HashMap(sense.exampleTranslations)
                val newExIsMachine = HashMap(sense.exampleIsMachine)
                sense.examples.forEach { ex ->
                    if (!newExTrans.containsKey(ex)) {
                        val tr = exTranslatedMap[ex.trim()]
                        if (!tr.isNullOrBlank()) {
                            newExTrans[ex] = tr
                            newExIsMachine[ex] = true
                        }
                    }
                }

                sense.copy(
                    definitionArabic = defAr,
                    definitionArabicIsMachine = defIsMachine,
                    exampleTranslations = newExTrans,
                    exampleIsMachine = newExIsMachine
                )
            }
            group.copy(senses = updatedSenses)
        }
        return entry.copy(posGroups = updatedGroups)
    }

    // --- Section 4.10 & 18.3: Lazy/Background Confusable Words (max 4 concurrent, cached in Room) ---
    suspend fun loadConfusableDetails(
        candidates: List<ConfusableCandidate>,
        maxCount: Int = 12
    ): Map<String, ConfusableWordDetail> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyMap()
        val semaphore = Semaphore(4)
        val results = coroutineScope {
            candidates.take(maxCount).map { candidate ->
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
        val baseMap = results.filterNotNull().associateBy { it.word.lowercase(Locale.US) }
        val packReady = translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
        if (!packReady || baseMap.isEmpty()) return@withContext baseMap

        val defsToTranslate = baseMap.values
            .filter { it.shortDefinitionArabic.isNullOrBlank() && it.shortDefinition.isNotBlank() }
            .associateBy { contextualDefPrompt(it.word, it.shortDefinition) }
        if (defsToTranslate.isEmpty()) return@withContext baseMap

        val translated = translationManager.translateBatchIfReady(
            texts = defsToTranslate.keys.toList(),
            sourceCode = TranslateLanguage.ENGLISH,
            targetCode = TranslateLanguage.ARABIC,
            concurrency = 4
        )
        baseMap.mapValues { (_, detail) ->
            val prompt = contextualDefPrompt(detail.word, detail.shortDefinition)
            val ar = translated[prompt]?.let { extractContextualSenseArabic(it, detail.word) }
            if (!ar.isNullOrBlank()) {
                detail.copy(shortDefinitionArabic = ar, shortDefinitionArabicIsMachine = true)
            } else {
                detail
            }
        }
    }

    // --- BUG 4 & Linguistic Upgrade: Phrasal Verbs, Idioms & Compound Expressions ---
    suspend fun loadPhrasalAndIdiomDetails(
        phrases: List<String>,
        baseWord: String = "",
        idiomSet: Set<String> = emptySet(),
        phrasalSet: Set<String> = emptySet(),
        compoundSet: Set<String> = emptySet(),
        maxCount: Int = 18
    ): List<PhraseDetailItem> = withContext(Dispatchers.IO) {
        val candidates = phrases.map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") }
            .distinctBy { it.lowercase(Locale.US) }
            .take(maxCount)
        if (candidates.isEmpty()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val semaphore = Semaphore(4)
        val isOnline = networkMonitor.isOnline()
        val packReady = translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
        val curatedMap = ContextualTranslationAnalyzer.getCuratedPhrasesForHeadword(baseWord, isArabic = false)
            .associateBy { it.phrase.lowercase(Locale.US) }

        coroutineScope {
            candidates.map { phrase ->
                async {
                    val key = phrase.lowercase(Locale.US)
                    val cacheKey = "$key|$SRC_PHRASE_DETAIL"
                    val cached = dao.getApiCache(cacheKey)
                    if (cached != null && (now - cached.timestampMs) <= CACHE_STALE_MS) {
                        val decoded = decodeOrNull<PhraseDetailItem>(cached.payloadJson)
                        if (decoded != null && (!decoded.arabicMeaning.isNullOrBlank() || !packReady)) {
                            return@async decoded
                        }
                    }

                    val category = when {
                        key in compoundSet -> "COMPOUND"
                        else -> LinguisticRanker.classifyEnglishPhrase(
                            phrase = phrase,
                            headword = baseWord,
                            explicitPhrasals = phrasalSet,
                            explicitIdioms = idiomSet
                        )
                    }
                    val grammarHint = LinguisticRanker.inferGrammarStructure(phrase, category)

                    // Check curated linguistic knowledge first if available (never fabricates unknown phrases)
                    curatedMap[key]?.let { known ->
                        val item = PhraseDetailItem(
                            phrase = phrase,
                            englishMeaning = known.englishMeaning,
                            arabicMeaning = known.arabicMeaning,
                            isArabicMachine = false,
                            example = known.example,
                            isIdiom = !known.isPhrasalVerb,
                            category = if (known.isPhrasalVerb) "PHRASAL_VERB" else "IDIOM",
                            usageExplanation = known.usageNote,
                            grammarStructure = grammarHint
                        )
                        saveSourceCache(key, SRC_PHRASE_DETAIL, json.encodeToString(item))
                        return@async item
                    }

                    val packEntry = dao.getOfflinePackWord(key)?.let { parseOfflinePackEntity(it) }
                    if (packEntry != null && packEntry.posGroups.isNotEmpty()) {
                        val s = packEntry.posGroups.first().senses.first()
                        val item = PhraseDetailItem(
                            phrase = phrase,
                            englishMeaning = s.definition,
                            arabicMeaning = s.definitionArabic,
                            isArabicMachine = s.definitionArabicIsMachine,
                            example = s.examples.firstOrNull().orEmpty(),
                            isIdiom = category == "IDIOM",
                            category = category,
                            usageExplanation = s.usageNote,
                            grammarStructure = grammarHint
                        )
                        saveSourceCache(key, SRC_PHRASE_DETAIL, json.encodeToString(item))
                        return@async item
                    }

                    if (!isOnline) return@async null

                    semaphore.withPermit {
                        val wik = withTimeoutOrNull(5_000L) { fetchWiktionaryByLanguageBlock(key.replace(Regex("\\s+"), "_"), "en", key, false) }
                        val firstWikSense = wik?.takeIf { !it.notFound }?.senses?.firstOrNull()
                        val fd = if (firstWikSense == null) {
                            withTimeoutOrNull(5_000L) { fetchFreeDictionarySource(key) }
                        } else null
                        val firstSense = firstWikSense ?: fd?.takeIf { !it.notFound }?.senses?.firstOrNull()
                            ?: return@withPermit null

                        val engDef = firstSense.definition
                        val existingEx = firstSense.examples.firstOrNull().orEmpty()
                        val tatoeba = withTimeoutOrNull(4_000L) { fetchTatoebaSource(key) }?.sentences?.firstOrNull()

                        val finalExample = existingEx.ifBlank { tatoeba?.english.orEmpty() }
                        var finalExArabic = firstSense.exampleTranslations[finalExample]
                        var finalExIsMachine = false
                        var finalExAudio = firstSense.exampleAudioUrls[finalExample]
                        if (tatoeba != null && (finalExample.equals(tatoeba.english, ignoreCase = true) || finalExArabic.isNullOrBlank())) {
                            if (finalExArabic.isNullOrBlank() && tatoeba.english.contains(key, ignoreCase = true)) {
                                finalExArabic = tatoeba.arabic
                                finalExIsMachine = false
                                finalExAudio = tatoeba.audioUrl
                            }
                        }

                        var arMeaning = firstSense.definitionArabic?.takeIf { it.isNotBlank() }
                        var arIsMachine = false
                        if (arMeaning == null && packReady) {
                            arMeaning = translationManager.translateShortIfReady(
                                text = contextualDefPrompt(phrase, engDef),
                                sourceCode = TranslateLanguage.ENGLISH,
                                targetCode = TranslateLanguage.ARABIC
                            ).getOrNull()?.let { extractContextualSenseArabic(it, phrase) }
                            arIsMachine = !arMeaning.isNullOrBlank()
                        }

                        if (finalExample.isNotBlank() && finalExArabic.isNullOrBlank() && packReady) {
                            finalExArabic = translationManager.translateShortIfReady(
                                text = finalExample,
                                sourceCode = TranslateLanguage.ENGLISH,
                                targetCode = TranslateLanguage.ARABIC
                            ).getOrNull()?.takeIf { it.isNotBlank() && ArabicTextUtils.containsArabicLetters(it) }
                            finalExIsMachine = !finalExArabic.isNullOrBlank()
                        }

                        val isIdiom = category == "IDIOM" ||
                            firstSense.partOfSpeech.contains("idiom", ignoreCase = true) ||
                            firstSense.partOfSpeech.contains("proverb", ignoreCase = true) ||
                            firstSense.partOfSpeech.contains("phrase", ignoreCase = true)
                        val resolvedCategory = if (isIdiom && category == "COMPOUND") "IDIOM" else category
                        val usageExplanation = firstSense.usageNote ?: when (resolvedCategory) {
                            "IDIOM" -> "تعبير اصطلاحي يُفهم معناه من السياق لا من الترجمة الحرفية"
                            "PHRASAL_VERB" -> "فعل مركب يتغير معناه باقترانه بحرف الجر"
                            else -> "تعبير مركب شائع الاستخدام"
                        }

                        val item = PhraseDetailItem(
                            phrase = phrase,
                            englishMeaning = engDef,
                            arabicMeaning = arMeaning,
                            isArabicMachine = arIsMachine,
                            example = finalExample,
                            exampleArabic = finalExArabic,
                            exampleIsMachine = finalExIsMachine,
                            exampleAudioUrl = finalExAudio,
                            isIdiom = isIdiom,
                            category = resolvedCategory,
                            usageExplanation = usageExplanation,
                            grammarStructure = grammarHint
                        )
                        saveSourceCache(key, SRC_PHRASE_DETAIL, json.encodeToString(item))
                        item
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    suspend fun loadArabicIdiomDetails(
        phrases: List<String>,
        baseWord: String = "",
        maxCount: Int = 12
    ): List<PhraseDetailItem> = withContext(Dispatchers.IO) {
        val candidates = phrases.map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") }
            .distinctBy { ArabicTextUtils.normalizeArabicForMatching(it) }
            .take(maxCount)
        if (candidates.isEmpty()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val semaphore = Semaphore(3)
        val isOnline = networkMonitor.isOnline()
        val packReady = translationManager.isPairDownloaded(TranslateLanguage.ARABIC, TranslateLanguage.ENGLISH)
        val curatedByNorm = ContextualTranslationAnalyzer.getCuratedPhrasesForHeadword(baseWord, isArabic = true)
            .associateBy { ArabicTextUtils.normalizeArabicForMatching(it.phrase) }

        coroutineScope {
            candidates.map { phrase ->
                async {
                    val normKey = ArabicTextUtils.normalizeArabicForMatching(phrase)
                    val cacheKey = "ar_$normKey|$SRC_PHRASE_DETAIL"
                    val cached = dao.getApiCache(cacheKey)
                    if (cached != null && (now - cached.timestampMs) <= CACHE_STALE_MS) {
                        val decoded = decodeOrNull<PhraseDetailItem>(cached.payloadJson)
                        if (decoded != null && decoded.englishMeaning.isNotBlank()) {
                            return@async decoded
                        }
                    }

                    curatedByNorm[normKey]?.let { known ->
                        val item = PhraseDetailItem(
                            phrase = known.phrase,
                            englishMeaning = known.englishMeaning,
                            arabicMeaning = known.arabicMeaning,
                            isArabicMachine = false,
                            example = known.example,
                            isIdiom = !known.isPhrasalVerb,
                            category = if (known.isPhrasalVerb) "PHRASAL_VERB" else "IDIOM",
                            usageExplanation = known.usageNote
                        )
                        saveSourceCache("ar_$normKey", SRC_PHRASE_DETAIL, json.encodeToString(item))
                        return@async item
                    }

                    val directOverride = ContextualTranslationAnalyzer.findDirectContextualOverride(
                        text = phrase,
                        sourceCode = TranslateLanguage.ARABIC,
                        targetCode = TranslateLanguage.ENGLISH
                    )
                    if (!directOverride.isNullOrBlank()) {
                        val item = PhraseDetailItem(
                            phrase = phrase,
                            englishMeaning = directOverride,
                            arabicMeaning = null,
                            isArabicMachine = false,
                            isIdiom = true,
                            category = "IDIOM",
                            usageExplanation = "تعبير اصطلاحي يُفهم معناه من السياق لا من الترجمة الحرفية"
                        )
                        saveSourceCache("ar_$normKey", SRC_PHRASE_DETAIL, json.encodeToString(item))
                        return@async item
                    }

                    var engMeaning: String? = null
                    var exArabic = ""
                    var exEnglish: String? = null

                    if (isOnline) {
                        semaphore.withPermit {
                            val wik = withTimeoutOrNull(4_500L) {
                                fetchArabicWiktionarySource(
                                    slugWithHamza = ArabicTextUtils.stripArabicDiacriticsKeepHamza(phrase),
                                    normalizedArabic = normKey,
                                    searchedDisplayWord = phrase
                                )
                            }
                            val firstSense = wik?.takeIf { !it.notFound }?.senses?.firstOrNull()
                            if (firstSense != null) {
                                engMeaning = firstSense.definitionArabic?.takeIf { it.isNotBlank() }
                                exArabic = firstSense.examples.firstOrNull().orEmpty()
                                exEnglish = firstSense.exampleTranslations[exArabic]
                            }
                        }
                    }

                    if (engMeaning.isNullOrBlank() && packReady) {
                        val prepared = ContextualTranslationAnalyzer.prepareArabicForTranslation(phrase)
                        val translated = translationManager.translateShortIfReady(
                            text = prepared,
                            sourceCode = TranslateLanguage.ARABIC,
                            targetCode = TranslateLanguage.ENGLISH
                        ).getOrNull()?.trim()?.takeIf { it.isNotBlank() }
                        if (!translated.isNullOrBlank()) {
                            engMeaning = ContextualTranslationAnalyzer.refineEnglishTranslation(phrase, translated)
                        }
                    }

                    val finalEng = engMeaning?.takeIf { it.isNotBlank() } ?: return@async null
                    val item = PhraseDetailItem(
                        phrase = phrase,
                        englishMeaning = finalEng,
                        arabicMeaning = null,
                        isArabicMachine = false,
                        example = exArabic,
                        exampleArabic = exEnglish,
                        exampleIsMachine = false,
                        isIdiom = true,
                        category = "IDIOM",
                        usageExplanation = "تعبير اصطلاحي عربي يُترجم حسب المعنى المقصود"
                    )
                    saveSourceCache("ar_$normKey", SRC_PHRASE_DETAIL, json.encodeToString(item))
                    item
                }
            }.awaitAll().filterNotNull()
        }
    }

    // --- Source A: Free Dictionary API (with Section 13 explicit usage/spelling label parsing) ---
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
                            val usageInfo = ArabicTextUtils.extractUsageFromPlainDefinition(defText)
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
                                    usageLabels = usageInfo.usageLabels,
                                    spellingVariantLabel = usageInfo.spellingVariantLabel,
                                    otherSpellingWord = usageInfo.otherSpellingWord,
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

    // --- Source B1: Wiktionary REST API ("en" block) + MediaWiki sections (Translations, Phrasal verbs, Idioms, Compounds, Derived/Related terms) ---
    private suspend fun fetchWiktionarySource(word: String): WiktionaryCachedPayload? {
        val slug = word.trim().replace(Regex("\\s+"), "_")
        return coroutineScope {
            val defDeferred = async {
                fetchWiktionaryByLanguageBlock(
                    slug = slug,
                    langBlockKey = "en",
                    baseWord = word,
                    extractPhrases = true
                )
            }
            val sectionsDeferred = async {
                if (!word.trim().contains(" ")) {
                    fetchWiktionaryEnglishSections(slug, word)
                } else {
                    null
                }
            }
            val defPayload = defDeferred.await() ?: return@coroutineScope null
            val secPayload = sectionsDeferred.await() ?: return@coroutineScope defPayload
            defPayload.copy(
                notFound = defPayload.notFound &&
                    secPayload.phrasalVerbs.isEmpty() &&
                    secPayload.idioms.isEmpty() &&
                    secPayload.compounds.isEmpty() &&
                    secPayload.derivedWords.isEmpty(),
                phrases = (defPayload.phrases + secPayload.phrasalVerbs + secPayload.idioms + secPayload.compounds)
                    .distinct().take(40),
                phrasalVerbs = secPayload.phrasalVerbs,
                idioms = secPayload.idioms,
                compounds = secPayload.compounds,
                collocations = secPayload.collocations,
                synonyms = secPayload.synonyms,
                antonyms = secPayload.antonyms,
                derivedWords = (defPayload.derivedWords + secPayload.derivedWords).distinct().take(25),
                relatedTerms = secPayload.relatedTerms,
                arabicGlosses = secPayload.arabicGlosses
            )
        }
    }

    private suspend fun fetchWiktionaryEnglishSections(
        slug: String,
        baseWord: String
    ): WiktionaryCachedPayload? {
        return try {
            val rawSections = wiktionaryApi.getSectionsRaw(slug).string()
            val parseObj = json.parseToJsonElement(rawSections).jsonObject["parse"]?.jsonObject
                ?: return null
            val sections = parseObj["sections"]?.jsonArray ?: return null

            var inEnglish = false
            var foundEnglishHeading = false
            val targets = ArrayList<Pair<String, String>>() // index -> category

            for (el in sections) {
                val obj = el.jsonObject
                val tocLevel = obj["toclevel"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: continue
                val line = obj["line"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val index = obj["index"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

                if (tocLevel == 1) {
                    if (line.equals("English", ignoreCase = true)) {
                        inEnglish = true
                        foundEnglishHeading = true
                    } else if (foundEnglishHeading) {
                        break // left the English block
                    }
                    continue
                }
                if (!inEnglish && foundEnglishHeading) continue
                val lower = line.lowercase(Locale.US)
                val category = when {
                    lower == "translations" -> "translations"
                    lower.contains("phrasal verb") -> "phrasal"
                    lower.contains("idiom") -> "idiom"
                    lower.contains("collocation") -> "collocation"
                    lower.contains("compound") -> "compound"
                    lower.contains("derived term") -> "derived"
                    lower.contains("related term") -> "related"
                    lower == "synonyms" -> "synonyms"
                    lower == "antonyms" -> "antonyms"
                    else -> null
                }
                if (category != null && index.isNotBlank()) {
                    targets.add(index to category)
                }
            }

            if (targets.isEmpty()) return null

            val phrasalVerbs = mutableListOf<String>()
            val idioms = mutableListOf<String>()
            val compounds = mutableListOf<String>()
            val collocations = mutableListOf<String>()
            val derivedWords = mutableListOf<String>()
            val relatedTerms = mutableListOf<String>()
            val synonyms = mutableListOf<String>()
            val antonyms = mutableListOf<String>()
            val arabicGlosses = mutableListOf<CachedArabicGloss>()
            val baseLower = baseWord.trim().lowercase(Locale.US)

            val translationTargets = targets.filter { it.second == "translations" }.take(6)
            val sectionPriority = mapOf(
                "idiom" to 1,
                "phrasal" to 2,
                "collocation" to 3,
                "compound" to 4,
                "synonyms" to 5,
                "antonyms" to 6,
                "derived" to 7,
                "related" to 8
            )
            val otherTargets = targets.filter { it.second != "translations" }
                .sortedBy { sectionPriority[it.second] ?: 99 }
                .take(16)

            coroutineScope {
                val transDeferred = translationTargets.map { (idx, _) ->
                    async {
                        runCatching {
                            val rawHtmlJson = wiktionaryApi.getSectionHtmlRaw(slug, idx).string()
                            val html = WiktionaryHtmlParser.extractHtmlFromParseJson(rawHtmlJson)
                            WiktionaryHtmlParser.parseEnglishTranslationsHtml(html)
                        }.getOrDefault(emptyList())
                    }
                }
                val linksDeferred = otherTargets.map { (idx, cat) ->
                    async {
                        val terms = if (cat in setOf("phrasal", "idiom", "compound", "collocation")) {
                            runCatching {
                                val rawHtmlJson = wiktionaryApi.getSectionHtmlRaw(slug, idx).string()
                                val html = WiktionaryHtmlParser.extractHtmlFromParseJson(rawHtmlJson)
                                WiktionaryHtmlParser.parseEnglishListSectionTerms(html, baseWord)
                            }.getOrDefault(emptyList())
                        } else {
                            emptyList()
                        }
                        if (terms.isNotEmpty()) {
                            cat to terms
                        } else {
                            val links = runCatching {
                                val raw = wiktionaryApi.getSectionLinksRaw(slug, idx).string()
                                val arr = json.parseToJsonElement(raw).jsonObject["parse"]
                                    ?.jsonObject?.get("links")?.jsonArray
                                arr?.mapNotNull { linkEl ->
                                    val lObj = linkEl.jsonObject
                                    val ns = lObj["ns"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1
                                    val exists = lObj["exists"] != null
                                    val title = lObj["*"]?.jsonPrimitive?.contentOrNull?.trim()
                                        ?: lObj["title"]?.jsonPrimitive?.contentOrNull?.trim()
                                    if (ns == 0 && exists && !title.isNullOrBlank() &&
                                        !title.contains(":") && !title.equals(baseLower, ignoreCase = true)
                                    ) {
                                        title
                                    } else {
                                        null
                                    }
                                }.orEmpty()
                            }.getOrDefault(emptyList())
                            cat to links
                        }
                    }
                }

                transDeferred.awaitAll().forEach { list ->
                    list.forEach { g ->
                        arabicGlosses.add(CachedArabicGloss(g.senseGloss, g.arabicTerms))
                    }
                }

                linksDeferred.awaitAll().forEach { (cat, links) ->
                    for (term in links) {
                        val clean = term.trim()
                        if (clean.length > 56) continue
                        when (cat) {
                            "phrasal" -> if (clean.contains(" ")) phrasalVerbs.add(clean)
                            "idiom" -> if (clean.contains(" ")) idioms.add(clean)
                            "collocation" -> if (clean.contains(" ")) collocations.add(clean)
                            "compound" -> if (clean.contains(" ")) compounds.add(clean)
                            "derived" -> {
                                val lower = clean.lowercase(Locale.US)
                                if (clean.contains(" ")) {
                                    val parts = lower.split(Regex("\\s+"))
                                    if (parts.size in 2..3 && parts.first() == baseLower && parts.last() in EXACT_PARTICLES) {
                                        phrasalVerbs.add(clean)
                                    } else if (parts.size >= 4) {
                                        idioms.add(clean)
                                    } else {
                                        compounds.add(clean)
                                    }
                                } else if (clean.all { it.isLetter() || it == '-' }) {
                                    derivedWords.add(clean)
                                }
                            }
                            "related" -> {
                                if (clean.contains(" ")) compounds.add(clean)
                                else if (clean.all { it.isLetter() || it == '-' }) relatedTerms.add(clean)
                            }
                            "synonyms" -> synonyms.add(clean)
                            "antonyms" -> antonyms.add(clean)
                        }
                    }
                }
            }

            WiktionaryCachedPayload(
                notFound = false,
                phrasalVerbs = phrasalVerbs.distinctBy { it.lowercase(Locale.US) }.take(25),
                idioms = idioms.distinctBy { it.lowercase(Locale.US) }.take(25),
                compounds = compounds.distinctBy { it.lowercase(Locale.US) }.take(25),
                collocations = collocations.distinctBy { it.lowercase(Locale.US) }.take(25),
                synonyms = synonyms.distinctBy { it.lowercase(Locale.US) }.take(25),
                antonyms = antonyms.distinctBy { it.lowercase(Locale.US) }.take(25),
                derivedWords = derivedWords.distinctBy { it.lowercase(Locale.US) }.take(25),
                relatedTerms = relatedTerms.distinctBy { it.lowercase(Locale.US) }.take(25),
                arabicGlosses = arabicGlosses.take(80)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Wiktionary sections fetch failed for $slug", e)
            null
        }
    }

    // --- Source B2: Wiktionary REST + Page HTML ("ar" block, multi-vocalization + full tashkeel) ---
    private suspend fun fetchArabicWiktionarySource(
        slugWithHamza: String,
        normalizedArabic: String,
        searchedDisplayWord: String = slugWithHamza
    ): WiktionaryCachedPayload? {
        val candidateSlugs = listOf(
            slugWithHamza.replace(Regex("\\s+"), "_"),
            normalizedArabic.replace(Regex("\\s+"), "_")
        ).distinct()

        for (slug in candidateSlugs) {
            val payload = coroutineScope {
                val htmlD = async {
                    runCatching {
                        val rawJson = wiktionaryApi.getPageHtmlRaw(slug).string()
                        val html = WiktionaryHtmlParser.extractHtmlFromParseJson(rawJson)
                        WiktionaryHtmlParser.parseArabicSectionFromPageHtml(html, searchedDisplayWord)
                    }.getOrNull()
                }
                val restD = async {
                    fetchWiktionaryByLanguageBlock(
                        slug = slug,
                        langBlockKey = "ar",
                        baseWord = slug,
                        extractPhrases = false
                    )
                }
                val htmlData = htmlD.await()
                val restPayload = restD.await()

                val htmlSenses = htmlData?.senses.orEmpty()
                val restSenses = restPayload?.takeIf { !it.notFound }?.senses.orEmpty()

                val mergedSenses = if (htmlSenses.isNotEmpty()) {
                    val existingEngGlosses = htmlSenses.mapNotNull { it.definitionArabic?.lowercase(Locale.US) }.toSet()
                    val extraRest = restSenses.filter { s ->
                        s.definition.lowercase(Locale.US) !in existingEngGlosses
                    }.map { s ->
                        s.copy(
                            definition = ArabicTextUtils.canonicalizeArabicWithDiacritics(searchedDisplayWord),
                            definitionArabic = s.definition,
                            definitionArabicIsMachine = false
                        )
                    }
                    htmlSenses + extraRest
                } else {
                    restSenses.map { s ->
                        s.copy(
                            definition = ArabicTextUtils.canonicalizeArabicWithDiacritics(searchedDisplayWord),
                            definitionArabic = s.definition,
                            definitionArabicIsMachine = false
                        )
                    }
                }

                if (mergedSenses.isNotEmpty() || (htmlData != null && htmlData.synonyms.isNotEmpty())) {
                    WiktionaryCachedPayload(
                        notFound = false,
                        senses = mergedSenses,
                        synonyms = htmlData?.synonyms.orEmpty(),
                        antonyms = htmlData?.antonyms.orEmpty(),
                        derivedWords = htmlData?.derivedTerms.orEmpty(),
                        relatedTerms = htmlData?.relatedTerms.orEmpty(),
                        idioms = htmlData?.idiomsAndPhrases.orEmpty(),
                        vocalizedHeadwords = htmlData?.vocalizedHeadwords.orEmpty(),
                        transliteration = htmlData?.transliteration.orEmpty()
                    )
                } else {
                    restPayload
                }
            }
            if (payload != null && !payload.notFound) {
                return payload
            }
        }
        return WiktionaryCachedPayload(notFound = true)
    }

    private suspend fun fetchWiktionaryByLanguageBlock(
        slug: String,
        langBlockKey: String,
        baseWord: String,
        extractPhrases: Boolean
    ): WiktionaryCachedPayload? {
        return try {
            val raw = wiktionaryApi.getDefinitionRaw(slug).string()
            val root = json.parseToJsonElement(raw).jsonObject
            val langArray = root[langBlockKey]?.jsonArray
                ?: return WiktionaryCachedPayload(notFound = true)
            val senses = mutableListOf<SenseItem>()
            val phrases = mutableListOf<String>()
            val derivedWords = mutableListOf<String>()
            var idx = 1

            for (secElem in langArray) {
                val sec = secElem.jsonObject
                val pos = sec["partOfSpeech"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "word" }
                val posAr = mapPosToArabic(pos)
                val defs = sec["definitions"]?.jsonArray ?: continue
                for (defElem in defs) {
                    val defObj = defElem.jsonObject
                    val defHtml = defObj["definition"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val cleanDef = ArabicTextUtils.stripHtmlPlain(defHtml)
                    if (cleanDef.isBlank()) continue

                    val usageInfo = ArabicTextUtils.extractUsageFromWiktionaryHtml(defHtml, cleanDef)

                    val exList = mutableListOf<String>()
                    val wiktionaryExTrans = HashMap<String, String>()
                    val wiktionaryExIsMachine = HashMap<String, Boolean>()

                    defObj["examples"]?.jsonArray?.forEach { exEl ->
                        val cleaned = ArabicTextUtils.stripHtmlPlain(exEl.jsonPrimitive.contentOrNull.orEmpty())
                        if (cleaned.isNotBlank()) exList.add(cleaned)
                    }
                    defObj["parsedExamples"]?.jsonArray?.forEach { pEl ->
                        val pObj = pEl.jsonObject
                        val exHtml = pObj["example"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val cleaned = ArabicTextUtils.stripHtmlPlain(exHtml)
                        if (cleaned.isNotBlank()) {
                            if (cleaned !in exList) exList.add(cleaned)
                            val trRaw = pObj["translation"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val cleanTr = ArabicTextUtils.stripHtmlPlain(trRaw)
                            if (cleanTr.isNotBlank() && ArabicTextUtils.containsArabicLetters(cleanTr)) {
                                wiktionaryExTrans[cleaned] = cleanTr
                                wiktionaryExIsMachine[cleaned] = false
                            }
                        }
                    }

                    if (extractPhrases) {
                        extractWikiLinksForPhrases(defHtml, baseWord, phrases, derivedWords)
                    }

                    val wiktionaryArabic = extractWiktionaryArabicTranslation(defObj)

                    senses.add(
                        SenseItem(
                            index = idx++,
                            partOfSpeech = pos,
                            partOfSpeechArabic = posAr,
                            definition = cleanDef,
                            definitionArabic = wiktionaryArabic,
                            definitionArabicIsMachine = false,
                            examples = exList.take(3),
                            exampleTranslations = wiktionaryExTrans,
                            exampleIsMachine = wiktionaryExIsMachine,
                            synonyms = emptyList(),
                            antonyms = emptyList(),
                            usageLabels = usageInfo.usageLabels,
                            spellingVariantLabel = usageInfo.spellingVariantLabel,
                            otherSpellingWord = usageInfo.otherSpellingWord,
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
            Log.w(TAG, "Wiktionary ($langBlockKey) failed for $slug", e)
            null
        }
    }

    // --- Source C: Datamuse API (English only) ---
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
                val wnDefsD = async {
                    if (!word.trim().contains(" ")) {
                        runCatching { datamuseApi.getWordNetDefinitions(word) }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }
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

                val wordNetSenses = parseDatamuseWordNetSenses(wnDefsD.await())

                DatamuseCachedPayload(
                    synonyms = synonyms,
                    antonyms = antonyms,
                    collocations = collocations,
                    prepositions = prepositions,
                    familyCandidates = familyCandidates,
                    confusableCandidates = confusableCandidates,
                    generalTerms = generalTerms,
                    specificTerms = specificTerms,
                    relatedWords = relatedWords,
                    wordNetSenses = wordNetSenses
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Datamuse failed for $word", e)
            null
        }
    }

    private suspend fun fetchWikimediaAudioForWord(
        word: String,
        needUs: Boolean,
        needUk: Boolean
    ): WikimediaAudioCachedPayload? {
        val api = wikimediaCommonsApi ?: return null
        if (!needUs && !needUk) return null
        return try {
            coroutineScope {
                val usD = if (needUs) async { fetchWikimediaAccentUrl(api, word, Accent.US) } else null
                val ukD = if (needUk) async { fetchWikimediaAccentUrl(api, word, Accent.UK) } else null
                WikimediaAudioCachedPayload(
                    usAudioUrl = usD?.await(),
                    ukAudioUrl = ukD?.await()
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Wikimedia audio lookup failed for $word", e)
            null
        }
    }

    private suspend fun fetchWikimediaAccentUrl(
        api: WikimediaCommonsApi,
        word: String,
        accent: Accent
    ): String? {
        val titles = WikimediaCommonsApi.buildCandidateTitles(word, accent)
        if (titles.isNotBlank()) {
            val raw = runCatching { api.getImageInfoByTitles(titles = titles).string() }.getOrNull()
            val direct = raw?.let { WikimediaCommonsApi.parseImageInfoAudioUrl(it) }
            if (direct != null) return direct
        }
        val prefixes = WikimediaCommonsApi.buildAllImagesPrefixes(word, accent)
        for (prefix in prefixes) {
            val raw = runCatching { api.searchAudioFiles(prefix = prefix).string() }.getOrNull()
            val matched = raw?.let { WikimediaCommonsApi.parseAllImagesAudioUrl(it) }
            if (matched != null) return matched
        }
        return null
    }

    // --- Source D1: Tatoeba API (eng -> ara, up to 15, with human audio URL if available) ---
    private suspend fun fetchTatoebaSource(word: String): TatoebaCachedPayload? {
        return try {
            val raw = tatoebaApi.searchSentencesRaw(query = word, from = "eng", to = "ara").string()
            val root = json.parseToJsonElement(raw).jsonObject
            val results = root["results"]?.jsonArray ?: return TatoebaCachedPayload()
            val pairs = mutableListOf<TatoebaSentence>()
            for (elem in results) {
                val obj = elem.jsonObject
                val enText = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (enText.isBlank()) continue
                val arText = extractTranslationFromTatoeba(obj["translations"] as? JsonArray, wantArabic = true)
                if (arText.isNotBlank()) {
                    val audioUrl = extractTatoebaEnglishAudioUrl(obj)
                    pairs.add(TatoebaSentence(english = enText, arabic = arText, audioUrl = audioUrl))
                    if (pairs.size >= 15) break
                }
            }
            TatoebaCachedPayload(sentences = pairs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Tatoeba (eng->ara) failed for $word", e)
            null
        }
    }

    // --- Source D2: Tatoeba API (ara -> eng, up to 15, Section 15.2) ---
    private suspend fun fetchTatoebaArabicToEnglish(arabicWord: String): TatoebaCachedPayload? {
        return try {
            val raw = tatoebaApi.searchSentencesRaw(query = arabicWord, from = "ara", to = "eng").string()
            val root = json.parseToJsonElement(raw).jsonObject
            val results = root["results"]?.jsonArray ?: return TatoebaCachedPayload()
            val pairs = mutableListOf<TatoebaSentence>()
            for (elem in results) {
                val obj = elem.jsonObject
                val arText = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (arText.isBlank()) continue
                val (enText, engAudioUrl) = extractEnglishWithAudioFromTatoeba(obj["translations"] as? JsonArray)
                if (enText.isNotBlank()) {
                    pairs.add(TatoebaSentence(english = enText, arabic = arText, audioUrl = engAudioUrl))
                    if (pairs.size >= 15) break
                }
            }
            TatoebaCachedPayload(sentences = pairs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Tatoeba (ara->eng) failed for $arabicWord", e)
            null
        }
    }

    private fun extractWiktionaryArabicTranslation(defObj: JsonObject): String? {
        val translations = defObj["translations"] as? JsonArray ?: return null
        for (item in translations) {
            val obj = item as? JsonObject ?: continue
            val code = obj["code"]?.jsonPrimitive?.contentOrNull
                ?: obj["lang"]?.jsonPrimitive?.contentOrNull
            if (code.equals("ar", ignoreCase = true) || code.equals("ara", ignoreCase = true)) {
                val word = obj["word"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: obj["text"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: obj["translation"]?.jsonPrimitive?.contentOrNull?.trim()
                if (!word.isNullOrBlank() && ArabicTextUtils.containsArabicLetters(word)) {
                    return word
                }
            }
        }
        return null
    }

    private fun extractTatoebaEnglishAudioUrl(sentenceObj: JsonObject): String? {
        val audios = sentenceObj["audios"] as? JsonArray ?: return null
        if (audios.isEmpty()) return null
        val firstAudio = audios.firstOrNull() as? JsonObject
        val directUrl = firstAudio?.get("download_url")?.jsonPrimitive?.contentOrNull?.trim()
            ?: firstAudio?.get("url")?.jsonPrimitive?.contentOrNull?.trim()
        if (!directUrl.isNullOrBlank()) return directUrl

        val sentenceId = sentenceObj["id"]?.jsonPrimitive?.contentOrNull?.trim()
        val lang = sentenceObj["lang"]?.jsonPrimitive?.contentOrNull?.trim() ?: "eng"
        if (!sentenceId.isNullOrBlank() && (lang == "eng" || lang == "en")) {
            return "https://audio.tatoeba.org/sentences/eng/$sentenceId.mp3"
        }
        return null
    }

    private fun extractEnglishWithAudioFromTatoeba(translationsArray: JsonArray?): Pair<String, String?> {
        if (translationsArray == null) return "" to null
        fun matchesEng(lang: String?, txt: String): Boolean {
            if (txt.isBlank()) return false
            return lang == "eng" || lang == "en" ||
                (!ArabicTextUtils.containsArabicLetters(txt) && txt.any { it in 'a'..'z' || it in 'A'..'Z' })
        }
        for (outer in translationsArray) {
            when (outer) {
                is JsonArray -> {
                    for (inner in outer) {
                        val obj = inner as? JsonObject ?: continue
                        val lang = obj["lang"]?.jsonPrimitive?.contentOrNull
                        val txt = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        if (matchesEng(lang, txt)) {
                            return txt to extractTatoebaEnglishAudioUrl(obj)
                        }
                    }
                }
                is JsonObject -> {
                    val lang = outer["lang"]?.jsonPrimitive?.contentOrNull
                    val txt = outer["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (matchesEng(lang, txt)) {
                        return txt to extractTatoebaEnglishAudioUrl(outer)
                    }
                }
                else -> Unit
            }
        }
        return "" to null
    }

    private fun extractTranslationFromTatoeba(
        translationsArray: JsonArray?,
        wantArabic: Boolean
    ): String {
        if (translationsArray == null) return ""
        fun matches(lang: String?, txt: String): Boolean {
            if (txt.isBlank()) return false
            return if (wantArabic) {
                lang == "ara" || lang == "ar" || ArabicTextUtils.containsArabicLetters(txt)
            } else {
                lang == "eng" || lang == "en" || (!ArabicTextUtils.containsArabicLetters(txt) && txt.any { it in 'a'..'z' || it in 'A'..'Z' })
            }
        }
        for (outer in translationsArray) {
            when (outer) {
                is JsonArray -> {
                    for (inner in outer) {
                        val obj = inner as? JsonObject ?: continue
                        val lang = obj["lang"]?.jsonPrimitive?.contentOrNull
                        val txt = obj["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                        if (matches(lang, txt)) return txt
                    }
                }
                is JsonObject -> {
                    val lang = outer["lang"]?.jsonPrimitive?.contentOrNull
                    val txt = outer["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (matches(lang, txt)) return txt
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

    private fun mergeArabicWordEntry(
        normalizedKey: String,
        displayWord: String,
        canonicalExact: String,
        hasExplicitTashkeel: Boolean,
        offlinePack: WordEntry?,
        wiktionary: WiktionaryCachedPayload?,
        tatoeba: TatoebaCachedPayload?,
        machineTranslations: List<String>,
        missingTranslationPack: Boolean,
        isFromCache: Boolean
    ): WordEntry? {
        val rawWkSenses = wiktionary?.takeIf { !it.notFound }?.senses.orEmpty()
        val packSenses = offlinePack?.posGroups?.flatMap { it.senses }.orEmpty()
        val allVocalized = (wiktionary?.vocalizedHeadwords.orEmpty() +
            rawWkSenses.mapNotNull { it.headwordVariant })
            .map { ArabicTextUtils.canonicalizeArabicWithDiacritics(it) }
            .filter { it.isNotBlank() && ArabicTextUtils.hasArabicDiacritics(it) }
            .distinct()

        // Respect explicit tashkeel if user searched with harakat (e.g. "مُرّ" vs "مَرَّ")
        val filteredWkSenses = if (hasExplicitTashkeel && rawWkSenses.isNotEmpty()) {
            val exactMatches = rawWkSenses.filter { s ->
                val hw = s.headwordVariant?.let { ArabicTextUtils.canonicalizeArabicWithDiacritics(it) }
                hw == canonicalExact
            }
            if (exactMatches.isNotEmpty()) exactMatches else rawWkSenses
        } else {
            rawWkSenses
        }

        val combinedSenses = if (filteredWkSenses.isNotEmpty() || packSenses.isNotEmpty()) {
            LinguisticRanker.dedupAndRankSenses(filteredWkSenses + packSenses)
        } else {
            emptyList()
        }

        val posGroups = LinguisticRanker.groupAndNumberPosGroups(combinedSenses, ::mapPosToArabic)

        val curatedArPhrases = ContextualTranslationAnalyzer.getCuratedPhrasesForHeadword(displayWord, isArabic = true)
            .map { k ->
                PhraseDetailItem(
                    phrase = k.phrase,
                    englishMeaning = k.englishMeaning,
                    arabicMeaning = k.arabicMeaning,
                    isArabicMachine = false,
                    example = k.example,
                    isIdiom = !k.isPhrasalVerb,
                    category = if (k.isPhrasalVerb) "PHRASAL_VERB" else "IDIOM",
                    usageExplanation = k.usageNote
                )
            }

        val allSynonyms = (wiktionary?.synonyms.orEmpty() + offlinePack?.synonyms.orEmpty())
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25)
        val allAntonyms = (wiktionary?.antonyms.orEmpty() + offlinePack?.antonyms.orEmpty())
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25)
        val allDerived = (wiktionary?.derivedWords.orEmpty().map {
            WordFamilyItem(word = it, partOfSpeech = "", partOfSpeechArabic = "مشتق")
        } + offlinePack?.wordFamily.orEmpty()).distinctBy { it.word }.take(20)
        val allRelated = (wiktionary?.relatedTerms.orEmpty() + offlinePack?.relatedWords.orEmpty())
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25)
        val allPhrases = (wiktionary?.idioms.orEmpty() +
            offlinePack?.prepositionsAndPhrases.orEmpty() +
            curatedArPhrases.map { it.phrase })
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25)
        val allArIdioms = (wiktionary?.idioms.orEmpty() +
            curatedArPhrases.filter { it.isIdiom }.map { it.phrase })
            .map { it.trim() }.filter { it.isNotBlank() && it.contains(" ") }.distinct().take(25)

        val tatoebaList = tatoeba?.sentences.orEmpty().take(5)
        val hasAnyData = posGroups.isNotEmpty() ||
            tatoebaList.isNotEmpty() ||
            machineTranslations.isNotEmpty() ||
            allSynonyms.isNotEmpty() ||
            allPhrases.isNotEmpty()

        if (!hasAnyData) return null

        val effectiveDisplay = if (!hasExplicitTashkeel && allVocalized.size == 1) {
            allVocalized.first()
        } else {
            displayWord
        }

        return WordEntry(
            word = normalizedKey,
            displayWord = effectiveDisplay,
            isArabicWord = true,
            lang = "ar",
            transliteration = wiktionary?.transliteration.orEmpty(),
            vocalizedForms = allVocalized,
            usIpa = "",
            ukIpa = "",
            usAudioUrl = null,
            ukAudioUrl = null,
            machineTranslations = machineTranslations,
            missingTranslationPack = missingTranslationPack,
            posGroups = posGroups,
            synonyms = allSynonyms,
            antonyms = allAntonyms,
            collocations = offlinePack?.collocations.orEmpty(),
            prepositionsAndPhrases = allPhrases,
            idioms = allArIdioms,
            phrasalVerbsAndIdioms = curatedArPhrases,
            wordFamily = allDerived,
            relatedWords = allRelated,
            tatoebaSentences = tatoebaList,
            hasWiktionaryAttribution = filteredWkSenses.isNotEmpty(),
            hasTatoebaAttribution = tatoebaList.isNotEmpty(),
            isFromOfflinePack = offlinePack != null,
            isFromCache = isFromCache
        )
    }

    private fun mergeWordEntry(
        word: String,
        offlinePack: WordEntry?,
        freeDict: FreeDictCachedPayload?,
        wiktionary: WiktionaryCachedPayload?,
        datamuse: DatamuseCachedPayload?,
        tatoeba: TatoebaCachedPayload?,
        wikimediaAudio: WikimediaAudioCachedPayload? = null,
        machineTranslations: List<String>,
        missingTranslationPack: Boolean,
        isFromCache: Boolean
    ): WordEntry? {
        val fdSenses = freeDict?.takeIf { !it.notFound }?.senses.orEmpty()
        val wkSenses = wiktionary?.takeIf { !it.notFound }?.senses.orEmpty()
        val wnSenses = datamuse?.wordNetSenses.orEmpty()
        val packSenses = offlinePack?.posGroups?.flatMap { it.senses }.orEmpty()

        val wkArabicGlosses = wiktionary?.arabicGlosses.orEmpty().map {
            WiktionaryArabicGloss(it.senseGloss, it.arabicTerms)
        }

        val combinedSenses = if (fdSenses.isNotEmpty() || wkSenses.isNotEmpty() || wnSenses.isNotEmpty() || packSenses.isNotEmpty()) {
            LinguisticRanker.dedupAndRankSenses(fdSenses + wkSenses + wnSenses + packSenses)
        } else {
            emptyList()
        }

        val tatoebaPool = tatoeba?.sentences.orEmpty().take(15)
        val tatoebaByNormalizedEnglish = tatoebaPool.associateBy {
            it.english.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
        }

        val usedFallbackTatoeba = HashSet<String>()
        val enrichedSenses = combinedSenses.map { sense ->
            val matchedWiktionaryArabic = if (sense.definitionArabic.isNullOrBlank() && wkArabicGlosses.isNotEmpty()) {
                WiktionaryHtmlParser.matchBestArabicTranslation(sense.definition, wkArabicGlosses)
            } else {
                null
            }

            val exList = sense.examples.toMutableList()
            val exTrans = HashMap(sense.exampleTranslations)
            val exMachine = HashMap(sense.exampleIsMachine)
            val exAudio = HashMap(sense.exampleAudioUrls)

            // Match existing examples against human Tatoeba translations + audio
            exList.forEach { ex ->
                val normEx = ex.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
                val tMatch = tatoebaByNormalizedEnglish[normEx]
                if (tMatch != null) {
                    if (tMatch.arabic.isNotBlank()) {
                        exTrans[ex] = tMatch.arabic
                        exMachine[ex] = false
                    }
                    if (!tMatch.audioUrl.isNullOrBlank()) {
                        exAudio[ex] = tMatch.audioUrl
                    }
                }
            }

            // Pick ONE concise example sentence per sense (prioritizing Tatoeba human-translated match if source lacked translation)
            val hasAnyHumanExTranslation = exList.any { !exTrans[it].isNullOrBlank() && exMachine[it] == false }
            if (!hasAnyHumanExTranslation && tatoebaPool.isNotEmpty()) {
                val contextMatch = pickContextTatoebaForSense(
                    word = word,
                    definition = sense.definition,
                    pool = tatoebaPool,
                    alreadyUsed = usedFallbackTatoeba
                )
                if (contextMatch != null) {
                    usedFallbackTatoeba.add(contextMatch.english)
                    if (exList.isEmpty()) {
                        exList.add(contextMatch.english)
                    } else {
                        exList[0] = contextMatch.english
                    }
                    exTrans[contextMatch.english] = contextMatch.arabic
                    exMachine[contextMatch.english] = false
                    if (!contextMatch.audioUrl.isNullOrBlank()) {
                        exAudio[contextMatch.english] = contextMatch.audioUrl
                    }
                }
            }

            val singleExample = exList.distinct().take(1)
            val singleKeySet = singleExample.toSet()
            sense.copy(
                definitionArabic = sense.definitionArabic?.takeIf { it.isNotBlank() } ?: matchedWiktionaryArabic,
                definitionArabicIsMachine = if (!sense.definitionArabic.isNullOrBlank()) {
                    sense.definitionArabicIsMachine
                } else {
                    false
                },
                examples = singleExample,
                exampleTranslations = exTrans.filterKeys { it in singleKeySet },
                exampleIsMachine = exMachine.filterKeys { it in singleKeySet },
                exampleAudioUrls = exAudio.filterKeys { it in singleKeySet }
            )
        }

        val posGroups = LinguisticRanker.groupAndNumberPosGroups(enrichedSenses, ::mapPosToArabic)

        val allSynonyms = (enrichedSenses.flatMap { it.synonyms } +
            wiktionary?.synonyms.orEmpty() +
            datamuse?.synonyms.orEmpty() +
            offlinePack?.synonyms.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val allAntonyms = (enrichedSenses.flatMap { it.antonyms } +
            wiktionary?.antonyms.orEmpty() +
            datamuse?.antonyms.orEmpty() +
            offlinePack?.antonyms.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val collocations = (wiktionary?.collocations.orEmpty() +
            datamuse?.collocations.orEmpty() +
            offlinePack?.collocations.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val curatedForWord = ContextualTranslationAnalyzer.getCuratedPhrasesForHeadword(word, isArabic = false)
        val curatedPhrasals = curatedForWord.filter { it.isPhrasalVerb }.map { it.phrase }
        val curatedIdioms = curatedForWord.filter { !it.isPhrasalVerb }.map { it.phrase }

        val allIdioms = (wiktionary?.idioms.orEmpty() + curatedIdioms)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val compoundExpressions = (wiktionary?.compounds.orEmpty() +
            wiktionary?.collocations.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(25)

        val prepositionsAndPhrases = (wiktionary?.phrasalVerbs.orEmpty() +
            curatedPhrasals +
            wiktionary?.idioms.orEmpty() +
            curatedIdioms +
            datamuse?.prepositions.orEmpty() +
            freeDict?.phrases.orEmpty() +
            wiktionary?.phrases.orEmpty() +
            offlinePack?.prepositionsAndPhrases.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(45)

        val initialPhrasalAndIdiomItems = curatedForWord.map { k ->
            val cat = if (k.isPhrasalVerb) "PHRASAL_VERB" else "IDIOM"
            PhraseDetailItem(
                phrase = k.phrase,
                englishMeaning = k.englishMeaning,
                arabicMeaning = k.arabicMeaning,
                isArabicMachine = false,
                example = k.example,
                isIdiom = !k.isPhrasalVerb,
                category = cat,
                usageExplanation = k.usageNote,
                grammarStructure = LinguisticRanker.inferGrammarStructure(k.phrase, cat)
            )
        }

        val wkFamily = wiktionary?.derivedWords.orEmpty().map {
            WordFamilyItem(word = it, partOfSpeech = "", partOfSpeechArabic = "مشتق")
        }
        val packFamily = offlinePack?.wordFamily.orEmpty()
        val dmFamily = datamuse?.familyCandidates.orEmpty()
        val wordFamily = (wkFamily + packFamily + dmFamily)
            .distinctBy { it.word.lowercase(Locale.US) }
            .take(20)

        val relatedWords = (wiktionary?.relatedTerms.orEmpty() + datamuse?.relatedWords.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(30)

        val confusableCandidates = if (datamuse != null && datamuse.confusableCandidates.isNotEmpty()) {
            datamuse.confusableCandidates
        } else {
            offlinePack?.confusableCandidates.orEmpty()
        }

        // Show up to 3 Tatoeba examples per word (BUG 5)
        val displayedTatoeba = tatoebaPool.take(3)

        val hasAnyData = posGroups.isNotEmpty() ||
            allSynonyms.isNotEmpty() ||
            allAntonyms.isNotEmpty() ||
            displayedTatoeba.isNotEmpty() ||
            relatedWords.isNotEmpty() ||
            prepositionsAndPhrases.isNotEmpty() ||
            allIdioms.isNotEmpty() ||
            machineTranslations.isNotEmpty()

        if (!hasAnyData) return null

        val resolvedUsAudio = normalizeAudioUrl(freeDict?.usAudioUrl)
            ?: normalizeAudioUrl(wikimediaAudio?.usAudioUrl)
        val resolvedUkAudio = normalizeAudioUrl(freeDict?.ukAudioUrl)
            ?: normalizeAudioUrl(wikimediaAudio?.ukAudioUrl)

        return WordEntry(
            word = word,
            displayWord = word,
            isArabicWord = false,
            lang = "en",
            usIpa = freeDict?.usIpa.orEmpty().ifBlank { offlinePack?.usIpa.orEmpty() },
            ukIpa = freeDict?.ukIpa.orEmpty(),
            usAudioUrl = resolvedUsAudio,
            ukAudioUrl = resolvedUkAudio,
            tags = offlinePack?.tags.orEmpty(),
            machineTranslations = machineTranslations,
            missingTranslationPack = missingTranslationPack,
            posGroups = posGroups,
            synonyms = allSynonyms,
            antonyms = allAntonyms,
            collocations = collocations,
            compoundExpressions = compoundExpressions,
            prepositionsAndPhrases = prepositionsAndPhrases,
            idioms = allIdioms,
            phrasalVerbsAndIdioms = initialPhrasalAndIdiomItems,
            wordFamily = wordFamily,
            confusableCandidates = confusableCandidates,
            generalTerms = datamuse?.generalTerms.orEmpty(),
            specificTerms = datamuse?.specificTerms.orEmpty(),
            relatedWords = relatedWords,
            tatoebaSentences = displayedTatoeba,
            hasWiktionaryAttribution = wkSenses.isNotEmpty(),
            hasTatoebaAttribution = displayedTatoeba.isNotEmpty(),
            isFromOfflinePack = offlinePack != null,
            isFromCache = isFromCache
        )
    }

    private fun pickContextTatoebaForSense(
        word: String,
        definition: String,
        pool: List<TatoebaSentence>,
        alreadyUsed: Set<String>
    ): TatoebaSentence? {
        val wordRegex = Regex("\\b${Regex.escape(word)}\\w*\\b", RegexOption.IGNORE_CASE)
        val defKeywords = definition.lowercase(Locale.US)
            .split(Regex("[^a-z]+"))
            .filter { it.length >= 4 && it !in COMMON_STOPWORDS && it != word.lowercase(Locale.US) }
            .toSet()

        val candidates = pool.filter {
            it.arabic.isNotBlank() &&
                it.english !in alreadyUsed &&
                wordRegex.containsMatchIn(it.english)
        }
        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { sent ->
            val sentWords = sent.english.lowercase(Locale.US).split(Regex("[^a-z]+")).toSet()
            val overlap = defKeywords.count { it in sentWords }
            (overlap * 10) + (if (!sent.audioUrl.isNullOrBlank()) 2 else 0)
        }
    }

    private fun parseOfflinePackEntity(entity: OfflinePackWordEntity): WordEntry? {
        return try {
            val root = JSONObject(entity.payloadJson)
            val word = root.optString("word", entity.word).trim()
            val lang = root.optString("lang", entity.lang).trim().lowercase(Locale.US)
                .ifBlank { if (ArabicTextUtils.containsArabicLetters(word)) "ar" else "en" }
            val isAr = lang == "ar" || ArabicTextUtils.containsArabicLetters(word)
            val phonetic = if (isAr) "" else root.optString("phonetic", entity.phonetic).trim()
            val rawSenses = mutableListOf<SenseItem>()
            val syns = mutableListOf<String>()
            val ants = mutableListOf<String>()

            val jsonTags = readJsonStringList(root.optJSONArray("tags"))
            val tags = if (jsonTags.isNotEmpty()) {
                jsonTags
            } else {
                entity.tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }

            val sensesArr = root.optJSONArray("senses")
            if (sensesArr != null) {
                for (i in 0 until sensesArr.length()) {
                    val sObj = sensesArr.optJSONObject(i) ?: continue
                    val pos = sObj.optString("partOfSpeech", "word").trim().ifBlank { "word" }
                    val def = sObj.optString("definition", "").trim()
                    if (def.isBlank()) continue
                    val defArabic = sObj.optString("definitionArabic", "").trim().takeIf { it.isNotEmpty() }

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
                            definitionArabic = defArabic,
                            definitionArabicIsMachine = false,
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
                word = entity.normalizedWord,
                displayWord = word.ifBlank { entity.word },
                isArabicWord = isAr,
                lang = lang,
                usIpa = phonetic,
                tags = tags,
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

        // Match US and UK human recordings (normalize http -> https & // -> https:)
        val usPhonetic = phonetics.firstOrNull {
            val audio = it.audio?.trim().orEmpty().lowercase(Locale.US)
            audio.isNotEmpty() && (audio.contains("-us") || audio.contains("_us"))
        } ?: phonetics.firstOrNull {
            val audio = it.audio?.trim().orEmpty().lowercase(Locale.US)
            audio.isNotEmpty() &&
                !audio.contains("-uk") && !audio.contains("_uk") &&
                !audio.contains("-au") && !audio.contains("_au") &&
                !audio.contains("-ca") && !audio.contains("-nz") &&
                !audio.contains("-in") && !audio.contains("-za")
        }
        val ukPhonetic = phonetics.firstOrNull {
            val audio = it.audio?.trim().orEmpty().lowercase(Locale.US)
            audio.isNotEmpty() && (audio.contains("-uk") || audio.contains("_uk"))
        }

        return PhoneticsTuple(
            usIpa = usPhonetic?.text?.trim()?.takeIf { it.isNotBlank() } ?: fallbackIpa,
            ukIpa = ukPhonetic?.text?.trim()?.takeIf { it.isNotBlank() } ?: fallbackIpa,
            usAudio = normalizeAudioUrl(usPhonetic?.audio),
            ukAudio = normalizeAudioUrl(ukPhonetic?.audio)
        )
    }

    private fun normalizeAudioUrl(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
            else -> trimmed
        }
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
        normalizedQuery: String,
        displayQuery: String,
        resolvedWord: String,
        previewSummary: String
    ) {
        dao.upsertHistory(
            SearchHistoryEntity(
                queryKey = queryKey,
                normalizedQuery = normalizedQuery,
                displayQuery = displayQuery,
                resolvedEnglishWord = resolvedWord,
                previewSummary = previewSummary,
                timestamp = System.currentTimeMillis()
            )
        )
        dao.trimHistoryTo50()
    }

    private inline fun <reified T> decodeOrNull(raw: String): T? =
        runCatching { json.decodeFromString<T>(raw) }.getOrNull()

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
        "particle" -> "أداة"
        "numeral" -> "عدد"
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

    companion object {
        internal fun parseDatamuseWordNetSenses(dtos: List<DatamuseWordDto>): List<SenseItem> {
            val result = ArrayList<SenseItem>()
            var idx = 1
            for (dto in dtos) {
                for (rawDef in dto.defs.orEmpty()) {
                    val tabIdx = rawDef.indexOf('\t')
                    val posCode = if (tabIdx > 0) rawDef.substring(0, tabIdx).trim() else ""
                    val defText = (if (tabIdx > 0) rawDef.substring(tabIdx + 1) else rawDef).trim()
                    if (defText.isBlank()) continue
                    val pos = mapWordNetPos(posCode)
                    val usageInfo = ArabicTextUtils.extractUsageFromPlainDefinition(defText)
                    result.add(
                        SenseItem(
                            index = idx++,
                            partOfSpeech = pos,
                            partOfSpeechArabic = mapPosStatic(pos),
                            definition = defText,
                            usageLabels = usageInfo.usageLabels,
                            spellingVariantLabel = usageInfo.spellingVariantLabel,
                            otherSpellingWord = usageInfo.otherSpellingWord,
                            source = SenseSource.WORDNET
                        )
                    )
                }
            }
            return result
        }

        private fun mapWordNetPos(code: String): String = when (code.lowercase(Locale.US)) {
            "n" -> "noun"
            "v" -> "verb"
            "adj", "s", "a" -> "adjective"
            "adv", "r" -> "adverb"
            else -> "word"
        }

        private fun mapPosStatic(pos: String): String = when (pos.lowercase(Locale.US)) {
            "noun" -> "اسم"
            "verb" -> "فعل"
            "adjective" -> "صفة"
            "adverb" -> "ظرف"
            else -> pos.ifBlank { "كلمة" }
        }
    }
}
