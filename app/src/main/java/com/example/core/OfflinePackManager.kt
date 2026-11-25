package com.example.core

import android.content.Context
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.LexiconDao
import com.example.data.local.OfflinePackWordEntity
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
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream

private const val TAG = "OfflinePackManager"
private const val PREFS_NAME = "offline_pack_prefs"
private const val KEY_PACK_DISK_BYTES = "pack_disk_bytes"

sealed interface OfflinePackState {
    data class Ready(
        val isInstalled: Boolean,
        val wordCount: Int,
        val diskSizeBytes: Long
    ) : OfflinePackState

    data class Downloading(
        val progress: Float,
        val indeterminate: Boolean,
        val statusText: String
    ) : OfflinePackState

    data class Error(
        val message: String,
        val isInstalled: Boolean,
        val wordCount: Int,
        val diskSizeBytes: Long
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

    suspend fun ensureInitialized() {
        if (initialized) return
        initialized = true
        refreshStatus()
    }

    suspend fun refreshStatus() {
        try {
            val count = withContext(Dispatchers.IO) { dao.getOfflinePackCount() }
            val diskBytes = if (count > 0) prefs.getLong(KEY_PACK_DISK_BYTES, 0L) else 0L
            _state.value = OfflinePackState.Ready(
                isInstalled = count > 0,
                wordCount = count,
                diskSizeBytes = diskBytes
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed refreshing offline pack status", e)
        }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        if (BuildConfig.GITHUB_REPO == "unknown/unknown" || !BuildConfig.GITHUB_REPO.contains("/")) {
            scope.launch {
                val count = withContext(Dispatchers.IO) { dao.getOfflinePackCount() }
                val diskBytes = if (count > 0) prefs.getLong(KEY_PACK_DISK_BYTES, 0L) else 0L
                _state.value = OfflinePackState.Error(
                    message = "الحزمة غير متاحة حالياً",
                    isInstalled = count > 0,
                    wordCount = count,
                    diskSizeBytes = diskBytes
                )
            }
            return
        }
        if (!networkMonitor.isOnline()) {
            scope.launch {
                val count = withContext(Dispatchers.IO) { dao.getOfflinePackCount() }
                val diskBytes = if (count > 0) prefs.getLong(KEY_PACK_DISK_BYTES, 0L) else 0L
                _state.value = OfflinePackState.Error(
                    message = "الحزمة غير متاحة حالياً",
                    isInstalled = count > 0,
                    wordCount = count,
                    diskSizeBytes = diskBytes
                )
            }
            return
        }

        downloadJob = scope.launch {
            val tempFile = File(appContext.cacheDir, "dictionary-pack.json.gz.tmp")
            try {
                _state.value = OfflinePackState.Downloading(
                    progress = 0f,
                    indeterminate = true,
                    statusText = "جاري تنزيل حزمة القاموس..."
                )

                val downloadedBytes = withContext(Dispatchers.IO) {
                    if (tempFile.exists()) tempFile.delete()
                    val request = Request.Builder()
                        .url(packDownloadUrl)
                        .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw PackUnavailableException("HTTP ${response.code}")
                        }
                        val body = response.body ?: throw PackUnavailableException("Empty body")
                        val total = body.contentLength()
                        FileOutputStream(tempFile).use { out ->
                            val buf = ByteArray(16 * 1024)
                            var copied = 0L
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
                                    val pct = (copied * 100 / total).toInt()
                                    val nowMs = SystemClock.elapsedRealtime()
                                    if (pct != lastPct && (pct == 100 || (nowMs - lastEmitMs) >= 350L)) {
                                        lastPct = pct
                                        lastEmitMs = nowMs
                                        _state.value = OfflinePackState.Downloading(
                                            progress = fraction,
                                            indeterminate = false,
                                            statusText = "جاري التنزيل ($pct%)..."
                                        )
                                    }
                                }
                            }
                            out.flush()
                        }
                    }

                    val gzSize = tempFile.length()
                    _state.value = OfflinePackState.Downloading(
                        progress = 0.72f,
                        indeterminate = true,
                        statusText = "جاري فك الضغط والفهرسة محلياً..."
                    )

                    dao.clearOfflinePack()
                    var totalJsonBytes = 0L
                    GZIPInputStream(BufferedInputStream(FileInputStream(tempFile), 32 * 1024)).use { gzStream ->
                        JsonReader(InputStreamReader(gzStream, Charsets.UTF_8)).use { reader ->
                            reader.isLenient = true
                            val batch = ArrayList<OfflinePackWordEntity>(300)
                            val token = reader.peek()
                            if (token == JsonToken.BEGIN_ARRAY) {
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    coroutineContext.ensureActive()
                                    val entity = readPackWordObject(reader)
                                    if (entity != null) {
                                        totalJsonBytes += entity.payloadJson.length
                                        batch.add(entity)
                                        if (batch.size >= 300) {
                                            dao.upsertOfflinePackBatch(batch.toList())
                                            batch.clear()
                                        }
                                    }
                                }
                                reader.endArray()
                            } else if (token == JsonToken.BEGIN_OBJECT) {
                                // Either {"words": [...]} or concatenated JSON objects (NDJSON)
                                reader.beginObject()
                                val firstName = if (reader.hasNext()) reader.nextName() else null
                                if (firstName == "words" && reader.peek() == JsonToken.BEGIN_ARRAY) {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        coroutineContext.ensureActive()
                                        val entity = readPackWordObject(reader)
                                        if (entity != null) {
                                            totalJsonBytes += entity.payloadJson.length
                                            batch.add(entity)
                                            if (batch.size >= 300) {
                                                dao.upsertOfflinePackBatch(batch.toList())
                                                batch.clear()
                                            }
                                        }
                                    }
                                    reader.endArray()
                                    while (reader.hasNext()) reader.skipValue()
                                    reader.endObject()
                                } else {
                                    val firstEntity = readRemainingFieldsAfterFirst(reader, firstName)
                                    reader.endObject()
                                    if (firstEntity != null) {
                                        totalJsonBytes += firstEntity.payloadJson.length
                                        batch.add(firstEntity)
                                    }
                                    while (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                        coroutineContext.ensureActive()
                                        val entity = readPackWordObject(reader)
                                        if (entity != null) {
                                            totalJsonBytes += entity.payloadJson.length
                                            batch.add(entity)
                                            if (batch.size >= 300) {
                                                dao.upsertOfflinePackBatch(batch.toList())
                                                batch.clear()
                                            }
                                        }
                                    }
                                }
                            }
                            if (batch.isNotEmpty()) {
                                dao.upsertOfflinePackBatch(batch.toList())
                                batch.clear()
                            }
                        }
                    }

