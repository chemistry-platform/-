package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

private const val TAG = "AudioPlayerManager"

enum class Accent(val code: String) {
    US("us"),
    UK("uk")
}

class AudioPlayerManager(
    context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val audioCacheDir = File(context.applicationContext.filesDir, "audio_cache").apply {
        if (!exists()) mkdirs()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    private val _playingKey = MutableStateFlow<String?>(null)
    val playingKey: StateFlow<String?> = _playingKey.asStateFlow()

    fun playHumanPronunciation(
        word: String,
        accent: Accent,
        audioUrl: String?,
        key: String
    ) {
        val normalizedUrl = normalizeAudioUrl(audioUrl)
        val cachedFile = cacheFileFor(word, accent)
        if (normalizedUrl == null && (!cachedFile.exists() || cachedFile.length() == 0L)) {
            return
        }

        playbackJob?.cancel()
        playbackJob = scope.launch {
            _playingKey.value = key
            try {
                val fileToPlay = withContext(Dispatchers.IO) {
                    if (cachedFile.exists() && cachedFile.length() > 0L) {
                        cachedFile
                    } else if (normalizedUrl != null) {
                        downloadToCache(normalizedUrl, cachedFile)
                    } else {
                        null
                    }
                }
                if (fileToPlay != null && fileToPlay.exists() && fileToPlay.length() > 0L) {
                    playLocalFile(fileToPlay)
                } else {
                    _playingKey.value = null
                }
            } catch (e: CancellationException) {
                _playingKey.value = null
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Audio playback failed for $word (${accent.code})", e)
                _playingKey.value = null
            }
        }
    }

    fun prefetchWordAudio(word: String, usAudioUrl: String?, ukAudioUrl: String?) {
        val usUrl = normalizeAudioUrl(usAudioUrl)
        val ukUrl = normalizeAudioUrl(ukAudioUrl)
        if (usUrl == null && ukUrl == null) return

        scope.launch(Dispatchers.IO) {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
            if (usUrl != null) {
                val usFile = cacheFileFor(word, Accent.US)
                if (!usFile.exists() || usFile.length() == 0L) {
                    runCatching { downloadToCache(usUrl, usFile) }
                }
            }
            if (ukUrl != null) {
                val ukFile = cacheFileFor(word, Accent.UK)
                if (!ukFile.exists() || ukFile.length() == 0L) {
                    runCatching { downloadToCache(ukUrl, ukFile) }
                }
            }
        }
    }

    fun hasAudioOrCache(word: String, accent: Accent, audioUrl: String?): Boolean {
        if (normalizeAudioUrl(audioUrl) != null) return true
        val file = cacheFileFor(word, accent)
        return file.exists() && file.length() > 0L
    }

    private fun playLocalFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { player ->
                    player.start()
                }
                setOnCompletionListener {
                    _playingKey.value = null
                }
                setOnErrorListener { _, _, _ ->
                    _playingKey.value = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer failed for cached file ${file.name}", e)
            _playingKey.value = null
        }
    }

    private fun downloadToCache(url: String, targetFile: File): File? {
        val tempFile = File(audioCacheDir, "${targetFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            FileOutputStream(tempFile).use { out ->
                body.byteStream().copyTo(out)
                out.flush()
            }
        }
        if (tempFile.exists() && tempFile.length() > 0L) {
            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            return targetFile
        }
        tempFile.delete()
        return null
    }

    private fun normalizeAudioUrl(url: String?): String? {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    }

    private fun cacheFileFor(word: String, accent: Accent): File {
        val normalized = word.trim().lowercase(Locale.US)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
        return File(audioCacheDir, "${digest}_${accent.code}.mp3")
    }

    fun release() {
        playbackJob?.cancel()
        playbackJob = null
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }
}
