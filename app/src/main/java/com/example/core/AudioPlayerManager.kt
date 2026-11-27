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
import androidx.core.content.ContextCompat
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
private const val NO_AUDIO_MESSAGE = "لا يوجد صوت متاح على هذا الجهاز"
private const val NO_ENGLISH_VOICE_MESSAGE = "محرك النطق الإنجليزي غير مثبت على الجهاز"
private const val NO_ARABIC_VOICE_MESSAGE = "الصوت العربي غير مثبت على الجهاز"

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
    private val failedAudioUrls = java.util.Collections.synchronizedSet(HashSet<String>())
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

    data class ReaderState(
        val key: String? = null,
        val sentences: List<String> = emptyList(),
        val currentIndex: Int = 0,
        val isActive: Boolean = false,
        val isPaused: Boolean = false,
        val accent: Accent = Accent.US,
        val isArabic: Boolean = false
    )

    private val _ttsProgress = MutableStateFlow<TtsProgress?>(null)
    val ttsProgress: StateFlow<TtsProgress?> = _ttsProgress.asStateFlow()

    private val _readerState = MutableStateFlow(ReaderState(accent = settingsPreferences.sentenceAccent.value))
    val readerState: StateFlow<ReaderState> = _readerState.asStateFlow()

    private val _ttsMissingEnglishVoice = MutableStateFlow(false)
    val ttsMissingEnglishVoice: StateFlow<Boolean> = _ttsMissingEnglishVoice.asStateFlow()

    private val _ttsMissingAccentLabel = MutableStateFlow<String?>(null)
    val ttsMissingAccentLabel: StateFlow<String?> = _ttsMissingAccentLabel.asStateFlow()

    private val _audioMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val audioMessage: SharedFlow<String> = _audioMessage.asSharedFlow()

    val audioSpeed: StateFlow<Float> = settingsPreferences.audioSpeed
    val sentenceAccent: StateFlow<Accent> = settingsPreferences.sentenceAccent

    private val ttsQueueLock = Any()
    @Volatile private var activeBatchId: Long = 0L
    @Volatile private var pendingChunks: List<String> = emptyList()
    @Volatile private var nextChunkToEnqueue: Int = 0
    @Volatile private var totalChunksInBatch: Int = 0
    @Volatile private var activeReaderSessionId: Long = 0L

    fun setAudioSpeed(speed: Float) {
        settingsPreferences.setAudioSpeed(speed)
        val sanitized = settingsPreferences.audioSpeed.value
        applySpeedToActiveMediaPlayer(sanitized)
        runCatching { tts?.setSpeechRate(sanitized) }

        val activeReader = _readerState.value
        if (activeReader.isActive && !activeReader.isPaused && activeReader.sentences.isNotEmpty()) {
            speakReaderSentenceAt(activeReader.currentIndex)
        }
    }

    fun stopPlayback(fromService: Boolean = false) {
        playbackJob?.cancel()
        playbackJob = null
        synchronized(ttsQueueLock) {
            activeBatchId = 0L
            pendingChunks = emptyList()
            nextChunkToEnqueue = 0
            totalChunksInBatch = 0
            activeReaderSessionId = 0L
        }
        stopMediaPlayerOnly()
        stopTtsOnly()
        _playingKey.value = null
        _ttsProgress.value = null
        val wasReaderActive = _readerState.value.isActive
        _readerState.value = ReaderState(accent = settingsPreferences.sentenceAccent.value)
        if (!fromService && wasReaderActive) {
            stopReaderService()
        }
    }

    /**
     * Pronounces a single word in US or UK accent:
     * 1) Real human recording (Free Dictionary API / Wikimedia Commons / cache).
     * 2) Automatic fallback to TextToSpeech in requested US/UK accent (and if that regional voice
     *    isn't installed, warns briefly and uses any installed English voice).
     */
    fun playHumanPronunciation(
        word: String,
        accent: Accent,
        audioUrl: String?,
        key: String
    ) {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) {
            emitMessage(NO_AUDIO_MESSAGE)
            return
        }
        val normalizedUrl = normalizeAudioUrl(audioUrl)
        stopPlayback()
        playbackJob = scope.launch {
            _playingKey.value = key
            try {
                val existingCache = findExistingWordCacheFile(cleaned, accent)
                val targetFile = if (existingCache != null) {
                    existingCache
                } else if (normalizedUrl != null) {
                    val downloadTarget = cacheFileFor(cleaned, accent, normalizedUrl)
                    val fastDownload = withTimeoutOrNull(650L) {
                        withContext(Dispatchers.IO) {
                            downloadToCache(normalizedUrl, downloadTarget)
                        }
                    }
                    if (fastDownload == null) {
                        // Cache in background for instant replay next time without delaying playback
                        prefetchWordAudio(
                            word = cleaned,
                            usAudioUrl = if (accent == Accent.US) normalizedUrl else null,
                            ukAudioUrl = if (accent == Accent.UK) normalizedUrl else null
                        )
                    }
                    fastDownload
                } else {
                    null
                }

                if (targetFile != null && targetFile.exists() && targetFile.length() > 0L) {
                    playLocalFile(
                        file = targetFile,
                        onCodecOrPlayErrorFallback = {
                            launchTtsWordFallback(cleaned, accent, key)
                        }
                    )
                } else {
                    if (normalizedUrl != null) {
                        Log.w(TAG, "Word human audio download failed ($normalizedUrl), falling back to TTS ($accent)")
                    }
                    playWordViaTts(cleaned, accent, key)
                }
            } catch (e: CancellationException) {
                _playingKey.value = null
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio playback failed for $cleaned (${accent.code}), falling back to TTS", e)
                playWordViaTts(cleaned, accent, key)
            }
        }
    }

    private fun launchTtsWordFallback(word: String, accent: Accent, key: String) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            _playingKey.value = key
            playWordViaTts(word, accent, key)
        }
    }

    private suspend fun playWordViaTts(word: String, accent: Accent, key: String) {
        val ready = ensureTtsInitialized()
        if (!ready) {
            _playingKey.value = null
            emitMessage(NO_ENGLISH_VOICE_MESSAGE)
            return
        }
        val isArabicText = ArabicTextUtils.containsArabicLetters(word)
        val available = withContext(Dispatchers.Default) {
            if (isArabicText) configureArabicTtsVoice() else configureBestEnglishVoice(accent)
        }
        if (!available) {
            _playingKey.value = null
            emitMessage(if (isArabicText) NO_ARABIC_VOICE_MESSAGE else NO_ENGLISH_VOICE_MESSAGE)
            return
        }
        _playingKey.value = key
        speakSentenceChunks(word)
    }

    /**
     * Sentences/phrases/examples:
     * 1) Human recording first if URL exists.
     * 2) Otherwise Android TextToSpeech with requested US/UK accent (or user default).
     */
    fun playSentenceAudio(
        sentence: String,
        audioUrl: String? = null,
        key: String,
        accentOverride: Accent? = null
    ) {
        val cleaned = sentence.trim()
        if (cleaned.isEmpty()) {
            emitMessage(NO_AUDIO_MESSAGE)
            return
        }

        val targetAccent = accentOverride ?: settingsPreferences.sentenceAccent.value
        stopPlayback()
        playbackJob = scope.launch {
            _playingKey.value = key
            try {
                val normalizedUrl = normalizeAudioUrl(audioUrl)
                if (normalizedUrl != null) {
                    val sentenceFile = sentenceCacheFileFor(normalizedUrl)
                    val fileToPlay = withTimeoutOrNull(650L) {
                        withContext(Dispatchers.IO) {
                            if (sentenceFile.exists() && sentenceFile.length() > 0L) {
                                sentenceFile
                            } else {
                                downloadToCache(normalizedUrl, sentenceFile)
                            }
                        }
                    }
                    if (fileToPlay != null && fileToPlay.exists() && fileToPlay.length() > 0L) {
                        playLocalFile(
                            file = fileToPlay,
                            onCodecOrPlayErrorFallback = {
                                scope.launch {
                                    _playingKey.value = key
                                    playSentenceViaTts(cleaned, targetAccent)
                                }
                            }
                        )
                        return@launch
                    } else {
                        Log.w(TAG, "Sentence human audio download failed ($normalizedUrl), falling back to TTS")
                    }
                }
                playSentenceViaTts(cleaned, targetAccent)
            } catch (e: CancellationException) {
                _playingKey.value = null
                _ttsProgress.value = null
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sentence playback failed", e)
                _playingKey.value = null
                _ttsProgress.value = null
                emitMessage(NO_AUDIO_MESSAGE)
            }
        }
    }

    private suspend fun playSentenceViaTts(cleaned: String, targetAccent: Accent) {
        val ready = ensureTtsInitialized()
        if (!ready) {
            Log.e(TAG, "TextToSpeech engine unavailable for sentence playback")
            _playingKey.value = null
            _ttsProgress.value = null
            emitMessage(NO_ENGLISH_VOICE_MESSAGE)
            return
        }

        val isArabicText = ArabicTextUtils.containsArabicLetters(cleaned)
        val available = withContext(Dispatchers.Default) {
            if (isArabicText) configureArabicTtsVoice() else configureBestEnglishVoice(targetAccent)
        }
        if (!available) {
            Log.e(TAG, "No supported TTS voice/language installed (isArabic=$isArabicText)")
            _playingKey.value = null
            _ttsProgress.value = null
            emitMessage(if (isArabicText) NO_ARABIC_VOICE_MESSAGE else NO_ENGLISH_VOICE_MESSAGE)
            return
        }

        val continuousSentence = cleaned.replace(Regex("\\s+"), " ").trim()
        speakSentenceChunks(continuousSentence, splitOnPunctuation = false)
    }

    /**
     * Long-text reader mode:
     * Splits into sentences, highlights active sentence, supports Pause/Resume/Skip Next/Prev/Stop,
     * accent switching, and background playback via LongTextReaderService.
     */
    fun startLongReading(
        key: String,
        text: String,
        accentOverride: Accent? = null
    ) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            emitMessage(NO_AUDIO_MESSAGE)
            return
        }
        val rawMax = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000)
        val safeMaxLen = (if (rawMax > 0) rawMax else 4000).coerceAtMost(3800)
        val sentences = splitTextSafelyForTts(cleaned, safeMaxLen)
        if (sentences.isEmpty()) {
            emitMessage(NO_AUDIO_MESSAGE)
            return
        }

        val targetAccent = accentOverride ?: settingsPreferences.sentenceAccent.value
        val isArabic = ArabicTextUtils.containsArabicLetters(cleaned)

        stopPlayback()
        val sessionId = System.nanoTime()
        synchronized(ttsQueueLock) {
            activeReaderSessionId = sessionId
        }
        _playingKey.value = key
        _readerState.value = ReaderState(
            key = key,
            sentences = sentences,
            currentIndex = 0,
            isActive = true,
            isPaused = false,
            accent = targetAccent,
            isArabic = isArabic
        )
        _ttsProgress.value = TtsProgress(1, sentences.size)
        syncReaderService(startForeground = true)
        speakReaderSentenceAt(0)
    }

    fun pauseLongReading(fromService: Boolean = false) {
        val state = _readerState.value
        if (!state.isActive || state.isPaused) return
        playbackJob?.cancel()
        playbackJob = null
        synchronized(ttsQueueLock) {
            activeReaderSessionId = 0L
        }
        stopTtsOnly()
        _readerState.value = state.copy(isPaused = true)
        if (!fromService) {
            syncReaderService(startForeground = false)
        } else {
            syncReaderService(startForeground = true)
        }
    }

    fun resumeLongReading(fromService: Boolean = false) {
        val state = _readerState.value
        if (!state.isActive || !state.isPaused || state.sentences.isEmpty()) return
        val sessionId = System.nanoTime()
        synchronized(ttsQueueLock) {
            activeReaderSessionId = sessionId
        }
        _readerState.value = state.copy(isPaused = false)
        _playingKey.value = state.key
        speakReaderSentenceAt(state.currentIndex)
        if (!fromService) {
            syncReaderService(startForeground = false)
        } else {
            syncReaderService(startForeground = true)
        }
    }

    fun skipNext(fromService: Boolean = false) {
        val state = _readerState.value
        if (!state.isActive || state.sentences.isEmpty()) return
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.sentences.size) {
            stopPlayback(fromService = fromService)
            return
        }
        moveReaderToIndex(nextIndex, fromService)
    }

    fun skipPrevious(fromService: Boolean = false) {
        val state = _readerState.value
        if (!state.isActive || state.sentences.isEmpty()) return
        val prevIndex = (state.currentIndex - 1).coerceAtLeast(0)
        moveReaderToIndex(prevIndex, fromService)
    }

    fun switchReaderAccent(accent: Accent) {
        settingsPreferences.setSentenceAccent(accent)
        val state = _readerState.value
        if (!state.isActive) return
        _readerState.value = state.copy(accent = accent)
        if (!state.isPaused) {
            speakReaderSentenceAt(state.currentIndex)
        }
    }

    private fun moveReaderToIndex(index: Int, fromService: Boolean) {
        val state = _readerState.value
        val clamped = index.coerceIn(0, (state.sentences.size - 1).coerceAtLeast(0))
        _readerState.value = state.copy(currentIndex = clamped)
        _ttsProgress.value = TtsProgress(clamped + 1, state.sentences.size)
        if (!state.isPaused) {
            speakReaderSentenceAt(clamped)
        } else {
            stopTtsOnly()
        }
        if (!fromService) {
            syncReaderService(startForeground = false)
        } else {
            syncReaderService(startForeground = true)
        }
    }

    private fun speakReaderSentenceAt(index: Int) {
        val state = _readerState.value
        if (!state.isActive || index !in state.sentences.indices) return
        val sentence = state.sentences[index]
        val sessionId = System.nanoTime()
        synchronized(ttsQueueLock) {
            activeBatchId = 0L
            pendingChunks = emptyList()
            nextChunkToEnqueue = 0
            totalChunksInBatch = 0
            activeReaderSessionId = sessionId
        }
        _playingKey.value = state.key
        _ttsProgress.value = TtsProgress(index + 1, state.sentences.size)

        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                val ready = ensureTtsInitialized()
                if (!ready) {
                    emitMessage(if (state.isArabic) NO_ARABIC_VOICE_MESSAGE else NO_ENGLISH_VOICE_MESSAGE)
                    stopPlayback()
                    return@launch
                }
                val voiceOk = withContext(Dispatchers.Default) {
                    if (state.isArabic) {
                        configureArabicTtsVoice()
                    } else {
                        configureBestEnglishVoice(state.accent)
                    }
                }
                if (!voiceOk) {
                    emitMessage(if (state.isArabic) NO_ARABIC_VOICE_MESSAGE else NO_ENGLISH_VOICE_MESSAGE)
                    stopPlayback()
                    return@launch
                }

                val engine = tts ?: return@launch
                runCatching { engine.setSpeechRate(settingsPreferences.audioSpeed.value) }
                val uttId = formatReaderUtteranceId(sessionId, index)
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        engine.speak(sentence, TextToSpeech.QUEUE_FLUSH, Bundle(), uttId)
                    }.getOrDefault(TextToSpeech.ERROR)
                }
                if (result == TextToSpeech.ERROR) {
                    Log.e(TAG, "Reader TTS speak ERROR at index=$index")
                    emitMessage(NO_AUDIO_MESSAGE)
                    stopPlayback()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Reader playback exception at index=$index", e)
                emitMessage(NO_AUDIO_MESSAGE)
                stopPlayback()
            }
        }
    }

    private fun syncReaderService(startForeground: Boolean) {
        val state = _readerState.value
        if (!state.isActive) {
            stopReaderService()
            return
        }
        runCatching {
            val intent = LongTextReaderService.intentFor(
                appContext,
                if (startForeground) LongTextReaderService.ACTION_START else LongTextReaderService.ACTION_SYNC
            )
            if (startForeground) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    private fun stopReaderService() {
        runCatching {
            appContext.stopService(LongTextReaderService.intentFor(appContext, LongTextReaderService.ACTION_STOP))
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
                    configureBestEnglishVoice(settingsPreferences.sentenceAccent.value)
                }
            } else {
                _ttsMissingEnglishVoice.value = true
                _ttsMissingAccentLabel.value = null
            }
        }
    }

    fun refreshTtsVoiceAvailability() {
        scope.launch {
            val ready = if (tts != null) true else ensureTtsInitialized()
            if (ready) {
                withContext(Dispatchers.Default) {
                    configureBestEnglishVoice(settingsPreferences.sentenceAccent.value)
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
                            val parsedBatch = parseUtteranceId(utteranceId)
                            if (parsedBatch != null && parsedBatch.first == activeBatchId && totalChunksInBatch > 0) {
                                _ttsProgress.value = TtsProgress(
                                    currentChunk = (parsedBatch.second + 1).coerceAtMost(totalChunksInBatch),
                                    totalChunks = totalChunksInBatch
                                )
                                return
                            }
                            val parsedReader = parseReaderUtteranceId(utteranceId)
                            if (parsedReader != null && parsedReader.first == activeReaderSessionId) {
                                val st = _readerState.value
                                if (st.isActive) {
                                    _ttsProgress.value = TtsProgress(
                                        currentChunk = (parsedReader.second + 1).coerceAtMost(st.sentences.size.coerceAtLeast(1)),
                                        totalChunks = st.sentences.size.coerceAtLeast(1)
                                    )
                                }
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            val parsedReader = parseReaderUtteranceId(utteranceId)
                            if (parsedReader != null) {
                                val (sessionId, completedIdx) = parsedReader
                                scope.launch {
                                    val st = _readerState.value
                                    if (sessionId == activeReaderSessionId && st.isActive && !st.isPaused) {
                                        val nextIdx = completedIdx + 1
                                        if (nextIdx < st.sentences.size) {
                                            _readerState.value = st.copy(currentIndex = nextIdx)
                                            syncReaderService(startForeground = false)
                                            speakReaderSentenceAt(nextIdx)
                                        } else {
                                            stopPlayback()
                                        }
                                    }
                                }
                                return
                            }

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
                            handleUtteranceError(utteranceId)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Log.e(TAG, "TTS utterance error code=$errorCode for $utteranceId")
                            handleUtteranceError(utteranceId)
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

    private fun handleUtteranceError(utteranceId: String?) {
        val readerParsed = parseReaderUtteranceId(utteranceId)
        if (readerParsed != null && readerParsed.first == activeReaderSessionId) {
            scope.launch {
                stopPlayback()
                emitMessage(NO_AUDIO_MESSAGE)
            }
            return
        }
        clearQueueOnError()
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
        emitMessage(NO_AUDIO_MESSAGE)
    }

    private fun configureBestEnglishVoice(preferredAccent: Accent): Boolean {
        val engine = tts ?: return false
        val preferredLocale = if (preferredAccent == Accent.UK) Locale.UK else Locale.US
        val preferredCountry = preferredLocale.country

        val allVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        val installedEnVoices = allVoices.filter { v ->
            v.locale?.language.equals("en", ignoreCase = true) &&
                !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }

        val exactVoices = installedEnVoices.filter {
            it.locale?.country.equals(preferredCountry, ignoreCase = true)
        }
        val bestExact = pickBestEnglishVoice(exactVoices, preferredCountry)
        if (bestExact != null) {
            val langCheck = runCatching { engine.setLanguage(bestExact.locale ?: preferredLocale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (langCheck >= TextToSpeech.LANG_AVAILABLE) {
                runCatching { engine.voice = bestExact }
                _ttsMissingEnglishVoice.value = false
                _ttsMissingAccentLabel.value = null
                return true
            }
        }

        val exactLangStatus = runCatching { engine.setLanguage(preferredLocale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (exactLangStatus == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            exactLangStatus == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        ) {
            _ttsMissingEnglishVoice.value = false
            _ttsMissingAccentLabel.value = null
            return true
        }

        // Preferred regional voice is missing — flag helpful banner & fallback to any English voice
        val missingLabel = if (preferredAccent == Accent.UK) "البريطاني (UK)" else "الأمريكي (US)"

        val fallbackVoice = pickBestEnglishVoice(installedEnVoices, preferredCountry)
        if (fallbackVoice != null) {
            val check = runCatching { engine.setLanguage(fallbackVoice.locale ?: Locale.ENGLISH) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (check >= TextToSpeech.LANG_AVAILABLE) {
                runCatching { engine.voice = fallbackVoice }
                _ttsMissingEnglishVoice.value = true
                _ttsMissingAccentLabel.value = missingLabel
                return true
            }
        }

        if (exactLangStatus >= TextToSpeech.LANG_AVAILABLE) {
            _ttsMissingEnglishVoice.value = false
            _ttsMissingAccentLabel.value = null
            return true
        }

        val fallbackEn = runCatching { engine.setLanguage(Locale.ENGLISH) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        val ok = fallbackEn >= TextToSpeech.LANG_AVAILABLE
        _ttsMissingEnglishVoice.value = true
        _ttsMissingAccentLabel.value = if (ok) missingLabel else null
        if (!ok) {
            Log.e(TAG, "No English TTS language/voice installed (status=$fallbackEn)")
        }
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

    private suspend fun speakSentenceChunks(
        text: String,
        splitOnPunctuation: Boolean = true
    ) {
        val engine = tts ?: run {
            _playingKey.value = null
            _ttsProgress.value = null
            emitMessage(NO_AUDIO_MESSAGE)
            return
        }
        val speed = settingsPreferences.audioSpeed.value
        runCatching { engine.setSpeechRate(speed) }

        val rawMax = runCatching { TextToSpeech.getMaxSpeechInputLength() }.getOrDefault(4000)
        val safeMaxLen = (if (rawMax > 0) rawMax else 4000).coerceAtMost(3800)
        val cleaned = text.trim()
        val chunks = withContext(Dispatchers.Default) {
            if (!splitOnPunctuation && cleaned.isNotEmpty() && cleaned.length <= safeMaxLen) {
                listOf(cleaned)
            } else {
                splitTextSafelyForTts(cleaned, safeMaxLen)
            }
        }
        if (chunks.isEmpty()) {
            _playingKey.value = null
            _ttsProgress.value = null
            emitMessage(NO_AUDIO_MESSAGE)
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
        val usUrl = normalizeAudioUrl(usAudioUrl)?.takeIf { it !in failedAudioUrls }
        val ukUrl = normalizeAudioUrl(ukAudioUrl)?.takeIf { it !in failedAudioUrls }
        if (usUrl == null && ukUrl == null) return

        scope.launch(Dispatchers.IO) {
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
            if (usUrl != null && usUrl !in failedAudioUrls) {
                val usFile = cacheFileFor(word, Accent.US, usUrl)
                if (!usFile.exists() || usFile.length() == 0L) {
                    runCatching { downloadToCache(usUrl, usFile) }
                }
            }
            if (ukUrl != null && ukUrl !in failedAudioUrls) {
                val ukFile = cacheFileFor(word, Accent.UK, ukUrl)
                if (!ukFile.exists() || ukFile.length() == 0L) {
                    runCatching { downloadToCache(ukUrl, ukFile) }
                }
            }
        }
    }

    fun hasAudioOrCache(word: String, accent: Accent, audioUrl: String?): Boolean {
        val normalized = normalizeAudioUrl(audioUrl)
        if (normalized != null && normalized !in failedAudioUrls) return true
        return findExistingWordCacheFile(word, accent) != null
    }

    private fun playLocalFile(
        file: File,
        onCodecOrPlayErrorFallback: (() -> Unit)? = null
    ) {
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "Audio file missing or empty: ${file.absolutePath}")
            if (onCodecOrPlayErrorFallback != null) {
                onCodecOrPlayErrorFallback()
            } else {
                _playingKey.value = null
                emitMessage(NO_AUDIO_MESSAGE)
            }
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
                        if (onCodecOrPlayErrorFallback != null) {
                            onCodecOrPlayErrorFallback()
                        } else {
                            _playingKey.value = null
                            emitMessage(NO_AUDIO_MESSAGE)
                        }
                    }
                }
                setOnCompletionListener {
                    _playingKey.value = null
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer OnError what=$what extra=$extra file=${file.name}")
                    runCatching { file.delete() }
                    if (onCodecOrPlayErrorFallback != null) {
                        onCodecOrPlayErrorFallback()
                    } else {
                        _playingKey.value = null
                        emitMessage(NO_AUDIO_MESSAGE)
                    }
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer setup failed for file ${file.name}", e)
            runCatching { file.delete() }
            if (onCodecOrPlayErrorFallback != null) {
                onCodecOrPlayErrorFallback()
            } else {
                _playingKey.value = null
                emitMessage(NO_AUDIO_MESSAGE)
            }
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
        if (url in failedAudioUrls) return null
        val tempFile = File(audioCacheDir, "${targetFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 410) {
                        failedAudioUrls.add(url)
                    }
                    Log.w(TAG, "HTTP ${response.code} downloading optional audio: $url")
                    return null
                }
                val contentType = response.header("Content-Type")?.lowercase(Locale.US).orEmpty()
                if (contentType.startsWith("text/") || contentType.contains("json")) {
                    failedAudioUrls.add(url)
                    Log.w(TAG, "Unexpected non-audio Content-Type ($contentType) for: $url")
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
            Log.w(TAG, "Optional audio download skipped for $url: ${e.message}")
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

    private fun emitMessage(message: String) {
        _audioMessage.tryEmit(message)
    }

    private fun findExistingWordCacheFile(word: String, accent: Accent): File? {
        val digest = sha256Short(word.trim().lowercase(Locale.US))
        for (ext in SUPPORTED_CACHE_EXTENSIONS) {
            val candidate = File(audioCacheDir, "${digest}_${accent.code}.$ext")
            if (candidate.exists() && candidate.length() > 0L) {
                return candidate
            }
        }
        return null
    }

    private fun cacheFileFor(word: String, accent: Accent, sourceUrl: String? = null): File {
        val normalized = word.trim().lowercase(Locale.US)
        val digest = sha256Short(normalized)
        val ext = inferAudioExtension(sourceUrl)
        return File(audioCacheDir, "${digest}_${accent.code}.$ext")
    }

    private fun sentenceCacheFileFor(url: String): File {
        val digest = sha256Short(url.trim())
        val ext = inferAudioExtension(url)
        return File(audioCacheDir, "sent_${digest}.$ext")
    }

    private fun inferAudioExtension(url: String?): String {
        val path = url?.substringBefore('?')?.substringBefore('#')?.lowercase(Locale.US).orEmpty()
        return when {
            path.endsWith(".ogg") -> "ogg"
            path.endsWith(".oga") -> "oga"
            path.endsWith(".wav") -> "wav"
            path.endsWith(".flac") -> "flac"
            path.endsWith(".m4a") -> "m4a"
            else -> "mp3"
        }
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
        private val SUPPORTED_CACHE_EXTENSIONS = listOf("mp3", "ogg", "oga", "wav", "flac", "m4a")

        private fun formatUtteranceId(batchId: Long, index: Int): String = "utt_${batchId}_$index"

        private fun formatReaderUtteranceId(sessionId: Long, index: Int): String = "rdr_${sessionId}_$index"

        private fun parseUtteranceId(utteranceId: String?): Pair<Long, Int>? {
            if (utteranceId.isNullOrBlank() || !utteranceId.startsWith("utt_")) return null
            val parts = utteranceId.split('_')
            if (parts.size != 3) return null
            val batchId = parts[1].toLongOrNull() ?: return null
            val idx = parts[2].toIntOrNull() ?: return null
            return batchId to idx
        }

        private fun parseReaderUtteranceId(utteranceId: String?): Pair<Long, Int>? {
            if (utteranceId.isNullOrBlank() || !utteranceId.startsWith("rdr_")) return null
            val parts = utteranceId.split('_')
            if (parts.size != 3) return null
            val sessionId = parts[1].toLongOrNull() ?: return null
            val idx = parts[2].toIntOrNull() ?: return null
            return sessionId to idx
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
