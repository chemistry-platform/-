package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ArabicTextUtils
import com.example.data.local.SearchHistoryEntity
import com.example.data.repository.LexiconRepository
import com.example.domain.model.LanguageDirection
import com.example.domain.model.SearchSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class QuickTranslateViewModel(
    private val repository: LexiconRepository,
    initialQuery: String,
    initialDirection: LanguageDirection
) : ViewModel() {

    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    private val _direction = MutableStateFlow(
        if (initialQuery.isNotBlank() && ArabicTextUtils.containsArabicLetters(initialQuery)) {
            LanguageDirection.AR_TO_EN
        } else {
            initialDirection
        }
    )
    val direction: StateFlow<LanguageDirection> = _direction.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = true
        )

    val recentHistory: StateFlow<List<SearchHistoryEntity>> = repository.recentHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    val suggestions: StateFlow<List<SearchSuggestion>> = _query
        .debounce(150L)
        .flatMapLatest { raw ->
            flow {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) {
                    _isLoading.value = false
                    emit(emptyList())
                } else {
                    _isLoading.value = true
                    try {
                        val list = repository.getLiveSuggestions(trimmed)
                        // Section 18.3: Prefetch top suggestion in the background
                        list.firstOrNull()?.word?.let { topWord ->
                            repository.prefetchWord(topWord)
                        }
                        emit(list)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emit(emptyList())
                    } finally {
                        _isLoading.value = false
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun onQueryChange(newValue: String) {
        _query.value = newValue
        if (newValue.isNotBlank()) {
            // Section 15.1: Auto-detect direction from script
            _direction.value = if (ArabicTextUtils.containsArabicLetters(newValue)) {
                LanguageDirection.AR_TO_EN
            } else {
                LanguageDirection.EN_TO_AR
            }
        } else {
            _isLoading.value = false
        }
    }

    fun swapLanguages() {
        _direction.value = when (_direction.value) {
            LanguageDirection.EN_TO_AR -> LanguageDirection.AR_TO_EN
            LanguageDirection.AR_TO_EN -> LanguageDirection.EN_TO_AR
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
        }
    }

    class Factory(
        private val repository: LexiconRepository,
        private val initialQuery: String,
        private val initialDirection: LanguageDirection
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QuickTranslateViewModel(repository, initialQuery, initialDirection) as T
        }
    }
}
