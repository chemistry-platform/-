package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "AudioPlayerManager"

class AudioPlayerManager(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var mediaPlayer: MediaPlayer? = null

    private val _speakingKey = MutableStateFlow<String?>(null)
    val speakingKey: StateFlow<String?> = _speakingKey.asStateFlow()

    init {
        tts = TextToSpeech(appContext) { status ->
            isTtsReady = (status == TextToSpeech.SUCCESS)
            if (!isTtsReady) {
                Log.w(TAG, "TextToSpeech initialization failed with status=$status")
            }
        }
    }

    fun speakWord(text: String, audioUrl: String?, locale: Locale, key: String) {
        if (text.isBlank()) return
        _speakingKey.value = key
        if (!audioUrl.isNullOrBlank()) {
            playRemoteAudio(
                url = audioUrl,
                onFailure = { speakWithTts(text, locale, key) }
            )
        } else {
            speakWithTts(text, locale, key)
        }
    }

    fun speakSentence(text: String, locale: Locale, key: String) {
        if (text.isBlank()) return
        _speakingKey.value = key
        speakWithTts(text, locale, key)
    }

    fun speakArabic(text: String, key: String) {
        if (text.isBlank()) return
        _speakingKey.value = key
        speakWithTts(text, Locale("ar"), key)
    }

    private fun playRemoteAudio(url: String, onFailure: () -> Unit) {
        try {
            mediaPlayer?.release()
            val normalizedUrl = if (url.startsWith("//")) "https:$url" else url
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(normalizedUrl)
                setOnPreparedListener { player ->
                    player.start()
                }
                setOnCompletionListener {
                    _speakingKey.value = null
                }
                setOnErrorListener { _, _, _ ->
                    onFailure()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote audio playback failed, falling back to TTS", e)
            onFailure()
        }
    }

    private fun speakWithTts(text: String, locale: Locale, key: String) {
        val engine = tts
        if (engine == null || !isTtsReady) {
            _speakingKey.value = null
            return
        }
        try {
            val result = engine.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                if (locale.language == "en") {
                    engine.setLanguage(Locale.ENGLISH)
                }
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, key)
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak error", e)
        } finally {
            _speakingKey.value = null
        }
    }

    fun release() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}
