package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.BuildConfig
import com.example.data.remote.WikimediaCommonsApi
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
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream

private const val TAG = "AudioPlayerManager"
private const val NO_AUDIO_MESSAGE = "No audio available on this device"
private const val NO_ENGLISH_VOICE_MESSAGE = "English speech voice is not installed on this device"
private const val NO_ARABIC_VOICE_MESSAGE = "Arabic speech voice is not installed on this device"
private const val MANIFEST_REFRESH_INTERVAL_MS = 7L * 24L * 60L * 60L * 1000L

enum class Accent(val code: String) {
    US("us"),
    UK("uk"),
    AR("ar")
}

enum class AudioSourceKind(val label: String) {
    CACHE_OR_VOICE_PACK("Local Cache / Voice Pack"),
    HUMAN_RECORDING("Human (FreeDict / Wikimedia)"),
    KOKORO_OPUS("Kokoro-82M (.opus)"),
    DEVICE_TTS("Device TTS"),
    NONE("Unavailable")
}

data class AudioDebugEntry(
    val timestampMs: Long,
    val queryWord: String,
    val accent: Accent,
    val sourceKind: AudioSourceKind,
    val detail: String,
    val networkBytes: Long
)

class AudioPlayerManager(
    context: Context,
    private val okHttpClient: OkHttpClient,
    private val settingsPreferences: UserSettingsPreferences,
    private val wikimediaCommonsApi: WikimediaCommonsApi? = null,
    private val offlineVoicePackManager: OfflineVoicePackManager? = null
) {
    private val appContext = context.applicationContext
    private val audioCacheDir = File(appContext.filesDir, "audio_cache").apply {
        if (!exists()) mkdirs()
    }
    private val manifestCacheFile = File(appContext.filesDir, "voice-index.json.gz")
    private val manifestPrefs = appContext.getSharedPreferences("voice_manifest_prefs", Context.MODE_PRIVATE)
    private val failedAudioUrls = java.util.Collections.synchronizedSet(HashSet<String>())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exoPlayer: ExoPlayer? = null
    private var playbackJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsInitDeferred: CompletableDeferred<Boolean>? = null

    private val manifestLock = Any()
    @Volatile private var manifestLoaded = false
    @Volatile private var manifestUsWords: Set<String> = emptySet()
    @Volatile private var manifestUkWords: Set<String> = emptySet()

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

    private val _audioDebugHistory = MutableStateFlow<List<AudioDebugEntry>>(emptyList())
    val audioDebugHistory: StateFlow<List<AudioDebugEntry>> = _audioDebugHistory.asStateFlow()

    private val _lastAudioNetworkBytes = MutableStateFlow(0L)
    val lastAudioNetworkBytes: StateFlow<Long> = _lastAudioNetworkBytes.asStateFlow()

    private val _totalAudioNetworkBytes = MutableStateFlow(0L)
    val totalAudioNetworkBytes: StateFlow<Long> = _totalAudioNetworkBytes.asStateFlow()

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
        applySpeedToActiveExoPlayer(sanitized)
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
        stopExoPlayerOnly()
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
     * Strict 4-tier audio playback chain (< 1s, zero prefetch, no 404 requests):
     * (a) Disk cache or Offline Voice Pack (0 KB)
     * (b) Human recording (Free Dictionary API phonetics URL or Wikimedia Commons En-us/En-uk/En-gb/Ar)
     * (c) Pre-generated Kokoro-82M .opus (~5 KB) ONLY if listed in cached voice-index.json.gz manifest
     * (d) Device Android TextToSpeech (US / UK / AR)
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
        val effectiveAccent = if (ArabicTextUtils.containsArabicLetters(cleaned)) Accent.AR else accent
        val normalizedUrl = normalizeAudioUrl(audioUrl)
        stopPlayback()
        playbackJob = scope.launch {
            _playingKey.value = key
            var downloadedBytes = 0L
            try {
                // Tier (a): disk cache or installed Offline Voice Pack
                val cachedOrPack = withContext(Dispatchers.IO) {
                    findExistingWordCacheFile(cleaned, effectiveAccent)
                        ?: offlineVoicePackManager?.findClip(cleaned, effectiveAccent)
                }
                if (cachedOrPack != null && cachedOrPack.exists() && cachedOrPack.length() > 0L) {
                    recordAudioDebug(cleaned, effectiveAccent, AudioSourceKind.CACHE_OR_VOICE_PACK, cachedOrPack.name, 0L)
                    playLocalFile(
                        file = cachedOrPack,
                        onCodecOrPlayErrorFallback = {
                            launchTtsFallback(cleaned, effectiveAccent, key, "Codec error fallback")
                        }
                    )
                    return@launch
                }

                // Tier (b) & (c): Remote within 950ms budget
                val resolvedRemote: Pair<File, Pair<AudioSourceKind, Long>>? = withTimeoutOrNull(950L) {
                    withContext(Dispatchers.IO) {
                        // Tier (b1): direct human recording URL (Free Dictionary API / cached Wiktionary URL)
                        if (normalizedUrl != null && normalizedUrl !in failedAudioUrls) {
                            val dlTarget = cacheFileFor(cleaned, effectiveAccent, normalizedUrl)
                            val dl = downloadToCacheWithSize(normalizedUrl, dlTarget)
                            if (dl != null) {
                                return@withContext dl.first to (AudioSourceKind.HUMAN_RECORDING to dl.second)
                            }
                        }

                        // Tier (b2): Wikimedia Commons live lookup for single words / concise phrases
                        if (wikimediaCommonsApi != null && !cleaned.contains('\n') && cleaned.split(Regex("\\s+")).size <= 4) {
                            val wikiUrl = lookupWikimediaUrl(cleaned, effectiveAccent)
                            if (wikiUrl != null && wikiUrl !in failedAudioUrls) {
                                val dlTarget = cacheFileFor(cleaned, effectiveAccent, wikiUrl)
                                val dl = downloadToCacheWithSize(wikiUrl, dlTarget)
                                if (dl != null) {
                                    return@withContext dl.first to (AudioSourceKind.HUMAN_RECORDING to dl.second)
                                }
                            }
                        }

                        // Tier (c): Pre-generated Kokoro .opus gated strictly by local voice-index.json.gz manifest (no 404s!)
                        if (effectiveAccent != Accent.AR) {
                            val kokoroUrl = resolveKokoroUrlIfInManifest(cleaned, effectiveAccent)
                            if (kokoroUrl != null && kokoroUrl !in failedAudioUrls) {
                                val dlTarget = cacheFileFor(cleaned, effectiveAccent, kokoroUrl)
                                val dl = downloadToCacheWithSize(kokoroUrl, dlTarget)
                                if (dl != null) {
                                    return@withContext dl.first to (AudioSourceKind.KOKORO_OPUS to dl.second)
                                }
                            }
                        }

                        null
                    }
                }

                if (resolvedRemote != null) {
                    val file = resolvedRemote.first
                    val kind = resolvedRemote.second.first
                    downloadedBytes = resolvedRemote.second.second
                    recordAudioDebug(cleaned, effectiveAccent, kind, file.name, downloadedBytes)
                    playLocalFile(
                        file = file,
                        onCodecOrPlayErrorFallback = {
                            launchTtsFallback(cleaned, effectiveAccent, key, "Codec error -> TTS")
                        }
                    )
                    return@launch
                }

                // Tier (d): Device Android TextToSpeech
                playWordViaTts(cleaned, effectiveAccent, key)
            } catch (e: CancellationException) {
                _playingKey.value = null
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Audio playback failed for $cleaned (${effectiveAccent.code}), falling back to TTS", e)
                playWordViaTts(cleaned, effectiveAccent, key)
            }
        }
    }

    private fun launchTtsFallback(word: String, accent: Accent, key: String, reason: String) {
        Log.w(TAG, "$reason for '$word' (${accent.code})")
        playbackJob?.cancel()
        playbackJob = scope.launch {
            _playingKey.value = key
            playWordViaTts(word, accent, key)
        }
    }

    private suspend fun playWordViaTts(word: String, accent: Accent, key: String) {
        val ready = ensureTtsInitialized()
        if (!ready) {
            recordAudioDebug(word, accent, AudioSourceKind.NONE, "TTS engine unavailable", 0L)
            _playingKey.value = null
            emitMessage(if (accent == Accent.AR) NO_ARABIC_VOICE_MESSAGE else NO_ENGLISH_VOICE_MESSAGE)
            return
        }
        val isArabicText = accent == Accent.AR || ArabicTextUtils.containsArabicLetters(word)
        val available = withContext(Dispatchers.Default) {
            if (isArabicText) configureArabicTtsVoice() else configureBestEnglishVoice(accent)
        }
        if (!available) {
            recordAudioDebug(word, accent, AudioSourceKind.NONE, "No voice installed", 0L)
            _playingKey.value = null
            emitMessage(if (isArabicText) NO_ARABIC_VOICE_MESSAGE else NO_ENGLISH_VOICE_MESSAGE)
            return
        }
        recordAudioDebug(word, if (isArabicText) Accent.AR else accent, AudioSourceKind.DEVICE_TTS, "Android TTS", 0L)
        _playingKey.value = key
        speakSentenceChunks(word)
    }

    /**
     * Sentences/phrases/examples:
     * Uses the exact same chain: cache / explicit human URL / Android TTS (US, UK, or Arabic).
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

        val isArabic = ArabicTextUtils.containsArabicLetters(cleaned)
        val targetAccent = if (isArabic) Accent.AR else (accentOverride ?: settingsPreferences.sentenceAccent.value)
        stopPlayback()
        playbackJob = scope.launch {
            _playingKey.value = key
            try {
                val normalizedUrl = normalizeAudioUrl(audioUrl)
                if (normalizedUrl != null) {
                    val fileAndBytes = withTimeoutOrNull(950L) {
                        withContext(Dispatchers.IO) {
                            val sentenceFile = sentenceCacheFileFor(normalizedUrl)
                            if (sentenceFile.exists() && sentenceFile.length() > 0L) {
                                sentenceFile to 0L
                            } else {
                                downloadToCacheWithSize(normalizedUrl, sentenceFile)
                            }
                        }
                    }
                    if (fileAndBytes != null && fileAndBytes.first.exists() && fileAndBytes.first.length() > 0L) {
                        val kind = if (fileAndBytes.second == 0L) AudioSourceKind.CACHE_OR_VOICE_PACK else AudioSourceKind.HUMAN_RECORDING
                        recordAudioDebug(cleaned.take(40), targetAccent, kind, fileAndBytes.first.name, fileAndBytes.second)
                        playLocalFile(
                            file = fileAndBytes.first,
                            onCodecOrPlayErrorFallback = {
                                scope.launch {
                                    _playingKey.value = key
                                    playSentenceViaTts(cleaned, targetAccent)
                                }
                            }
                        )
                        return@launch
                    } else {
                        Log.w(TAG, "Sentence human audio download unavailable ($normalizedUrl), falling back to TTS")
                    }
                }
                playSentenceViaTts(cleaned, targetAccent)
            } catch (e: CancellationException) {
                _playingKey.value = null
                _ttsProgress.value = null
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sentence playback failed", e)
                recordAudioDebug(cleaned.take(40), targetAccent, AudioSourceKind.NONE, e.message ?: "Error", 0L)
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
            recordAudioDebug(cleaned.take(40), targetAccent, AudioSourceKind.NONE, "TTS unavailable", 0L)
            _playingKey.value = null
            _ttsProgress.value = null
            emitMessage(if (targetAccent == Accent.AR) NO_ARABIC_VOICE_MESSAGE else NO_ENGLISH_VOICE_MESSAGE)
            return
        }

        val isArabicText = targetAccent == Accent.AR || ArabicTextUtils.containsArabicLetters(cleaned)
        val available = withContext(Dispatchers.Default) {
            if (isArabicText) configureArabicTtsVoice() else configureBestEnglishVoice(targetAccent)
        }
        if (!available) {
            Log.e(TAG, "No supported TTS voice/language installed (isArabic=$isArabicText)")
            recordAudioDebug(cleaned.take(40), targetAccent, AudioSourceKind.NONE, "No TTS voice", 0L)
            _playingKey.value = null
            _ttsProgress.value = null
            emitMessage(if (isArabicText) NO_ARABIC_VOICE_MESSAGE else NO_ENGLISH_VOICE_MESSAGE)
            return
        }

        recordAudioDebug(cleaned.take(40), if (isArabicText) Accent.AR else targetAccent, AudioSourceKind.DEVICE_TTS, "Android TTS", 0L)
        val continuousSentence = cleaned.replace(Regex("\\s+"), " ").trim()
        speakSentenceChunks(continuousSentence, splitOnPunctuation = false)
    }

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

        val isArabic = ArabicTextUtils.containsArabicLetters(cleaned)
        val targetAccent = if (isArabic) Accent.AR else (accentOverride ?: settingsPreferences.sentenceAccent.value)

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
        syncReaderService(startForeground = fromService)
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
        syncReaderService(startForeground = fromService)
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
        syncReaderService(startForeground = fromService)
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
                    if (state.isArabic || state.accent == Accent.AR) {
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

        val missingLabel = if (preferredAccent == Accent.UK) "UK" else "US"
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

    fun hasAudioOrCache(word: String, accent: Accent, audioUrl: String?): Boolean {
        val normalized = normalizeAudioUrl(audioUrl)
        if (normalized != null && normalized !in failedAudioUrls) return true
        return findExistingWordCacheFile(word, accent) != null ||
            offlineVoicePackManager?.findClip(word, accent) != null
    }

    /**
     * Resolves expected audio source tier strictly offline/from manifest & metadata without making speculative 404 requests.
     */
    fun classifyAudioSource(
        word: String,
        accent: Accent,
        knownHumanUrl: String? = null
    ): AudioSourceKind {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return AudioSourceKind.NONE
        val effectiveAccent = if (ArabicTextUtils.containsArabicLetters(cleaned)) Accent.AR else accent
        if (findExistingWordCacheFile(cleaned, effectiveAccent) != null ||
            offlineVoicePackManager?.findClip(cleaned, effectiveAccent) != null
        ) {
            return AudioSourceKind.CACHE_OR_VOICE_PACK
        }
        val normUrl = normalizeAudioUrl(knownHumanUrl)
        if (normUrl != null && normUrl !in failedAudioUrls) {
            return AudioSourceKind.HUMAN_RECORDING
        }
        if (effectiveAccent != Accent.AR && resolveKokoroUrlIfInManifest(cleaned, effectiveAccent) != null) {
            return AudioSourceKind.KOKORO_OPUS
        }
        return AudioSourceKind.DEVICE_TTS
    }

    fun seedVoiceManifestForTest(usWords: Set<String>, ukWords: Set<String>) {
        synchronized(manifestLock) {
            manifestUsWords = usWords.map { it.lowercase(Locale.US) }.toSet()
            manifestUkWords = ukWords.map { it.lowercase(Locale.US) }.toSet()
            manifestLoaded = true
        }
    }

    private suspend fun lookupWikimediaUrl(word: String, accent: Accent): String? {
        val api = wikimediaCommonsApi ?: return null
        val titles = WikimediaCommonsApi.buildCandidateTitles(word, accent)
        if (titles.isNotBlank()) {
            val fromTitles = runCatching {
                val raw = api.getImageInfoByTitles(titles = titles).string()
                WikimediaCommonsApi.parseImageInfoAudioUrl(raw)
            }.getOrNull()
            if (fromTitles != null) return fromTitles
        }
        val prefixes = WikimediaCommonsApi.buildAllImagesPrefixes(word, accent)
        for (prefix in prefixes) {
            val found = runCatching {
                val raw = api.searchAudioFiles(prefix = prefix, limit = 4).string()
                WikimediaCommonsApi.parseAllImagesAudioUrl(raw)
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    private fun resolveKokoroUrlIfInManifest(word: String, accent: Accent): String? {
        if (accent == Accent.AR) return null
        val lower = word.trim().lowercase(Locale.US)
        if (lower.isEmpty() || !lower.all { it in 'a'..'z' }) return null
        ensureVoiceManifestLoaded()
        val available = when (accent) {
            Accent.US -> lower in manifestUsWords
            Accent.UK -> lower in manifestUkWords
            Accent.AR -> false
        }
        if (!available) return null
        val pagesBase = deriveGitHubPagesBaseUrl() ?: return null
        return "$pagesBase/clips/${accent.code}/$lower.opus"
    }

    private fun ensureVoiceManifestLoaded() {
        if (manifestLoaded) return
        synchronized(manifestLock) {
            if (manifestLoaded) return
            if (manifestCacheFile.exists() && manifestCacheFile.length() > 0L) {
                runCatching {
                    parseManifestGzipFile(manifestCacheFile)
                }.onFailure {
                    Log.w(TAG, "Failed reading cached voice-index.json.gz", it)
                }
            }
            manifestLoaded = true
        }
        val lastFetched = manifestPrefs.getLong("last_manifest_fetch_ms", 0L)
        val now = System.currentTimeMillis()
        if ((now - lastFetched) > MANIFEST_REFRESH_INTERVAL_MS && deriveGitHubPagesBaseUrl() != null) {
            scope.launch(Dispatchers.IO) {
                refreshManifestSilently()
            }
        }
    }

    private fun refreshManifestSilently() {
        val baseUrl = deriveGitHubPagesBaseUrl() ?: return
        val manifestUrl = "$baseUrl/voice-index.json.gz"
        val tmp = File(appContext.cacheDir, "voice-index.json.gz.tmp")
        runCatching {
            val req = Request.Builder()
                .url(manifestUrl)
                .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val body = resp.body ?: return
                FileOutputStream(tmp).use { out ->
                    body.byteStream().copyTo(out)
                    out.flush()
                }
            }
            if (tmp.exists() && tmp.length() > 0L) {
                tmp.copyTo(manifestCacheFile, overwrite = true)
                tmp.delete()
                manifestPrefs.edit().putLong("last_manifest_fetch_ms", System.currentTimeMillis()).apply()
                synchronized(manifestLock) {
                    parseManifestGzipFile(manifestCacheFile)
                }
            }
        }.onFailure {
            tmp.delete()
            Log.d(TAG, "Voice manifest not fetched (${it.message})")
        }
    }

    private fun parseManifestGzipFile(file: File) {
        val raw = GZIPInputStream(FileInputStream(file)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(raw)
        val usArr = root.optJSONArray("us")
        val ukArr = root.optJSONArray("uk")
        val usSet = HashSet<String>(usArr?.length() ?: 0)
        val ukSet = HashSet<String>(ukArr?.length() ?: 0)
        if (usArr != null) {
            for (i in 0 until usArr.length()) {
                val w = usArr.optString(i).trim().lowercase(Locale.US)
                if (w.isNotEmpty()) usSet.add(w)
            }
        }
        if (ukArr != null) {
            for (i in 0 until ukArr.length()) {
                val w = ukArr.optString(i).trim().lowercase(Locale.US)
                if (w.isNotEmpty()) ukSet.add(w)
            }
        }
        manifestUsWords = usSet
        manifestUkWords = ukSet
    }

    private fun deriveGitHubPagesBaseUrl(): String? {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank() || repo == "unknown/unknown" || !repo.contains("/")) return null
        val parts = repo.split('/', limit = 2)
        val owner = parts[0].trim()
        val name = parts[1].trim()
        if (owner.isEmpty() || name.isEmpty()) return null
        return "https://$owner.github.io/$name"
    }

    private fun recordAudioDebug(
        query: String,
        accent: Accent,
        sourceKind: AudioSourceKind,
        detail: String,
        bytes: Long
    ) {
        val entry = AudioDebugEntry(
            timestampMs = System.currentTimeMillis(),
            queryWord = query,
            accent = accent,
            sourceKind = sourceKind,
            detail = detail,
            networkBytes = bytes
        )
        _lastAudioNetworkBytes.value = bytes
        if (bytes > 0L) {
            _totalAudioNetworkBytes.value = _totalAudioNetworkBytes.value + bytes
        }
        val updated = (listOf(entry) + _audioDebugHistory.value).take(20)
        _audioDebugHistory.value = updated
    }

    @OptIn(UnstableApi::class)
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "ExoPlayer requires MainLooper; falling back")
            onCodecOrPlayErrorFallback?.invoke() ?: run {
                _playingKey.value = null
                emitMessage(NO_AUDIO_MESSAGE)
            }
            return
        }
        try {
            stopExoPlayerOnly()
            val speed = settingsPreferences.audioSpeed.value
            val player = ExoPlayer.Builder(appContext).build()
            exoPlayer = player
            val attrs = Media3AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(C.USAGE_MEDIA)
                .build()
            player.setAudioAttributes(attrs, true)
            player.playbackParameters = PlaybackParameters(speed)
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _playingKey.value = null
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "ExoPlayer error (${error.errorCodeName}) for ${file.name}", error)
                    runCatching {
                        if (file.parentFile?.name == "audio_cache") file.delete()
                    }
                    stopExoPlayerOnly()
                    if (onCodecOrPlayErrorFallback != null) {
                        onCodecOrPlayErrorFallback()
                    } else {
                        _playingKey.value = null
                        emitMessage(NO_AUDIO_MESSAGE)
                    }
                }
            })
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            player.play()
        } catch (e: Throwable) {
            Log.e(TAG, "ExoPlayer setup failed for file ${file.name}", e)
            stopExoPlayerOnly()
            if (onCodecOrPlayErrorFallback != null) {
                onCodecOrPlayErrorFallback()
            } else {
                _playingKey.value = null
                emitMessage(NO_AUDIO_MESSAGE)
            }
        }
    }

    private fun applySpeedToActiveExoPlayer(speed: Float) {
        val player = exoPlayer ?: return
        runCatching {
            player.playbackParameters = PlaybackParameters(speed)
        }.onFailure {
            Log.w(TAG, "Active ExoPlayer speed change ignored", it)
        }
    }

    private fun downloadToCacheWithSize(url: String, targetFile: File): Pair<File, Long>? {
        if (url in failedAudioUrls) return null
        val tempFile = File(audioCacheDir, "${targetFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Al-Makhzanji-Dictionary/1.0 (Android)")
                .build()

            var bytesCopied = 0L
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404 || response.code == 410) {
                        failedAudioUrls.add(url)
                    }
                    Log.w(TAG, "HTTP ${response.code} downloading audio: $url")
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
                    bytesCopied = body.byteStream().copyTo(out)
                    out.flush()
                }
            }
            if (tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                targetFile to bytesCopied
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio download skipped for $url: ${e.message}")
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
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> null
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
            path.endsWith(".opus") -> "opus"
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

    private fun stopExoPlayerOnly() {
        runCatching { exoPlayer?.stop() }
        runCatching { exoPlayer?.release() }
        exoPlayer = null
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
        private val SUPPORTED_CACHE_EXTENSIONS = listOf("opus", "mp3", "ogg", "oga", "wav", "flac", "m4a")

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
