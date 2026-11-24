package com.example.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val key: String, val labelAr: String) {
    LIGHT("light", "فاتح"),
    DARK("dark", "داكن"),
    SYSTEM("system", "حسب النظام");

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
