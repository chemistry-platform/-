package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.Accent
import com.example.core.AppUpdateManager
import com.example.core.AudioPlayerManager
import com.example.core.OfflinePackManager
import com.example.core.OfflinePackState
import com.example.core.UpdateUiState
import com.example.core.UserSettingsPreferences
import com.example.data.local.SearchHistoryEntity
import com.example.data.repository.LexiconRepository
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoreViewModel(
    private val repository: LexiconRepository,
    private val updateManager: AppUpdateManager,
    private val offlinePackManager: OfflinePackManager,
    private val themePreferences: ThemePreferences,
    private val settingsPreferences: UserSettingsPreferences,
    private val audioPlayerManager: AudioPlayerManager
) : ViewModel() {

    val cachedWordCount: StateFlow<Int> = repository.cachedCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0
        )

    val recentHistory: StateFlow<List<SearchHistoryEntity>> = repository.recentHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = true
        )

    val updateUiState: StateFlow<UpdateUiState> = updateManager.uiState
    val offlinePackState: StateFlow<OfflinePackState> = offlinePackManager.state
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode

    val audioSpeed: StateFlow<Float> = settingsPreferences.audioSpeed
    val sentenceAccent: StateFlow<Accent> = settingsPreferences.sentenceAccent
    val showArabicTranslations: StateFlow<Boolean> = settingsPreferences.showArabicTranslations
    val showMachineTranslationLabel: StateFlow<Boolean> = settingsPreferences.showMachineTranslationLabel
    val literalTranslation: StateFlow<Boolean> = settingsPreferences.literalTranslation

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        themePreferences.setThemeMode(mode)
    }

    fun setAudioSpeed(speed: Float) {
        audioPlayerManager.setAudioSpeed(speed)
    }

    fun setSentenceAccent(accent: Accent) {
        settingsPreferences.setSentenceAccent(accent)
    }

    fun setShowArabicTranslations(show: Boolean) {
        settingsPreferences.setShowArabicTranslations(show)
    }

    fun setShowMachineTranslationLabel(show: Boolean) {
        settingsPreferences.setShowMachineTranslationLabel(show)
    }

    fun setLiteralTranslation(literal: Boolean) {
        settingsPreferences.setLiteralTranslation(literal)
    }

    fun checkForUpdateNow() {
        updateManager.checkForUpdate(force = true)
    }

    fun startOfflinePackDownload() {
        offlinePackManager.startDownload()
    }

    fun cancelOfflinePackDownload() {
        offlinePackManager.cancelDownload()
    }

    fun deleteOfflinePack() {
        offlinePackManager.deletePack()
        _actionMessage.value = "تم حذف حزمة القاموس الأوفلاين"
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearApiCache()
            _actionMessage.value = "تم مسح ذاكرة التخزين المؤقت"
        }
    }

    fun deleteHistoryItem(queryKey: String) {
        viewModelScope.launch {
            repository.deleteHistoryItem(queryKey)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _actionMessage.value = "تم مسح سجل البحث"
        }
    }

    class Factory(
        private val repository: LexiconRepository,
        private val updateManager: AppUpdateManager,
        private val offlinePackManager: OfflinePackManager,
        private val themePreferences: ThemePreferences,
        private val settingsPreferences: UserSettingsPreferences,
        private val audioPlayerManager: AudioPlayerManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MoreViewModel(
                repository = repository,
                updateManager = updateManager,
                offlinePackManager = offlinePackManager,
                themePreferences = themePreferences,
                settingsPreferences = settingsPreferences,
                audioPlayerManager = audioPlayerManager
            ) as T
        }
    }
}
