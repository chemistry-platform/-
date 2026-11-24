package com.example.data.repository

import android.os.Process
import android.util.Log
import android.util.LruCache
import com.example.core.ArabicTextUtils
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
private const val SRC_WIKTIONARY_AR = "wiktionary_ar"
private const val SRC_DATAMUSE = "datamuse"
private const val SRC_TATOEBA = "tatoeba"
private const val SRC_TATOEBA_AR = "tatoeba_ar"
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

    suspend fun clearApiCache() {
        synchronized(readyEntryCache) {
            readyEntryCache.evictAll()
        }
        dao.clearApiCache()
        dao.clearWordIndex()
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
        val memKey = "${if (isArabic) "ar" else "en"}:$normalized"
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
                            fetchArabicWiktionarySource(slug, normalized)
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

    // --- Section 14: Sentence & Phrase Lookup (Both Directions) ---
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
                if (wordsCount in 2..4) {
                    checkKnownPhraseEntryExists(cleaned, isArabic)
                } else {
                    false
                }
            }

            val tatoebaDeferred = async(Dispatchers.IO) {
                if (!networkMonitor.isOnline()) return@async emptyList()
                withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    val queryForTatoeba = wordTokens.take(4).joinToString(" ")
                    if (isArabic) {
                        fetchTatoebaArabicToEnglish(queryForTatoeba)?.sentences.orEmpty()
                    } else {
                        fetchTatoebaSource(queryForTatoeba)?.sentences.orEmpty()
                    }
                }.orEmpty()
            }

            val (mt, missing) = mtDeferred.await()
            machineTranslation = mt
            missingPack = missing
            directPhraseFound = phraseCheckDeferred.await()
            tatoebaList = tatoebaDeferred.await()
        }

        send(
            SentenceLookupResult(
                originalText = cleaned,
                isArabicInput = isArabic,
                machineTranslation = machineTranslation,
                missingTranslationPack = missingPack,
                wordChips = wordTokens,
                tatoebaSentences = tatoebaList,
                isUpdating = false,
                directPhraseEntryAvailable = directPhraseFound
            )
        )
    }

    private suspend fun checkKnownPhraseEntryExists(phrase: String, isArabic: Boolean): Boolean {
        val key = ArabicTextUtils.normalizeForKey(phrase)
        if (dao.getOfflinePackWord(key) != null) return true
        if (dao.getWordIndex(key) != null) return true
        if (!networkMonitor.isOnline()) return false

        return if (isArabic) {
            val slug = ArabicTextUtils.stripArabicDiacriticsKeepHamza(phrase)
            val wik = withTimeoutOrNull(4_500L) { fetchArabicWiktionarySource(slug, key) }
            wik != null && !wik.notFound && wik.senses.isNotEmpty()
        } else {
            val fd = withTimeoutOrNull(4_500L) { fetchFreeDictionarySource(key) }
            if (fd != null && !fd.notFound && fd.senses.isNotEmpty()) return true
            val wik = withTimeoutOrNull(4_500L) { fetchWiktionarySource(key) }
            wik != null && !wik.notFound && wik.senses.isNotEmpty()
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
        val memKey = "ar:$normalized"

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
                            fetchArabicWiktionarySource(slugWithHamza, normalized)
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
            val summary = firstSense?.definition.orEmpty()
                .ifBlank { finalEntry.machineTranslations.joinToString(" • ") }
                .take(180)
            dao.upsertWordIndex(
                WordIndexEntity(
                    word = slugWithHamza,
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
                queryKey = normalized,
                normalizedQuery = normalized,
                displayQuery = displayWord,
                resolvedWord = displayWord,
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

        if (!forceRefresh) {
            val memCached = synchronized(readyEntryCache) { readyEntryCache.get(memKey) }
            if (memCached != null) {
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

        var needFreeDict = true
        var needWiktionary = true
        var needDatamuse = true
        var needTatoeba = true

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
        }

        // Section 15.3: ML Kit quick en -> ar translation of headword when pack is downloaded
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
                emptyList<String>() to !translationManager.isPairDownloaded(
                    TranslateLanguage.ENGLISH,
                    TranslateLanguage.ARABIC
                )
            }
        }

        val online = networkMonitor.isOnline()
        val anyRemoteNeeded = online && (needFreeDict || needWiktionary || needDatamuse || needTatoeba)

        val initialEntry = withContext(Dispatchers.Default) {
            mergeWordEntry(
                word = normalized,
                offlinePack = offlinePackEntry,
                freeDict = freeDictPayload,
                wiktionary = wiktionaryPayload,
                datamuse = datamusePayload,
                tatoeba = tatoebaPayload,
                machineTranslations = mtHeadwordResult.first,
                missingTranslationPack = mtHeadwordResult.second,
                isFromCache = (freeDictPayload != null || wiktionaryPayload != null)
            )
        }

        if (initialEntry != null) {
            audioPlayerManager.prefetchWordAudio(
                word = initialEntry.word,
                usAudioUrl = initialEntry.usAudioUrl,
                ukAudioUrl = initialEntry.ukAudioUrl
            )
            emitState(
                WordLoadState.Success(
                    entry = initialEntry,
                    isUpdating = anyRemoteNeeded
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
            suspend fun emitUpdated(isFinal: Boolean = false) {
                val merged = withContext(Dispatchers.Default) {
                    mergeMutex.withLock {
                        mergeWordEntry(
                            word = normalized,
                            offlinePack = offlinePackEntry,
                            freeDict = freeDictPayload,
                            wiktionary = wiktionaryPayload,
                            datamuse = datamusePayload,
                            tatoeba = tatoebaPayload,
                            machineTranslations = mtHeadwordResult.first,
                            missingTranslationPack = mtHeadwordResult.second,
                            isFromCache = false
                        )
                    }
                }
                if (merged != null) {
                    audioPlayerManager.prefetchWordAudio(
                        word = merged.word,
                        usAudioUrl = merged.usAudioUrl,
                        ukAudioUrl = merged.ukAudioUrl
                    )
                    emitState(
                        WordLoadState.Success(
                            entry = merged,
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

        val finalEntry = withContext(Dispatchers.Default) {
            mergeWordEntry(
                word = normalized,
                offlinePack = offlinePackEntry,
                freeDict = freeDictPayload,
                wiktionary = wiktionaryPayload,
                datamuse = datamusePayload,
                tatoeba = tatoebaPayload,
                machineTranslations = mtHeadwordResult.first,
                missingTranslationPack = mtHeadwordResult.second,
                isFromCache = !anyRemoteNeeded
            )
        }

        if (finalEntry == null ||
            (finalEntry.posGroups.isEmpty() &&
                finalEntry.synonyms.isEmpty() &&
                finalEntry.tatoebaSentences.isEmpty() &&
                finalEntry.relatedWords.isEmpty() &&
                finalEntry.machineTranslations.isEmpty())
        ) {
            emitState(
                WordLoadState.Error(
                    message = "لم يتم العثور على مداخل قاموسية لـ \"$trimmed\".",
                    isOffline = !networkMonitor.isOnline()
                )
            )
            return
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
                    summaryDefinition = firstSense?.definition.orEmpty().take(180),
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
                previewSummary = firstSense?.definition.orEmpty()
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
        results.filterNotNull().associateBy { it.word.lowercase(Locale.US) }
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

    // --- Source B1: Wiktionary REST API ("en" block) ---
    private suspend fun fetchWiktionarySource(word: String): WiktionaryCachedPayload? {
        return fetchWiktionaryByLanguageBlock(
            slug = word.trim().replace(Regex("\\s+"), "_"),
            langBlockKey = "en",
            baseWord = word,
            extractPhrases = true
        )
    }

    // --- Source B2: Wiktionary REST API ("ar" block, Section 15.2) ---
    private suspend fun fetchArabicWiktionarySource(
        slugWithHamza: String,
        normalizedArabic: String
    ): WiktionaryCachedPayload? {
        val primary = fetchWiktionaryByLanguageBlock(
            slug = slugWithHamza.replace(Regex("\\s+"), "_"),
            langBlockKey = "ar",
            baseWord = slugWithHamza,
            extractPhrases = false
        )
        if (primary != null && (!primary.notFound || slugWithHamza == normalizedArabic)) {
            return primary
        }
        return fetchWiktionaryByLanguageBlock(
            slug = normalizedArabic.replace(Regex("\\s+"), "_"),
            langBlockKey = "ar",
            baseWord = normalizedArabic,
            extractPhrases = false
        )
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
                    defObj["examples"]?.jsonArray?.forEach { exEl ->
                        val cleaned = ArabicTextUtils.stripHtmlPlain(exEl.jsonPrimitive.contentOrNull.orEmpty())
                        if (cleaned.isNotBlank()) exList.add(cleaned)
                    }
                    defObj["parsedExamples"]?.jsonArray?.forEach { pEl ->
                        val exHtml = pEl.jsonObject["example"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val cleaned = ArabicTextUtils.stripHtmlPlain(exHtml)
                        if (cleaned.isNotBlank() && cleaned !in exList) exList.add(cleaned)
                    }

                    if (extractPhrases) {
                        extractWikiLinksForPhrases(defHtml, baseWord, phrases, derivedWords)
                    }

                    senses.add(
                        SenseItem(
                            index = idx++,
                            partOfSpeech = pos,
                            partOfSpeechArabic = posAr,
                            definition = cleanDef,
                            examples = exList.take(3),
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

    // --- Source D1: Tatoeba API (eng -> ara, up to 15) ---
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
                    pairs.add(TatoebaSentence(english = enText, arabic = arText))
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
                val enText = extractTranslationFromTatoeba(obj["translations"] as? JsonArray, wantArabic = false)
                if (enText.isNotBlank()) {
                    pairs.add(TatoebaSentence(english = enText, arabic = arText))
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
        offlinePack: WordEntry?,
        wiktionary: WiktionaryCachedPayload?,
        tatoeba: TatoebaCachedPayload?,
        machineTranslations: List<String>,
        missingTranslationPack: Boolean,
        isFromCache: Boolean
    ): WordEntry? {
        val wkSenses = wiktionary?.takeIf { !it.notFound }?.senses.orEmpty()
        val packSenses = offlinePack?.posGroups?.flatMap { it.senses }.orEmpty()
        val combinedSenses = if (wkSenses.isNotEmpty() || packSenses.isNotEmpty()) {
            dedupSenses(wkSenses + packSenses)
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

        val tatoebaList = tatoeba?.sentences.orEmpty().take(15)
        val hasAnyData = posGroups.isNotEmpty() ||
            tatoebaList.isNotEmpty() ||
            machineTranslations.isNotEmpty()

        if (!hasAnyData) return null

        return WordEntry(
            word = normalizedKey,
            displayWord = displayWord,
            isArabicWord = true,
            lang = "ar",
            usIpa = "",
            ukIpa = "",
            usAudioUrl = null,
            ukAudioUrl = null,
            machineTranslations = machineTranslations,
            missingTranslationPack = missingTranslationPack,
            posGroups = posGroups,
            synonyms = offlinePack?.synonyms.orEmpty(),
            antonyms = offlinePack?.antonyms.orEmpty(),
            collocations = offlinePack?.collocations.orEmpty(),
            prepositionsAndPhrases = offlinePack?.prepositionsAndPhrases.orEmpty(),
            tatoebaSentences = tatoebaList,
            hasWiktionaryAttribution = wkSenses.isNotEmpty(),
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
        machineTranslations: List<String>,
        missingTranslationPack: Boolean,
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
            datamuse?.relatedWords?.isNotEmpty() == true ||
            machineTranslations.isNotEmpty()

        if (!hasAnyData) return null

        return WordEntry(
            word = word,
            displayWord = word,
            isArabicWord = false,
            lang = "en",
            usIpa = freeDict?.usIpa.orEmpty().ifBlank { offlinePack?.usIpa.orEmpty() },
            ukIpa = freeDict?.ukIpa.orEmpty(),
            usAudioUrl = freeDict?.usAudioUrl,
            ukAudioUrl = freeDict?.ukAudioUrl,
            machineTranslations = machineTranslations,
            missingTranslationPack = missingTranslationPack,
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
            val word = root.optString("word", entity.word).trim()
            val lang = root.optString("lang", entity.lang).trim().lowercase(Locale.US)
                .ifBlank { if (ArabicTextUtils.containsArabicLetters(word)) "ar" else "en" }
            val isAr = lang == "ar" || ArabicTextUtils.containsArabicLetters(word)
            val phonetic = if (isAr) "" else root.optString("phonetic", entity.phonetic).trim()
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
                word = entity.normalizedWord,
                displayWord = word.ifBlank { entity.word },
                isArabicWord = isAr,
                lang = lang,
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
}
