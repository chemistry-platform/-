package com.example.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AudioSpeedOptions {
    val ALL = listOf(0.75f, 1.0f, 1.25f, 1.5f)

    fun formatLabel(speed: Float): String = when (speed) {
        0.75f -> "0.75x"
        1.25f -> "1.25x"
        1.5f -> "1.5x"
        else -> "1x"
    }

    fun sanitize(raw: Float): Float {
        return ALL.minByOrNull { kotlin.math.abs(it - raw) } ?: 1.0f
    }
}

/**
 * SharedPreferences-backed user preferences for audio & Arabic-first display (Sections 19.4, 19.5, 19.8, 19.9).
 * Uses SharedPreferences directly (no DataStore).
 */
class UserSettingsPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("app_user_settings", Context.MODE_PRIVATE)

    private val _audioSpeed = MutableStateFlow(
        AudioSpeedOptions.sanitize(prefs.getFloat(KEY_AUDIO_SPEED, 1.0f))
    )
    val audioSpeed: StateFlow<Float> = _audioSpeed.asStateFlow()

    private val _sentenceAccent = MutableStateFlow(
        Accent.entries.firstOrNull { it.code == prefs.getString(KEY_SENTENCE_ACCENT, Accent.US.code) }
            ?: Accent.US
    )
    val sentenceAccent: StateFlow<Accent> = _sentenceAccent.asStateFlow()

    private val _showArabicTranslations = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_ARABIC_TRANSLATIONS, true)
    )
    val showArabicTranslations: StateFlow<Boolean> = _showArabicTranslations.asStateFlow()

    private val _showMachineTranslationLabel = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_MACHINE_LABEL, true)
    )
    val showMachineTranslationLabel: StateFlow<Boolean> = _showMachineTranslationLabel.asStateFlow()

    fun setAudioSpeed(speed: Float) {
        val valid = AudioSpeedOptions.sanitize(speed)
        prefs.edit().putFloat(KEY_AUDIO_SPEED, valid).apply()
        _audioSpeed.value = valid
    }

    fun setSentenceAccent(accent: Accent) {
        prefs.edit().putString(KEY_SENTENCE_ACCENT, accent.code).apply()
        _sentenceAccent.value = accent
    }

    fun setShowArabicTranslations(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_ARABIC_TRANSLATIONS, show).apply()
        _showArabicTranslations.value = show
    }

    fun setShowMachineTranslationLabel(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_MACHINE_LABEL, show).apply()
        _showMachineTranslationLabel.value = show
    }

    private companion object {
        const val KEY_AUDIO_SPEED = "audio_speed"
        const val KEY_SENTENCE_ACCENT = "sentence_accent"
        const val KEY_SHOW_ARABIC_TRANSLATIONS = "show_arabic_translations"
        const val KEY_SHOW_MACHINE_LABEL = "show_machine_translation_label"
    }
}
