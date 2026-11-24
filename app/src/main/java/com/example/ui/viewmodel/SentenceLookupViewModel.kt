package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ArabicTextUtils
import com.example.data.repository.LexiconRepository
import com.example.domain.model.LanguageDirection
import com.example.domain.model.SentenceLookupResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SentenceLookupViewModel(
    private val repository: LexiconRepository,
    initialQuery: String
) : ViewModel() {

    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    private val _direction = MutableStateFlow(
        if (ArabicTextUtils.containsArabicLetters(initialQuery)) {
            LanguageDirection.AR_TO_EN
        } else {
            LanguageDirection.EN_TO_AR
        }
    )
    val direction: StateFlow<LanguageDirection> = _direction.asStateFlow()

    private val _state = MutableStateFlow(
        SentenceLookupResult(
            originalText = initialQuery.trim(),
            isArabicInput = ArabicTextUtils.containsArabicLetters(initialQuery),
            wordChips = ArabicTextUtils.extractWordTokens(initialQuery),
            isUpdating = true
        )
    )
    val state: StateFlow<SentenceLookupResult> = _state.asStateFlow()

    private var lookupJob: Job? = null

    init {
        lookupSentence(initialQuery)
    }

    fun lookupSentence(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        _query.value = cleaned
        _direction.value = if (ArabicTextUtils.containsArabicLetters(cleaned)) {
            LanguageDirection.AR_TO_EN
        } else {
            LanguageDirection.EN_TO_AR
        }

        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            repository.observeSentenceLookup(cleaned).collect { result ->
                _state.value = result
            }
        }
    }

    fun toggleDirectionManual() {
        _direction.value = when (_direction.value) {
            LanguageDirection.EN_TO_AR -> LanguageDirection.AR_TO_EN
            LanguageDirection.AR_TO_EN -> LanguageDirection.EN_TO_AR
        }
    }

    class Factory(
        private val repository: LexiconRepository,
        private val initialQuery: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SentenceLookupViewModel(repository, initialQuery) as T
        }
    }
}
