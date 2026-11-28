package com.example.core

import android.content.Context
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.LexiconDao
import com.example.data.local.OfflinePackWordEntity
import com.example.data.local.PackIdiomEntity
import com.example.data.local.ReverseArabicEntryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

private const val TAG = "OfflinePackManager"
private const val PREFS_NAME = "offline_pack_prefs"
private const val KEY_PACK_DISK_BYTES = "pack_disk_bytes"
private const val KEY_PACK_ETAG = "pack_etag"
private const val KEY_PACK_TOTAL_BYTES = "pack_total_bytes"

sealed interface OfflinePackState {
    data class Ready(
        val isInstalled: Boolean,
        val wordCount: Int,
        val diskSizeBytes: Long,
        val idiomCount: Int = 0,
        val reverseCount: Int = 0,
        val updateAvailable: Boolean = false,
        val updateCheckedMessage: String? = null
    ) : OfflinePackState

    data class Downloading(
        val progress: Float,
        val indeterminate: Boolean,
        val statusText: String,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = 0L
    ) : OfflinePackState

    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float
    ) : OfflinePackState

    data class Error(
        val message: String,
        val isInstalled: Boolean,
        val wordCount: Int,
        val diskSizeBytes: Long,
        val idiomCount: Int = 0,
        val reverseCount: Int = 0
    ) : OfflinePackState
}

