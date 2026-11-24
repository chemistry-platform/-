package com.example.core

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

private const val TAG = "AudioPlayerManager"
private const val NO_AUDIO_MESSAGE = "لا يوجد صوت متاح"

enum class Accent(val code: String) {
    US("us"),
    UK("uk")
}

class AudioPlayerManager(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val settingsPreferences: UserSettingsPreferences
) {
    private val appContext = context.applicationContext
    private val audioCacheDir = File(appContext.filesDir, "audio_cache").apply {
        if (!exists()) mkdirs()
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsInitDeferred: CompletableDeferred<Boolean>? = null

    private val _playingKey = MutableStateFlow<String?>(null)
    val playingKey: StateFlow<String?> = _playingKey.asStateFlow()

    data class TtsProgress(
        val currentChunk: Int,
        val totalChunks: Int
    )

    private val _ttsProgress = MutableStateFlow<TtsProgress?>(null)
    val ttsProgress: StateFlow<TtsProgress?> = _ttsProgress.asStateFlow()

    private val _ttsMissingEnglishVoice = MutableStateFlow(false)
    val ttsMissingEnglishVoice: StateFlow<Boolean> = _ttsMissingEnglishVoice.asStateFlow()

    private val _audioMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val audioMessage: SharedFlow<String> = _audioMessage.asSharedFlow()

    val audioSpeed: StateFlow<Float> = settingsPreferences.audioSpeed
    val sentenceAccent: StateFlow<Accent> = settingsPreferences.sentenceAccent

    private val ttsQueueLock = Any()
    @Volatile private var activeBatchId: Long = 0L
    @Volatile private var pendingChunks: List<String> = emptyList()
    @Volatile private var nextChunkToEnqueue: Int = 0
    @Volatile private var totalChunksInBatch: Int = 0

    fun setAudioSpeed(speed: Float) {
        settingsPreferences.setAudioSpeed(speed)
        val sanitized = settingsPreferences.audioSpeed.value
        applySpeedToActiveMediaPlayer(sanitized)
        runCatching { tts?.setSpeechRate(sanitized) }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        synchronized(ttsQueueLock) {
            activeBatchId = 0L
            pendingChunks = emptyList()
            nextChunkToEnqueue = 0
            totalChunksInBatch = 0
        }
        stopMediaPlayerOnly()
        stopTtsOnly()
        _playingKey.value = null
        _ttsProgress.value = null
    }

    /**
     * Single words use real human US/UK recordings ONLY (never TTS).
     * Shows "لا يوجد صوت متاح" if no recording or playback fails.
     */
    fun playHumanPronunciation(
        word: String,
        accent: Accent,
        audioUrl: String?,
        key: String
    ) {
        val normalizedUrl = normalizeAudioUrl(audioUrl)
        val cachedFile = cacheFileFor(word, accent)
        if (normalizedUrl == null && (!cachedFile.exists() || cachedFile.length() == 0L)) {
            Log.w(TAG, "No human audio URL or cached file for $word (${accent.code})")
            emitNoAudioAvailable()
            return
        }

        stopPlayback()
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
                    Log.e(TAG, "Human pronunciation unavailable/download failed for $word ($normalizedUrl)")
                    _playingKey.value = null
                    emitNoAudioAvailable()
                }
            } catch (e: CancellationException) {
                _playingKey.value = null
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio playback failed for $word (${accent.code})", e)
                _playingKey.value = null
                emitNoAudioAvailable()
            }
        }
    }

    /**
     * Sentences/phrases/long texts:
     * 1) Real human recording first if URL exists (cached locally).
     * 2) Otherwise Android TextToSpeech with sentence chunking, maxSpeechInputLength enforcement,
     *    sliding QUEUE_ADD window for 1000+ words, progress tracking, and non-blocking splitting.
     * 3) Shows "لا يوجد صوت متاح" if playback/TTS is unavailable.
     */
    fun playSentenceAudio(
        sentence: String,
        audioUrl: String? = null,
        key: String
    ) {
        val cleaned = sentence.trim()
        if (cleaned.isEmpty()) {
            emitNoAudioAvailable()
            return
        }

        stopPlayback()
        playbackJob = scope.launch {
            _playingKey.value = key
            try {
                val normalizedUrl = normalizeAudioUrl(audioUrl)
                if (normalizedUrl != null) {
                    val sentenceFile = sentenceCacheFileFor(normalizedUrl)
                    val fileToPlay = withContext(Dispatchers.IO) {
                        if (sentenceFile.exists() && sentenceFile.length() > 0L) {
                            sentenceFile
                        } else {
                            downloadToCache(normalizedUrl, sentenceFile)
                        }
                    }
                    if (fileToPlay != null && fileToPlay.exists() && fileToPlay.length() > 0L) {
                        playLocalFile(fileToPlay)
                        return@launch
                    } else {
                        Log.w(TAG, "Sentence human audio download failed ($normalizedUrl), falling back to TTS")
                    }
                }

                val ready = ensureTtsInitialized()
                if (!ready) {
                    Log.e(TAG, "TextToSpeech engine unavailable for sentence playback")
                    _playingKey.value = null
                    _ttsProgress.value = null
                    emitNoAudioAvailable()
                    return@launch
                }

                val isArabicText = ArabicTextUtils.containsArabicLetters(cleaned)
                val available = withContext(Dispatchers.Default) {
                    if (isArabicText) configureArabicTtsVoice() else configureBestEnglishVoice()
                }
                if (!available) {
                    Log.e(TAG, "No supported TTS voice/language installed (isArabic=$isArabicText)")
                    _playingKey.value = null
                    _ttsProgress.value = null
                    emitNoAudioAvailable()
                    return@launch
                }

                speakSentenceChunks(cleaned)
            } catch (e: CancellationException) {
                _playingKey.value = null
                _ttsProgress.value = null
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sentence playback failed", e)
                _playingKey.value = null
                _ttsProgress.value = null
                emitNoAudioAvailable()
            }
        }
    }

    /**
     * Non-blocking lazy check for English TTS voice availability.
     */
    fun prepareTtsCheckIfNeeded() {
        scope.launch {
            val ready = ensureTtsInitialized()
            if (ready) {
                withContext(Dispatchers.Default) {
                    configureBestEnglishVoice()
                }
            } else {
                _ttsMissingEnglishVoice.value = true
            }
        }
    }

    fun refreshTtsVoiceAvailability() {
        scope.launch {
            val ready = if (tts != null) true else ensureTtsInitialized()
            if (ready) {
                withContext(Dispatchers.Default) {
                    configureBestEnglishVoice()
                }
            }
        }
    }

    fun buildInstallTtsDataIntent(): Intent =
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)

    private suspend fun ensureTtsInitialized(): Boolean {
        val existing = tts
        val existingDeferred = ttsInitDeferred
        if (existing != null && existingDeferred != null) {
            val ok = withTimeoutOrNull(8_000L) { existingDeferred.await() } ?: false
            if (ok) return true
            runCatching { existing.shutdown() }
            tts = null
            ttsInitDeferred = null
        }

        val deferred = CompletableDeferred<Boolean>()
        ttsInitDeferred = deferred
        try {
            var instanceRef: TextToSpeech? = null
            val instance = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    instanceRef?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            val parsed = parseUtteranceId(utteranceId) ?: return
                            if (parsed.first == activeBatchId && totalChunksInBatch > 0) {
                                _ttsProgress.value = TtsProgress(
                                    currentChunk = (parsed.second + 1).coerceAtMost(totalChunksInBatch),
                                    totalChunks = totalChunksInBatch
                                )
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            val parsed = parseUtteranceId(utteranceId) ?: return
                            val (batchId, chunkIdx) = parsed
                            var finishedAll = false
                            synchronized(ttsQueueLock) {
                                if (batchId != activeBatchId) return
                                val engine = tts
                                if (engine != null && nextChunkToEnqueue < pendingChunks.size) {
                                    val nextIdx = nextChunkToEnqueue
                                    val nextText = pendingChunks[nextIdx]
                                    val uttId = formatUtteranceId(batchId, nextIdx)
                                    val res = runCatching {
                                        engine.speak(nextText, TextToSpeech.QUEUE_ADD, Bundle(), uttId)
                                    }.getOrDefault(TextToSpeech.ERROR)
                                    if (res == TextToSpeech.ERROR) {
                                        Log.e(TAG, "TTS sliding QUEUE_ADD failed at chunk=$nextIdx")
                                    }
                                    nextChunkToEnqueue = nextIdx + 1
                                } else if (chunkIdx >= totalChunksInBatch - 1) {
                                    finishedAll = true
                                    activeBatchId = 0L
                                    pendingChunks = emptyList()
                                    nextChunkToEnqueue = 0
                                    totalChunksInBatch = 0
                                }
                            }
                            if (finishedAll) {
                                _playingKey.value = null
                                _ttsProgress.value = null
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            Log.e(TAG, "TTS utterance error (legacy) for $utteranceId")
                            clearQueueOnError()
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.e(TAG, "TTS utterance error code=$errorCode for $utteranceId")
                            clearQueueOnError()
                        }
                    })
                    if (!deferred.isCompleted) deferred.complete(true)
                } else {
                    Log.e(TAG, "TextToSpeech onInit failed with status=$status")
                    if (!deferred.isCompleted) deferred.complete(false)
                }
            }
            instanceRef = instance
            tts = instance
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech construction failed", e)
            tts = null
            ttsInitDeferred = null
            if (!deferred.isCompleted) deferred.complete(false)
            return false
        }

        val success = withTimeoutOrNull(8_000L) { deferred.await() } ?: false
        if (!success) {
            runCatching { tts?.shutdown() }
            tts = null
            ttsInitDeferred = null
        }
        return success
    }

    private fun clearQueueOnError() {
        synchronized(ttsQueueLock) {
            activeBatchId = 0L
            pendingChunks = emptyList()
            nextChunkToEnqueue = 0
            totalChunksInBatch = 0
        }
        _playingKey.value = null
        _ttsProgress.value = null
        emitNoAudioAvailable()
    }

    private fun configureBestEnglishVoice(): Boolean {
        val engine = tts ?: return false
        val preferredAccent = settingsPreferences.sentenceAccent.value
        val preferredLocale = if (preferredAccent == Accent.UK) Locale.UK else Locale.US
        val preferredCountry = preferredLocale.country

        val allVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val installedEnVoices = allVoices.filter { v ->
            v.locale?.language.equals("en", ignoreCase = true) &&
                !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }

        val bestVoice = pickBestEnglishVoice(installedEnVoices, preferredCountry)
        if (bestVoice != null) {
            val langCheck = runCatching { engine.setLanguage(bestVoice.locale ?: preferredLocale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (langCheck >= TextToSpeech.LANG_AVAILABLE) {
                runCatching { engine.voice = bestVoice }
                _ttsMissingEnglishVoice.value = false
                return true
            }
            Log.w(TAG, "Selected English voice locale check returned $langCheck (${bestVoice.locale})")
        }

        val langStatus = runCatching { engine.setLanguage(preferredLocale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (langStatus >= TextToSpeech.LANG_AVAILABLE) {
            _ttsMissingEnglishVoice.value = false
            return true
        }
        Log.e(TAG, "English TTS setLanguage($preferredLocale) failed status=$langStatus")

        val fallbackEn = runCatching { engine.setLanguage(Locale.ENGLISH) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val ok = fallbackEn >= TextToSpeech.LANG_AVAILABLE
        if (!ok) {
            Log.e(TAG, "Fallback Locale.ENGLISH setLanguage failed status=$fallbackEn")
        }
        _ttsMissingEnglishVoice.value = !ok
        return ok
    }

    private fun configureArabicTtsVoice(): Boolean {
        val engine = tts ?: return false
        val arLocale = Locale("ar")
        val allVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val installedArVoices = allVoices.filter { v ->
            v.locale?.language.equals("ar", ignoreCase = true) &&
                !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        val bestAr = installedArVoices.sortedWith(
            compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                .thenByDescending { it.quality }
        ).firstOrNull()

        val status = runCatching {
            engine.setLanguage(bestAr?.locale ?: arLocale)
        }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)

        if (status >= TextToSpeech.LANG_AVAILABLE) {
            if (bestAr != null) runCatching { engine.voice = bestAr }
            return true
        }
        Log.e(TAG, "Arabic TTS setLanguage failed status=$status")
        return false
    }

    private fun pickBestEnglishVoice(voices: List<Voice>, preferredCountry: String): Voice? {
        if (voices.isEmpty()) return null
        val comparator = compareByDescending<Voice> {
            it.locale?.country.equals(preferredCountry, ignoreCase = true)
        }.thenByDescending {
            !it.isNetworkConnectionRequired
        }.thenByDescending {
            it.quality
        }
        return voices.sortedWith(comparator).firstOrNull()
    }

    private suspend fun speakSentenceChunks(text: String) {
        val engine = tts ?: run {
            _playingKey.value = null
            _ttsProgress.value = null
            emitNoAudioAvailable()
            return
        }
        val speed = settingsPreferences.audioSpeed.value
        runCatching { engine.setSpeechRate(speed) }

        val rawMax = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000)
        val safeMaxLen = (if (rawMax > 0) rawMax else 4000).coerceAtMost(3800)
        val chunks = withContext(Dispatchers.Default) {
            splitTextSafelyForTts(text, safeMaxLen)
        }
        if (chunks.isEmpty()) {
            _playingKey.value = null
            _ttsProgress.value = null
            emitNoAudioAvailable()
            return
        }

        val batchId = System.nanoTime()
        val initialWindow = minOf(8, chunks.size)
        val params = Bundle()

        withContext(Dispatchers.Default) {
            synchronized(ttsQueueLock) {
                activeBatchId = batchId
                pendingChunks = chunks
                totalChunksInBatch = chunks.size
                nextChunkToEnqueue = initialWindow
            }
            _ttsProgress.value = if (chunks.size > 1) TtsProgress(1, chunks.size) else null

            for (index in 0 until initialWindow) {
                if (activeBatchId != batchId) break
                val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val uttId = formatUtteranceId(batchId, index)
                val result = runCatching {
                    engine.speak(chunks[index], queueMode, params, uttId)
                }.getOrDefault(TextToSpeech.ERROR)
                if (result == TextToSpeech.ERROR) {
                    Log.e(TAG, "TextToSpeech.speak returned ERROR on chunk=$index")
                    clearQueueOnError()
                    return@withContext
                }
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
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Audio file missing or empty: ${file.absolutePath}")
            _playingKey.value = null
            emitNoAudioAvailable()
            return
        }
        try {
            stopMediaPlayerOnly()
            val speed = settingsPreferences.audioSpeed.value
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                FileInputStream(file).use { fis ->
                    setDataSource(fis.fd)
                }
                setOnPreparedListener { player ->
                    try {
                        player.start()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != 1.0f) {
                            try {
                                player.playbackParams = PlaybackParams().setSpeed(speed)
                            } catch (e: Throwable) {
                                Log.w(TAG, "PlaybackParams speed=$speed unsupported on codec, playing at 1.0x", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "MediaPlayer start failed for ${file.name}", e)
                        _playingKey.value = null
                        emitNoAudioAvailable()
                    }
                }
                setOnCompletionListener {
                    _playingKey.value = null
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer OnError what=$what extra=$extra file=${file.name}")
                    runCatching { file.delete() }
                    _playingKey.value = null
                    emitNoAudioAvailable()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer setup failed for file ${file.name}", e)
            runCatching { file.delete() }
            _playingKey.value = null
            emitNoAudioAvailable()
        }
    }

    private fun applySpeedToActiveMediaPlayer(speed: Float) {
        val player = mediaPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                if (player.isPlaying) {
                    player.playbackParams = PlaybackParams().setSpeed(speed)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Active MediaPlayer speed change ignored", e)
            }
        }
    }

    private fun downloadToCache(url: String, targetFile: File): File? {
        val tempFile = File(audioCacheDir, "${targetFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP ${response.code} downloading audio: $url")
                    return null
                }
                val contentType = response.header("Content-Type")?.lowercase(Locale.US).orEmpty()
                if (contentType.startsWith("text/") || contentType.contains("json")) {
                    Log.e(TAG, "Unexpected non-audio Content-Type ($contentType) for: $url")
                    return null
                }
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
                targetFile
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio download exception for $url", e)
            tempFile.delete()
            null
        }
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

    private fun emitNoAudioAvailable() {
        _audioMessage.tryEmit(NO_AUDIO_MESSAGE)
    }

    private fun cacheFileFor(word: String, accent: Accent): File {
        val normalized = word.trim().lowercase(Locale.US)
        val digest = sha256Short(normalized)
        return File(audioCacheDir, "${digest}_${accent.code}.mp3")
    }

    private fun sentenceCacheFileFor(url: String): File {
        val digest = sha256Short(url.trim())
        return File(audioCacheDir, "sent_${digest}.mp3")
    }

    private fun sha256Short(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    private fun stopMediaPlayerOnly() {
        runCatching { mediaPlayer?.reset() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }

    private fun stopTtsOnly() {
        runCatching { tts?.stop() }
    }

    fun release() {
        stopPlayback()
        runCatching { tts?.shutdown() }
        tts = null
        ttsInitDeferred = null
    }

    companion object {
        private fun formatUtteranceId(batchId: Long, index: Int): String = "utt_${batchId}_$index"

        private fun parseUtteranceId(utteranceId: String?): Pair<Long, Int>? {
            if (utteranceId.isNullOrBlank() || !utteranceId.startsWith("utt_")) return null
            val parts = utteranceId.split('_')
            if (parts.size != 3) return null
            val batchId = parts[1].toLongOrNull() ?: return null
            val idx = parts[2].toIntOrNull() ?: return null
            return batchId to idx
        }

        internal fun splitTextSafelyForTts(text: String, maxChunkLength: Int = 3800): List<String> {
            val cleaned = text.trim()
            if (cleaned.isEmpty()) return emptyList()
            val limit = maxChunkLength.coerceAtLeast(100)

            val rawSentences = cleaned
                .split(Regex("(?<=[.!?;؟\\n])\\s+|\\n+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val result = ArrayList<String>(rawSentences.size)
            for (sentence in rawSentences) {
                if (sentence.length <= limit) {
                    result.add(sentence)
                } else {
                    appendSplitSegment(sentence, limit, result)
                }
            }
            return result
        }

        private fun appendSplitSegment(segment: String, limit: Int, out: MutableList<String>) {
            val clauses = segment
                .split(Regex("(?<=[,،;:])\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (clause in clauses) {
                if (clause.length <= limit) {
                    out.add(clause)
                } else {
                    var start = 0
                    while (start < clause.length) {
                        val end = minOf(start + limit, clause.length)
                        if (end == clause.length) {
                            val tail = clause.substring(start).trim()
                            if (tail.isNotEmpty()) out.add(tail)
                            break
                        }
                        var splitAt = clause.lastIndexOf(' ', end)
                        if (splitAt <= start) {
                            splitAt = end
                        }
                        val piece = clause.substring(start, splitAt).trim()
                        if (piece.isNotEmpty()) out.add(piece)
                        start = if (splitAt < clause.length && clause[splitAt] == ' ') splitAt + 1 else splitAt
                    }
                }
            }
        }
    }
}

