package com.example.core

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.BuildConfig
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
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlin.coroutines.coroutineContext

private const val VOICE_PACK_TAG = "OfflineVoicePackManager"
private const val VOICE_PACK_PREFS = "offline_voice_pack_prefs"
private const val KEY_VOICE_CLIP_COUNT = "voice_clip_count"
private const val KEY_VOICE_DISK_BYTES = "voice_disk_bytes"
private const val KEY_VOICE_TOTAL_BYTES = "voice_total_bytes"

sealed interface OfflineVoicePackState {
    data class Ready(
        val isInstalled: Boolean,
        val clipCount: Int,
        val diskSizeBytes: Long
    ) : OfflineVoicePackState

    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val statusText: String
    ) : OfflineVoicePackState

    data class Paused(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progress: Float
    ) : OfflineVoicePackState

    data class Error(
        val message: String,
        val isInstalled: Boolean,
        val clipCount: Int,
        val diskSizeBytes: Long
    ) : OfflineVoicePackState
}

/**
 * Optional Offline Voice Pack (~30 MB, top 5,000 English words US & UK Kokoro .opus clips).
 * Downloaded on user request with HTTP Range Pause/Resume and stored in filesDir/voice_pack/.
 */
class OfflineVoicePackManager(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val networkMonitor: NetworkMonitor
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(VOICE_PACK_PREFS, Context.MODE_PRIVATE)
    private val voicePackDir = File(appContext.filesDir, "voice_pack")
    private val partFile = File(appContext.cacheDir, "offline-voice-pack-5k.tar.gz.part")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var downloadJob: Job? = null

    private val _state = MutableStateFlow<OfflineVoicePackState>(
        OfflineVoicePackState.Ready(isInstalled = false, clipCount = 0, diskSizeBytes = 0L)
    )
    val state: StateFlow<OfflineVoicePackState> = _state.asStateFlow()

    val downloadUrl: String
        get() = "https://github.com/${BuildConfig.GITHUB_REPO}/releases/download/voice-pack-5k/offline-voice-pack-5k.tar.gz"

    init {
        scope.launch { refreshStatus() }
    }

    suspend fun refreshStatus() {
        val status = withContext(Dispatchers.IO) {
            if (partFile.exists() && partFile.length() > 0L) {
                val total = prefs.getLong(KEY_VOICE_TOTAL_BYTES, 31_000_000L).coerceAtLeast(1L)
                val downloaded = partFile.length()
                val frac = (downloaded.toFloat() / total.toFloat()).coerceIn(0.01f, 0.99f)
                return@withContext OfflineVoicePackState.Paused(downloaded, total, frac)
            }
            val count = prefs.getInt(KEY_VOICE_CLIP_COUNT, 0)
            val diskBytes = prefs.getLong(KEY_VOICE_DISK_BYTES, 0L)
            val hasFiles = count > 0 && voicePackDir.exists() && (voicePackDir.listFiles()?.isNotEmpty() == true)
            OfflineVoicePackState.Ready(
                isInstalled = hasFiles,
                clipCount = if (hasFiles) count else 0,
                diskSizeBytes = if (hasFiles) diskBytes else 0L
            )
        }
        _state.value = status
    }

    fun findClip(word: String, accent: Accent): File? {
        if (accent == Accent.AR) return null
        val cleaned = word.trim().lowercase(Locale.US)
        if (cleaned.isEmpty() || cleaned.contains(' ')) return null
        val f = File(File(voicePackDir, accent.code), "$cleaned.opus")
        return if (f.exists() && f.length() > 0L) f else null
    }

    fun startOrResumeDownload() {
        if (downloadJob?.isActive == true) return
        if (BuildConfig.GITHUB_REPO == "unknown/unknown" || !BuildConfig.GITHUB_REPO.contains("/")) {
            _state.value = OfflineVoicePackState.Error(
                message = "Voice pack is not published for this local build yet",
                isInstalled = false,
                clipCount = 0,
                diskSizeBytes = 0L
            )
            return
        }
        if (!networkMonitor.isOnline()) {
            _state.value = OfflineVoicePackState.Error(
                message = "No internet connection",
                isInstalled = prefs.getInt(KEY_VOICE_CLIP_COUNT, 0) > 0,
                clipCount = prefs.getInt(KEY_VOICE_CLIP_COUNT, 0),
                diskSizeBytes = prefs.getLong(KEY_VOICE_DISK_BYTES, 0L)
            )
            return
        }

        downloadJob = scope.launch {
            try {
                val existingBytes = withContext(Dispatchers.IO) {
                    if (partFile.exists()) partFile.length() else 0L
                }
                _state.value = OfflineVoicePackState.Downloading(
                    progress = 0f,
                    downloadedBytes = existingBytes,
                    totalBytes = prefs.getLong(KEY_VOICE_TOTAL_BYTES, 31_000_000L),
                    statusText = "Downloading Offline Voice Pack..."
                )

                val result = withContext(Dispatchers.IO) {
                    val reqBuilder = Request.Builder()
                        .url(downloadUrl)
                        .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
                    var resumeOffset = if (partFile.exists()) partFile.length() else 0L
                    if (resumeOffset > 0L) {
                        reqBuilder.header("Range", "bytes=$resumeOffset-")
                    }

                    okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                        if (response.code == 416) {
                            // Already complete on disk
                        } else if (!response.isSuccessful && response.code != 206) {
                            throw IllegalStateException("HTTP ${response.code}")
                        } else {
                            val isPartial = response.code == 206
                            if (!isPartial) {
                                resumeOffset = 0L
                            }
                            val body = response.body ?: throw IllegalStateException("Empty body")
                            val contentLen = body.contentLength()
                            val totalBytes = if (contentLen > 0L) resumeOffset + contentLen else 31_000_000L
                            prefs.edit().putLong(KEY_VOICE_TOTAL_BYTES, totalBytes).apply()

                            FileOutputStream(partFile, isPartial).use { out ->
                                val buf = ByteArray(16 * 1024)
                                var downloaded = resumeOffset
                                var lastEmitMs = 0L
                                val input = body.byteStream()
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val read = input.read(buf)
                                    if (read == -1) break
                                    out.write(buf, 0, read)
                                    downloaded += read
                                    val nowMs = SystemClock.elapsedRealtime()
                                    if ((nowMs - lastEmitMs) >= 350L) {
                                        lastEmitMs = nowMs
                                        val frac = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 0.85f)
                                        val pct = ((downloaded * 100L) / totalBytes.coerceAtLeast(1L)).toInt().coerceIn(0, 100)
                                        _state.value = OfflineVoicePackState.Downloading(
                                            progress = frac,
                                            downloadedBytes = downloaded,
                                            totalBytes = totalBytes,
                                            statusText = "Downloading Offline Voice Pack ($pct%)..."
                                        )
                                    }
                                }
                                out.flush()
                            }
                        }
                    }

                    _state.value = OfflineVoicePackState.Downloading(
                        progress = 0.88f,
                        downloadedBytes = partFile.length(),
                        totalBytes = partFile.length().coerceAtLeast(1L),
                        statusText = "Unpacking audio clips..."
                    )

                    if (voicePackDir.exists()) voicePackDir.deleteRecursively()
                    voicePackDir.mkdirs()

                    var extractedCount = 0
                    var extractedBytes = 0L
                    GZIPInputStream(BufferedInputStream(FileInputStream(partFile), 32 * 1024)).use { gz ->
                        extractedCount = extractTarArchive(gz, voicePackDir) { bytesWritten ->
                            extractedBytes += bytesWritten
                        }
                    }
                    partFile.delete()
                    Pair(extractedCount, extractedBytes)
                }

                prefs.edit()
                    .putInt(KEY_VOICE_CLIP_COUNT, result.first)
                    .putLong(KEY_VOICE_DISK_BYTES, result.second)
                    .remove(KEY_VOICE_TOTAL_BYTES)
                    .apply()
                refreshStatus()
            } catch (e: CancellationException) {
                refreshStatus()
                throw e
            } catch (e: Exception) {
                Log.w(VOICE_PACK_TAG, "Offline voice pack download failed", e)
                val count = prefs.getInt(KEY_VOICE_CLIP_COUNT, 0)
                val bytes = prefs.getLong(KEY_VOICE_DISK_BYTES, 0L)
                _state.value = OfflineVoicePackState.Error(
                    message = "Voice pack unavailable right now",
                    isInstalled = count > 0,
                    clipCount = count,
                    diskSizeBytes = bytes
                )
            }
        }
    }

    fun pauseDownload() {
        downloadJob?.cancel()
        downloadJob = null
        scope.launch { refreshStatus() }
    }

    fun deletePack() {
        downloadJob?.cancel()
        downloadJob = null
        scope.launch {
            withContext(Dispatchers.IO) {
                if (partFile.exists()) partFile.delete()
                if (voicePackDir.exists()) voicePackDir.deleteRecursively()
                prefs.edit()
                    .remove(KEY_VOICE_CLIP_COUNT)
                    .remove(KEY_VOICE_DISK_BYTES)
                    .remove(KEY_VOICE_TOTAL_BYTES)
                    .apply()
            }
            refreshStatus()
        }
    }

    private fun extractTarArchive(
        tarStream: InputStream,
        destRoot: File,
        onBytesWritten: (Long) -> Unit
    ): Int {
        val header = ByteArray(512)
        val buffer = ByteArray(16 * 1024)
        var count = 0
        val rootCanonical = destRoot.canonicalPath

        while (true) {
            val readHeader = readFully(tarStream, header)
            if (readHeader < 512 || header.all { it == 0.toByte() }) break

            val name = extractTarString(header, 0, 100)
            val prefix = extractTarString(header, 345, 155)
            val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
            val typeFlag = header[156].toInt().toChar()
            val sizeOctal = extractTarString(header, 124, 12)
            val fileSize = sizeOctal.toLongOrNull(8) ?: 0L

            if (typeFlag == '0' || typeFlag == '\u0000') {
                val safeRel = fullName.trimStart('/').replace('\\', '/')
                val outFile = File(destRoot, safeRel)
                val canonicalOut = outFile.canonicalPath
                if (canonicalOut.startsWith(rootCanonical + File.separator) && safeRel.endsWith(".opus")) {
                    outFile.parentFile?.mkdirs()
                    var remaining = fileSize
                    FileOutputStream(outFile).use { fos ->
                        while (remaining > 0L) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val n = tarStream.read(buffer, 0, toRead)
                            if (n <= 0) break
                            fos.write(buffer, 0, n)
                            remaining -= n
                        }
                    }
                    onBytesWritten(fileSize)
                    count++
                } else {
                    skipFully(tarStream, fileSize)
                }
            } else {
                skipFully(tarStream, fileSize)
            }

            val padding = ((512 - (fileSize % 512)) % 512)
            if (padding > 0L) {
                skipFully(tarStream, padding)
            }
        }
        return count
    }

    private fun readFully(stream: InputStream, dst: ByteArray): Int {
        var offset = 0
        while (offset < dst.size) {
            val n = stream.read(dst, offset, dst.size - offset)
            if (n <= 0) break
            offset += n
        }
        return offset
    }

    private fun skipFully(stream: InputStream, bytes: Long) {
        var remaining = bytes
        val skipBuf = ByteArray(4096)
        while (remaining > 0L) {
            val n = stream.read(skipBuf, 0, minOf(remaining, skipBuf.size.toLong()).toInt())
            if (n <= 0) break
            remaining -= n
        }
    }

    private fun extractTarString(buf: ByteArray, offset: Int, len: Int): String {
        val end = (offset until offset + len).firstOrNull { buf[it] == 0.toByte() } ?: (offset + len)
        return String(buf, offset, end - offset, Charsets.UTF_8).trim()
    }
}
