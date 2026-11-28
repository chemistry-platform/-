package com.example.data.repository

import android.os.Process
import android.util.Log
import android.util.LruCache
import com.example.core.Accent
import com.example.core.AppLanguage
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
import com.example.data.local.PackIdiomEntity
import com.example.data.local.ReverseArabicEntryEntity
import com.example.data.local.SearchHistoryEntity
import com.example.data.local.TranslationCacheEntity
import com.example.data.local.WordIndexEntity
import com.example.data.remote.DatamuseApi
import com.example.data.remote.DatamuseWordDto
import com.example.data.remote.DictionaryEntryDto
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.MyMemoryApi
import com.example.data.remote.TatoebaApi
import com.example.data.remote.WikimediaCommonsApi
import com.example.data.remote.WiktionaryApi
import com.example.domain.model.ArabicVocalizedGroup
import com.example.domain.model.AwlWordItem
import com.example.domain.model.ConfusableCandidate
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.ContextualWordChip
import com.example.domain.model.DetectedIdiom
import com.example.domain.model.InstantTranslationResult
import com.example.domain.model.LiveSearchPage
import com.example.domain.model.PhraseDetailItem
import com.example.domain.model.PhraseSheetData
import com.example.domain.model.PosSenseGroup
import com.example.domain.model.ReverseEnglishMatch
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SenseItem
import com.example.domain.model.SenseSource
import com.example.domain.model.SentenceLookupResult
import com.example.domain.model.TatoebaSentence
import com.example.domain.model.WordEntry
import com.example.domain.model.WordFamilyItem
import com.example.domain.model.WordFormItem
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
private const val SOURCE_TIMEOUT_MS = 4_000L

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
    val transliteration: String = "",
    val wordForms: List<WordFormItem> = emptyList()
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
    private val wikimediaCommonsApi: WikimediaCommonsApi? = null,
    private val myMemoryApi: MyMemoryApi? = null
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

    // --- Item 1: Local-only background prebuild for top live-search candidates (0 internet, 0 audio) ---
    fun peekReadyEntry(rawQuery: String): WordEntry? {
        val trimmed = rawQuery.trim().replace(Regex("\\s+"), " ")
        if (trimmed.isEmpty()) return null
        val isArabic = ArabicTextUtils.containsArabicLetters(trimmed)
        val memKey = if (isArabic) {
            if (ArabicTextUtils.hasArabicDiacritics(trimmed)) {
                "ar_v:${ArabicTextUtils.canonicalizeArabicWithDiacritics(trimmed)}"
            } else {
                "ar:${ArabicTextUtils.normalizeArabicForMatching(trimmed)}"
            }
        } else {
            "en:${trimmed.lowercase(Locale.US)}"
        }
        return synchronized(readyEntryCache) { readyEntryCache.get(memKey) }
    }

    fun prefetchWord(rawWord: String) {
        prebuildLocalCandidates(listOf(rawWord))
    }

    fun prebuildLocalCandidates(rawWords: List<String>) {
        val candidates = rawWords.map { it.trim() }
            .filter { it.isNotBlank() && !ArabicTextUtils.isMultiWordQuery(it) }
            .distinct()
            .take(10)
        if (candidates.isEmpty()) return

        prefetchScope.launch {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
            for (trimmed in candidates) {
                try {
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
                    val alreadyCached = synchronized(readyEntryCache) { readyEntryCache.get(memKey) != null }
                    if (alreadyCached) continue

                    if (isArabic) {
                        val slugWithHamza = ArabicTextUtils.stripArabicDiacriticsKeepHamza(trimmed)
                        val hasExactTashkeel = ArabicTextUtils.hasArabicDiacritics(trimmed)
                        val canonicalExact = ArabicTextUtils.canonicalizeArabicWithDiacritics(trimmed)
                        val packRow = dao.getOfflinePackWordByLang(slugWithHamza, "ar")
                            ?: dao.getOfflinePackWordByLang(normalized, "ar")
                            ?: dao.getOfflinePackWord(normalized)
                        val offlinePack = packRow?.let { parseOfflinePackEntity(it) }
                        val wikPayload = dao.getApiCache("$normalized|$SRC_WIKTIONARY_AR")
                            ?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
                        val tatoebaPayload = dao.getApiCache("$normalized|$SRC_TATOEBA_AR")
                            ?.let { decodeOrNull<TatoebaCachedPayload>(it.payloadJson) }
                        val reverseRows = dao.getReverseArabicMatches(normalized)
                        val reverseGroups = if (reverseRows.isNotEmpty()) {
                            val enAudioMap = HashMap<String, WordIndexEntity>(reverseRows.size)
                            reverseRows.map { it.englishWord.lowercase(Locale.US) }
                                .distinct()
                                .chunked(400)
                                .forEach { chunk ->
                                    dao.getWordIndexesByNormalized(chunk).forEach { idx ->
                                        enAudioMap[idx.normalizedWord] = idx
                                    }
                                }
                            buildReverseArabicGroups(reverseRows, enAudioMap, trimmed, hasExactTashkeel, canonicalExact)
                        } else emptyList()

                        val built = mergeArabicWordEntry(
                            normalizedKey = normalized,
                            displayWord = trimmed,
                            canonicalExact = canonicalExact,
                            hasExplicitTashkeel = hasExactTashkeel,
                            offlinePack = offlinePack,
                            wiktionary = wikPayload,
                            tatoeba = tatoebaPayload,
                            reverseGroups = reverseGroups,
                            machineTranslations = emptyList(),
                            missingTranslationPack = false,
                            isFromCache = true
                        )?.let { applyRoomTranslationCache(it) }
                        if (built != null && built.posGroups.isNotEmpty()) {
                            synchronized(readyEntryCache) { readyEntryCache.put(memKey, built) }
                        }
                    } else {
                        val packRow = dao.getOfflinePackWordByLang(normalized, "en")
                            ?: dao.getOfflinePackWord(normalized)
                        val offlinePack = packRow?.let { parseOfflinePackEntity(it) }
                        val freeDict = dao.getApiCache("$normalized|$SRC_FREEDICT")
                            ?.let { decodeOrNull<FreeDictCachedPayload>(it.payloadJson) }
                        val wik = dao.getApiCache("$normalized|$SRC_WIKTIONARY")
                            ?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
                        val datamuse = dao.getApiCache("$normalized|$SRC_DATAMUSE")
                            ?.let { decodeOrNull<DatamuseCachedPayload>(it.payloadJson) }
                        val tatoeba = dao.getApiCache("$normalized|$SRC_TATOEBA")
                            ?.let { decodeOrNull<TatoebaCachedPayload>(it.payloadJson) }
                        val wikimediaAudio = dao.getApiCache("$normalized|$SRC_WIKIMEDIA_AUDIO")
                            ?.let { decodeOrNull<WikimediaAudioCachedPayload>(it.payloadJson) }
                        val containingIdioms = dao.findIdiomsContainingWord(normalized, limit = 20)

                        val built = mergeWordEntry(
                            word = normalized,
                            offlinePack = offlinePack,
                            freeDict = freeDict,
                            wiktionary = wik,
                            datamuse = datamuse,
                            tatoeba = tatoeba,
                            wikimediaAudio = wikimediaAudio,
                            machineTranslations = emptyList(),
                            missingTranslationPack = false,
                            isFromCache = true,
                            containingPackIdioms = containingIdioms
                        )?.let { applyRoomTranslationCache(it) }
                        if (built != null && built.posGroups.isNotEmpty()) {
                            synchronized(readyEntryCache) { readyEntryCache.put(memKey, built) }
                        }
                    }
                } catch (_: Exception) {
                    // Best-effort local-only prebuild
                }
            }
        }
    }

    suspend fun ensureWordReadyForOpen(
        rawQuery: String,
        timeoutMs: Long = 1450L
    ): WordEntry? {
        peekReadyEntry(rawQuery)?.let { return it }
        var resolved: WordEntry? = null
        withTimeoutOrNull(timeoutMs) {
            observeWordEntry(rawQuery, forceRefresh = false).collect { state ->
                if (state is WordLoadState.Success) {
                    resolved = state.entry
                    return@collect
                }
            }
        }
        return resolved ?: peekReadyEntry(rawQuery)
    }

    // --- Section 6 & 15.5: Live Indexed Prefix Search with POS Preview & Pagination ---
    suspend fun getLiveSuggestions(query: String): List<SearchSuggestion> =
        getLiveSuggestionsPaged(query = query, offset = 0, limit = 40).items

    private fun hasValidTargetPreview(item: SearchSuggestion, isArabicQuery: Boolean): Boolean {
        val preview = item.posPreview.ifBlank { item.summaryDefinition }.trim()
        if (preview.isBlank()) return false
        return if (!isArabicQuery) {
            ArabicTextUtils.containsArabicLetters(preview) ||
                (item.isIdiom && preview.removePrefix("idiom").trim().isNotBlank())
        } else {
            preview.any { it in 'a'..'z' || it in 'A'..'Z' }
        }
    }

    /**
     * Non-blocking live search flow: emits local/cached matches within ~50ms with no flicker,
     * supplementing from free online sources only if local matches are fewer than requested.
     */
    fun observeLiveSuggestions(
        query: String,
        limit: Int = 10
    ): Flow<LiveSearchPage> = flow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            emit(LiveSearchPage(emptyList(), hasMore = false))
            return@flow
        }
        val isArabic = ArabicTextUtils.containsArabicLetters(trimmed)
        val safeLimit = limit.coerceIn(1, 500)

        val localMerged = queryLocalSuggestionCandidates(trimmed, isArabic, fetchWindow = (safeLimit + 100).coerceAtMost(10_000))
        val localEnriched = enrichSuggestionsLocalOnly(localMerged.take(safeLimit + 20), isArabicQuery = isArabic)
            .filter { hasValidTargetPreview(it, isArabic) }
        val localVisible = localEnriched.take(safeLimit)
        val localHasMore = localEnriched.size > localVisible.size ||
            (localMerged.size > safeLimit + 20)

        if (localVisible.isNotEmpty() || !networkMonitor.isOnline()) {
            emit(LiveSearchPage(items = localVisible, hasMore = localHasMore))
        }

        if (networkMonitor.isOnline() && localVisible.size < safeLimit) {
            val onlinePage = getLiveSuggestionsPaged(query = trimmed, offset = 0, limit = safeLimit)
            if (onlinePage.items.isNotEmpty() && onlinePage != LiveSearchPage(localVisible, localHasMore)) {
                emit(onlinePage)
            } else if (localVisible.isEmpty()) {
                emit(onlinePage)
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun queryLocalSuggestionCandidates(
        trimmed: String,
        isArabic: Boolean,
        fetchWindow: Int
    ): List<SearchSuggestion> {
        val normalized = if (isArabic) {
            ArabicTextUtils.normalizeArabicForMatching(trimmed)
        } else {
            trimmed.lowercase(Locale.US)
        }
        val prefixEnd = ArabicTextUtils.computePrefixUpperBound(normalized)

        val candidates = ArrayList<SearchSuggestion>()

        // 1. Offline Pack Indexed Words (Prefix B-tree range + substring contains)
        val packPrefixRows = dao.searchOfflinePackPrefixRange(normalized, prefixEnd, limit = fetchWindow, offset = 0)
        val packContainsRows = if (packPrefixRows.size < fetchWindow) {
            dao.searchOfflinePackContains(normalized, normalized, prefixEnd, limit = fetchWindow - packPrefixRows.size, offset = 0)
        } else {
            emptyList()
        }
        (packPrefixRows + packContainsRows).forEach { row ->
            val itemIsAr = row.lang == "ar" || ArabicTextUtils.containsArabicLetters(row.word)
            val basePreview = row.previewSummary.ifBlank { row.summaryDefinition }
            val preview = if (basePreview.isBlank() || (basePreview.length in 130..145 && row.payloadJson.length > 250)) {
                parseOfflinePackEntity(row)?.let { LinguisticRanker.buildPosPreviewFromEntry(it) }
                    ?.takeIf { it.length > basePreview.length }
                    ?: basePreview
            } else {
                basePreview
            }
            candidates.add(
                SearchSuggestion(
                    word = row.word,
                    partOfSpeech = row.posTagsCsv.substringBefore(',').trim(),
                    summaryDefinition = preview,
                    posPreview = preview,
                    frequencyRank = row.frequencyRank,
                    isArabic = itemIsAr,
                    isLocal = true
                )
            )
        }

        // 2. Cached Word Index (Prefix B-tree range + contains)
        val indexPrefixRows = dao.searchIndexPrefixRange(normalized, prefixEnd, limit = fetchWindow, offset = 0)
        val indexContainsRows = if (indexPrefixRows.size < fetchWindow) {
            dao.searchIndexContains(normalized, normalized, prefixEnd, limit = fetchWindow - indexPrefixRows.size, offset = 0)
        } else {
            emptyList()
        }
        (indexPrefixRows + indexContainsRows).forEach { row ->
            val itemIsAr = row.lang == "ar" || ArabicTextUtils.containsArabicLetters(row.word)
            val preview = row.previewSummary.ifBlank {
                val abbr = LinguisticRanker.abbreviatePosTag(row.partOfSpeech)
                if (abbr.isNotBlank() && row.summaryDefinition.isNotBlank()) "$abbr ${row.summaryDefinition}" else row.summaryDefinition
            }
            candidates.add(
                SearchSuggestion(
                    word = row.word,
                    partOfSpeech = mapPosToArabic(row.partOfSpeech),
                    summaryDefinition = preview,
                    posPreview = preview,
                    frequencyRank = row.frequencyRank,
                    usAudioUrl = if (itemIsAr) null else row.usAudioUrl,
                    ukAudioUrl = if (itemIsAr) null else row.ukAudioUrl,
                    isArabic = itemIsAr,
                    isLocal = true
                )
            )
        }

        // 3. Language-specific local indexes
        if (isArabic) {
            val revPrefixRows = dao.searchReverseArabicByPrefix(normalized, prefixEnd, limit = fetchWindow * 2, offset = 0)
            val revContainsRows = if (revPrefixRows.size < fetchWindow) {
                dao.searchReverseArabicContains(normalized, normalized, prefixEnd, limit = fetchWindow, offset = 0)
            } else {
                emptyList()
            }
            val byVocalized = (revPrefixRows + revContainsRows).groupBy {
                ArabicTextUtils.canonicalizeArabicWithDiacritics(it.vocalizedArabic)
                    .ifBlank { it.normalizedArabic }
            }
            byVocalized.forEach { (vocalizedHead, rows) ->
                val bestRank = rows.minOfOrNull { it.frequencyRank } ?: Int.MAX_VALUE
                val preview = formatReverseArabicPreview(rows)
                candidates.add(
                    SearchSuggestion(
                        word = vocalizedHead,
                        partOfSpeech = rows.firstOrNull()?.partOfSpeech.orEmpty(),
                        summaryDefinition = preview,
                        posPreview = preview,
                        frequencyRank = bestRank,
                        isArabic = true,
                        isLocal = true
                    )
                )
            }

            dao.searchHistoryByPrefix(normalized, limit = 10).forEach { h ->
                if (ArabicTextUtils.containsArabicLetters(h.displayQuery)) {
                    candidates.add(
                        SearchSuggestion(
                            word = h.displayQuery,
                            summaryDefinition = h.previewSummary,
                            posPreview = h.previewSummary,
                            isArabic = true,
                            isLocal = true
                        )
                    )
                }
            }
        } else {
            // Matching offline idioms & phrasal verbs
            val idiomPrefixRows = dao.searchPackIdiomsByPrefix(normalized, prefixEnd, limit = 15, offset = 0)
            val idiomContainsRows = if (normalized.length >= 3 && idiomPrefixRows.size < 10) {
                dao.searchPackIdiomsByContains(normalized, normalized, prefixEnd, limit = 10, offset = 0)
            } else {
                emptyList()
            }
            (idiomPrefixRows + idiomContainsRows).forEach { idiom ->
                val preview = buildString {
                    append("idiom ")
                    append(idiom.arabicMeaning.ifBlank { idiom.englishDefinition })
                }.trim()
                candidates.add(
                    SearchSuggestion(
                        word = idiom.phrase,
                        partOfSpeech = "idiom",
                        summaryDefinition = preview,
                        posPreview = preview,
                        frequencyRank = idiom.frequencyRank,
                        isArabic = false,
                        isLocal = true,
                        isIdiom = true
                    )
                )
            }
        }

        return deduplicateAndMergeSuggestions(candidates, isArabic)
            .sortedWith(rankedSuggestionComparator(normalized, isArabic))
    }

    suspend fun getLiveSuggestionsPaged(
        query: String,
        offset: Int = 0,
        limit: Int = 40
    ): LiveSearchPage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext LiveSearchPage(emptyList(), hasMore = false)
        val isArabic = ArabicTextUtils.containsArabicLetters(trimmed)
        val normalized = if (isArabic) {
            ArabicTextUtils.normalizeArabicForMatching(trimmed)
        } else {
            trimmed.lowercase(Locale.US)
        }
        val safeLimit = limit.coerceIn(1, 500)
        val fetchWindow = (offset + safeLimit + 100).coerceAtMost(10_000)

        val dedupedLocal = queryLocalSuggestionCandidates(trimmed, isArabic, fetchWindow)

        if (isArabic) {
            val page = dedupedLocal.drop(offset).take(safeLimit).toMutableList()
            if (offset == 0 &&
                trimmed.length >= 2 &&
                networkMonitor.isOnline() &&
                page.none { ArabicTextUtils.normalizeArabicForMatching(it.word) == normalized }
            ) {
                val onlineOnly = enrichArabicSuggestionsWithEnglish(
                    listOf(
                        SearchSuggestion(
                            word = trimmed,
                            partOfSpeech = "",
                            summaryDefinition = "",
                            posPreview = "",
                            frequencyRank = 0,
                            isArabic = true,
                            isLocal = false
                        )
                    )
                )
                val first = onlineOnly.firstOrNull()
                if (first != null && hasValidTargetPreview(first, isArabicQuery = true)) {
                    page.add(0, first)
                }
            }
            val enriched = enrichSuggestionsWithTranslations(page.take(safeLimit), isArabicQuery = true)
                .filter { hasValidTargetPreview(it, isArabicQuery = true) }
            val hasMore = dedupedLocal.size > offset + page.size
            return@withContext LiveSearchPage(items = enriched, hasMore = hasMore)
        }

        val localWithMeaningCount = dedupedLocal.count { hasValidTargetPreview(it, isArabicQuery = false) }
        if (offset > 0 || !networkMonitor.isOnline() || localWithMeaningCount >= safeLimit) {
            val page = dedupedLocal.drop(offset).take(safeLimit)
            val enriched = enrichSuggestionsWithTranslations(page, isArabicQuery = false)
                .filter { hasValidTargetPreview(it, isArabicQuery = false) }
            val hasMore = dedupedLocal.size > offset + page.size
            return@withContext LiveSearchPage(items = enriched, hasMore = hasMore)
        }

        // Supplement with Datamuse on initial English page when local translated entries < requested limit
        val maxDatamuse = safeLimit.coerceIn(25, 60)
        val remoteWords = coroutineScope {
            val sugDeferred = async {
                withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    runCatching { datamuseApi.getAutocompleteSuggestions(normalized, max = maxDatamuse) }
                        .getOrDefault(emptyList())
                }.orEmpty()
            }
            val spDeferred = async {
                withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    runCatching { datamuseApi.getSpellingMatches("$normalized*", max = maxDatamuse) }
                        .getOrDefault(emptyList())
                }.orEmpty()
            }
            (sugDeferred.await() + spDeferred.await())
        }

        val remoteSuggestions = remoteWords.mapNotNull { dto ->
            val w = dto.word?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lowerW = w.lowercase(Locale.US)
            val cachedIndex = dao.getWordIndex(lowerW)
            val posShort = mapDatamusePosShort(dto.tags)
            val rawDef = cachedIndex?.previewSummary?.takeIf { it.isNotBlank() }
                ?: cachedIndex?.summaryDefinition.orEmpty()
            val preview = when {
                rawDef.isNotBlank() && posShort.isNotBlank() && !rawDef.startsWith(posShort) -> "$posShort $rawDef"
                rawDef.isNotBlank() -> rawDef
                else -> posShort
            }
            SearchSuggestion(
                word = w,
                partOfSpeech = mapDatamusePos(dto.tags),
                summaryDefinition = preview,
                posPreview = preview,
                frequencyRank = cachedIndex?.frequencyRank ?: (Int.MAX_VALUE - 1),
                usAudioUrl = cachedIndex?.usAudioUrl,
                ukAudioUrl = cachedIndex?.ukAudioUrl,
                isArabic = false,
                isLocal = cachedIndex != null
            )
        }

        val mergedAll = deduplicateAndMergeSuggestions(dedupedLocal + remoteSuggestions, isArabic = false)
            .sortedWith(rankedSuggestionComparator(normalized, isArabic = false))
        val pageWindow = mergedAll.drop(offset).take((safeLimit + 10).coerceAtMost(500))
        val enriched = enrichSuggestionsWithTranslations(pageWindow, isArabicQuery = false)
            .filter { hasValidTargetPreview(it, isArabicQuery = false) }
            .take(safeLimit)
        val hasMore = mergedAll.size > offset + pageWindow.size || mergedAll.drop(offset).size > enriched.size
        LiveSearchPage(items = enriched, hasMore = hasMore)
    }

    private fun deduplicateAndMergeSuggestions(
        items: List<SearchSuggestion>,
        isArabic: Boolean
    ): List<SearchSuggestion> {
        val map = LinkedHashMap<String, SearchSuggestion>(items.size)
        for (item in items) {
            val key = if (isArabic) {
                val canonical = ArabicTextUtils.canonicalizeArabicWithDiacritics(item.word)
                canonical.ifBlank { ArabicTextUtils.normalizeArabicForMatching(item.word) }
            } else {
                item.word.trim().lowercase(Locale.US)
            }
            if (key.isEmpty()) continue
            val existing = map[key]
            if (existing == null) {
                map[key] = item
            } else {
                val bestPreview = when {
                    existing.posPreview.isNotBlank() && item.posPreview.isNotBlank() -> {
                        val existingHasAr = ArabicTextUtils.containsArabicLetters(existing.posPreview)
                        val itemHasAr = ArabicTextUtils.containsArabicLetters(item.posPreview)
                        when {
                            !isArabic && itemHasAr && !existingHasAr -> item.posPreview
                            !isArabic && existingHasAr && !itemHasAr -> existing.posPreview
                            isArabic && !itemHasAr && existingHasAr -> item.posPreview
                            isArabic && !existingHasAr && itemHasAr -> existing.posPreview
                            item.posPreview.length > existing.posPreview.length -> item.posPreview
                            else -> existing.posPreview
                        }
                    }
                    else -> existing.posPreview.ifBlank { item.posPreview }.ifBlank {
                        existing.summaryDefinition.ifBlank { item.summaryDefinition }
                    }
                }
                val bestRank = minOf(existing.frequencyRank, item.frequencyRank)
                map[key] = existing.copy(
                    partOfSpeech = existing.partOfSpeech.ifBlank { item.partOfSpeech },
                    summaryDefinition = bestPreview,
                    posPreview = bestPreview,
                    frequencyRank = bestRank,
                    usAudioUrl = existing.usAudioUrl ?: item.usAudioUrl,
                    ukAudioUrl = existing.ukAudioUrl ?: item.ukAudioUrl,
                    isLocal = existing.isLocal || item.isLocal,
                    isIdiom = existing.isIdiom || item.isIdiom
                )
            }
        }
        return map.values.toList()
    }

    private fun rankedSuggestionComparator(prefix: String, isArabic: Boolean): Comparator<SearchSuggestion> {
        fun norm(w: String): String = if (isArabic) {
            ArabicTextUtils.normalizeArabicForMatching(w)
        } else {
            w.trim().lowercase(Locale.US)
        }
        fun bucket(w: String): Int {
            val n = norm(w)
            return when {
                n == prefix -> 0
                n.startsWith(prefix) -> 1
                else -> 2
            }
        }
        return compareBy<SearchSuggestion> { bucket(it.word) }
            .thenBy { it.frequencyRank }
            .thenBy { it.word.length }
            .thenByDescending { it.isLocal }
    }

    private fun formatReverseArabicPreview(rows: List<ReverseArabicEntryEntity>): String {
        val byPos = rows.groupBy { LinguisticRanker.abbreviatePosTag(it.partOfSpeech) }
        val parts = ArrayList<String>(byPos.size)
        for ((abbr, group) in byPos) {
            val words = group.map { it.englishWord }.distinct().joinToString("؛ ")
            if (words.isNotBlank()) {
                parts.add(if (abbr.isNotBlank()) "$abbr $words" else words)
            }
        }
        return parts.joinToString("؛ ")
    }

    private fun formatArabicMeaningsFromReverseRows(rows: List<ReverseArabicEntryEntity>): String {
        if (rows.isEmpty()) return ""
        val byPos = rows.groupBy { LinguisticRanker.abbreviatePosTag(it.partOfSpeech) }
        val parts = ArrayList<String>(byPos.size)
        for ((abbr, group) in byPos) {
            val meanings = group.map {
                val voc = ArabicTextUtils.canonicalizeArabicWithDiacritics(it.vocalizedArabic)
                if (voc.isNotBlank() && ArabicTextUtils.containsArabicLetters(voc)) voc else it.normalizedArabic
            }.filter { it.isNotBlank() && ArabicTextUtils.containsArabicLetters(it) }
            .distinct()
            .joinToString("؛ ")
            if (meanings.isNotBlank()) {
                parts.add(if (abbr.isNotBlank()) "$abbr $meanings" else meanings)
            }
        }
        return parts.joinToString("؛ ")
    }

    private fun mergePosPreviews(
        preview1: String,
        preview2: String,
        isTargetArabic: Boolean
    ): String {
        if (preview1.isBlank()) return preview2
        if (preview2.isBlank()) return preview1
        val p1HasTarget = if (isTargetArabic) ArabicTextUtils.containsArabicLetters(preview1)
                          else preview1.any { it in 'a'..'z' || it in 'A'..'Z' }
        val p2HasTarget = if (isTargetArabic) ArabicTextUtils.containsArabicLetters(preview2)
                          else preview2.any { it in 'a'..'z' || it in 'A'..'Z' }
        if (!p1HasTarget && p2HasTarget) return preview2
        if (!p2HasTarget && p1HasTarget) return preview1
        if (!p1HasTarget && !p2HasTarget) return if (preview1.length >= preview2.length) preview1 else preview2

        val posMap = LinkedHashMap<String, LinkedHashSet<String>>()
        val splitRegex = Regex("؛\\s+(?=(?:v\\.|n\\.|adj\\.|adv\\.|prep\\.|conj\\.|pron\\.|interj\\.|det\\.|part\\.|num\\.|idiom\\s|phr\\.|prov\\.))")
        val leadTagRegex = Regex("^((?:v\\.|n\\.|adj\\.|adv\\.|prep\\.|conj\\.|pron\\.|interj\\.|det\\.|part\\.|num\\.|idiom|phr\\.|prov\\.)\\s*)(.*)$")

        fun parseIntoMap(p: String) {
            val segments = p.split(splitRegex)
            for (seg in segments) {
                val match = leadTagRegex.find(seg.trim())
                val tag = match?.groupValues?.get(1)?.trim().orEmpty()
                val text = match?.groupValues?.get(2)?.trim() ?: seg.trim()
                val bucket = posMap.getOrPut(tag) { LinkedHashSet() }
                text.split('؛', ';').forEach { g ->
                    val cleanG = g.trim()
                    if (cleanG.isNotBlank()) bucket.add(cleanG)
                }
            }
        }
        parseIntoMap(preview1)
        parseIntoMap(preview2)
        val sections = ArrayList<String>(posMap.size)
        for ((tag, glosses) in posMap) {
            if (glosses.isEmpty()) continue
            val joined = glosses.joinToString("؛ ")
            sections.add(if (tag.isNotBlank()) "$tag $joined" else joined)
        }
        return sections.joinToString("؛ ")
    }

    private suspend fun enrichSuggestionsLocalOnly(
        items: List<SearchSuggestion>,
        isArabicQuery: Boolean
    ): List<SearchSuggestion> {
        if (items.isEmpty()) return items
        return if (!isArabicQuery) {
            enrichEnglishSuggestionsWithArabic(items, allowOnline = false)
        } else {
            enrichArabicSuggestionsWithEnglish(items, allowOnline = false)
        }
    }

    private suspend fun enrichSuggestionsWithTranslations(
        items: List<SearchSuggestion>,
        isArabicQuery: Boolean
    ): List<SearchSuggestion> {
        if (items.isEmpty()) return items
        return if (!isArabicQuery) {
            enrichEnglishSuggestionsWithArabic(items, allowOnline = true)
        } else {
            enrichArabicSuggestionsWithEnglish(items, allowOnline = true)
        }
    }

    private suspend fun enrichEnglishSuggestionsWithArabic(
        items: List<SearchSuggestion>,
        allowOnline: Boolean = true
    ): List<SearchSuggestion> {
        val englishWords = items.map { it.word.trim().lowercase(Locale.US) }.distinct()
        val reverseRows = dao.getReverseArabicForEnglishWords(englishWords)
        val reverseByWord = reverseRows.groupBy { it.englishWord.trim().lowercase(Locale.US) }

        val firstPass = items.map { item ->
            val lowerWord = item.word.trim().lowercase(Locale.US)
            val revRowsForWord = reverseByWord[lowerWord].orEmpty()
            val revMeaning = if (revRowsForWord.isNotEmpty()) formatArabicMeaningsFromReverseRows(revRowsForWord) else ""

            val existingPreview = item.posPreview.ifBlank {
                if (ArabicTextUtils.containsArabicLetters(item.summaryDefinition)) item.summaryDefinition else ""
            }
            val existingHasAr = ArabicTextUtils.containsArabicLetters(existingPreview)

            val merged = when {
                revMeaning.isNotBlank() && existingHasAr -> mergePosPreviews(existingPreview, revMeaning, isTargetArabic = true)
                revMeaning.isNotBlank() -> revMeaning
                existingHasAr -> existingPreview
                else -> ""
            }

            if (merged.isNotBlank()) {
                item.copy(
                    posPreview = merged,
                    summaryDefinition = if (item.summaryDefinition.isBlank() || !ArabicTextUtils.containsArabicLetters(item.summaryDefinition)) merged else item.summaryDefinition
                )
            } else {
                var fallbackAr = ""
                val packWord = dao.getOfflinePackWord(lowerWord)
                if (packWord != null && packWord.payloadJson.isNotBlank()) {
                    val entry = parseOfflinePackEntity(packWord)
                    if (entry != null) {
                        val fromEntry = LinguisticRanker.buildPosPreviewFromEntry(entry)
                        if (ArabicTextUtils.containsArabicLetters(fromEntry)) {
                            fallbackAr = fromEntry
                        }
                    }
                }

                if (fallbackAr.isBlank()) {
                    val cachedIdx = dao.getWordIndex(lowerWord)
                    if (cachedIdx != null && ArabicTextUtils.containsArabicLetters(cachedIdx.previewSummary)) {
                        fallbackAr = cachedIdx.previewSummary
                    }
                }

                if (fallbackAr.isBlank()) {
                    val roomKey = translationCacheKey("whole|${TranslateLanguage.ENGLISH}", TranslateLanguage.ARABIC, item.word)
                    val cachedTr = dao.getTranslationCacheBatch(listOf(roomKey)).firstOrNull()?.translatedText?.trim()
                    if (!cachedTr.isNullOrBlank() && ArabicTextUtils.containsArabicLetters(cachedTr)) {
                        val abbr = LinguisticRanker.abbreviatePosTag(item.partOfSpeech)
                        fallbackAr = if (abbr.isNotBlank() && !cachedTr.startsWith(abbr)) "$abbr $cachedTr" else cachedTr
                    }
                }

                if (fallbackAr.isBlank() && !item.isIdiom) {
                    val mtRes = translationManager.translateShortIfReady(item.word, TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
                    val tr = mtRes.getOrNull()?.trim()
                    if (!tr.isNullOrBlank() && ArabicTextUtils.containsArabicLetters(tr)) {
                        val abbr = LinguisticRanker.abbreviatePosTag(item.partOfSpeech)
                        fallbackAr = if (abbr.isNotBlank()) "$abbr $tr" else tr
                    }
                }

                if (fallbackAr.isNotBlank()) {
                    item.copy(
                        posPreview = fallbackAr,
                        summaryDefinition = fallbackAr
                    )
                } else {
                    item
                }
            }
        }

        if (!allowOnline || !networkMonitor.isOnline()) return firstPass

        // Online enrichment for ALL uncached English suggestions lacking Arabic meanings
        val needOnlineIndices = firstPass.mapIndexedNotNull { idx, item ->
            if (!ArabicTextUtils.containsArabicLetters(item.posPreview) && !item.isIdiom) idx else null
        }
        if (needOnlineIndices.isEmpty()) return firstPass

        val semaphore = Semaphore(6)
        val resolvedByIdx = coroutineScope {
            needOnlineIndices.map { idx ->
                async {
                    val item = firstPass[idx]
                    val preview = semaphore.withPermit {
                        fetchOnlinePreviewEnToAr(item.word, item.partOfSpeech)
                    }
                    idx to preview
                }
            }.awaitAll().filter { !it.second.isNullOrBlank() }.toMap()
        }
        if (resolvedByIdx.isEmpty()) return firstPass

        return firstPass.mapIndexed { idx, item ->
            val onlinePreview = resolvedByIdx[idx]
            if (!onlinePreview.isNullOrBlank()) {
                item.copy(posPreview = onlinePreview, summaryDefinition = onlinePreview)
            } else {
                item
            }
        }
    }

    private suspend fun enrichArabicSuggestionsWithEnglish(
        items: List<SearchSuggestion>,
        allowOnline: Boolean = true
    ): List<SearchSuggestion> {
        val normalizedWords = items.map { ArabicTextUtils.normalizeArabicForMatching(it.word) }.distinct()
        val reverseRows = dao.getReverseArabicForNormalizedWords(normalizedWords)
        val reverseByNorm = reverseRows.groupBy { it.normalizedArabic }

        val firstPass = items.map { item ->
            val normKey = ArabicTextUtils.normalizeArabicForMatching(item.word)
            val revRowsForWord = reverseByNorm[normKey].orEmpty()
            val revMeaning = if (revRowsForWord.isNotEmpty()) formatReverseArabicPreview(revRowsForWord) else ""

            val existingPreview = item.posPreview.ifBlank {
                if (item.summaryDefinition.any { it in 'a'..'z' || it in 'A'..'Z' }) item.summaryDefinition else ""
            }
            val existingHasEn = existingPreview.any { it in 'a'..'z' || it in 'A'..'Z' }

            val merged = when {
                revMeaning.isNotBlank() && existingHasEn -> mergePosPreviews(existingPreview, revMeaning, isTargetArabic = false)
                revMeaning.isNotBlank() -> revMeaning
                existingHasEn -> existingPreview
                else -> ""
            }

            if (merged.isNotBlank()) {
                item.copy(
                    posPreview = merged,
                    summaryDefinition = if (item.summaryDefinition.isBlank() || ArabicTextUtils.containsArabicLetters(item.summaryDefinition)) merged else item.summaryDefinition
                )
            } else {
                var fallbackEn = ""
                val packWord = dao.getOfflinePackWord(normKey)
                if (packWord != null && packWord.payloadJson.isNotBlank()) {
                    val entry = parseOfflinePackEntity(packWord)
                    if (entry != null) {
                        val enMeanings = LinguisticRanker.extractPosHeadwordMeanings(
                            entry,
                            maxPerPos = Int.MAX_VALUE,
                            language = AppLanguage.ENGLISH
                        )
                        val fromEntry = enMeanings.joinToString("؛ ") { group ->
                            val abbr = LinguisticRanker.abbreviatePosTag(group.partOfSpeech)
                            val joined = group.meanings.joinToString("؛ ")
                            if (abbr.isNotBlank()) "$abbr $joined" else joined
                        }.ifBlank { LinguisticRanker.buildPosPreviewFromEntry(entry) }
                        if (fromEntry.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                            fallbackEn = fromEntry
                        }
                    }
                }

                if (fallbackEn.isBlank()) {
                    val roomKey = translationCacheKey("whole|${TranslateLanguage.ARABIC}", TranslateLanguage.ENGLISH, item.word)
                    val cachedTr = dao.getTranslationCacheBatch(listOf(roomKey)).firstOrNull()?.translatedText?.trim()
                    if (!cachedTr.isNullOrBlank() && cachedTr.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                        val abbr = LinguisticRanker.abbreviatePosTag(item.partOfSpeech)
                        fallbackEn = if (abbr.isNotBlank() && !cachedTr.startsWith(abbr)) "$abbr $cachedTr" else cachedTr
                    }
                }

                if (fallbackEn.isBlank() && !item.isIdiom) {
                    val mtRes = translationManager.translateShortIfReady(item.word, TranslateLanguage.ARABIC, TranslateLanguage.ENGLISH)
                    val tr = mtRes.getOrNull()?.trim()
                    if (!tr.isNullOrBlank() && tr.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                        val abbr = LinguisticRanker.abbreviatePosTag(item.partOfSpeech)
                        fallbackEn = if (abbr.isNotBlank()) "$abbr $tr" else tr
                    }
                }

                if (fallbackEn.isNotBlank()) {
                    item.copy(
                        posPreview = fallbackEn,
                        summaryDefinition = fallbackEn
                    )
                } else {
                    item
                }
            }
        }

        if (!allowOnline || !networkMonitor.isOnline()) return firstPass

        // Online enrichment for ALL Arabic suggestions missing English preview
        val needOnlineIndices = firstPass.mapIndexedNotNull { idx, item ->
            val hasEn = item.posPreview.any { it in 'a'..'z' || it in 'A'..'Z' }
            if (!hasEn && !item.isIdiom) idx else null
        }
        if (needOnlineIndices.isEmpty()) return firstPass

        val semaphore = Semaphore(6)
        val resolvedByIdx = coroutineScope {
            needOnlineIndices.map { idx ->
                async {
                    val item = firstPass[idx]
                    val preview = semaphore.withPermit {
                        fetchOnlinePreviewArToEn(item.word, item.partOfSpeech)
                    }
                    idx to preview
                }
            }.awaitAll().filter { !it.second.isNullOrBlank() }.toMap()
        }
        if (resolvedByIdx.isEmpty()) return firstPass

        return firstPass.mapIndexed { idx, item ->
            val onlinePreview = resolvedByIdx[idx]
            if (!onlinePreview.isNullOrBlank()) {
                item.copy(posPreview = onlinePreview, summaryDefinition = onlinePreview)
            } else {
                item
            }
        }
    }

    // --- BUG 6: Live Instant Translation under Search Bar (cached first, non-blocking, strict non-literal idioms) ---
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

        // Strict Idiom Rule: Check curated & pack idioms as ONE unit first
        ContextualTranslationAnalyzer.lookupCuratedIdiom(cleaned, isArabic)?.let { curated ->
            val meaning = if (isArabic) curated.englishMeaning else curated.arabicMeaning
            return InstantTranslationResult(
                query = cleaned,
                translation = meaning,
                isArabicSource = isArabic,
                isMachine = false,
                missingPack = false,
                isLoading = false
            )
        }

        if (!isSingleWord && !isArabic) {
            val packIdiom = withContext(Dispatchers.IO) { dao.getPackIdiomExact(normalized) }
            if (packIdiom != null) {
                var ar = packIdiom.arabicMeaning.takeIf { it.isNotBlank() }
                var isMachine = false
                if (ar == null && packIdiom.englishDefinition.isNotBlank()) {
                    ar = translateDefinitionOnlyToArabic(packIdiom.englishDefinition)
                    isMachine = !ar.isNullOrBlank()
                }
                return InstantTranslationResult(
                    query = cleaned,
                    translation = ar,
                    isArabicSource = false,
                    isMachine = isMachine,
                    missingPack = false,
                    noMeaningFound = ar.isNullOrBlank(),
                    isLoading = false
                )
            }
        }

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
                    val idxCandidate = idx.previewSummary.ifBlank { idx.summaryDefinition }
                    if (idxCandidate.isNotBlank() &&
                        ((!isArabic && ArabicTextUtils.containsArabicLetters(idxCandidate)) ||
                            (isArabic && !ArabicTextUtils.containsArabicLetters(idxCandidate)))
                    ) {
                        cachedTranslation = idxCandidate
                    }
                }
                val pack = dao.getOfflinePackWord(normalized)
                if (pack != null) {
                    val parsedPack = parseOfflinePackEntity(pack)
                    val packFull = parsedPack?.let { LinguisticRanker.buildPosPreviewFromEntry(it) }
                        ?.takeIf { it.isNotBlank() }
                        ?: pack.previewSummary.ifBlank { pack.summaryDefinition }
                    if (packFull.isNotBlank() &&
                        ((!isArabic && ArabicTextUtils.containsArabicLetters(packFull)) ||
                            (isArabic && !ArabicTextUtils.containsArabicLetters(packFull))) &&
                        packFull.length >= (cachedTranslation?.length ?: 0)
                    ) {
                        cachedTranslation = packFull
                    }
                }
                if (isArabic) {
                    val rev = dao.getReverseArabicMatches(normalized)
                    if (rev.isNotEmpty()) {
                        val revFormatted = formatReverseArabicPreview(rev)
                        if (revFormatted.length > (cachedTranslation?.length ?: 0)) {
                            cachedTranslation = revFormatted
                        }
                    }
                }
            } else if (!isArabic) {
                // Check Wiktionary cache for multi-word English phrase/idiom so we translate its DEFINITION if needed
                val wik = dao.getApiCache("$normalized|$SRC_WIKTIONARY")
                    ?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
                    ?.takeIf { !it.notFound && it.senses.isNotEmpty() }
                if (wik != null) {
                    val firstSense = wik.senses.first()
                    cachedTranslation = firstSense.definitionArabic?.takeIf { it.isNotBlank() }
                        ?: translateDefinitionOnlyToArabic(firstSense.definition)
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

        // Never machine-translate short unknown English idioms word-by-word unless sentence-like or literal mode explicitly requested
        if (!isSingleWord && !isArabic && !literalMode && looksLikePhraseOrIdiomNotSentence(cleaned)) {
            val wikDef = withContext(Dispatchers.IO) {
                if (!networkMonitor.isOnline()) null
                else withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    fetchWiktionaryByLanguageBlock(
                        slug = cleaned.lowercase(Locale.US).replace(Regex("\\s+"), "_"),
                        langBlockKey = "en",
                        baseWord = cleaned,
                        extractPhrases = false
                    )
                }?.takeIf { !it.notFound }?.senses?.firstOrNull()
            }
            if (wikDef != null) {
                val arFromWik = wikDef.definitionArabic?.takeIf { it.isNotBlank() }
                    ?: translateDefinitionOnlyToArabic(wikDef.definition)
                return InstantTranslationResult(
                    query = cleaned,
                    translation = arFromWik,
                    isArabicSource = false,
                    isMachine = wikDef.definitionArabic.isNullOrBlank() && !arFromWik.isNullOrBlank(),
                    missingPack = false,
                    noMeaningFound = arFromWik.isNullOrBlank(),
                    usAudioUrl = usAudio,
                    ukAudioUrl = ukAudio,
                    isLoading = false
                )
            }
            return InstantTranslationResult(
                query = cleaned,
                translation = null,
                isArabicSource = false,
                isMachine = false,
                missingPack = false,
                noMeaningFound = true,
                usAudioUrl = usAudio,
                ukAudioUrl = ukAudio,
                isLoading = false
            )
        }

        val packReady = withContext(Dispatchers.IO) {
            translationManager.isPairDownloaded(srcLang, tgtLang)
        }
        val online = networkMonitor.isOnline()
        val (mt, _) = translateOnlineOrLocal(
            text = cleaned,
            sourceCode = srcLang,
            targetCode = tgtLang,
            cacheScope = cacheScope,
            packReadyHint = packReady
        )

        if (mt.isNullOrBlank() && !packReady && !online) {
            return InstantTranslationResult(
                query = cleaned,
                translation = null,
                isArabicSource = isArabic,
                isMachine = false,
                missingPack = true,
                usAudioUrl = usAudio,
                ukAudioUrl = ukAudio,
                isLoading = false
            )
        }

        return InstantTranslationResult(
            query = cleaned,
            translation = mt,
            isArabicSource = isArabic,
            isMachine = false,
            missingPack = false,
            usAudioUrl = usAudio,
            ukAudioUrl = ukAudio,
            isLoading = false
        )
    }

    /**
     * Heuristic distinguishing short unpunctuated 2..4 word phrases/idioms from full sentences
     * (sentences typically have >=5 words, punctuation like '.', '?', '!', or auxiliary verbs).
     */
    private fun looksLikePhraseOrIdiomNotSentence(cleaned: String): Boolean {
        if (cleaned.any { it == '.' || it == '?' || it == '!' || it == ',' }) return false
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size !in 2..5) return false
        val sentenceAux = setOf("is", "are", "was", "were", "am", "will", "would", "can", "could", "should", "did", "does", "do", "have", "has", "had")
        if (tokens.any { it.lowercase(Locale.US) in sentenceAux }) return false
        return true
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
            val translated = translateBatchOnlineOrLocal(
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

        if (arMissing.isNotEmpty()) {
            val translated = translateBatchOnlineOrLocal(
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

        // Strict non-literal handling for multi-word English idioms / phrasal verbs
        if (!isArabic && ArabicTextUtils.isMultiWordQuery(phrase)) {
            val packReady = withContext(Dispatchers.IO) {
                translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
            }
            val online = networkMonitor.isOnline()
            val strictItem = resolveEnglishIdiomStrict(
                phrase = phrase,
                baseWord = "",
                category = "IDIOM",
                isOnline = online,
                packReady = packReady
            )
            return false to PhraseSheetData(
                phrase = phrase,
                englishMeaning = strictItem.englishMeaning,
                arabicMeaning = strictItem.arabicMeaning,
                isArabicMachine = false,
                missingArabicPack = !online && !packReady && strictItem.englishMeaning.isNotBlank() && strictItem.arabicMeaning.isNullOrBlank(),
                noMeaningFound = strictItem.noMeaningFound,
                example = strictItem.example,
                exampleArabic = strictItem.exampleArabic,
                exampleIsMachine = false,
                exampleAudioUrl = strictItem.exampleAudioUrl,
                isLoading = false
            )
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

        val online = networkMonitor.isOnline()
        if (meaning.isNullOrBlank()) {
            val (translated, _) = translateOnlineOrLocal(
                text = phrase,
                sourceCode = srcLang,
                targetCode = tgtLang,
                cacheScope = "whole|$srcLang"
            )
            meaning = translated
            if (meaning.isNullOrBlank() && !online) {
                missingPack = !withContext(Dispatchers.IO) {
                    translationManager.isPairDownloaded(srcLang, tgtLang)
                }
            }
        }

        // Fetch one real example sentence from Tatoeba if online
        val tatoebaExample = withContext(Dispatchers.IO) {
            if (!online) null
            else {
                withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
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
            isArabicMachine = false,
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

    // --- Sections 4, 14 & Item 13: Contextual Sentence & Idiom Recognition (Both Directions, Ready-Before-Show) ---
    fun observeSentenceLookup(rawText: String): Flow<SentenceLookupResult> = channelFlow {
        val cleaned = rawText.trim().replace(Regex("\\s+"), " ")
        val isArabic = ArabicTextUtils.containsArabicLetters(cleaned)
        val wordTokens = ArabicTextUtils.extractWordTokens(cleaned)
        val wordsCount = wordTokens.size
        val literalMode = translationManager.isLiteralTranslationEnabled()
        val srcLang = if (isArabic) TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH
        val tgtLang = if (isArabic) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC
        val cacheScope = if (literalMode) "lit|$srcLang" else "whole|$srcLang"

        var machineTranslation: String? = null
        var missingPack = false
        var tatoebaList: List<TatoebaSentence> = emptyList()
        var directPhraseFound = false
        var detectedIdioms: List<DetectedIdiom> = emptyList()
        var chipMeanings: Map<String, String> = emptyMap()

        coroutineScope {
            val mtDeferred = async(Dispatchers.IO) {
                if (!literalMode) {
                    ContextualTranslationAnalyzer.findDirectContextualOverride(cleaned, srcLang, tgtLang)?.let { direct ->
                        return@async direct to false
                    }
                }
                val (tr, _) = translateOnlineOrLocal(
                    text = cleaned,
                    sourceCode = srcLang,
                    targetCode = tgtLang,
                    cacheScope = cacheScope,
                    allowContextualRewrite = !literalMode
                )
                if (!tr.isNullOrBlank()) {
                    tr to false
                } else {
                    val missing = !networkMonitor.isOnline() &&
                        !translationManager.isPairDownloaded(srcLang, tgtLang)
                    null to missing
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

            val chipsDeferred = async(Dispatchers.IO) {
                val cached = getCachedChipMeanings(wordTokens)
                val fetched = if (cached.size < wordTokens.size) {
                    withTimeoutOrNull(1400L) {
                        fetchMissingChipMeanings(wordTokens.take(20), cached)
                    }.orEmpty()
                } else emptyMap()
                cached + fetched
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
            chipMeanings = chipsDeferred.await()
            tatoebaList = tatoebaDeferred.await()
        }

        val contextualChips: List<ContextualWordChip> = ContextualTranslationAnalyzer.analyzeSentenceWordsInContext(
            sentence = cleaned,
            isArabic = isArabic,
            chipMeanings = chipMeanings
        )

        send(
            SentenceLookupResult(
                originalText = cleaned,
                isArabicInput = isArabic,
                machineTranslation = machineTranslation,
                missingTranslationPack = missingPack,
                wordChips = wordTokens,
                contextualWordChips = contextualChips,
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
                        ContextualTranslationAnalyzer.lookupCuratedIdiom(phrase, isArabic = false)?.let { known ->
                            return@withPermit DetectedIdiom(
                                phrase = phrase,
                                englishMeaning = known.englishMeaning,
                                arabicMeaning = known.arabicMeaning,
                                isArabicMachine = false,
                                example = known.example,
                                isPhrasalVerb = known.isPhrasalVerb,
                                usageNote = known.usageNote
                            )
                        }
                        val packIdiom = dao.getPackIdiomExact(phrase)
                        if (packIdiom != null) {
                            var arMeaning = packIdiom.arabicMeaning.takeIf { it.isNotBlank() }
                            var isMachine = false
                            if (arMeaning == null && packIdiom.englishDefinition.isNotBlank() && packReady) {
                                arMeaning = translateDefinitionOnlyToArabic(packIdiom.englishDefinition)
                                isMachine = !arMeaning.isNullOrBlank()
                            }
                            return@withPermit DetectedIdiom(
                                phrase = packIdiom.phrase,
                                englishMeaning = packIdiom.englishDefinition,
                                arabicMeaning = arMeaning,
                                isArabicMachine = isMachine,
                                example = packIdiom.exampleSentence,
                                isPhrasalVerb = packIdiom.category.equals("PHRASAL_VERB", ignoreCase = true)
                            )
                        }

                        val wikKey = "$phrase|$SRC_WIKTIONARY"
                        var wik = dao.getApiCache(wikKey)?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
                        if (wik == null && isOnline) {
                            wik = withTimeoutOrNull(SOURCE_TIMEOUT_MS) { fetchWiktionarySource(phrase) }
                            if (wik != null) {
                                saveSourceCache(phrase, SRC_WIKTIONARY, json.encodeToString(wik))
                            }
                        }
                        val validWik = wik?.takeIf { !it.notFound && it.senses.isNotEmpty() } ?: return@async null
                        val firstSense = validWik.senses.first()
                        val arabicMeaning = firstSense.definitionArabic?.takeIf { it.isNotBlank() }
                            ?: if (firstSense.definition.isNotBlank() && (packReady || isOnline)) {
                                // Strict non-literal rule: translate ONLY the English definition, NEVER raw idiom words
                                translateDefinitionOnlyToArabic(firstSense.definition)
                            } else null
                        DetectedIdiom(
                            phrase = phrase,
                            englishMeaning = firstSense.definition,
                            arabicMeaning = arabicMeaning,
                            isArabicMachine = false,
                            example = firstSense.examples.firstOrNull().orEmpty()
                        )
                    }
                }
            }.awaitAll().filterNotNull().distinctBy { it.phrase.lowercase(Locale.US) }.take(4)
        }
    }

    private suspend fun checkKnownPhraseEntryExists(phrase: String, isArabic: Boolean): Boolean {
        val key = ArabicTextUtils.normalizeForKey(phrase)
        if (ContextualTranslationAnalyzer.lookupCuratedIdiom(phrase, isArabic) != null) return true
        if (!isArabic && dao.getPackIdiomExact(key) != null) return true
        if (dao.getOfflinePackWord(key) != null) return true
        if (dao.getWordIndex(key) != null) return true

        val cachedWik = dao.getApiCache("$key|$SRC_WIKTIONARY")?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
        if (cachedWik != null && !cachedWik.notFound && cachedWik.senses.isNotEmpty()) return true
        val cachedFd = dao.getApiCache("$key|$SRC_FREEDICT")?.let { decodeOrNull<FreeDictCachedPayload>(it.payloadJson) }
        if (cachedFd != null && !cachedFd.notFound && cachedFd.senses.isNotEmpty()) return true

        if (!networkMonitor.isOnline()) return false

        return if (isArabic) {
            val slug = ArabicTextUtils.stripArabicDiacriticsKeepHamza(phrase)
            val wik = withTimeoutOrNull(SOURCE_TIMEOUT_MS) { fetchArabicWiktionarySource(slug, key, phrase) }
            wik != null && !wik.notFound && wik.senses.isNotEmpty()
        } else {
            val wik = withTimeoutOrNull(SOURCE_TIMEOUT_MS) { fetchWiktionarySource(key) }
            if (wik != null && !wik.notFound && wik.senses.isNotEmpty()) {
                saveSourceCache(key, SRC_WIKTIONARY, json.encodeToString(wik))
                return true
            }
            val fd = withTimeoutOrNull(SOURCE_TIMEOUT_MS) { fetchFreeDictionarySource(key) }
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
            if (memCached != null && !needsBatchTranslation(memCached)) {
                emitState(WordLoadState.Success(memCached.copy(displayWord = displayWord, isFromCache = true), isUpdating = false))
                return
            }
        }

        val now = System.currentTimeMillis()
        var offlinePackEntry: WordEntry? = null
        var wiktionaryPayload: WiktionaryCachedPayload? = null
        var tatoebaPayload: TatoebaCachedPayload? = null
        var reverseGroups: List<ArabicVocalizedGroup> = emptyList()
        var needWiktionary = true
        var needTatoeba = true

        withContext(Dispatchers.IO) {
            val packRow = dao.getOfflinePackWordByLang(slugWithHamza, "ar")
                ?: dao.getOfflinePackWordByLang(normalized, "ar")
                ?: dao.getOfflinePackWord(normalized)
            if (packRow != null) {
                offlinePackEntry = parseOfflinePackEntity(packRow)
            }

            // Reverse Arabic -> English lookup grouped by vocalized Arabic form
            val reverseRows = dao.getReverseArabicMatches(normalized)
            if (reverseRows.isNotEmpty()) {
                val enAudioMap = HashMap<String, WordIndexEntity>(reverseRows.size)
                reverseRows.map { it.englishWord.lowercase(Locale.US) }
                    .distinct()
                    .chunked(400)
                    .forEach { chunk ->
                        dao.getWordIndexesByNormalized(chunk).forEach { idx ->
                            enAudioMap[idx.normalizedWord] = idx
                        }
                    }
                reverseGroups = buildReverseArabicGroups(
                    rows = reverseRows,
                    audioIndex = enAudioMap,
                    fallbackDisplayWord = displayWord,
                    hasExplicitTashkeel = hasExplicitTashkeel,
                    canonicalExact = canonicalExact
                )
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

        val online = networkMonitor.isOnline()
        val arPackReady = withContext(Dispatchers.IO) {
            translationManager.isPairDownloaded(TranslateLanguage.ARABIC, TranslateLanguage.ENGLISH)
        }
        val (rawMt, _) = translateOnlineOrLocal(
            text = displayWord,
            sourceCode = TranslateLanguage.ARABIC,
            targetCode = TranslateLanguage.ENGLISH,
            cacheScope = "whole|${TranslateLanguage.ARABIC}",
            packReadyHint = arPackReady
        )
        val machineChips = rawMt.orEmpty().split(Regex("[,;/؛]"))
            .map { it.trim() }
            .filter { it.isNotBlank() && !ArabicTextUtils.containsArabicLetters(it) }
            .distinctBy { it.lowercase(Locale.US) }
        val missingPack = !online && !arPackReady && machineChips.isEmpty()

        val anyRemoteNeeded = online && (needWiktionary || needTatoeba)

        if (!anyRemoteNeeded) {
            val localRaw = mergeArabicWordEntry(
                normalizedKey = normalized,
                displayWord = displayWord,
                canonicalExact = canonicalExact,
                hasExplicitTashkeel = hasExplicitTashkeel,
                offlinePack = offlinePackEntry,
                wiktionary = wiktionaryPayload,
                tatoeba = tatoebaPayload,
                reverseGroups = reverseGroups,
                machineTranslations = machineChips,
                missingTranslationPack = missingPack,
                isFromCache = wiktionaryPayload != null || reverseGroups.isNotEmpty()
            )
            if (localRaw == null && missingPack && machineChips.isEmpty()) {
                emitState(
                    WordLoadState.Error(
                        message = "No internet connection and offline dictionary data is unavailable.",
                        isOffline = true,
                        needsLanguagePack = true
                    )
                )
                return
            }
        }

        if (anyRemoteNeeded) {
            val mergeMutex = Mutex()
            coroutineScope {
                if (needWiktionary) {
                    launch(Dispatchers.IO) {
                        val fetched = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchArabicWiktionarySource(slugWithHamza, normalized, displayWord)
                        }
                        if (fetched != null) {
                            mergeMutex.withLock { wiktionaryPayload = fetched }
                            saveSourceCache(normalized, SRC_WIKTIONARY_AR, json.encodeToString(fetched))
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
                        }
                    }
                }
            }
        }

        val mergedFinal = withContext(Dispatchers.Default) {
            mergeArabicWordEntry(
                normalizedKey = normalized,
                displayWord = displayWord,
                canonicalExact = canonicalExact,
                hasExplicitTashkeel = hasExplicitTashkeel,
                offlinePack = offlinePackEntry,
                wiktionary = wiktionaryPayload,
                tatoeba = tatoebaPayload,
                reverseGroups = reverseGroups,
                machineTranslations = machineChips,
                missingTranslationPack = missingPack,
                isFromCache = !anyRemoteNeeded
            )
        }

        if (mergedFinal == null) {
            emitState(
                WordLoadState.Error(
                    message = if (missingPack) {
                        "No entries found for \"$displayWord\"."
                    } else {
                        "No entries found for \"$displayWord\"."
                    },
                    isOffline = !networkMonitor.isOnline(),
                    needsLanguagePack = missingPack
                )
            )
            return
        }

        val withCached = applyRoomTranslationCache(mergedFinal)
        val canTranslateNow = arPackReady || (online && myMemoryApi != null)
        val finalEntry = if (canTranslateNow && needsBatchTranslation(withCached)) {
            translateMissingSensesInBatch(withCached)
        } else {
            withCached
        }.let { entry ->
            if (online) entry.copy(missingTranslationPack = false) else entry
        }

        synchronized(readyEntryCache) {
            readyEntryCache.put(memKey, finalEntry)
        }

        withContext(Dispatchers.IO) {
            val firstSense = finalEntry.posGroups.firstOrNull()?.senses?.firstOrNull()
            val summary = firstSense?.definitionArabic?.takeIf { it.isNotBlank() }
                ?: firstSense?.definition.orEmpty()
                    .ifBlank { finalEntry.machineTranslations.joinToString(" • ") }
                    .ifBlank {
                        finalEntry.reverseArabicGroups.firstOrNull()?.matches
                            ?.joinToString("؛ ") { it.englishWord }.orEmpty()
                    }
                    .take(180)
            dao.upsertWordIndex(
                WordIndexEntity(
                    word = finalEntry.displayWord.ifBlank { slugWithHamza },
                    normalizedWord = normalized,
                    lang = "ar",
                    summaryDefinition = summary,
                    previewSummary = LinguisticRanker.buildPosPreviewFromEntry(finalEntry),
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
        val isMultiWord = ArabicTextUtils.isMultiWordQuery(trimmed)
        val isPackReadyNow = withContext(Dispatchers.IO) {
            translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
        }

        if (!forceRefresh) {
            val memCached = synchronized(readyEntryCache) { readyEntryCache.get(memKey) }
            if (memCached != null && (!memCached.missingTranslationPack || !isPackReadyNow) && !needsBatchTranslation(memCached)) {
                emitState(WordLoadState.Success(memCached.copy(isFromCache = true), isUpdating = false))
                return
            }
        }

        val now = System.currentTimeMillis()
        var offlinePackEntry: WordEntry? = null
        var exactPackIdiom: PackIdiomEntity? = null
        var containingPackIdioms: List<PackIdiomEntity> = emptyList()
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

        val curatedIdiom = ContextualTranslationAnalyzer.lookupCuratedIdiom(trimmed, isArabic = false)

        withContext(Dispatchers.IO) {
            val packRow = dao.getOfflinePackWordByLang(normalized, "en") ?: dao.getOfflinePackWord(normalized)
            packRow?.let { offlinePackEntry = parseOfflinePackEntity(it) }

            if (isMultiWord) {
                exactPackIdiom = dao.getPackIdiomExact(normalized)
            } else {
                containingPackIdioms = dao.findIdiomsContainingWord(normalized, limit = 30)
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
            cachedRows[SRC_WIKIMEDIA_AUDIO]?.let { row ->
                wikimediaAudioPayload = decodeOrNull<WikimediaAudioCachedPayload>(row.payloadJson)
                if (!forceRefresh && wikimediaAudioPayload != null && (now - row.timestampMs) <= CACHE_STALE_MS) {
                    needWikimediaAudio = false
                }
            }
        }

        val online = networkMonitor.isOnline()
        val mtHeadwordResult: Pair<List<String>, Boolean> = when {
            curatedIdiom != null -> listOf(curatedIdiom.arabicMeaning) to false
            exactPackIdiom != null -> {
                val p = exactPackIdiom!!
                val ar = p.arabicMeaning.takeIf { it.isNotBlank() }
                    ?: if (p.englishDefinition.isNotBlank()) translateDefinitionOnlyToArabic(p.englishDefinition) else null
                (if (!ar.isNullOrBlank()) listOf(ar) else emptyList()) to false
            }
            isMultiWord -> {
                val override = ContextualTranslationAnalyzer.findDirectContextualOverride(
                    text = trimmed,
                    sourceCode = TranslateLanguage.ENGLISH,
                    targetCode = TranslateLanguage.ARABIC
                )
                if (!override.isNullOrBlank()) listOf(override) to false
                else emptyList<String>() to false
            }
            else -> {
                val (ar, _) = translateOnlineOrLocal(
                    text = trimmed,
                    sourceCode = TranslateLanguage.ENGLISH,
                    targetCode = TranslateLanguage.ARABIC,
                    cacheScope = "whole|${TranslateLanguage.ENGLISH}",
                    packReadyHint = isPackReadyNow
                )
                val chips = if (!ar.isNullOrBlank()) listOf(ar) else emptyList()
                val missing = !online && !isPackReadyNow && chips.isEmpty()
                chips to missing
            }
        }

        val cachedMissingAudio = (freeDictPayload?.usAudioUrl.isNullOrBlank() || freeDictPayload?.ukAudioUrl.isNullOrBlank())
        val shouldTryWikimedia = online && wikimediaCommonsApi != null && needWikimediaAudio && (needFreeDict || cachedMissingAudio)
        val anyRemoteNeeded = online && (needFreeDict || needWiktionary || needDatamuse || needTatoeba || shouldTryWikimedia)

        if (!online && freeDictPayload == null && wiktionaryPayload == null && offlinePackEntry == null && exactPackIdiom == null) {
            emitState(
                WordLoadState.Error(
                    message = "No internet connection and this word is not in your local dictionary pack.",
                    isOffline = true
                )
            )
            return
        }

        if (anyRemoteNeeded) {
            val mergeMutex = Mutex()
            coroutineScope {
                if (needFreeDict) {
                    launch(Dispatchers.IO) {
                        val fetched = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                            fetchFreeDictionarySource(normalized)
                        }
                        if (fetched != null) {
                            mergeMutex.withLock { freeDictPayload = fetched }
                            saveSourceCache(normalized, SRC_FREEDICT, json.encodeToString(fetched))
                        }
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
                isFromCache = !anyRemoteNeeded,
                exactPackIdiom = exactPackIdiom,
                containingPackIdioms = containingPackIdioms
            )
        }

        if (mergedFinal == null ||
            (mergedFinal.posGroups.isEmpty() &&
                mergedFinal.synonyms.isEmpty() &&
                mergedFinal.tatoebaSentences.isEmpty() &&
                mergedFinal.relatedWords.isEmpty() &&
                mergedFinal.phrasalVerbsAndIdioms.isEmpty() &&
                mergedFinal.machineTranslations.isEmpty())
        ) {
            emitState(
                WordLoadState.Error(
                    message = if (isMultiWord) {
                        "No idiom meaning found for \"$trimmed\"."
                    } else {
                        "No dictionary entries found for \"$trimmed\"."
                    },
                    isOffline = !networkMonitor.isOnline()
                )
            )
            return
        }

        val withCachedTranslations = applyRoomTranslationCache(mergedFinal)
        val canTranslateNow = isPackReadyNow || (online && myMemoryApi != null)

        val finalEntry = if (canTranslateNow && needsBatchTranslation(withCachedTranslations)) {
            translateMissingSensesInBatch(withCachedTranslations)
        } else {
            withCachedTranslations
        }.let { entry ->
            if (online) entry.copy(missingTranslationPack = false) else entry
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
                    previewSummary = LinguisticRanker.buildPosPreviewFromEntry(finalEntry),
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

    private fun contextualDefCacheKey(word: String, definition: String, isArabicTarget: Boolean = true): String =
        if (isArabicTarget) {
            translationCacheKey("ctx|${word.trim().lowercase(Locale.US)}|${TranslateLanguage.ENGLISH}", TranslateLanguage.ARABIC, definition)
        } else {
            translationCacheKey("ctx|${ArabicTextUtils.normalizeArabicForMatching(word)}|${TranslateLanguage.ARABIC}", TranslateLanguage.ENGLISH, definition)
        }

    private fun extractContextualSenseArabic(rawTranslated: String, headword: String): String {
        val trimmed = rawTranslated.trim()
        val colonIdx = trimmed.indexOf(':').takeIf { it > 0 } ?: trimmed.indexOf('：')
        if (colonIdx in 1..32) {
            val prefix = trimmed.substring(0, colonIdx).trim()
            val after = trimmed.substring(colonIdx + 1).trim()
            if (after.isNotBlank() && (prefix.equals(headword, ignoreCase = true) || prefix.split(Regex("\\s+")).size <= 2)) {
                return after
            }
        }
        return trimmed
    }

    private suspend fun applyRoomTranslationCache(entry: WordEntry): WordEntry {
        if (entry.posGroups.isEmpty()) return entry
        val defKeys = HashMap<String, String>() // cacheKey -> def
        val exKeys = HashMap<String, String>()  // cacheKey -> ex
        entry.posGroups.forEach { group ->
            group.senses.forEach { sense ->
                val def = sense.definition.trim()
                val defIsAr = ArabicTextUtils.containsArabicLetters(def)
                if (sense.definitionArabic.isNullOrBlank() && def.isNotBlank()) {
                    defKeys[contextualDefCacheKey(entry.word, def, isArabicTarget = !defIsAr)] = def
                }
                sense.examples.forEach { ex ->
                    if (!sense.exampleTranslations.containsKey(ex)) {
                        val trimmedEx = ex.trim()
                        val exIsAr = ArabicTextUtils.containsArabicLetters(trimmedEx)
                        val key = if (exIsAr) {
                            translationCacheKey(TranslateLanguage.ARABIC, TranslateLanguage.ENGLISH, trimmedEx)
                        } else {
                            translationCacheKey(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC, trimmedEx)
                        }
                        exKeys[key] = trimmedEx
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
                val defIsAr = ArabicTextUtils.containsArabicLetters(sense.definition)
                val needsDef = sense.definitionArabic.isNullOrBlank() &&
                    sense.definition.isNotBlank() &&
                    (!entry.isArabicWord || defIsAr)
                val needsEx = sense.examples.any { ex ->
                    !sense.exampleTranslations.containsKey(ex) && ex.isNotBlank()
                }
                needsDef || needsEx
            }
        }
    }

    private suspend fun translateMissingSensesInBatch(entry: WordEntry): WordEntry {
        if (entry.posGroups.isEmpty()) return entry
        val enDefs = LinkedHashSet<String>()
        val arDefs = LinkedHashSet<String>()
        val enExamples = LinkedHashSet<String>()
        val arExamples = LinkedHashSet<String>()

        entry.posGroups.forEach { group ->
            group.senses.forEach { sense ->
                val def = sense.definition.trim()
                val defIsAr = ArabicTextUtils.containsArabicLetters(def)
                if (sense.definitionArabic.isNullOrBlank() && def.isNotBlank()) {
                    if (defIsAr) arDefs.add(def) else if (!entry.isArabicWord) enDefs.add(def)
                }
                sense.examples.forEach { ex ->
                    val cleanEx = ex.trim()
                    if (!sense.exampleTranslations.containsKey(ex) && cleanEx.isNotBlank()) {
                        if (ArabicTextUtils.containsArabicLetters(cleanEx)) arExamples.add(cleanEx)
                        else enExamples.add(cleanEx)
                    }
                }
            }
        }
        if (enDefs.isEmpty() && arDefs.isEmpty() && enExamples.isEmpty() && arExamples.isEmpty()) {
            return entry
        }

        val defMap = HashMap<String, String>()
        val exMap = HashMap<String, String>()
        val cacheEntities = ArrayList<TranslationCacheEntity>()
        val now = System.currentTimeMillis()

        // EN -> AR defs & examples
        if (enDefs.isNotEmpty() || enExamples.isNotEmpty()) {
            val promptToDef = enDefs.associateBy { contextualDefPrompt(entry.word, it) }
            val batchInputs = (promptToDef.keys.toList().take(24) + enExamples.toList().take(12))
            val rawBatchResults = translateBatchOnlineOrLocal(
                texts = batchInputs,
                sourceCode = TranslateLanguage.ENGLISH,
                targetCode = TranslateLanguage.ARABIC,
                concurrency = 4
            )
            promptToDef.forEach { (prompt, rawDef) ->
                val rawAr = rawBatchResults[prompt]?.trim()
                if (!rawAr.isNullOrBlank()) {
                    val cleanedAr = extractContextualSenseArabic(rawAr, entry.word)
                    defMap[rawDef] = cleanedAr
                    cacheEntities.add(
                        TranslationCacheEntity(
                            cacheKey = contextualDefCacheKey(entry.word, rawDef, isArabicTarget = true),
                            translatedText = cleanedAr,
                            timestampMs = now
                        )
                    )
                }
            }
            enExamples.forEach { rawEx ->
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
        }

        // AR -> EN defs & examples (Symmetry Rule)
        if (arDefs.isNotEmpty() || arExamples.isNotEmpty()) {
            val arPromptToDef = arDefs.associateBy { contextualDefPrompt(entry.word, it) }
            val arInputs = (arPromptToDef.keys.toList().take(20) + arExamples.toList().take(12))
            val arBatchResults = translateBatchOnlineOrLocal(
                texts = arInputs,
                sourceCode = TranslateLanguage.ARABIC,
                targetCode = TranslateLanguage.ENGLISH,
                concurrency = 4
            )
            arPromptToDef.forEach { (prompt, rawDef) ->
                val rawEn = arBatchResults[prompt]?.trim()
                if (!rawEn.isNullOrBlank()) {
                    val cleanedEn = extractContextualSenseArabic(rawEn, entry.word)
                    defMap[rawDef] = cleanedEn
                    cacheEntities.add(
                        TranslationCacheEntity(
                            cacheKey = contextualDefCacheKey(entry.word, rawDef, isArabicTarget = false),
                            translatedText = cleanedEn,
                            timestampMs = now
                        )
                    )
                }
            }
            arExamples.forEach { rawEx ->
                val rawEn = arBatchResults[rawEx]?.trim()
                if (!rawEn.isNullOrBlank() && rawEn.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                    exMap[rawEx] = rawEn
                    cacheEntities.add(
                        TranslationCacheEntity(
                            cacheKey = translationCacheKey(TranslateLanguage.ARABIC, TranslateLanguage.ENGLISH, rawEx),
                            translatedText = rawEn,
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
                    false
                }

                val newExTrans = HashMap(sense.exampleTranslations)
                val newExIsMachine = HashMap(sense.exampleIsMachine)
                sense.examples.forEach { ex ->
                    if (!newExTrans.containsKey(ex)) {
                        val tr = exTranslatedMap[ex.trim()]
                        if (!tr.isNullOrBlank()) {
                            newExTrans[ex] = tr
                            newExIsMachine[ex] = false
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
        maxCount: Int = 45
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
                            shortDefinitionArabic = firstSense.definitionArabic,
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
                                shortDefinitionArabic = s.definitionArabic,
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
                                    shortDefinitionArabic = s.definitionArabic,
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
        if (baseMap.isEmpty()) return@withContext baseMap

        val defsToTranslate = baseMap.values
            .filter { it.shortDefinitionArabic.isNullOrBlank() && it.shortDefinition.isNotBlank() }
            .associateBy { contextualDefPrompt(it.word, it.shortDefinition) }
        if (defsToTranslate.isEmpty()) return@withContext baseMap

        val translated = translateBatchOnlineOrLocal(
            texts = defsToTranslate.keys.toList(),
            sourceCode = TranslateLanguage.ENGLISH,
            targetCode = TranslateLanguage.ARABIC,
            concurrency = 4
        )
        baseMap.mapValues { (_, detail) ->
            val prompt = contextualDefPrompt(detail.word, detail.shortDefinition)
            val ar = translated[prompt]?.let { extractContextualSenseArabic(it, detail.word) }
            if (!ar.isNullOrBlank()) {
                detail.copy(shortDefinitionArabic = ar, shortDefinitionArabicIsMachine = false)
            } else {
                detail
            }
        }
    }

    private suspend fun translateViaOnlineMyMemory(
        text: String,
        sourceCode: String,
        targetCode: String,
        allowContextualRewrite: Boolean = false
    ): String? {
        val api = myMemoryApi ?: return null
        if (!networkMonitor.isOnline()) return null
        val cleaned = text.trim()
        if (cleaned.isBlank()) return null
        if (allowContextualRewrite) {
            ContextualTranslationAnalyzer.findDirectContextualOverride(cleaned, sourceCode, targetCode)?.let {
                return it
            }
        }
        val queryToSend = if (allowContextualRewrite) {
            when {
                sourceCode == TranslateLanguage.ARABIC && targetCode == TranslateLanguage.ENGLISH ->
                    ContextualTranslationAnalyzer.prepareArabicForTranslation(cleaned)
                sourceCode == TranslateLanguage.ENGLISH && targetCode == TranslateLanguage.ARABIC ->
                    ContextualTranslationAnalyzer.prepareEnglishForTranslation(cleaned)
                else -> cleaned
            }
        } else cleaned

        val langPair = when {
            sourceCode == TranslateLanguage.ARABIC && targetCode == TranslateLanguage.ENGLISH -> "ar|en"
            sourceCode == TranslateLanguage.ENGLISH && targetCode == TranslateLanguage.ARABIC -> "en|ar"
            else -> "$sourceCode|$targetCode"
        }
        val wantArabic = targetCode == TranslateLanguage.ARABIC
        val rawResult = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            runCatching {
                val raw = api.translate(query = queryToSend, langPair = langPair).string()
                MyMemoryApi.parseTranslatedText(rawJson = raw, sourceText = queryToSend, wantArabic = wantArabic)
            }.getOrNull()
        } ?: return null

        return if (allowContextualRewrite) {
            when {
                sourceCode == TranslateLanguage.ARABIC && targetCode == TranslateLanguage.ENGLISH ->
                    ContextualTranslationAnalyzer.refineEnglishTranslation(cleaned, rawResult)
                sourceCode == TranslateLanguage.ENGLISH && targetCode == TranslateLanguage.ARABIC ->
                    ContextualTranslationAnalyzer.refineArabicTranslation(cleaned, rawResult)
                else -> rawResult
            }
        } else {
            rawResult
        }
    }

    private suspend fun translateOnlineOrLocal(
        text: String,
        sourceCode: String,
        targetCode: String,
        cacheScope: String = "whole|$sourceCode",
        packReadyHint: Boolean? = null,
        allowContextualRewrite: Boolean = !translationManager.isLiteralTranslationEnabled()
    ): Pair<String?, Boolean> {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return null to false
        if (allowContextualRewrite) {
            ContextualTranslationAnalyzer.findDirectContextualOverride(cleaned, sourceCode, targetCode)?.let { direct ->
                return direct to false
            }
        }
        val roomKey = translationCacheKey(cacheScope, targetCode, cleaned)
        val cached = withContext(Dispatchers.IO) {
            dao.getTranslationCacheBatch(listOf(roomKey))
                .firstOrNull()?.translatedText?.takeIf { it.isNotBlank() }
        }
        if (!cached.isNullOrBlank()) return cached to false

        val packReady = packReadyHint ?: withContext(Dispatchers.IO) {
            translationManager.isPairDownloaded(sourceCode, targetCode)
        }
        var translated: String? = null
        if (packReady) {
            translated = withContext(Dispatchers.Default) {
                translationManager.translateShortIfReady(
                    text = cleaned,
                    sourceCode = sourceCode,
                    targetCode = targetCode
                ).getOrNull()?.trim()?.takeIf { it.isNotBlank() }
            }
        }
        if (translated.isNullOrBlank() && networkMonitor.isOnline()) {
            translated = translateViaOnlineMyMemory(
                text = cleaned,
                sourceCode = sourceCode,
                targetCode = targetCode,
                allowContextualRewrite = allowContextualRewrite
            )
        }
        if (!translated.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                dao.upsertTranslationCacheBatch(
                    listOf(TranslationCacheEntity(roomKey, translated, System.currentTimeMillis()))
                )
            }
        }
        val missingOfflinePack = translated.isNullOrBlank() && !networkMonitor.isOnline() && !packReady
        return translated to missingOfflinePack
    }

    private suspend fun translateBatchOnlineOrLocal(
        texts: List<String>,
        sourceCode: String,
        targetCode: String,
        concurrency: Int = 4
    ): Map<String, String> {
        val cleaned = texts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>(cleaned.size)

        val packReady = withContext(Dispatchers.IO) {
            translationManager.isPairDownloaded(sourceCode, targetCode)
        }
        if (packReady) {
            val mlResults = translationManager.translateBatchIfReady(
                texts = cleaned,
                sourceCode = sourceCode,
                targetCode = targetCode,
                concurrency = concurrency
            )
            out.putAll(mlResults)
        }

        val remaining = cleaned.filter { it !in out }
        if (remaining.isNotEmpty() && myMemoryApi != null && networkMonitor.isOnline()) {
            // Bundle items into compact newline-separated blocks <= 400 chars (keeps per-word KB minimal)
            val blocks = ArrayList<List<String>>()
            var currentBlock = ArrayList<String>()
            var currentLen = 0
            for (item in remaining.take(30)) {
                val safeLine = item.replace('\n', ' ').trim()
                if (safeLine.isEmpty()) continue
                if (currentBlock.isNotEmpty() && (currentLen + safeLine.length + 1 > 400 || currentBlock.size >= 8)) {
                    blocks.add(currentBlock)
                    currentBlock = ArrayList()
                    currentLen = 0
                }
                currentBlock.add(item)
                currentLen += safeLine.length + 1
            }
            if (currentBlock.isNotEmpty()) blocks.add(currentBlock)

            val sem = Semaphore(concurrency.coerceIn(1, 3))
            coroutineScope {
                blocks.take(4).map { block ->
                    async {
                        val joinedQuery = block.joinToString("\n") { it.replace('\n', ' ').trim() }
                        val blockTranslated = sem.withPermit {
                            translateViaOnlineMyMemory(joinedQuery, sourceCode, targetCode)
                        }
                        val splitLines = blockTranslated?.split('\n')?.map { it.trim() }.orEmpty()
                        if (splitLines.size == block.size) {
                            block.mapIndexedNotNull { idx, src ->
                                val tr = splitLines[idx]
                                if (tr.isNotBlank()) src to tr else null
                            }
                        } else if (block.size == 1 && !blockTranslated.isNullOrBlank()) {
                            listOf(block[0] to blockTranslated)
                        } else {
                            // Compact fallback for up to 3 unaligned items in the block
                            block.take(3).mapNotNull { src ->
                                val tr = sem.withPermit { translateViaOnlineMyMemory(src, sourceCode, targetCode) }
                                if (!tr.isNullOrBlank()) src to tr else null
                            }
                        }
                    }
                }.awaitAll().flatten().forEach { (src, tr) ->
                    out[src] = tr
                }
            }
        }
        return out
    }

    private suspend fun fetchOnlinePreviewEnToAr(word: String, posHint: String): String? {
        val cleanWord = word.trim()
        val lower = cleanWord.lowercase(Locale.US)
        if (cleanWord.isBlank() || ArabicTextUtils.containsArabicLetters(cleanWord)) return null

        val slug = lower.replace(Regex("\\s+"), "_")
        val abbr = LinguisticRanker.abbreviatePosTag(posHint)

        // Query Wiktionary REST JSON + Wiktionary HTML Translations table + Free Dictionary in parallel (4s timeout)
        val (restParsed, htmlTerms) = coroutineScope {
            val restDeferred = async {
                withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    runCatching {
                        val raw = wiktionaryApi.getDefinitionRaw(slug).string()
                        val root = json.parseToJsonElement(raw).jsonObject
                        val langArray = root["en"]?.jsonArray ?: return@runCatching emptyList<Pair<String, List<String>>>() to emptyList<String>()
                        val byPosAr = LinkedHashMap<String, LinkedHashSet<String>>()
                        val detectedPos = LinkedHashSet<String>()
                        for (secEl in langArray) {
                            val sec = secEl.jsonObject
                            val pos = sec["partOfSpeech"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            val posAbbr = LinguisticRanker.abbreviatePosTag(pos)
                            if (posAbbr.isNotBlank()) detectedPos.add(posAbbr)
                            val defs = sec["definitions"]?.jsonArray ?: continue
                            for (defEl in defs) {
                                val ar = extractWiktionaryArabicTranslation(defEl.jsonObject) ?: continue
                                val bucket = byPosAr.getOrPut(posAbbr) { LinkedHashSet() }
                                ar.split('؛', ';', '،').forEach { term ->
                                    val cleanT = term.trim()
                                    if (cleanT.isNotBlank() && ArabicTextUtils.containsArabicLetters(cleanT)) {
                                        bucket.add(cleanT)
                                    }
                                }
                            }
                        }
                        val posList = byPosAr.entries.mapNotNull { (p, terms) ->
                            if (terms.isEmpty()) null else p to terms.toList()
                        }
                        posList to detectedPos.toList()
                    }.getOrNull()
                }
            }
            val htmlDeferred = async {
                withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    runCatching {
                        val rawHtmlJson = wiktionaryApi.getPageHtmlRaw(slug).string()
                        val html = WiktionaryHtmlParser.extractHtmlFromParseJson(rawHtmlJson)
                        WiktionaryHtmlParser.parseEnglishTranslationsHtml(html)
                            .flatMap { it.arabicTerms }
                            .filter { ArabicTextUtils.containsArabicLetters(it) }
                            .distinct()
                    }.getOrDefault(emptyList())
                }.orEmpty()
            }
            restDeferred.await() to htmlDeferred.await()
        }

        val restPosAr = restParsed?.first.orEmpty()
        val detectedPosAbbr = restParsed?.second?.firstOrNull().orEmpty().ifBlank { abbr }

        val wikTerms = LinkedHashSet<String>()
        if (restPosAr.isNotEmpty()) {
            restPosAr.forEach { (p, terms) ->
                if (terms.isNotEmpty()) {
                    wikTerms.add(if (p.isNotBlank()) "$p ${terms.joinToString("؛ ")}" else terms.joinToString("؛ "))
                }
            }
        }
        val alreadyHasArTerms = restPosAr.flatMapTo(HashSet()) { it.second }
        val extraHtmlTerms = htmlTerms.filter { it !in alreadyHasArTerms }.take(12)
        if (extraHtmlTerms.isNotEmpty()) {
            val tag = if (restPosAr.isEmpty()) detectedPosAbbr else ""
            val joined = extraHtmlTerms.joinToString("؛ ")
            wikTerms.add(if (tag.isNotBlank()) "$tag $joined" else joined)
        }

        val preview = if (wikTerms.isNotEmpty()) {
            wikTerms.joinToString("؛ ")
        } else {
            val ar = translateViaOnlineMyMemory(cleanWord, TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
                ?.takeIf { ArabicTextUtils.containsArabicLetters(it) }
                ?: translationManager.translateShortIfReady(
                    cleanWord,
                    TranslateLanguage.ENGLISH,
                    TranslateLanguage.ARABIC
                ).getOrNull()?.trim()?.takeIf { ArabicTextUtils.containsArabicLetters(it) }
                ?: return null
            if (detectedPosAbbr.isNotBlank()) "$detectedPosAbbr $ar" else ar
        }

        if (preview.isNotBlank()) {
            val roomKey = translationCacheKey("whole|${TranslateLanguage.ENGLISH}", TranslateLanguage.ARABIC, cleanWord)
            runCatching {
                dao.upsertTranslationCacheBatch(listOf(TranslationCacheEntity(roomKey, preview, System.currentTimeMillis())))
                val existing = dao.getWordIndex(lower)
                if (existing == null || !ArabicTextUtils.containsArabicLetters(existing.previewSummary)) {
                    dao.upsertWordIndex(
                        WordIndexEntity(
                            word = cleanWord,
                            normalizedWord = lower,
                            lang = "en",
                            summaryDefinition = existing?.summaryDefinition.orEmpty().ifBlank { preview.take(180) },
                            previewSummary = preview,
                            partOfSpeech = existing?.partOfSpeech.orEmpty().ifBlank { posHint },
                            frequencyRank = existing?.frequencyRank ?: Int.MAX_VALUE,
                            usAudioUrl = existing?.usAudioUrl,
                            ukAudioUrl = existing?.ukAudioUrl,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return preview.takeIf { it.isNotBlank() }
    }

    private suspend fun fetchOnlinePreviewArToEn(word: String, posHint: String): String? {
        val cleanWord = word.trim()
        val norm = ArabicTextUtils.normalizeArabicForMatching(cleanWord)
        if (cleanWord.isBlank()) return null

        val wikEn = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            runCatching {
                val slug = ArabicTextUtils.stripArabicDiacriticsKeepHamza(cleanWord).replace(Regex("\\s+"), "_")
                val wik = fetchWiktionaryByLanguageBlock(slug, "ar", cleanWord, false)
                val valid = wik?.takeIf { !it.notFound && it.senses.isNotEmpty() } ?: return@runCatching ""
                val byPos = LinkedHashMap<String, LinkedHashSet<String>>()
                valid.senses.forEach { s ->
                    val posAbbr = LinguisticRanker.abbreviatePosTag(s.partOfSpeech)
                    val bucket = byPos.getOrPut(posAbbr) { LinkedHashSet() }
                    val concise = s.definition.substringBefore(';').substringBefore('.').trim()
                    if (concise.isNotBlank() && concise.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                        bucket.add(concise)
                    }
                }
                byPos.entries.joinToString("؛ ") { (p, defs) ->
                    val joined = defs.joinToString("؛ ")
                    if (p.isNotBlank()) "$p $joined" else joined
                }
            }.getOrDefault("")
        }.orEmpty()

        val preview = if (wikEn.isNotBlank()) {
            wikEn
        } else {
            val en = translateViaOnlineMyMemory(cleanWord, TranslateLanguage.ARABIC, TranslateLanguage.ENGLISH)
                ?.takeIf { it.any { c -> c in 'a'..'z' || c in 'A'..'Z' } }
                ?: return null
            val abbr = LinguisticRanker.abbreviatePosTag(posHint)
            if (abbr.isNotBlank()) "$abbr $en" else en
        }

        if (preview.isNotBlank()) {
            val roomKey = translationCacheKey("whole|${TranslateLanguage.ARABIC}", TranslateLanguage.ENGLISH, cleanWord)
            runCatching {
                dao.upsertTranslationCacheBatch(listOf(TranslationCacheEntity(roomKey, preview, System.currentTimeMillis())))
                val existing = dao.getWordIndex(norm)
                if (existing == null || !existing.previewSummary.any { it in 'a'..'z' || it in 'A'..'Z' }) {
                    dao.upsertWordIndex(
                        WordIndexEntity(
                            word = cleanWord,
                            normalizedWord = norm,
                            lang = "ar",
                            summaryDefinition = existing?.summaryDefinition.orEmpty().ifBlank { preview.take(180) },
                            previewSummary = preview,
                            partOfSpeech = existing?.partOfSpeech.orEmpty().ifBlank { posHint },
                            frequencyRank = existing?.frequencyRank ?: Int.MAX_VALUE,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return preview.takeIf { it.isNotBlank() }
    }

    /**
     * Translates ONLY the verified English definition of an idiom/phrase into Arabic
     * (NEVER passes raw idiom words like "hit the town" to MT).
     */
    private suspend fun translateDefinitionOnlyToArabic(englishDefinition: String): String? {
        val cleanDef = englishDefinition.trim()
        if (cleanDef.isBlank()) return null
        val cacheKey = translationCacheKey("defonly|${TranslateLanguage.ENGLISH}", TranslateLanguage.ARABIC, cleanDef)
        val cached = withContext(Dispatchers.IO) {
            dao.getTranslationCacheBatch(listOf(cacheKey)).firstOrNull()?.translatedText?.takeIf { it.isNotBlank() }
        }
        if (!cached.isNullOrBlank()) return cached

        val packReady = withContext(Dispatchers.IO) {
            translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
        }
        var rawTranslated: String? = null
        if (packReady) {
            rawTranslated = withContext(Dispatchers.Default) {
                translationManager.translateShortIfReady(
                    text = cleanDef,
                    sourceCode = TranslateLanguage.ENGLISH,
                    targetCode = TranslateLanguage.ARABIC
                ).getOrNull()?.trim()?.takeIf { it.isNotBlank() && ArabicTextUtils.containsArabicLetters(it) }
            }
        }
        if (rawTranslated.isNullOrBlank() && networkMonitor.isOnline()) {
            rawTranslated = translateViaOnlineMyMemory(
                text = cleanDef,
                sourceCode = TranslateLanguage.ENGLISH,
                targetCode = TranslateLanguage.ARABIC
            )?.takeIf { ArabicTextUtils.containsArabicLetters(it) }
        }
        val valid = rawTranslated?.takeIf { it.isNotBlank() } ?: return null

        withContext(Dispatchers.IO) {
            dao.upsertTranslationCacheBatch(
                listOf(TranslationCacheEntity(cacheKey, valid, System.currentTimeMillis()))
            )
        }
        return valid
    }

    /**
     * Strict Non-Literal Idiom / Phrasal Resolver:
     * 1) Curated idiom DB / Offline pack_idioms / offline_pack_words
     * 2) Live Wiktionary idiom/phrase entry (+ FreeDictionary fallback if online)
     * 3) If only English definition exists, translates ONLY the definition (never raw idiom words)
     * 4) If no definition found -> returns noMeaningFound = true (never literal translation)
     */
    suspend fun resolveEnglishIdiomStrict(
        phrase: String,
        baseWord: String = "",
        category: String = "IDIOM",
        isOnline: Boolean = networkMonitor.isOnline(),
        packReady: Boolean? = null
    ): PhraseDetailItem = withContext(Dispatchers.IO) {
        val cleaned = phrase.trim().replace(Regex("\\s+"), " ")
        val key = cleaned.lowercase(Locale.US)
        val grammarHint = LinguisticRanker.inferGrammarStructure(cleaned, category)
        val effectivePackReady = packReady ?: translationManager.isPairDownloaded(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.ARABIC
        )

        // 1a. Curated idiom registry
        ContextualTranslationAnalyzer.lookupCuratedIdiom(cleaned, isArabic = false)?.let { known ->
            val cat = if (known.isPhrasalVerb) "PHRASAL_VERB" else "IDIOM"
            return@withContext PhraseDetailItem(
                phrase = cleaned,
                englishMeaning = known.englishMeaning,
                arabicMeaning = known.arabicMeaning,
                isArabicMachine = false,
                example = known.example,
                isIdiom = !known.isPhrasalVerb,
                category = cat,
                usageExplanation = known.usageNote,
                grammarStructure = grammarHint,
                noMeaningFound = false
            )
        }

        // 1b. Offline pack_idioms (>= 30k phrases)
        dao.getPackIdiomExact(key)?.let { packIdiom ->
            val engDef = packIdiom.englishDefinition.trim()
            var arMeaning = packIdiom.arabicMeaning.trim().takeIf { it.isNotBlank() }
            if (arMeaning == null && engDef.isNotBlank() && (effectivePackReady || isOnline)) {
                arMeaning = translateDefinitionOnlyToArabic(engDef)
            }
            val exAr = packIdiom.exampleArabic.trim().takeIf { it.isNotBlank() }
            val noMeaning = engDef.isBlank() && arMeaning.isNullOrBlank()
            return@withContext PhraseDetailItem(
                phrase = packIdiom.phrase,
                englishMeaning = engDef,
                arabicMeaning = arMeaning,
                isArabicMachine = false,
                example = packIdiom.exampleSentence,
                exampleArabic = exAr,
                exampleIsMachine = false,
                isIdiom = packIdiom.category.equals("IDIOM", ignoreCase = true) || category == "IDIOM",
                category = packIdiom.category.ifBlank { category },
                usageExplanation = if (noMeaning) null else "تعبير اصطلاحي يُفهم معناه من السياق لا من الترجمة الحرفية",
                grammarStructure = grammarHint,
                noMeaningFound = noMeaning
            )
        }

        // 1c. Offline pack words entry for the phrase
        dao.getOfflinePackWord(key)?.let { parseOfflinePackEntity(it) }?.let { packEntry ->
            val s = packEntry.posGroups.firstOrNull()?.senses?.firstOrNull()
            if (s != null && (s.definition.isNotBlank() || !s.definitionArabic.isNullOrBlank())) {
                var arMeaning = s.definitionArabic?.takeIf { it.isNotBlank() }
                if (arMeaning == null && s.definition.isNotBlank() && (effectivePackReady || isOnline)) {
                    arMeaning = translateDefinitionOnlyToArabic(s.definition)
                }
                return@withContext PhraseDetailItem(
                    phrase = cleaned,
                    englishMeaning = s.definition,
                    arabicMeaning = arMeaning,
                    isArabicMachine = false,
                    example = s.examples.firstOrNull().orEmpty(),
                    isIdiom = category == "IDIOM",
                    category = category,
                    usageExplanation = s.usageNote,
                    grammarStructure = grammarHint,
                    noMeaningFound = false
                )
            }
        }

        // 1d. Cached API phrase detail (valid only if it has real English definition)
        val cacheKey = "$key|$SRC_PHRASE_DETAIL"
        val cached = dao.getApiCache(cacheKey)
        if (cached != null && (System.currentTimeMillis() - cached.timestampMs) <= CACHE_STALE_MS) {
            val decoded = decodeOrNull<PhraseDetailItem>(cached.payloadJson)
            if (decoded != null && !decoded.noMeaningFound && decoded.englishMeaning.isNotBlank() &&
                (!decoded.arabicMeaning.isNullOrBlank() || (!effectivePackReady && !isOnline))
            ) {
                return@withContext decoded
            }
        }

        // 2. Wiktionary idiom/phrase entry (cached or live)
        val cachedWik = dao.getApiCache("$key|$SRC_WIKTIONARY")
            ?.let { decodeOrNull<WiktionaryCachedPayload>(it.payloadJson) }
        val wik = cachedWik ?: if (isOnline) {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                fetchWiktionaryByLanguageBlock(key.replace(Regex("\\s+"), "_"), "en", key, false)
            }
        } else null
        val firstWikSense = wik?.takeIf { !it.notFound }?.senses?.firstOrNull()
        val fd = if (firstWikSense == null && isOnline) {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) { fetchFreeDictionarySource(key) }
        } else null
        val firstSense = firstWikSense ?: fd?.takeIf { !it.notFound }?.senses?.firstOrNull()

        if (firstSense == null || (firstSense.definition.isBlank() && firstSense.definitionArabic.isNullOrBlank())) {
            // 4. Never fall back to literal word-by-word translation!
            return@withContext PhraseDetailItem(
                phrase = cleaned,
                englishMeaning = "",
                arabicMeaning = null,
                isArabicMachine = false,
                example = "",
                isIdiom = true,
                category = category,
                grammarStructure = grammarHint,
                noMeaningFound = true
            )
        }

        val engDef = firstSense.definition
        val existingEx = firstSense.examples.firstOrNull().orEmpty()
        val tatoeba = if (isOnline) {
            withTimeoutOrNull(SOURCE_TIMEOUT_MS) { fetchTatoebaSource(key) }?.sentences?.firstOrNull()
        } else null

        val finalExample = existingEx.ifBlank { tatoeba?.english.orEmpty() }
        var finalExArabic = firstSense.exampleTranslations[finalExample]
        var finalExAudio = firstSense.exampleAudioUrls[finalExample]
        if (tatoeba != null && (finalExample.equals(tatoeba.english, ignoreCase = true) || finalExArabic.isNullOrBlank())) {
            if (finalExArabic.isNullOrBlank() && tatoeba.english.contains(key, ignoreCase = true)) {
                finalExArabic = tatoeba.arabic
                finalExAudio = tatoeba.audioUrl
            }
        }

        // 3. Only translate the English DEFINITION (never the raw idiom words)
        var arMeaning = firstSense.definitionArabic?.takeIf { it.isNotBlank() }
        if (arMeaning == null && engDef.isNotBlank() && (effectivePackReady || isOnline)) {
            arMeaning = translateDefinitionOnlyToArabic(engDef)
        }

        if (finalExample.isNotBlank() && finalExArabic.isNullOrBlank() && (effectivePackReady || isOnline)) {
            finalExArabic = translateOnlineOrLocal(
                text = finalExample,
                sourceCode = TranslateLanguage.ENGLISH,
                targetCode = TranslateLanguage.ARABIC,
                packReadyHint = effectivePackReady
            ).first?.takeIf { ArabicTextUtils.containsArabicLetters(it) }
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
            phrase = cleaned,
            englishMeaning = engDef,
            arabicMeaning = arMeaning,
            isArabicMachine = false,
            example = finalExample,
            exampleArabic = finalExArabic,
            exampleIsMachine = false,
            exampleAudioUrl = finalExAudio,
            isIdiom = isIdiom,
            category = resolvedCategory,
            usageExplanation = usageExplanation,
            grammarStructure = grammarHint,
            noMeaningFound = false
        )
        saveSourceCache(key, SRC_PHRASE_DETAIL, json.encodeToString(item))
        item
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

        val semaphore = Semaphore(4)
        val isOnline = networkMonitor.isOnline()
        val packReady = translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)

        coroutineScope {
            candidates.map { phrase ->
                async {
                    val key = phrase.lowercase(Locale.US)
                    val category = when {
                        key in compoundSet -> "COMPOUND"
                        else -> LinguisticRanker.classifyEnglishPhrase(
                            phrase = phrase,
                            headword = baseWord,
                            explicitPhrasals = phrasalSet,
                            explicitIdioms = idiomSet
                        )
                    }
                    semaphore.withPermit {
                        val resolved = resolveEnglishIdiomStrict(
                            phrase = phrase,
                            baseWord = baseWord,
                            category = category,
                            isOnline = isOnline,
                            packReady = packReady
                        )
                        if (resolved.noMeaningFound && category != "IDIOM" && category != "PHRASAL_VERB") {
                            null
                        } else {
                            resolved
                        }
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
                            val wik = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
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

                    if (engMeaning.isNullOrBlank() && (packReady || isOnline)) {
                        val prepared = ContextualTranslationAnalyzer.prepareArabicForTranslation(phrase)
                        val (translated, _) = translateOnlineOrLocal(
                            text = prepared,
                            sourceCode = TranslateLanguage.ARABIC,
                            targetCode = TranslateLanguage.ENGLISH,
                            packReadyHint = packReady
                        )
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
                    .distinct().take(60),
                phrasalVerbs = secPayload.phrasalVerbs,
                idioms = secPayload.idioms,
                compounds = secPayload.compounds,
                collocations = secPayload.collocations,
                synonyms = secPayload.synonyms,
                antonyms = secPayload.antonyms,
                derivedWords = (defPayload.derivedWords + secPayload.derivedWords).distinct().take(40),
                relatedTerms = secPayload.relatedTerms,
                arabicGlosses = secPayload.arabicGlosses,
                wordForms = secPayload.wordForms
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
                    lower in setOf("verb", "noun", "adjective", "adverb", "pronoun", "preposition") -> "pos_headword"
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
            val wordForms = mutableListOf<WordFormItem>()
            val baseLower = baseWord.trim().lowercase(Locale.US)

            val translationTargets = targets.filter { it.second == "translations" }.take(2)
            val posHeadwordTargets = targets.filter { it.second == "pos_headword" }.take(2)
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
            val otherTargets = targets.filter { it.second != "translations" && it.second != "pos_headword" }
                .sortedBy { sectionPriority[it.second] ?: 99 }
                .take(8)

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
                val formsDeferred = posHeadwordTargets.map { (idx, _) ->
                    async {
                        runCatching {
                            val rawHtmlJson = wiktionaryApi.getSectionHtmlRaw(slug, idx).string()
                            val html = WiktionaryHtmlParser.extractHtmlFromParseJson(rawHtmlJson)
                            WiktionaryHtmlParser.parseEnglishHeadwordForms(html, baseWord)
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
                formsDeferred.awaitAll().forEach { formList ->
                    wordForms.addAll(formList)
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
                phrasalVerbs = phrasalVerbs.distinctBy { it.lowercase(Locale.US) }.take(60),
                idioms = idioms.distinctBy { it.lowercase(Locale.US) }.take(60),
                compounds = compounds.distinctBy { it.lowercase(Locale.US) }.take(60),
                collocations = collocations.distinctBy { it.lowercase(Locale.US) }.take(60),
                synonyms = synonyms.distinctBy { it.lowercase(Locale.US) }.take(60),
                antonyms = antonyms.distinctBy { it.lowercase(Locale.US) }.take(60),
                derivedWords = derivedWords.distinctBy { it.lowercase(Locale.US) }.take(40),
                relatedTerms = relatedTerms.distinctBy { it.lowercase(Locale.US) }.take(60),
                arabicGlosses = arabicGlosses.take(150),
                wordForms = wordForms.distinctBy { "${it.form.lowercase(Locale.US)}|${it.label}" }.take(12)
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
                val synD = async { runCatching { datamuseApi.getSynonyms(word, 50) }.getOrDefault(emptyList()) }
                val antD = async { runCatching { datamuseApi.getAntonyms(word, 50) }.getOrDefault(emptyList()) }
                val mlD = async { runCatching { datamuseApi.getMeansLike(word, max = 40) }.getOrDefault(emptyList()) }
                val trgD = async { runCatching { datamuseApi.getTriggeredWords(word, max = 40) }.getOrDefault(emptyList()) }
                val jjbD = async { runCatching { datamuseApi.getAdjectiveCollocations(word, 40) }.getOrDefault(emptyList()) }
                val jjaD = async { runCatching { datamuseApi.getNounCollocations(word, 40) }.getOrDefault(emptyList()) }
                val bgaD = async { runCatching { datamuseApi.getFollowers(word, 40) }.getOrDefault(emptyList()) }
                val spcD = async { runCatching { datamuseApi.getMoreGeneralTerms(word, 30) }.getOrDefault(emptyList()) }
                val genD = async { runCatching { datamuseApi.getMoreSpecificTerms(word, 30) }.getOrDefault(emptyList()) }
                val homD = async { runCatching { datamuseApi.getHomophones(word, 30) }.getOrDefault(emptyList()) }
                val slD = async { runCatching { datamuseApi.getSoundsLike(word, 30) }.getOrDefault(emptyList()) }
                val spD = async { runCatching { datamuseApi.getSimilarSpelling(word, 30) }.getOrDefault(emptyList()) }
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
                        runCatching { datamuseApi.getSpellingMatches("$stem*", max = 25) }
                            .getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                }

                val synonyms = synD.await().mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(50)

                val antonyms = antD.await().mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(50)

                val collocations = (jjbD.await().mapNotNull { it.word?.trim()?.let { adj -> "$adj $word" } } +
                    jjaD.await().mapNotNull { it.word?.trim()?.let { n -> "$word $n" } })
                    .distinct()
                    .take(50)

                val prepositions = bgaD.await().mapNotNull { it.word?.trim()?.lowercase(Locale.US) }
                    .filter { it in EXACT_PARTICLES }
                    .distinct()
                    .map { "$word $it" }

                val generalTerms = spcD.await().mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(30)

                val specificTerms = genD.await().mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(30)

                val relatedWords = (mlD.await() + trgD.await())
                    .mapNotNull { it.word?.trim() }
                    .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                    .distinct()
                    .take(50)

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
        val collected = LinkedHashMap<String, String>()
        for (item in translations) {
            val obj = item as? JsonObject ?: continue
            val code = obj["code"]?.jsonPrimitive?.contentOrNull
                ?: obj["lang"]?.jsonPrimitive?.contentOrNull
            if (code.equals("ar", ignoreCase = true) || code.equals("ara", ignoreCase = true)) {
                val word = obj["word"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: obj["text"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: obj["translation"]?.jsonPrimitive?.contentOrNull?.trim()
                if (!word.isNullOrBlank() && ArabicTextUtils.containsArabicLetters(word)) {
                    val norm = ArabicTextUtils.normalizeArabicForMatching(word)
                    val prev = collected[norm]
                    if (prev == null || (!ArabicTextUtils.hasArabicDiacritics(prev) && ArabicTextUtils.hasArabicDiacritics(word))) {
                        collected[norm] = word
                    }
                }
            }
        }
        return collected.values.joinToString("؛ ").takeIf { it.isNotBlank() }
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
            .take(45)
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

    private fun buildReverseArabicGroups(
        rows: List<ReverseArabicEntryEntity>,
        audioIndex: Map<String, WordIndexEntity>,
        fallbackDisplayWord: String,
        hasExplicitTashkeel: Boolean,
        canonicalExact: String
    ): List<ArabicVocalizedGroup> {
        if (rows.isEmpty()) return emptyList()
        val grouped = rows.groupBy { row ->
            ArabicTextUtils.canonicalizeArabicWithDiacritics(row.vocalizedArabic)
                .ifBlank { fallbackDisplayWord }
        }
        val groups = grouped.map { (vocalized, groupRows) ->
            val sortedRows = groupRows.sortedWith(
                compareBy<ReverseArabicEntryEntity> { it.frequencyRank }.thenBy { it.englishWord }
            )
            val matches = sortedRows
                .distinctBy { "${it.englishWord.lowercase(Locale.US)}|${it.partOfSpeech.lowercase(Locale.US)}" }
                .map { r ->
                    val idx = audioIndex[r.englishWord.lowercase(Locale.US)]
                    ReverseEnglishMatch(
                        englishWord = r.englishWord,
                        partOfSpeech = r.partOfSpeech,
                        englishDefinition = r.englishDefinition,
                        usAudioUrl = idx?.usAudioUrl,
                        ukAudioUrl = idx?.ukAudioUrl,
                        source = r.source
                    )
                }
            ArabicVocalizedGroup(
                vocalizedForm = vocalized,
                normalizedForm = sortedRows.first().normalizedArabic,
                matches = matches
            ) to (sortedRows.minOfOrNull { it.frequencyRank } ?: Int.MAX_VALUE)
        }

        val sortedGroups = groups.sortedWith(
            compareBy<Pair<ArabicVocalizedGroup, Int>> {
                if (hasExplicitTashkeel && it.first.vocalizedForm == canonicalExact) 0 else 1
            }.thenBy { it.second }.thenBy { it.first.vocalizedForm }
        ).map { it.first }

        return sortedGroups
    }

    private fun mergeArabicWordEntry(
        normalizedKey: String,
        displayWord: String,
        canonicalExact: String,
        hasExplicitTashkeel: Boolean,
        offlinePack: WordEntry?,
        wiktionary: WiktionaryCachedPayload?,
        tatoeba: TatoebaCachedPayload?,
        reverseGroups: List<ArabicVocalizedGroup> = emptyList(),
        machineTranslations: List<String>,
        missingTranslationPack: Boolean,
        isFromCache: Boolean
    ): WordEntry? {
        val rawWkSenses = wiktionary?.takeIf { !it.notFound }?.senses.orEmpty()
        val packSenses = offlinePack?.posGroups?.flatMap { it.senses }.orEmpty()
        val allVocalized = (wiktionary?.vocalizedHeadwords.orEmpty() +
            rawWkSenses.mapNotNull { it.headwordVariant } +
            reverseGroups.map { it.vocalizedForm })
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

        val reverseDerivedSenses = if (filteredWkSenses.isEmpty() && packSenses.isEmpty() && reverseGroups.isNotEmpty()) {
            var idx = 1
            reverseGroups.flatMap { grp ->
                grp.matches.take(8).map { match ->
                    SenseItem(
                        index = idx++,
                        partOfSpeech = match.partOfSpeech.ifBlank { "word" },
                        partOfSpeechArabic = mapPosToArabic(match.partOfSpeech),
                        definition = grp.vocalizedForm,
                        definitionArabic = buildString {
                            append(match.englishWord)
                            if (match.englishDefinition.isNotBlank()) {
                                append(" — ")
                                append(match.englishDefinition)
                            }
                        },
                        definitionArabicIsMachine = false,
                        headwordVariant = grp.vocalizedForm,
                        source = SenseSource.OFFLINE_PACK
                    )
                }
            }
        } else {
            emptyList()
        }

        val combinedSenses = if (filteredWkSenses.isNotEmpty() || packSenses.isNotEmpty() || reverseDerivedSenses.isNotEmpty()) {
            LinguisticRanker.dedupAndRankSenses(filteredWkSenses + packSenses + reverseDerivedSenses)
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
            reverseGroups.isNotEmpty() ||
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
            reverseArabicGroups = reverseGroups,
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
            isFromOfflinePack = offlinePack != null || reverseGroups.isNotEmpty(),
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
        isFromCache: Boolean,
        exactPackIdiom: PackIdiomEntity? = null,
        containingPackIdioms: List<PackIdiomEntity> = emptyList()
    ): WordEntry? {
        val curatedExact = ContextualTranslationAnalyzer.lookupCuratedIdiom(word, isArabic = false)
        val exactIdiomSense = when {
            curatedExact != null -> listOf(
                SenseItem(
                    index = 1,
                    partOfSpeech = if (curatedExact.isPhrasalVerb) "verb" else "idiom",
                    partOfSpeechArabic = if (curatedExact.isPhrasalVerb) "فعل مركب" else "تعبير اصطلاحي",
                    definition = curatedExact.englishMeaning,
                    definitionArabic = curatedExact.arabicMeaning,
                    definitionArabicIsMachine = false,
                    examples = listOfNotNull(curatedExact.example.takeIf { it.isNotBlank() }),
                    usageNote = curatedExact.usageNote,
                    source = SenseSource.OFFLINE_PACK
                )
            )
            exactPackIdiom != null && (exactPackIdiom.englishDefinition.isNotBlank() || exactPackIdiom.arabicMeaning.isNotBlank()) -> listOf(
                SenseItem(
                    index = 1,
                    partOfSpeech = if (exactPackIdiom.category.equals("PHRASAL_VERB", ignoreCase = true)) "verb" else "idiom",
                    partOfSpeechArabic = if (exactPackIdiom.category.equals("PHRASAL_VERB", ignoreCase = true)) "فعل مركب" else "تعبير اصطلاحي",
                    definition = exactPackIdiom.englishDefinition.ifBlank { exactPackIdiom.phrase },
                    definitionArabic = exactPackIdiom.arabicMeaning.takeIf { it.isNotBlank() },
                    definitionArabicIsMachine = false,
                    examples = listOfNotNull(exactPackIdiom.exampleSentence.takeIf { it.isNotBlank() }),
                    exampleTranslations = if (exactPackIdiom.exampleSentence.isNotBlank() && exactPackIdiom.exampleArabic.isNotBlank()) {
                        mapOf(exactPackIdiom.exampleSentence to exactPackIdiom.exampleArabic)
                    } else emptyMap(),
                    exampleIsMachine = if (exactPackIdiom.exampleSentence.isNotBlank() && exactPackIdiom.exampleArabic.isNotBlank()) {
                        mapOf(exactPackIdiom.exampleSentence to false)
                    } else emptyMap(),
                    source = SenseSource.OFFLINE_PACK
                )
            )
            else -> emptyList()
        }

        val fdSenses = freeDict?.takeIf { !it.notFound }?.senses.orEmpty()
        val wkSenses = wiktionary?.takeIf { !it.notFound }?.senses.orEmpty()
        val wnSenses = datamuse?.wordNetSenses.orEmpty()
        val packSenses = offlinePack?.posGroups?.flatMap { it.senses }.orEmpty()

        val wkArabicGlosses = wiktionary?.arabicGlosses.orEmpty().map {
            WiktionaryArabicGloss(it.senseGloss, it.arabicTerms)
        }

        val combinedSenses = if (exactIdiomSense.isNotEmpty() || fdSenses.isNotEmpty() || wkSenses.isNotEmpty() || wnSenses.isNotEmpty() || packSenses.isNotEmpty()) {
            LinguisticRanker.dedupAndRankSenses(exactIdiomSense + fdSenses + wkSenses + wnSenses + packSenses)
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

        val posGroupsRaw = LinguisticRanker.groupAndNumberPosGroups(enrichedSenses, ::mapPosToArabic)
        val posGroups = if (wkArabicGlosses.isNotEmpty() && posGroupsRaw.isNotEmpty()) {
            val existingArNorms = HashSet<String>()
            posGroupsRaw.forEach { g ->
                g.senses.forEach { s ->
                    s.definitionArabic?.split('؛', ';', '،')?.forEach { part ->
                        val n = ArabicTextUtils.normalizeArabicForMatching(part.trim())
                        if (n.isNotBlank()) existingArNorms.add(n)
                    }
                }
            }
            val unusedTerms = LinkedHashMap<String, String>()
            wkArabicGlosses.forEach { gloss ->
                gloss.arabicTerms.forEach { term ->
                    val clean = term.trim()
                    val norm = ArabicTextUtils.normalizeArabicForMatching(clean)
                    if (norm.isNotBlank() && norm !in existingArNorms) {
                        val prev = unusedTerms[norm]
                        if (prev == null || (!ArabicTextUtils.hasArabicDiacritics(prev) && ArabicTextUtils.hasArabicDiacritics(clean))) {
                            unusedTerms[norm] = clean
                        }
                    }
                }
            }
            if (unusedTerms.isNotEmpty()) {
                val firstGroup = posGroupsRaw.first()
                val firstSense = firstGroup.senses.first()
                val extraJoined = unusedTerms.values.joinToString("؛ ")
                val combinedAr = listOfNotNull(firstSense.definitionArabic?.takeIf { it.isNotBlank() }, extraJoined)
                    .joinToString("؛ ")
                val updatedFirstGroup = firstGroup.copy(
                    senses = listOf(firstSense.copy(definitionArabic = combinedAr)) + firstGroup.senses.drop(1)
                )
                listOf(updatedFirstGroup) + posGroupsRaw.drop(1)
            } else {
                posGroupsRaw
            }
        } else {
            posGroupsRaw
        }

        val allSynonyms = (enrichedSenses.flatMap { it.synonyms } +
            wiktionary?.synonyms.orEmpty() +
            datamuse?.synonyms.orEmpty() +
            offlinePack?.synonyms.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(60)

        val allAntonyms = (enrichedSenses.flatMap { it.antonyms } +
            wiktionary?.antonyms.orEmpty() +
            datamuse?.antonyms.orEmpty() +
            offlinePack?.antonyms.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(60)

        val collocations = (wiktionary?.collocations.orEmpty() +
            datamuse?.collocations.orEmpty() +
            offlinePack?.collocations.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(60)

        val curatedForWord = ContextualTranslationAnalyzer.getCuratedPhrasesForHeadword(word, isArabic = false)
        val curatedPhrasals = curatedForWord.filter { it.isPhrasalVerb }.map { it.phrase }
        val curatedIdioms = curatedForWord.filter { !it.isPhrasalVerb }.map { it.phrase }

        val packIdiomPhrases = containingPackIdioms.filter { !it.category.equals("PHRASAL_VERB", ignoreCase = true) }.map { it.phrase }
        val packPhrasalPhrases = containingPackIdioms.filter { it.category.equals("PHRASAL_VERB", ignoreCase = true) }.map { it.phrase }

        val allIdioms = (wiktionary?.idioms.orEmpty() + curatedIdioms + packIdiomPhrases)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(60)

        val compoundExpressions = (wiktionary?.compounds.orEmpty() +
            wiktionary?.collocations.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(50)

        val prepositionsAndPhrases = (wiktionary?.phrasalVerbs.orEmpty() +
            curatedPhrasals +
            packPhrasalPhrases +
            wiktionary?.idioms.orEmpty() +
            curatedIdioms +
            packIdiomPhrases +
            datamuse?.prepositions.orEmpty() +
            freeDict?.phrases.orEmpty() +
            wiktionary?.phrases.orEmpty() +
            offlinePack?.prepositionsAndPhrases.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(80)

        val curatedItems = curatedForWord.map { k ->
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
        val packItems = (listOfNotNull(exactPackIdiom) + containingPackIdioms)
            .filter { it.englishDefinition.isNotBlank() || it.arabicMeaning.isNotBlank() }
            .map { p ->
                val cat = p.category.ifBlank { "IDIOM" }
                PhraseDetailItem(
                    phrase = p.phrase,
                    englishMeaning = p.englishDefinition,
                    arabicMeaning = p.arabicMeaning.takeIf { it.isNotBlank() },
                    isArabicMachine = false,
                    example = p.exampleSentence,
                    exampleArabic = p.exampleArabic.takeIf { it.isNotBlank() },
                    exampleIsMachine = false,
                    isIdiom = !cat.equals("PHRASAL_VERB", ignoreCase = true),
                    category = cat,
                    usageExplanation = "تعبير اصطلاحي يُفهم معناه من السياق لا من الترجمة الحرفية",
                    grammarStructure = LinguisticRanker.inferGrammarStructure(p.phrase, cat)
                )
            }
        val initialPhrasalAndIdiomItems = (curatedItems + packItems)
            .distinctBy { it.phrase.trim().lowercase(Locale.US) }

        val wkFamily = wiktionary?.derivedWords.orEmpty().map {
            WordFamilyItem(word = it, partOfSpeech = "", partOfSpeechArabic = "مشتق")
        }
        val packFamily = offlinePack?.wordFamily.orEmpty()
        val dmFamily = datamuse?.familyCandidates.orEmpty()
        val wordFamily = (wkFamily + packFamily + dmFamily)
            .distinctBy { it.word.lowercase(Locale.US) }
            .take(30)

        val relatedWords = (wiktionary?.relatedTerms.orEmpty() + datamuse?.relatedWords.orEmpty())
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.US) }
            .take(60)

        val confusableCandidates = if (datamuse != null && datamuse.confusableCandidates.isNotEmpty()) {
            datamuse.confusableCandidates
        } else {
            offlinePack?.confusableCandidates.orEmpty()
        }

        val combinedForms = (wiktionary?.wordForms.orEmpty() + offlinePack?.wordForms.orEmpty())
            .distinctBy { "${it.form.lowercase(Locale.US)}|${it.label}" }
            .take(12)

        // Show up to 3 Tatoeba examples per word (BUG 5)
        val displayedTatoeba = tatoebaPool.take(3)

        val hasAnyData = posGroups.isNotEmpty() ||
            allSynonyms.isNotEmpty() ||
            allAntonyms.isNotEmpty() ||
            displayedTatoeba.isNotEmpty() ||
            relatedWords.isNotEmpty() ||
            prepositionsAndPhrases.isNotEmpty() ||
            allIdioms.isNotEmpty() ||
            initialPhrasalAndIdiomItems.isNotEmpty() ||
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
            frequencyRank = offlinePack?.frequencyRank ?: Int.MAX_VALUE,
            wordForms = combinedForms,
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
            isFromOfflinePack = offlinePack != null || exactPackIdiom != null || containingPackIdioms.isNotEmpty(),
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
            val rank = root.optInt("rank", entity.frequencyRank)
            val wordForms = mutableListOf<WordFormItem>()
            val formsArr = root.optJSONArray("forms")
            if (formsArr != null) {
                for (i in 0 until formsArr.length()) {
                    val obj = formsArr.optJSONObject(i)
                    if (obj != null) {
                        val f = obj.optString("form", "").trim()
                        val l = obj.optString("label", "").trim()
                        if (f.isNotEmpty()) {
                            val lAr = when (l) {
                                "pa. pple." -> "اسم المفعول"
                                "pa. t." -> "الماضي"
                                "3rd sg." -> "المضارع المفرد"
                                "pres. pple." -> "اسم الفاعل"
                                "pl." -> "الجمع"
                                "comp." -> "المقارنة"
                                "sup." -> "التفضيل"
                                else -> l
                            }
                            wordForms.add(WordFormItem(form = f, label = l, labelArabic = lAr))
                        }
                    } else {
                        val fStr = formsArr.optString(i, "").trim()
                        if (fStr.isNotEmpty()) {
                            wordForms.add(WordFormItem(form = fStr))
                        }
                    }
                }
            }

            WordEntry(
                word = entity.normalizedWord,
                displayWord = word.ifBlank { entity.word },
                isArabicWord = isAr,
                lang = lang,
                usIpa = phonetic,
                tags = tags,
                frequencyRank = rank,
                wordForms = wordForms,
                posGroups = posGroups,
                synonyms = syns.distinct().take(60),
                antonyms = ants.distinct().take(60),
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

    private fun mapDatamusePosShort(tags: List<String>?): String {
        val tag = tags?.firstOrNull()?.lowercase(Locale.US) ?: return ""
        return when (tag) {
            "n" -> "n."
            "v" -> "v."
            "adj" -> "adj."
            "adv" -> "adv."
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
