package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LexiconRepository
import com.example.domain.model.LanguageDirection
import com.example.domain.model.SearchSuggestion
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class QuickTranslateViewModel(
    private val repository: LexiconRepository,
    initialQuery: String,
    initialDirection: LanguageDirection
) : ViewModel() {

    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    private val _direction = MutableStateFlow(initialDirection)
    val direction: StateFlow<LanguageDirection> = _direction.asStateFlow()

    private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SearchSuggestion>> = _suggestions.asStateFlow()

    private val _sentenceTranslation = MutableStateFlow<String?>(null)
    val sentenceTranslation: StateFlow<String?> = _sentenceTranslation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = true
        )

    init {
        viewModelScope.launch {
            combine(_query.debounce(280L), _direction) { q, dir -> q to dir }
                .collectLatest { (q, dir) ->
                    performLiveLookup(q, dir)
                }
        }
    }

    fun onQueryChange(newValue: String) {
        _query.value = newValue
        if (newValue.isBlank()) {
            _suggestions.value = emptyList()
            _sentenceTranslation.value = null
            _errorMessage.value = null
            _isLoading.value = false
        }
    }

    fun swapLanguages() {
        _direction.value = when (_direction.value) {
            LanguageDirection.EN_TO_AR -> LanguageDirection.AR_TO_EN
            LanguageDirection.AR_TO_EN -> LanguageDirection.EN_TO_AR
        }
    }

    private suspend fun performLiveLookup(rawQuery: String, dir: LanguageDirection) {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            _suggestions.value = emptyList()
            _sentenceTranslation.value = null
            _isLoading.value = false
            return
        }

        _isLoading.value = true
        _errorMessage.value = null

        val effectiveDir = if (trimmed.any { it in '\u0600'..'\u06FF' }) {
            LanguageDirection.AR_TO_EN
        } else {
            dir
        }

        val wordCount = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val isSentence = wordCount >= 2 || trimmed.length > 24

        if (isSentence) {
            val translated = repository.translateSentence(trimmed, effectiveDir)
            _sentenceTranslation.value = translated.getOrNull()
            if (translated.isFailure) {
                _errorMessage.value = "تعذر تحميل الترجمة الفورية. تحقق من الشبكة أو قم بتحميل الحزمة المحلية من قسم أكثر."
            }
        } else {
            _sentenceTranslation.value = null
        }

        val list = runCatching { repository.getLiveSuggestions(trimmed, effectiveDir) }
            .getOrDefault(emptyList())
        _suggestions.value = list
        _isLoading.value = false
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
