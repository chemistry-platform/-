package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.OfflinePackManager
import com.example.core.OfflinePackState
import com.example.data.repository.LexiconRepository
import com.example.domain.model.LanguageDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: LexiconRepository,
    private val offlinePackManager: OfflinePackManager? = null
) : ViewModel() {

    private val _languageDirection = MutableStateFlow(LanguageDirection.EN_TO_AR)
    val languageDirection: StateFlow<LanguageDirection> = _languageDirection.asStateFlow()

    val offlinePackState: StateFlow<OfflinePackState>? = offlinePackManager?.state

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = true
        )

    private val _openWordEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openWordEvent: SharedFlow<String> = _openWordEvent.asSharedFlow()

    private var openWordJob: Job? = null

    fun swapLanguages() {
        _languageDirection.value = when (_languageDirection.value) {
            LanguageDirection.EN_TO_AR -> LanguageDirection.AR_TO_EN
            LanguageDirection.AR_TO_EN -> LanguageDirection.EN_TO_AR
        }
    }

    fun requestOfflinePackDownload() {
        offlinePackManager?.startDownload()
    }

    fun openWordReady(word: String) {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return
        openWordJob?.cancel()
        openWordJob = viewModelScope.launch {
            repository.ensureWordReadyForOpen(cleaned, timeoutMs = 1450L)
            _openWordEvent.emit(cleaned)
        }
    }

    class Factory(
        private val repository: LexiconRepository,
        private val offlinePackManager: OfflinePackManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, offlinePackManager) as T
        }
    }
}