class OfflinePackManager(
    context: Context,
    private val dao: LexiconDao,
    private val okHttpClient: OkHttpClient,
    private val networkMonitor: NetworkMonitor
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<OfflinePackState>(
        OfflinePackState.Ready(isInstalled = false, wordCount = 0, diskSizeBytes = 0L)
    )
    val state: StateFlow<OfflinePackState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    @Volatile
    private var initialized = false

    val packDownloadUrl: String
        get() = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/dictionary-pack/dictionary-pack.json.gz"

    private val partFile: File
        get() = File(appContext.cacheDir, "dictionary-pack.json.gz.part")

    suspend fun ensureInitialized() {
        if (initialized) return
        initialized = true
        refreshStatus()
    }

    suspend fun refreshStatus(updateAvailable: Boolean = false, updateMsg: String? = null) {
        try {
            if (partFile.exists() && partFile.length() > 0L && downloadJob?.isActive != true) {
                val downloaded = partFile.length()
                val total = prefs.getLong(KEY_PACK_TOTAL_BYTES, 75_000_000L).coerceAtLeast(1L)
                val frac = (downloaded.toFloat() / total.toFloat()).coerceIn(0.01f, 0.99f)
                _state.value = OfflinePackState.Paused(
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    progress = frac
                )
                return
            }
            val (wordCount, idiomCount, reverseCount) = withContext(Dispatchers.IO) {
                Triple(
                    dao.getOfflinePackCount(),
                    dao.getPackIdiomCount(),
                    dao.getReverseArabicCount()
                )
            }
            val installed = wordCount > 0 || idiomCount > 0
            val diskBytes = if (installed) prefs.getLong(KEY_PACK_DISK_BYTES, 0L) else 0L
            _state.value = OfflinePackState.Ready(
                isInstalled = installed,
                wordCount = wordCount,
                diskSizeBytes = diskBytes,
                idiomCount = idiomCount,
                reverseCount = reverseCount,
                updateAvailable = updateAvailable,
                updateCheckedMessage = updateMsg
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed refreshing Pro Pack status", e)
        }
    }

    fun checkForUpdates() {
        if (downloadJob?.isActive == true) return
        if (BuildConfig.GITHUB_REPO == "unknown/unknown" || !BuildConfig.GITHUB_REPO.contains("/") || !networkMonitor.isOnline()) {
            scope.launch { refreshStatus(updateAvailable = false, updateMsg = "Up to date") }
            return
        }
        scope.launch {
            val (hasUpdate, msg) = withContext(Dispatchers.IO) {
                runCatching {
                    val req = Request.Builder()
                        .url(packDownloadUrl)
                        .head()
                        .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use false to "Up to date"
                        val remoteEtag = resp.header("ETag").orEmpty().trim()
                        val remoteLen = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                        val localEtag = prefs.getString(KEY_PACK_ETAG, "").orEmpty()
                        val localLen = prefs.getLong(KEY_PACK_TOTAL_BYTES, 0L)
                        val changed = (remoteEtag.isNotBlank() && localEtag.isNotBlank() && remoteEtag != localEtag) ||
                            (remoteLen > 0L && localLen > 0L && remoteLen != localLen)
                        if (changed) true to "Update available" else false to "Up to date"
                    }
                }.getOrDefault(false to "Up to date")
            }
            refreshStatus(updateAvailable = hasUpdate, updateMsg = msg)
        }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        if (BuildConfig.GITHUB_REPO == "unknown/unknown" || !BuildConfig.GITHUB_REPO.contains("/")) {
            scope.launch {
                val (wordCount, idiomCount, reverseCount) = withContext(Dispatchers.IO) {
                    Triple(dao.getOfflinePackCount(), dao.getPackIdiomCount(), dao.getReverseArabicCount())
                }
                val installed = wordCount > 0 || idiomCount > 0
                val diskBytes = if (installed) prefs.getLong(KEY_PACK_DISK_BYTES, 0L) else 0L
                _state.value = OfflinePackState.Error(
                    message = "Pro Pack is unavailable right now",
                    isInstalled = installed,
                    wordCount = wordCount,
                    diskSizeBytes = diskBytes,
                    idiomCount = idiomCount,
                    reverseCount = reverseCount
                )
            }
            return
        }
        if (!networkMonitor.isOnline()) {
            scope.launch {
                val (wordCount, idiomCount, reverseCount) = withContext(Dispatchers.IO) {
                    Triple(dao.getOfflinePackCount(), dao.getPackIdiomCount(), dao.getReverseArabicCount())
                }
                val installed = wordCount > 0 || idiomCount > 0
                val diskBytes = if (installed) prefs.getLong(KEY_PACK_DISK_BYTES, 0L) else 0L
                _state.value = OfflinePackState.Error(
                    message = "No internet connection",
                    isInstalled = installed,
                    wordCount = wordCount,
                    diskSizeBytes = diskBytes,
                    idiomCount = idiomCount,
                    reverseCount = reverseCount
                )
            }
            return
        }

        downloadJob = scope.launch {
            val tempFile = partFile
            try {
                val existingBytes = withContext(Dispatchers.IO) {
                    if (tempFile.exists()) tempFile.length() else 0L
                }
                val savedTotal = prefs.getLong(KEY_PACK_TOTAL_BYTES, 0L)
                _state.value = OfflinePackState.Downloading(
                    progress = if (savedTotal > 0L) (existingBytes.toFloat() / savedTotal.toFloat()).coerceIn(0f, 0.7f) else 0f,
                    indeterminate = existingBytes == 0L,
                    statusText = "Downloading Pro Pack...",
                    downloadedBytes = existingBytes,
                    totalBytes = savedTotal
                )

                val downloadedBytes = withContext(Dispatchers.IO) {
                    var resumeOffset = if (tempFile.exists()) tempFile.length() else 0L
                    val reqBuilder = Request.Builder()
                        .url(packDownloadUrl)
                        .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
                    if (resumeOffset > 0L) {
                        reqBuilder.header("Range", "bytes=$resumeOffset-")
                    }

                    var etagHeader = ""
                    okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                        if (response.code == 416) {
                            // Partial file already complete
                        } else if (!response.isSuccessful && response.code != 206) {
                            throw PackUnavailableException("HTTP ${response.code}")
                        } else {
                            val isPartial = response.code == 206
                            if (!isPartial) {
                                resumeOffset = 0L
                            }
                            etagHeader = response.header("ETag").orEmpty().trim()
                            val body = response.body ?: throw PackUnavailableException("Empty body")
                            val contentLen = body.contentLength()
                            val total = if (contentLen > 0L) resumeOffset + contentLen else -1L
                            if (total > 0L) {
                                prefs.edit().putLong(KEY_PACK_TOTAL_BYTES, total).apply()
                            }
                            FileOutputStream(tempFile, isPartial).use { out ->
                                val buf = ByteArray(16 * 1024)
                                var copied = resumeOffset
                                var lastPct = -1
                                var lastEmitMs = 0L
                                val input = body.byteStream()
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buf)
                                    if (read == -1) break
                                    out.write(buf, 0, read)
                                    copied += read
                                    if (total > 0L) {
                                        val fraction = (copied.toFloat() / total.toFloat()).coerceIn(0f, 0.7f)
                                        val pct = (copied * 100 / total).toInt().coerceIn(0, 100)
                                        val nowMs = SystemClock.elapsedRealtime()
                                        if (pct != lastPct && (pct == 100 || (nowMs - lastEmitMs) >= 350L)) {
                                            lastPct = pct
                                            lastEmitMs = nowMs
                                            _state.value = OfflinePackState.Downloading(
                                                progress = fraction,
                                                indeterminate = false,
                                                statusText = "Downloading Pro Pack ($pct%)...",
                                                downloadedBytes = copied,
                                                totalBytes = total
                                            )
                                        }
                                    }
                                }
                                out.flush()
                            }
                        }
                    }

                    val gzSize = tempFile.length()
                    _state.value = OfflinePackState.Downloading(
                        progress = 0.75f,
                        indeterminate = true,
                        statusText = "Indexing words, idioms & Arabic index...",
                        downloadedBytes = gzSize,
                        totalBytes = gzSize
                    )

                    val importedJsonBytes = GZIPInputStream(BufferedInputStream(FileInputStream(tempFile), 32 * 1024)).use { gzStream ->
                        importPackFromJsonStream(gzStream) { indexedCount ->
                            val frac = (0.75f + (indexedCount / 200_000f).coerceAtMost(0.24f)).coerceIn(0.75f, 0.99f)
                            _state.value = OfflinePackState.Downloading(
                                progress = frac,
                                indeterminate = false,
                                statusText = "Indexing Pro Pack ($indexedCount entries)...",
                                downloadedBytes = gzSize,
                                totalBytes = gzSize
                            )
                        }
                    }
                    if (etagHeader.isNotBlank()) {
                        prefs.edit().putString(KEY_PACK_ETAG, etagHeader).apply()
                    }
                    tempFile.delete()
                    maxOf(importedJsonBytes, gzSize)
                }

                prefs.edit()
                    .putLong(KEY_PACK_DISK_BYTES, downloadedBytes)
                    .apply()
                refreshStatus()
            } catch (e: CancellationException) {
                refreshStatus()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pro Pack download failed", e)
                val (wordCount, idiomCount, reverseCount) = withContext(Dispatchers.IO) {
                    Triple(dao.getOfflinePackCount(), dao.getPackIdiomCount(), dao.getReverseArabicCount())
                }
                val installed = wordCount > 0 || idiomCount > 0
                val diskBytes = if (installed) prefs.getLong(KEY_PACK_DISK_BYTES, 0L) else 0L
                _state.value = OfflinePackState.Error(
                    message = "Pro Pack is unavailable right now",
                    isInstalled = installed,
                    wordCount = wordCount,
                    diskSizeBytes = diskBytes,
                    idiomCount = idiomCount,
                    reverseCount = reverseCount
                )
            }
        }
    }

    /**
     * Streams a decompressed JSON pack stream into Room (`offline_pack_words`, `pack_idioms`, `reverse_arabic_index`).
     * Supports:
     * - v2 schema object: `{"version": 2, "words": [...], "idioms": [...], "reverse_ar": [...]}` (keys in ANY order)
     * - legacy array: `[...]`
     * - NDJSON stream: `{...}\n{...}`
     */
    suspend fun importPackFromJsonStream(
        inputStream: InputStream,
        onProgress: ((Int) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        dao.clearAllOfflinePackData()

        var totalJsonBytes = 0L
        var totalEntries = 0
        var hasExplicitReverseAr = false

        val wordBatch = ArrayList<OfflinePackWordEntity>(350)
        val idiomBatch = ArrayList<PackIdiomEntity>(350)
        val explicitReverseBatch = ArrayList<ReverseArabicEntryEntity>(500)
        val derivedReverseBatch = ArrayList<ReverseArabicEntryEntity>(500)
        val seenDerivedReverse = HashSet<String>(4096)

        suspend fun flushWords() {
            if (wordBatch.isNotEmpty()) {
                dao.upsertOfflinePackBatch(wordBatch.toList())
                totalEntries += wordBatch.size
                wordBatch.clear()
                onProgress?.invoke(totalEntries)
            }
        }

        suspend fun flushIdioms() {
            if (idiomBatch.isNotEmpty()) {
                dao.upsertPackIdiomsBatch(idiomBatch.toList())
                totalEntries += idiomBatch.size
                idiomBatch.clear()
                onProgress?.invoke(totalEntries)
            }
        }

        suspend fun flushExplicitReverse() {
            if (explicitReverseBatch.isNotEmpty()) {
                dao.upsertReverseArabicBatch(explicitReverseBatch.toList())
                totalEntries += explicitReverseBatch.size
                explicitReverseBatch.clear()
                onProgress?.invoke(totalEntries)
            }
        }

        suspend fun flushDerivedReverse() {
            if (derivedReverseBatch.isNotEmpty()) {
                dao.upsertReverseArabicBatch(derivedReverseBatch.toList())
                derivedReverseBatch.clear()
            }
        }

        JsonReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            reader.isLenient = true
            val rootToken = reader.peek()
            when (rootToken) {
                JsonToken.BEGIN_ARRAY -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        coroutineContext.ensureActive()
                        val parsed = readPackWordObject(reader)
                        if (parsed != null) {
                            totalJsonBytes += parsed.entity.payloadJson.length
                            wordBatch.add(parsed.entity)
                            appendDerivedReverseEntries(parsed.derivedReverse, derivedReverseBatch, seenDerivedReverse)
                            if (wordBatch.size >= 350) flushWords()
                            if (derivedReverseBatch.size >= 500) flushDerivedReverse()
                        }
                    }
                    reader.endArray()
                }
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    var handledAsContainer = false
                    var pendingField: String? = if (reader.hasNext()) reader.nextName() else null

                    while (pendingField != null) {
                        coroutineContext.ensureActive()
                        val field = pendingField
                        pendingField = null
                        when (field) {
                            "version", "generated_at", "meta", "stats" -> {
                                handledAsContainer = true
                                reader.skipValue()
                            }
                            "words" -> {
                                handledAsContainer = true
                                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        coroutineContext.ensureActive()
                                        val parsed = readPackWordObject(reader)
                                        if (parsed != null) {
                                            totalJsonBytes += parsed.entity.payloadJson.length
                                            wordBatch.add(parsed.entity)
                                            if (!hasExplicitReverseAr) {
                                                appendDerivedReverseEntries(parsed.derivedReverse, derivedReverseBatch, seenDerivedReverse)
                                            }
                                            if (wordBatch.size >= 350) flushWords()
                                            if (derivedReverseBatch.size >= 500) flushDerivedReverse()
                                        }
                                    }
                                    reader.endArray()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            "idioms" -> {
                                handledAsContainer = true
                                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        coroutineContext.ensureActive()
                                        val idiom = readPackIdiomObject(reader)
                                        if (idiom != null) {
                                            totalJsonBytes += idiom.englishDefinition.length + idiom.arabicMeaning.length
                                            idiomBatch.add(idiom)
                                            if (idiomBatch.size >= 350) flushIdioms()
                                        }
                                    }
                                    reader.endArray()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            "reverse_ar" -> {
                                handledAsContainer = true
                                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    hasExplicitReverseAr = true
                                    derivedReverseBatch.clear()
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        coroutineContext.ensureActive()
                                        readReverseArabicItems(reader, explicitReverseBatch)
                                        if (explicitReverseBatch.size >= 500) flushExplicitReverse()
                                    }
                                    reader.endArray()
                                } else {
                                    reader.skipValue()
                                }
                            }
                            else -> {
                                if (!handledAsContainer) {
                                    // NDJSON stream where first object is itself a word item
                                    val firstParsed = readRemainingFieldsAfterFirst(reader, field)
                                    reader.endObject()
                                    if (firstParsed != null) {
                                        totalJsonBytes += firstParsed.entity.payloadJson.length
                                        wordBatch.add(firstParsed.entity)
                                        appendDerivedReverseEntries(firstParsed.derivedReverse, derivedReverseBatch, seenDerivedReverse)
                                    }
                                    while (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                        coroutineContext.ensureActive()
                                        val parsed = readPackWordObject(reader)
                                        if (parsed != null) {
                                            totalJsonBytes += parsed.entity.payloadJson.length
                                            wordBatch.add(parsed.entity)
                                            appendDerivedReverseEntries(parsed.derivedReverse, derivedReverseBatch, seenDerivedReverse)
                                            if (wordBatch.size >= 350) flushWords()
                                            if (derivedReverseBatch.size >= 500) flushDerivedReverse()
                                        }
                                    }
                                    pendingField = null
                                    break
                                } else {
                                    reader.skipValue()
                                }
                            }
                        }
                        if (reader.hasNext()) {
                            pendingField = reader.nextName()
                        }
                    }
                    if (handledAsContainer) {
                        reader.endObject()
                    }
                }
                else -> reader.skipValue()
            }
        }

        flushWords()
        flushIdioms()
        if (hasExplicitReverseAr) {
            flushExplicitReverse()
        } else {
            flushDerivedReverse()
        }

        totalJsonBytes
    }

    fun pauseDownload() {
        downloadJob?.cancel()
        downloadJob = null
        scope.launch { refreshStatus() }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        scope.launch {
            withContext(Dispatchers.IO) {
                if (partFile.exists()) partFile.delete()
                prefs.edit().remove(KEY_PACK_TOTAL_BYTES).apply()
            }
            refreshStatus()
        }
    }

    fun deletePack() {
        downloadJob?.cancel()
        downloadJob = null
        scope.launch {
            withContext(Dispatchers.IO) {
                if (partFile.exists()) partFile.delete()
                dao.clearAllOfflinePackData()
                prefs.edit()
                    .remove(KEY_PACK_DISK_BYTES)
                    .remove(KEY_PACK_TOTAL_BYTES)
                    .remove(KEY_PACK_ETAG)
                    .apply()
            }
            refreshStatus()
        }
    }

    private data class ParsedPackWord(
        val entity: OfflinePackWordEntity,
        val derivedReverse: List<ReverseArabicEntryEntity>
    )

    private fun readPackWordObject(reader: JsonReader): ParsedPackWord? {
        reader.beginObject()
        return try {
            readRemainingFieldsAfterFirst(reader, null)
        } finally {
            reader.endObject()
        }
    }

    private fun readRemainingFieldsAfterFirst(
        reader: JsonReader,
        initialFieldName: String?
    ): ParsedPackWord? {
        val root = JSONObject()
        var word = ""
        var lang = ""
        var phonetic = ""
        var ukPhonetic = ""
        var summaryDef = ""
        var explicitPreview = ""
        var freqRank = Int.MAX_VALUE
        val tagsList = mutableListOf<String>()
        var sensesArr: JSONArray? = null

        var currentName = initialFieldName
        while (currentName != null || reader.hasNext()) {
            val name = currentName ?: reader.nextName()
            currentName = null
            when (name) {
                "word" -> {
                    word = readStringOrNull(reader).orEmpty().trim()
                    root.put("word", word)
                }
                "lang" -> {
                    lang = readStringOrNull(reader).orEmpty().trim().lowercase(Locale.US)
                    if (lang.isNotEmpty()) root.put("lang", lang)
                }
                "phonetic", "usIpa" -> {
                    phonetic = readStringOrNull(reader).orEmpty().trim()
                    if (phonetic.isNotEmpty()) root.put("phonetic", phonetic)
                }
                "ukIpa" -> {
                    ukPhonetic = readStringOrNull(reader).orEmpty().trim()
                    if (ukPhonetic.isNotEmpty()) root.put("ukIpa", ukPhonetic)
                }
                "rank", "frequencyRank" -> {
                    freqRank = readIntOrDefault(reader, Int.MAX_VALUE)
                    if (freqRank != Int.MAX_VALUE) root.put("rank", freqRank)
                }
                "preview" -> {
                    explicitPreview = readStringOrNull(reader).orEmpty().trim()
                    if (explicitPreview.isNotEmpty()) root.put("preview", explicitPreview)
                }
                "tags" -> {
                    val tagsArr = readStringArray(reader)
                    root.put("tags", tagsArr)
                    for (i in 0 until tagsArr.length()) {
                        val tag = tagsArr.optString(i).trim()
                        if (tag.isNotEmpty()) tagsList.add(tag)
                    }
                }
                "senses" -> {
                    val arr = readSensesArray(reader)
                    sensesArr = arr
                    root.put("senses", arr)
                    if (summaryDef.isEmpty() && arr.length() > 0) {
                        summaryDef = arr.optJSONObject(0)?.optString("definition").orEmpty()
                    }
                }
                "collocations", "phrases", "idioms", "family", "confusables", "synonyms", "antonyms" -> {
                    root.put(name, readStringArray(reader))
                }
                "forms" -> {
                    root.put("forms", readFormsArray(reader))
                }
                else -> reader.skipValue()
            }
        }

        val trimmedWord = word.trim()
        if (trimmedWord.isBlank()) return null
        val resolvedLang = when {
            lang == "ar" || lang == "en" -> lang
            ArabicTextUtils.containsArabicLetters(trimmedWord) -> "ar"
            else -> "en"
        }
        val keyWord = if (resolvedLang == "ar") {
            ArabicTextUtils.stripArabicDiacriticsKeepHamza(trimmedWord)
        } else {
            trimmedWord.lowercase(Locale.US)
        }
        val normalized = if (resolvedLang == "ar") {
            ArabicTextUtils.normalizeArabicForMatching(trimmedWord)
        } else {
            trimmedWord.lowercase(Locale.US)
        }

        val posList = LinkedHashSet<String>()
        val derivedReverse = ArrayList<ReverseArabicEntryEntity>()
        val previewSb = StringBuilder()

        if (sensesArr != null && sensesArr.length() > 0) {
            val groupedGlosses = LinkedHashMap<String, LinkedHashMap<String, String>>()
            for (i in 0 until sensesArr.length()) {
                val sObj = sensesArr.optJSONObject(i) ?: continue
                val pos = sObj.optString("partOfSpeech", "word").trim().ifBlank { "word" }
                val engDef = sObj.optString("definition", "").trim()
                val arDef = sObj.optString("definitionArabic", "").trim()
                val abbr = LinguisticRanker.abbreviatePosTag(pos)
                if (abbr.isNotBlank()) posList.add(abbr)

                if (resolvedLang == "en" && arDef.isNotBlank()) {
                    val bucket = groupedGlosses.getOrPut(abbr) { LinkedHashMap() }
                    val glosses = arDef.split('؛', ';', '،')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && ArabicTextUtils.containsArabicLetters(it) }
                    for (g in glosses) {
                        val normAr = ArabicTextUtils.normalizeArabicForMatching(g)
                        val dedupKey = normAr.ifEmpty { g }
                        val existingGloss = bucket[dedupKey]
                        if (existingGloss == null) {
                            bucket[dedupKey] = g
                        } else if (!ArabicTextUtils.hasArabicDiacritics(existingGloss) &&
                            ArabicTextUtils.hasArabicDiacritics(g)
                        ) {
                            bucket[dedupKey] = g
                        }
                        if (normAr.isNotEmpty()) {
                            derivedReverse.add(
                                ReverseArabicEntryEntity(
                                    normalizedArabic = normAr,
                                    vocalizedArabic = ArabicTextUtils.canonicalizeArabicWithDiacritics(g),
                                    englishWord = keyWord,
                                    partOfSpeech = pos,
                                    englishDefinition = engDef,
                                    frequencyRank = freqRank,
                                    source = "wiktionary"
                                )
                            )
                        }
                    }
                } else if (resolvedLang == "ar") {
                    val bucket = groupedGlosses.getOrPut(abbr) { LinkedHashMap() }
                    val previewVal = engDef.ifBlank { arDef }
                    if (previewVal.isNotBlank()) {
                        bucket.putIfAbsent(previewVal.lowercase(Locale.US), previewVal)
                    }
                }
            }
            for ((abbr, glossMap) in groupedGlosses) {
                if (glossMap.isEmpty()) continue
                if (previewSb.isNotEmpty()) previewSb.append("؛ ")
                if (abbr.isNotEmpty()) previewSb.append(abbr).append(' ')
                previewSb.append(glossMap.values.joinToString("؛ "))
            }
            if (previewSb.isEmpty() && summaryDef.isNotBlank()) {
                val hasArabic = ArabicTextUtils.containsArabicLetters(summaryDef)
                if ((resolvedLang == "en" && hasArabic) || (resolvedLang == "ar" && !hasArabic)) {
                    previewSb.append(summaryDef)
                }
            }
        }

        val computedPreview = previewSb.toString()
        val finalPreview = when {
            explicitPreview.isBlank() -> computedPreview
            computedPreview.length > explicitPreview.length -> computedPreview
            else -> explicitPreview
        }
        val distinctTags = tagsList.distinct()
        val hasAwl = distinctTags.any { it.equals("AWL", ignoreCase = true) }

        val entity = OfflinePackWordEntity(
            word = keyWord,
            normalizedWord = normalized,
            lang = resolvedLang,
            phonetic = phonetic,
            summaryDefinition = summaryDef.ifBlank { finalPreview },
            previewSummary = finalPreview,
            posTagsCsv = posList.joinToString(","),
            frequencyRank = freqRank,
            tagsCsv = distinctTags.joinToString(","),
            hasAwlTag = hasAwl,
            payloadJson = root.toString()
        )
        return ParsedPackWord(entity, derivedReverse)
    }

    private fun appendDerivedReverseEntries(
        candidates: List<ReverseArabicEntryEntity>,
        batch: MutableList<ReverseArabicEntryEntity>,
        seenKeys: MutableSet<String>
    ) {
        if (candidates.isEmpty()) return
        for (c in candidates) {
            val k = "${c.normalizedArabic}|${c.vocalizedArabic}|${c.englishWord}|${c.partOfSpeech}"
            if (seenKeys.size > 80_000) seenKeys.clear()
            if (seenKeys.add(k)) {
                batch.add(c)
            }
        }
    }

    private fun readPackIdiomObject(reader: JsonReader): PackIdiomEntity? {
        reader.beginObject()
        var phrase = ""
        var engDef = ""
        var arMeaning = ""
        var ex = ""
        var exAr = ""
        var cat = "IDIOM"
        var rank = Int.MAX_VALUE
        try {
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "phrase", "word" -> phrase = readStringOrNull(reader).orEmpty().trim()
                    "def", "englishDefinition" -> engDef = readStringOrNull(reader).orEmpty().trim()
                    "ar", "arabicMeaning" -> arMeaning = readStringOrNull(reader).orEmpty().trim()
                    "ex", "example" -> ex = readStringOrNull(reader).orEmpty().trim()
                    "exAr", "exampleArabic" -> exAr = readStringOrNull(reader).orEmpty().trim()
                    "cat", "category" -> cat = readStringOrNull(reader).orEmpty().trim().ifBlank { "IDIOM" }
                    "rank", "frequencyRank" -> rank = readIntOrDefault(reader, Int.MAX_VALUE)
                    else -> reader.skipValue()
                }
            }
        } finally {
            reader.endObject()
        }
        val cleanPhrase = phrase.replace(Regex("\\s+"), " ").trim()
        if (cleanPhrase.isBlank() || engDef.isBlank()) return null
        val lower = cleanPhrase.lowercase(Locale.US)
        val tokens = lower.split(Regex("[^a-z0-9']+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val tokensSearch = if (tokens.isNotBlank()) " $tokens " else ""
        return PackIdiomEntity(
            phrase = lower,
            normalizedPhrase = lower,
            englishDefinition = engDef,
            arabicMeaning = arMeaning,
            exampleSentence = ex,
            exampleArabic = exAr,
            category = cat,
            frequencyRank = rank,
            tokensSearch = tokensSearch
        )
    }

    /**
     * Reads reverse_ar items in either grouped form:
     * `{"ar": "مر", "voc": "مُرّ", "matches": [{"en": "bitter", "pos": "adj", "def": "...", "rank": 2100, "src": "omwn"}]}`
     * OR flat form:
     * `{"ar": "مر", "voc": "مُرّ", "en": "bitter", "pos": "adj", "def": "...", "rank": 2100, "src": "omwn"}`
     */
    private fun readReverseArabicItems(reader: JsonReader, out: MutableList<ReverseArabicEntryEntity>) {
        reader.beginObject()
        var arNormField = ""
        var vocField = ""
        var flatEn = ""
        var flatPos = ""
        var flatDef = ""
        var flatRank = Int.MAX_VALUE
        var flatSrc = "pack"
        val nestedMatches = ArrayList<ReverseArabicEntryEntity>()
        try {
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "ar" -> arNormField = readStringOrNull(reader).orEmpty().trim()
                    "voc" -> vocField = readStringOrNull(reader).orEmpty().trim()
                    "en" -> flatEn = readStringOrNull(reader).orEmpty().trim().lowercase(Locale.US)
                    "pos" -> flatPos = readStringOrNull(reader).orEmpty().trim()
                    "def" -> flatDef = readStringOrNull(reader).orEmpty().trim()
                    "rank" -> flatRank = readIntOrDefault(reader, Int.MAX_VALUE)
                    "src" -> flatSrc = readStringOrNull(reader).orEmpty().trim().ifBlank { "pack" }
                    "matches" -> {
                        if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginObject()
                                var mEn = ""
                                var mPos = ""
                                var mDef = ""
                                var mRank = Int.MAX_VALUE
                                var mSrc = "pack"
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "en" -> mEn = readStringOrNull(reader).orEmpty().trim().lowercase(Locale.US)
                                        "pos" -> mPos = readStringOrNull(reader).orEmpty().trim()
                                        "def" -> mDef = readStringOrNull(reader).orEmpty().trim()
                                        "rank" -> mRank = readIntOrDefault(reader, Int.MAX_VALUE)
                                        "src" -> mSrc = readStringOrNull(reader).orEmpty().trim().ifBlank { "pack" }
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (mEn.isNotBlank()) {
                                    nestedMatches.add(
                                        ReverseArabicEntryEntity(
                                            normalizedArabic = "",
                                            vocalizedArabic = "",
                                            englishWord = mEn,
                                            partOfSpeech = mPos,
                                            englishDefinition = mDef,
                                            frequencyRank = mRank,
                                            source = mSrc
                                        )
                                    )
                                }
                            }
                            reader.endArray()
                        } else {
                            reader.skipValue()
                        }
                    }
                    else -> reader.skipValue()
                }
            }
        } finally {
            reader.endObject()
        }
        val effectiveVoc = ArabicTextUtils.canonicalizeArabicWithDiacritics(vocField.ifBlank { arNormField })
        val effectiveNorm = ArabicTextUtils.normalizeArabicForMatching(arNormField.ifBlank { vocField })
        if (effectiveNorm.isBlank() || effectiveVoc.isBlank()) return

        if (nestedMatches.isNotEmpty()) {
            nestedMatches.forEach { m ->
                out.add(m.copy(normalizedArabic = effectiveNorm, vocalizedArabic = effectiveVoc))
            }
        } else if (flatEn.isNotBlank()) {
            out.add(
                ReverseArabicEntryEntity(
                    normalizedArabic = effectiveNorm,
                    vocalizedArabic = effectiveVoc,
                    englishWord = flatEn,
                    partOfSpeech = flatPos,
                    englishDefinition = flatDef,
                    frequencyRank = flatRank,
                    source = flatSrc
                )
            )
        }
    }

    private fun readSensesArray(reader: JsonReader): JSONArray {
        val array = JSONArray()
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return array
        }
        reader.beginArray()
        while (reader.hasNext()) {
            reader.beginObject()
            val obj = JSONObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "partOfSpeech", "pos" -> obj.put("partOfSpeech", readStringOrNull(reader).orEmpty())
                    "definition", "def" -> obj.put("definition", readStringOrNull(reader).orEmpty())
                    "definitionArabic", "ar" -> {
                        val ar = readStringOrNull(reader).orEmpty().trim()
                        if (ar.isNotEmpty()) obj.put("definitionArabic", ar)
                    }
                    "examples" -> obj.put("examples", readStringArray(reader))
                    "synonyms" -> obj.put("synonyms", readStringArray(reader))
                    "antonyms" -> obj.put("antonyms", readStringArray(reader))
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            array.put(obj)
        }
        reader.endArray()
        return array
    }

    private fun readStringArray(reader: JsonReader): JSONArray {
        val array = JSONArray()
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return array
        }
        reader.beginArray()
        while (reader.hasNext()) {
            val v = readStringOrNull(reader)
            if (!v.isNullOrBlank()) array.put(v.trim())
        }
        reader.endArray()
        return array
    }

    private fun readFormsArray(reader: JsonReader): JSONArray {
        val array = JSONArray()
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return array
        }
        reader.beginArray()
        while (reader.hasNext()) {
            when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> {
                    reader.beginObject()
                    var formText = ""
                    var labelText = ""
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "form", "word" -> formText = readStringOrNull(reader).orEmpty().trim()
                            "label", "tag" -> labelText = readStringOrNull(reader).orEmpty().trim()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    if (formText.isNotEmpty()) {
                        array.put(
                            JSONObject().apply {
                                put("form", formText)
                                if (labelText.isNotEmpty()) put("label", labelText)
                            }
                        )
                    }
                }
                else -> {
                    val v = readStringOrNull(reader)?.trim().orEmpty()
                    if (v.isNotEmpty()) {
                        array.put(JSONObject().apply { put("form", v) })
                    }
                }
            }
        }
        reader.endArray()
        return array
    }

    private fun readStringOrNull(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.NUMBER, JsonToken.BOOLEAN -> {
                reader.nextString()
            }
            else -> reader.nextString()
        }
    }

    private fun readIntOrDefault(reader: JsonReader, fallback: Int): Int {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                fallback
            }
            JsonToken.NUMBER -> runCatching { reader.nextInt() }.getOrDefault(fallback)
            JsonToken.STRING -> reader.nextString().toIntOrNull() ?: fallback
            else -> {
                reader.skipValue()
                fallback
            }
        }
    }

    private class PackUnavailableException(message: String) : Exception(message)
}
