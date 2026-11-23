package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LexiconRepository
import com.example.data.repository.WordLoadResult
import com.example.domain.model.ArabicSearchResolution
import com.example.domain.model.LanguageDirection
import com.example.domain.model.WordEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WordDetailTab {
    SUMMARY,
    DICTIONARY,
    WORDNET
}

sealed interface WordDetailUiState {
    data object Loading : WordDetailUiState
    data class Success(
        val entry: WordEntry,
        val arabicResolution: ArabicSearchResolution? = null
    ) : WordDetailUiState
    data class Error(
        val message: String,
        val isOffline: Boolean = false
    ) : WordDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class WordDetailViewModel(
    private val repository: LexiconRepository,
    initialQuery: String
) : ViewModel() {

    private val _currentQuery = MutableStateFlow(initialQuery)
    val currentQuery: StateFlow<String> = _currentQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(WordDetailTab.SUMMARY)
    val selectedTab: StateFlow<WordDetailTab> = _selectedTab.asStateFlow()

    private val _languageDirection = MutableStateFlow(LanguageDirection.EN_TO_AR)
    val languageDirection: StateFlow<LanguageDirection> = _languageDirection.asStateFlow()

    private val _uiState = MutableStateFlow<WordDetailUiState>(WordDetailUiState.Loading)
    val uiState: StateFlow<WordDetailUiState> = _uiState.asStateFlow()

    private val _activeEntryWord = MutableStateFlow(initialQuery)

    val isFavorite: StateFlow<Boolean> = _activeEntryWord
        .flatMapLatest { word ->
            if (word.isBlank()) flowOf(false) else repository.observeIsFavorite(word)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )

    init {
        loadWord(initialQuery)
    }

    fun selectTab(tab: WordDetailTab) {
        _selectedTab.value = tab
    }

    fun swapLanguages() {
        _languageDirection.value = when (_languageDirection.value) {
            LanguageDirection.EN_TO_AR -> LanguageDirection.AR_TO_EN
            LanguageDirection.AR_TO_EN -> LanguageDirection.EN_TO_AR
        }
    }

    fun loadWord(query: String, forceRefresh: Boolean = false) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        _currentQuery.value = cleaned
        viewModelScope.launch {
            _uiState.value = WordDetailUiState.Loading
            when (val result = repository.lookupQuery(cleaned, forceRefresh = forceRefresh)) {
                is WordLoadResult.Success -> {
                    _activeEntryWord.value = result.entry.word
                    _uiState.value = WordDetailUiState.Success(entry = result.entry)
                }
                is WordLoadResult.ArabicResolved -> {
                    val primary = result.primaryEntry
                    if (primary != null) {
                        _activeEntryWord.value = primary.word
                        _uiState.value = WordDetailUiState.Success(
                            entry = primary,
                            arabicResolution = result.resolution
                        )
                    } else {
                        _uiState.value = WordDetailUiState.Error(
                            message = "لم يتم العثور على مداخل قاموسية مطابقة للكلمة العربية \"${result.resolution.arabicQuery}\""
                        )
                    }
                }
                is WordLoadResult.Error -> {
                    _uiState.value = WordDetailUiState.Error(
                        message = result.message,
                        isOffline = result.isOffline
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value as? WordDetailUiState.Success ?: return
        viewModelScope.launch {
            repository.toggleFavorite(state.entry, isFavorite.value)
        }
    }

    class Factory(
        private val repository: LexiconRepository,
        private val initialQuery: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WordDetailViewModel(repository, initialQuery) as T
        }
    }
}