                    maxOf(totalJsonBytes, gzSize)
                }

                prefs.edit().putLong(KEY_PACK_DISK_BYTES, downloadedBytes).apply()
                refreshStatus()
            } catch (e: CancellationException) {
                refreshStatus()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Offline dictionary pack download failed", e)
                val count = withContext(Dispatchers.IO) { dao.getOfflinePackCount() }
                val diskBytes = if (count > 0) prefs.getLong(KEY_PACK_DISK_BYTES, 0L) else 0L
                _state.value = OfflinePackState.Error(
                    message = "الحزمة غير متاحة حالياً",
                    isInstalled = count > 0,
                    wordCount = count,
                    diskSizeBytes = diskBytes
                )
            } finally {
                runCatching { if (tempFile.exists()) tempFile.delete() }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun deletePack() {
        downloadJob?.cancel()
        downloadJob = null
        scope.launch {
            withContext(Dispatchers.IO) {
                dao.clearOfflinePack()
                prefs.edit().remove(KEY_PACK_DISK_BYTES).apply()
            }
            refreshStatus()
        }
    }

    private fun readPackWordObject(reader: JsonReader): OfflinePackWordEntity? {
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
    ): OfflinePackWordEntity? {
        val root = JSONObject()
        var word = ""
        var lang = ""
        var phonetic = ""
        var summaryDef = ""
        val tagsList = mutableListOf<String>()

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
                "phonetic" -> {
                    phonetic = readStringOrNull(reader).orEmpty().trim()
                    if (phonetic.isNotEmpty()) root.put("phonetic", phonetic)
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
                    root.put("senses", arr)
                    if (summaryDef.isEmpty() && arr.length() > 0) {
                        summaryDef = arr.optJSONObject(0)?.optString("definition").orEmpty()
                    }
                }
                "collocations", "phrases", "family", "confusables" -> {
                    root.put(name, readStringArray(reader))
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
        val distinctTags = tagsList.distinct()
        val hasAwl = distinctTags.any { it.equals("AWL", ignoreCase = true) }
        return OfflinePackWordEntity(
            word = keyWord,
            normalizedWord = normalized,
            lang = resolvedLang,
            phonetic = phonetic,
            summaryDefinition = summaryDef.take(180),
            tagsCsv = distinctTags.joinToString(","),
            hasAwlTag = hasAwl,
            payloadJson = root.toString()
        )
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
                    "partOfSpeech" -> obj.put("partOfSpeech", readStringOrNull(reader).orEmpty())
                    "definition" -> obj.put("definition", readStringOrNull(reader).orEmpty())
                    "definitionArabic" -> {
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

    private fun readStringOrNull(reader: JsonReader): String? {
        return if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            null
        } else {
            reader.nextString()
        }
    }

    private class PackUnavailableException(message: String) : Exception(message)
}
