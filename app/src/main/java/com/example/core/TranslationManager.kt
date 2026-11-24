package com.example.core

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "TranslationManager"
private const val SHORT_CACHE_MAX_LEN = 220
private const val MAX_CHUNK_CHARS = 500
private const val CONCURRENT_TRANSLATION_CHUNKS = 4
private const val PROGRESS_THROTTLE_MS = 350L

data class LanguagePackInfo(
    val code: String,
    val arabicName: String,
    val nativeName: String,
    val isDownloaded: Boolean,
    val isDownloading: Boolean = false
)

data class ChunkSlice(
    val index: Int,
    val byteOffset: Long,
    val byteLength: Int
)

class TranslationManager(
    context: Context,
    private val networkMonitor: NetworkMonitor
) {
    private val appContext = context.applicationContext
    private val modelManager: RemoteModelManager by lazy { RemoteModelManager.getInstance() }
    private val translators = ConcurrentHashMap<Pair<String, String>, Translator>()
    private val shortCache = object : LruCache<String, String>(500) {}
    private val downloadMutex = Mutex()

    private val _languages = MutableStateFlow<List<LanguagePackInfo>>(buildInitialLanguages())
    val languages: StateFlow<List<LanguagePackInfo>> = _languages.asStateFlow()

    private val _downloadingCodes = MutableStateFlow<Set<String>>(emptySet())

    private val streamOutputFile = File(appContext.cacheDir, "streamed_translation_output.txt")

    private fun buildInitialLanguages(): List<LanguagePackInfo> {
        val arabicLocale = Locale("ar")
        return TranslateLanguage.getAllLanguages()
            .map { code ->
                val locale = Locale.forLanguageTag(code)
                val arName = locale.getDisplayName(arabicLocale).takeIf { it.isNotBlank() } ?: code
                val nativeName = locale.getDisplayName(locale).takeIf { it.isNotBlank() } ?: code
                LanguagePackInfo(
                    code = code,
                    arabicName = arName,
                    nativeName = nativeName,
                    isDownloaded = false,
                    isDownloading = false
                )
            }
            .sortedWith(
                compareByDescending<LanguagePackInfo> { it.code == TranslateLanguage.ARABIC }
                    .thenByDescending { it.code == TranslateLanguage.ENGLISH }
                    .thenBy { it.arabicName }
            )
    }

    suspend fun refreshDownloadedLanguages() {
        try {
            val downloaded = withContext(Dispatchers.IO) {
                modelManager.getDownloadedModels(TranslateRemoteModel::class.java).awaitTask()
                    .map { it.language }
                    .toSet()
            }
            val activeDownloads = _downloadingCodes.value
            _languages.value = _languages.value.map { item ->
                item.copy(
                    isDownloaded = item.code in downloaded,
                    isDownloading = item.code in activeDownloads
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed refreshing downloaded ML Kit models", e)
        }
    }

    suspend fun isPairDownloaded(sourceCode: String, targetCode: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val srcModel = TranslateRemoteModel.Builder(sourceCode).build()
                val tgtModel = TranslateRemoteModel.Builder(targetCode).build()
                val srcOk = modelManager.isModelDownloaded(srcModel).awaitTask()
                val tgtOk = modelManager.isModelDownloaded(tgtModel).awaitTask()
                srcOk && tgtOk
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun isOnCellularOnly(): Boolean = networkMonitor.isOnCellularWithoutWifi()

    fun isOnline(): Boolean = networkMonitor.isOnline()

    suspend fun downloadLanguage(code: String): Result<Unit> {
        _downloadingCodes.value = _downloadingCodes.value + code
        updateDownloadingFlag(code, true)
        return try {
            downloadMutex.withLock {
                val model = TranslateRemoteModel.Builder(code).build()
                val conditions = DownloadConditions.Builder().build()
                withTimeout(120_000L) {
                    modelManager.download(model, conditions).awaitTask()
                }
            }
            _downloadingCodes.value = _downloadingCodes.value - code
            refreshDownloadedLanguages()
            Result.success(Unit)
        } catch (e: CancellationException) {
            _downloadingCodes.value = _downloadingCodes.value - code
            updateDownloadingFlag(code, false)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading language pack: $code", e)
            _downloadingCodes.value = _downloadingCodes.value - code
            updateDownloadingFlag(code, false)
            Result.failure(e)
        }
    }

    suspend fun downloadPair(sourceCode: String, targetCode: String): Result<Unit> {
        val r1 = downloadLanguage(sourceCode)
        if (r1.isFailure) return r1
        return if (targetCode != sourceCode) {
            downloadLanguage(targetCode)
        } else {
            Result.success(Unit)
        }
    }

    suspend fun deleteLanguage(code: String): Result<Unit> {
        return try {
            downloadMutex.withLock {
                val model = TranslateRemoteModel.Builder(code).build()
                modelManager.deleteDownloadedModel(model).awaitTask()
            }
            refreshDownloadedLanguages()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting language pack: $code", e)
            Result.failure(e)
        }
    }

    private fun updateDownloadingFlag(code: String, downloading: Boolean) {
        _languages.value = _languages.value.map {
            if (it.code == code) it.copy(isDownloading = downloading) else it
        }
    }

    private fun getOrCreateTranslator(sourceCode: String, targetCode: String): Translator {
        val key = sourceCode to targetCode
        return translators.getOrPut(key) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceCode)
                .setTargetLanguage(targetCode)
                .build()
            Translation.getClient(options)
        }
    }

    /**
     * Translates short text only when both language packs are already downloaded.
     * Uses thread-safe LruCache + shared Translator instance.
     */
    suspend fun translateShortIfReady(
        text: String,
        sourceCode: String = TranslateLanguage.ENGLISH,
        targetCode: String = TranslateLanguage.ARABIC
    ): Result<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.success("")
        if (sourceCode == targetCode) return Result.success(trimmed)

        val cacheKey = "$sourceCode|$targetCode|$trimmed"
        if (trimmed.length <= SHORT_CACHE_MAX_LEN) {
            synchronized(shortCache) {
                shortCache.get(cacheKey)?.let { return Result.success(it) }
            }
        }

        if (!isPairDownloaded(sourceCode, targetCode)) {
            return Result.failure(MissingLanguagePackException(sourceCode, targetCode))
        }

        return try {
            val translator = getOrCreateTranslator(sourceCode, targetCode)
            val translated = withTimeout(15_000L) {
                translator.translate(trimmed).awaitTask().trim()
            }
            if (trimmed.length <= SHORT_CACHE_MAX_LEN && translated.isNotEmpty()) {
                synchronized(shortCache) {
                    shortCache.put(cacheKey, translated)
                }
            }
            Result.success(translated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Translation failed ($sourceCode->$targetCode)", e)
            Result.failure(e)
        }
    }

    /**
     * Section 19.10: Translates multiple senses/examples in one parallel batch (up to 4 concurrent),
     * reusing shared Translator instances without blocking the UI thread.
     */
    suspend fun translateBatchIfReady(
        texts: List<String>,
        sourceCode: String = TranslateLanguage.ENGLISH,
        targetCode: String = TranslateLanguage.ARABIC,
        concurrency: Int = CONCURRENT_TRANSLATION_CHUNKS
    ): Map<String, String> = withContext(Dispatchers.Default) {
        val unique = texts.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (unique.isEmpty()) return@withContext emptyMap()
        if (sourceCode == targetCode) return@withContext unique.associateWith { it }
        if (!isPairDownloaded(sourceCode, targetCode)) return@withContext emptyMap()

        val translator = getOrCreateTranslator(sourceCode, targetCode)
        val semaphore = Semaphore(concurrency.coerceIn(1, 4))
        val results = coroutineScope {
            unique.map { item ->
                async {
                    val cacheKey = "$sourceCode|$targetCode|$item"
                    if (item.length <= SHORT_CACHE_MAX_LEN) {
                        synchronized(shortCache) {
                            shortCache.get(cacheKey)
                        }?.let { return@async item to it }
                    }
                    semaphore.withPermit {
                        ensureActive()
                        val translated = runCatching {
                            withTimeout(15_000L) {
                                translator.translate(item).awaitTask().trim()
                            }
                        }.getOrNull()
                        if (!translated.isNullOrBlank()) {
                            if (item.length <= SHORT_CACHE_MAX_LEN) {
                                synchronized(shortCache) { shortCache.put(cacheKey, translated) }
                            }
                            item to translated
                        } else {
                            null
                        }
                    }
                }
            }.awaitAll()
        }
        results.filterNotNull().toMap()
    }

    /**
     * Splits text into chunks <= 500 chars by paragraph, then sentence, without cutting words.
     */
    fun splitIntoChunks(text: String, maxChars: Int = MAX_CHUNK_CHARS): List<String> {
        if (text.isEmpty()) return emptyList()
        val result = ArrayList<String>()
        val paragraphs = text.split('\n')
        for (pIndex in paragraphs.indices) {
            val paragraph = paragraphs[pIndex]
            val suffix = if (pIndex < paragraphs.lastIndex) "\n" else ""
            if (paragraph.isBlank()) {
                if (suffix.isNotEmpty()) {
                    if (result.isNotEmpty() && result.last().length + 1 <= maxChars) {
                        result[result.lastIndex] = result.last() + "\n"
                    } else {
                        result.add("\n")
                    }
                }
                continue
            }
            if (paragraph.length + suffix.length <= maxChars) {
                result.add(paragraph + suffix)
                continue
            }
            val sentences = splitIntoSentences(paragraph)
            val current = StringBuilder()
            for (s in sentences) {
                if (s.length > maxChars) {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.setLength(0)
                    }
                    splitLongSentenceByWord(s, maxChars, result)
                } else {
                    if (current.isNotEmpty() && current.length + s.length + 1 > maxChars) {
                        result.add(current.toString())
                        current.setLength(0)
                    }
                    if (current.isNotEmpty()) current.append(' ')
                    current.append(s)
                }
            }
            if (current.isNotEmpty()) {
                result.add(current.toString() + suffix)
            } else if (suffix.isNotEmpty() && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + suffix
            }
        }
        return result
    }

    private fun splitIntoSentences(paragraph: String): List<String> {
        val out = ArrayList<String>()
        var start = 0
        var i = 0
        while (i < paragraph.length) {
            val ch = paragraph[i]
            if (ch == '.' || ch == '!' || ch == '?' || ch == '؟' || ch == '؛') {
                val end = i + 1
                val slice = paragraph.substring(start, end).trim()
                if (slice.isNotEmpty()) out.add(slice)
                start = end
            }
            i++
        }
        if (start < paragraph.length) {
            val tail = paragraph.substring(start).trim()
            if (tail.isNotEmpty()) out.add(tail)
        }
        return out
    }

    private fun splitLongSentenceByWord(sentence: String, maxChars: Int, out: MutableList<String>) {
        var idx = 0
        val len = sentence.length
        while (idx < len) {
            while (idx < len && sentence[idx].isWhitespace()) idx++
            if (idx >= len) break
            var end = (idx + maxChars).coerceAtMost(len)
            if (end < len) {
                var back = end
                while (back > idx && !sentence[back].isWhitespace()) {
                    back--
                }
                if (back > idx) {
                    end = back
                }
            }
            out.add(sentence.substring(idx, end).trim())
            idx = end
        }
    }

    /**
     * Streams chunks from an arbitrary Reader (or text), translating 4 chunks concurrently,
     * preserving exact order while writing incrementally to disk (`streamOutputFile`).
     * Never accumulates the full translated output in memory.
     */
    suspend fun streamTranslateText(
        text: String,
        sourceCode: String,
        targetCode: String,
        onProgress: (completedChunks: Int, totalChunks: Int) -> Unit,
        onNewSliceWritten: (ChunkSlice) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            withContext(Dispatchers.IO) { streamOutputFile.writeText("") }
            return@withContext Result.success(streamOutputFile)
        }
        if (!isPairDownloaded(sourceCode, targetCode)) {
            return@withContext Result.failure(MissingLanguagePackException(sourceCode, targetCode))
        }

        val chunks = splitIntoChunks(text, MAX_CHUNK_CHARS)
        val total = chunks.size
        if (total == 0) {
            withContext(Dispatchers.IO) { streamOutputFile.writeText("") }
            return@withContext Result.success(streamOutputFile)
        }

        streamTranslateChunkSequence(
            totalChunksHint = total,
            chunkBatches = listOf(chunks),
            sourceCode = sourceCode,
            targetCode = targetCode,
            onProgress = onProgress,
            onNewSliceWritten = onNewSliceWritten
        )
    }

    suspend fun streamTranslateTxtUri(
        uri: Uri,
        sourceCode: String,
        targetCode: String,
        onProgress: (completedChunks: Int, totalChunks: Int) -> Unit,
        onNewSliceWritten: (ChunkSlice) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!isPairDownloaded(sourceCode, targetCode)) {
            return@withContext Result.failure(MissingLanguagePackException(sourceCode, targetCode))
        }

        try {
            // Pass 1: count chunks cleanly in bounded buffer (~32KB) without storing file in RAM
            var totalChunks = 0
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val batch = readChunkBatchFromReader(reader, batchSize = 32)
                        if (batch.isEmpty()) break
                        totalChunks += batch.size
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("تعذر فتح الملف"))

            if (totalChunks == 0) {
                streamOutputFile.writeText("")
                return@withContext Result.success(streamOutputFile)
            }

            if (streamOutputFile.exists()) streamOutputFile.delete()
            streamOutputFile.createNewFile()

            val translator = getOrCreateTranslator(sourceCode, targetCode)
            val semaphore = Semaphore(CONCURRENT_TRANSLATION_CHUNKS)
            var globalCompleted = 0
            var chunkGlobalIndex = 0
            var byteOffset = 0L
            var lastProgressEmitMs = 0L

            FileOutputStream(streamOutputFile, false).use { fos ->
                BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8)).use { writer ->
                    appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val batch = readChunkBatchFromReader(reader, batchSize = 16)
                                if (batch.isEmpty()) break

                                val translatedBatch = coroutineScope {
                                    batch.map { chunk ->
                                        async(Dispatchers.Default) {
                                            semaphore.withPermit {
                                                ensureActive()
                                                translateSingleChunkRaw(translator, sourceCode, targetCode, chunk)
                                            }
                                        }
                                    }.awaitAll()
                                }

                                for (translated in translatedBatch) {
                                    coroutineContext.ensureActive()
                                    val bytes = translated.toByteArray(Charsets.UTF_8)
                                    writer.write(translated)
                                    writer.flush()
                                    val slice = ChunkSlice(
                                        index = chunkGlobalIndex,
                                        byteOffset = byteOffset,
                                        byteLength = bytes.size
                                    )
                                    byteOffset += bytes.size
                                    chunkGlobalIndex++
                                    globalCompleted++
                                    onNewSliceWritten(slice)
                                    val nowMs = SystemClock.elapsedRealtime()
                                    if (globalCompleted >= totalChunks || (nowMs - lastProgressEmitMs) >= PROGRESS_THROTTLE_MS) {
                                        lastProgressEmitMs = nowMs
                                        onProgress(globalCompleted, totalChunks)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Result.success(streamOutputFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stream file translation failed", e)
            Result.failure(e)
        }
    }

    private suspend fun streamTranslateChunkSequence(
        totalChunksHint: Int,
        chunkBatches: List<List<String>>,
        sourceCode: String,
        targetCode: String,
        onProgress: (completedChunks: Int, totalChunks: Int) -> Unit,
        onNewSliceWritten: (ChunkSlice) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (streamOutputFile.exists()) streamOutputFile.delete()
            streamOutputFile.createNewFile()

            val translator = getOrCreateTranslator(sourceCode, targetCode)
            val semaphore = Semaphore(CONCURRENT_TRANSLATION_CHUNKS)
            var completed = 0
            var globalIndex = 0
            var byteOffset = 0L
            var lastProgressEmitMs = 0L

            FileOutputStream(streamOutputFile, false).use { fos ->
                BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8)).use { writer ->
                    for (batch in chunkBatches) {
                        for (subBatch in batch.chunked(16)) {
                            coroutineContext.ensureActive()
                            val translatedBatch = coroutineScope {
                                subBatch.map { chunk ->
                                    async(Dispatchers.Default) {
                                        semaphore.withPermit {
                                            ensureActive()
                                            translateSingleChunkRaw(translator, sourceCode, targetCode, chunk)
                                        }
                                    }
                                }.awaitAll()
                            }
                            for (translated in translatedBatch) {
                                coroutineContext.ensureActive()
                                val bytes = translated.toByteArray(Charsets.UTF_8)
                                writer.write(translated)
                                writer.flush()
                                val slice = ChunkSlice(
                                    index = globalIndex,
                                    byteOffset = byteOffset,
                                    byteLength = bytes.size
                                )
                                byteOffset += bytes.size
                                globalIndex++
                                completed++
                                onNewSliceWritten(slice)
                                val nowMs = SystemClock.elapsedRealtime()
                                if (completed >= totalChunksHint || (nowMs - lastProgressEmitMs) >= PROGRESS_THROTTLE_MS) {
                                    lastProgressEmitMs = nowMs
                                    onProgress(completed, totalChunksHint)
                                }
                            }
                        }
                    }
                }
            }
            Result.success(streamOutputFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Chunk sequence translation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Section 16.5: Streaming chunk pipeline for a single document page (about 500 chars per chunk,
     * up to 4 concurrent chunks, ordered output, shared translator cache).
     */
    suspend fun translatePageTextStreaming(
        pageText: String,
        sourceCode: String,
        targetCode: String
    ): Result<String> = withContext(Dispatchers.Default) {
        if (pageText.isBlank()) return@withContext Result.success("")
        if (sourceCode == targetCode) return@withContext Result.success(pageText)
        if (!isPairDownloaded(sourceCode, targetCode)) {
            return@withContext Result.failure(MissingLanguagePackException(sourceCode, targetCode))
        }
        return@withContext try {
            val chunks = splitIntoChunks(pageText, MAX_CHUNK_CHARS)
            val translator = getOrCreateTranslator(sourceCode, targetCode)
            val semaphore = Semaphore(CONCURRENT_TRANSLATION_CHUNKS)
            val out = StringBuilder(pageText.length)
            for (subBatch in chunks.chunked(16)) {
                coroutineContext.ensureActive()
                val translatedBatch = coroutineScope {
                    subBatch.map { chunk ->
                        async(Dispatchers.Default) {
                            semaphore.withPermit {
                                ensureActive()
                                translateSingleChunkRaw(translator, sourceCode, targetCode, chunk)
                            }
                        }
                    }.awaitAll()
                }
                for (part in translatedBatch) {
                    out.append(part)
                }
            }
            Result.success(out.toString().trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Page streaming translation failed", e)
            Result.failure(e)
        }
    }

    private suspend fun translateSingleChunkRaw(
        translator: Translator,
        sourceCode: String,
        targetCode: String,
        chunk: String
    ): String {
        if (chunk.isBlank()) return chunk
        if (sourceCode == targetCode) return chunk
        val leadingNewlines = chunk.takeWhile { it == '\n' }
        val trailingNewlines = chunk.takeLastWhile { it == '\n' }
        val core = chunk.trim('\n', '\r')
        if (core.isBlank()) return chunk

        val translatedCore = if (core.length <= SHORT_CACHE_MAX_LEN) {
            val key = "$sourceCode|$targetCode|$core"
            val cached = synchronized(shortCache) { shortCache.get(key) }
            if (cached != null) {
                cached
            } else {
                val res = withTimeout(20_000L) {
                    translator.translate(core).awaitTask().trim()
                }
                if (res.isNotEmpty()) {
                    synchronized(shortCache) { shortCache.put(key, res) }
                }
                res
            }
        } else {
            withTimeout(20_000L) {
                translator.translate(core).awaitTask().trim()
            }
        }
        val sep = if (trailingNewlines.isEmpty() && !chunk.endsWith(' ')) " " else ""
        return leadingNewlines + translatedCore + trailingNewlines + sep
    }

    private fun readChunkBatchFromReader(reader: BufferedReader, batchSize: Int): List<String> {
        val chunks = ArrayList<String>(batchSize)
        while (chunks.size < batchSize) {
            val line = reader.readLine() ?: break
            val subChunks = splitIntoChunks(line + "\n", MAX_CHUNK_CHARS)
            if (subChunks.isEmpty()) {
                chunks.add("\n")
            } else {
                chunks.addAll(subChunks)
            }
        }
        return chunks
    }

    suspend fun readSliceText(slice: ChunkSlice): String = withContext(Dispatchers.IO) {
        if (!streamOutputFile.exists() || slice.byteLength <= 0) return@withContext ""
        RandomAccessFile(streamOutputFile, "r").use { raf ->
            raf.seek(slice.byteOffset)
            val bytes = ByteArray(slice.byteLength)
            val read = raf.read(bytes)
            if (read <= 0) "" else String(bytes, 0, read, Charsets.UTF_8)
        }
    }

    suspend fun exportStreamedTranslationToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!streamOutputFile.exists()) {
                return@withContext Result.failure(IllegalStateException("لا توجد ترجمة للتصدير"))
            }
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(streamOutputFile).use { input ->
                    input.copyTo(out, bufferSize = 8 * 1024)
                    out.flush()
                }
            } ?: return@withContext Result.failure(IllegalStateException("تعذر فتح مسار الحفظ"))
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Export .txt failed", e)
            Result.failure(e)
        }
    }

    fun close() {
        translators.values.forEach { translator ->
            runCatching { translator.close() }
        }
        translators.clear()
    }
}

class MissingLanguagePackException(
    val sourceCode: String,
    val targetCode: String
) : Exception("Missing offline language pack: $sourceCode -> $targetCode")

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }
