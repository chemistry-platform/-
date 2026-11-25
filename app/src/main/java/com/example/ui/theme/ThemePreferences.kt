package com.example.ui.theme

import android.content.Context
import com.example.core.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val key: String, val labelAr: String, val labelEn: String) {
    LIGHT("light", "فاتح", "Light"),
    DARK("dark", "داكن", "Dark"),
    SYSTEM("system", "حسب النظام", "System");

    fun labelFor(language: AppLanguage): String =
        if (language == AppLanguage.ARABIC) labelAr else labelEn

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

class ThemePreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromKey(prefs.getString("theme_mode", ThemeMode.SYSTEM.key))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.key).apply()
        _themeMode.value = mode
    }
}
