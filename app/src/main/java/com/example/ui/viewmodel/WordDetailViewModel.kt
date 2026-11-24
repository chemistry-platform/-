package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.TranslationManager
import com.example.data.repository.LexiconRepository
import com.example.data.repository.WordLoadState
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.WordEntry
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WordDetailViewModel(
    private val repository: LexiconRepository,
    private val translationManager: TranslationManager,
    initialQuery: String,
    private val fallbackBaseWord: String? = null,
    private val initialHighlightChip: String? = null
) : ViewModel() {

    private val _currentQuery = MutableStateFlow(initialQuery)
    val currentQuery: StateFlow<String> = _currentQuery.asStateFlow()

    private val _uiState = MutableStateFlow<WordLoadState>(WordLoadState.Loading)
    val uiState: StateFlow<WordLoadState> = _uiState.asStateFlow()

    private val _highlightedChip = MutableStateFlow<String?>(null)
    val highlightedChip: StateFlow<String?> = _highlightedChip.asStateFlow()

    private val _confusableDetails = MutableStateFlow<Map<String, ConfusableWordDetail>>(emptyMap())
    val confusableDetails: StateFlow<Map<String, ConfusableWordDetail>> = _confusableDetails.asStateFlow()

    private val _isLoadingConfusables = MutableStateFlow(false)
    val isLoadingConfusables: StateFlow<Boolean> = _isLoadingConfusables.asStateFlow()

    private val _arabicTranslations = MutableStateFlow<Map<String, String>>(emptyMap())
    val arabicTranslations: StateFlow<Map<String, String>> = _arabicTranslations.asStateFlow()

    private val _missingLanguagePackBanner = MutableStateFlow(false)
    val missingLanguagePackBanner: StateFlow<Boolean> = _missingLanguagePackBanner.asStateFlow()

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

    private var loadJob: Job? = null
    private var confusableLoadedForWord: String? = null

    init {
        loadWord(
            query = initialQuery,
            forceRefresh = false,
            fallbackWord = fallbackBaseWord,
            highlightChip = initialHighlightChip
        )
    }

    fun loadWord(
        query: String,
        forceRefresh: Boolean = false,
        fallbackWord: String? = null,
        highlightChip: String? = null
    ) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        _currentQuery.value = cleaned
        _confusableDetails.value = emptyMap()
        _isLoadingConfusables.value = false
        _arabicTranslations.value = emptyMap()
        _missingLanguagePackBanner.value = false
        confusableLoadedForWord = null

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            var switchedToFallback = false
            repository.observeWordEntry(cleaned, forceRefresh = forceRefresh).collect { state ->
                when (state) {
                    is WordLoadState.Loading -> {
                        _uiState.value = WordLoadState.Loading
                    }
                    is WordLoadState.Success -> {
                        _activeEntryWord.value = state.entry.word
                        _uiState.value = state
                    }
                    is WordLoadState.Error -> {
                        if (!switchedToFallback && !fallbackWord.isNullOrBlank()) {
                            switchedToFallback = true
                            _highlightedChip.value = highlightChip ?: cleaned
                            _currentQuery.value = fallbackWord
                            repository.observeWordEntry(fallbackWord, forceRefresh = false)
                                .collect { fbState ->
                                    if (fbState is WordLoadState.Success) {
                                        _activeEntryWord.value = fbState.entry.word
                                    }
                                    _uiState.value = fbState
                                }
                        } else {
                            _uiState.value = state
                        }
                    }
                }
            }
        }
    }

    fun clearHighlightedChip() {
        _highlightedChip.value = null
    }

    fun loadConfusablesIfNeeded() {
        val entry = (_uiState.value as? WordLoadState.Success)?.entry ?: return
        if (entry.confusableCandidates.isEmpty()) return
        if (confusableLoadedForWord == entry.word || _isLoadingConfusables.value) return

        confusableLoadedForWord = entry.word
        viewModelScope.launch {
            _isLoadingConfusables.value = true
            try {
                val loaded = repository.loadConfusableDetails(entry.confusableCandidates)
                _confusableDetails.value = loaded
            } finally {
                _isLoadingConfusables.value = false
            }
        }
    }

    fun toggleArabicHelp(itemKey: String, englishText: String) {
        val currentMap = _arabicTranslations.value
        if (currentMap.containsKey(itemKey)) {
            _arabicTranslations.value = currentMap - itemKey
            return
        }
        viewModelScope.launch {
            val result = translationManager.translateShortIfReady(
                text = englishText,
                sourceCode = TranslateLanguage.ENGLISH,
                targetCode = TranslateLanguage.ARABIC
            )
            val translated = result.getOrNull()
            if (!translated.isNullOrBlank()) {
                _missingLanguagePackBanner.value = false
                _arabicTranslations.value = _arabicTranslations.value + (itemKey to translated)
            } else {
                _missingLanguagePackBanner.value = true
            }
        }
    }

    fun dismissMissingPackBanner() {
        _missingLanguagePackBanner.value = false
    }

    fun toggleFavorite() {
        val entry: WordEntry = (_uiState.value as? WordLoadState.Success)?.entry ?: return
        viewModelScope.launch {
            repository.toggleFavorite(entry, isFavorite.value)
        }
    }

    class Factory(
        private val repository: LexiconRepository,
        private val translationManager: TranslationManager,
        private val initialQuery: String,
        private val fallbackBaseWord: String? = null,
        private val highlightChip: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WordDetailViewModel(
                repository = repository,
                translationManager = translationManager,
                initialQuery = initialQuery,
                fallbackBaseWord = fallbackBaseWord,
                initialHighlightChip = highlightChip
            ) as T
        }
    }
}
