package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ArabicTextUtils
import com.example.data.local.SearchHistoryEntity
import com.example.data.repository.LexiconRepository
import com.example.domain.model.InstantTranslationResult
import com.example.domain.model.LanguageDirection
import com.example.domain.model.SearchSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SearchSuggestion>> = _suggestions.asStateFlow()

    private val _hasMoreSuggestions = MutableStateFlow(false)
    val hasMoreSuggestions: StateFlow<Boolean> = _hasMoreSuggestions.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _submittedQuery = MutableStateFlow<String?>(null)
    val isSearchSubmitted: StateFlow<Boolean> = combine(_submittedQuery, _query) { sub, q ->
        val trimmed = q.trim()
        !sub.isNullOrBlank() && sub == trimmed && trimmed.isNotEmpty()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = false
    )

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

    init {
        viewModelScope.launch {
            _query
                .debounce(80L)
                .flatMapLatest { raw ->
                    flow {
                        val trimmed = raw.trim()
                        if (trimmed.isEmpty()) {
                            _isLoading.value = false
                            _hasMoreSuggestions.value = false
                            emit(emptyList<SearchSuggestion>() to false)
                        } else {
                            _isLoading.value = true
                            try {
                                val page = repository.getLiveSuggestionsPaged(
                                    query = trimmed,
                                    offset = 0,
                                    limit = PAGE_SIZE
                                )
                                page.items.firstOrNull()?.word?.let { topWord ->
                                    if (!page.items.first().isIdiom) {
                                        repository.prefetchWord(topWord)
                                    }
                                }
                                emit(page.items to page.hasMore)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                emit(emptyList<SearchSuggestion>() to false)
                            } finally {
                                _isLoading.value = false
                            }
                        }
                    }
                }
                .collect { (items, hasMore) ->
                    if (_query.value.trim().isEmpty()) {
                        _suggestions.value = emptyList()
                        _hasMoreSuggestions.value = false
                    } else {
                        _suggestions.value = items
                        _hasMoreSuggestions.value = hasMore
                    }
                    _isLoadingMore.value = false
                }
        }
    }

    fun loadMoreSuggestions() {
        val trimmed = _query.value.trim()
        if (trimmed.isEmpty() || _isLoadingMore.value || !_hasMoreSuggestions.value) return
        val offset = _suggestions.value.size
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val page = repository.getLiveSuggestionsPaged(
                    query = trimmed,
                    offset = offset,
                    limit = PAGE_SIZE
                )
                if (_query.value.trim() == trimmed) {
                    val existingKeys = _suggestions.value.mapTo(HashSet()) { it.word.lowercase() }
                    val appended = page.items.filter { it.word.lowercase() !in existingKeys }
                    _suggestions.value = _suggestions.value + appended
                    _hasMoreSuggestions.value = page.hasMore && appended.isNotEmpty()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _hasMoreSuggestions.value = false
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    // Live summary/translation card shown ONLY when user taps the Search icon or submits search
    val instantTranslation: StateFlow<InstantTranslationResult?> = combine(_submittedQuery, _query) { sub, q ->
        val trimmed = q.trim()
        if (sub != null && sub == trimmed && trimmed.isNotEmpty()) trimmed else null
    }
        .distinctUntilChanged()
        .flatMapLatest { submitted ->
            flow {
                if (submitted == null) {
                    emit(null)
                } else {
                    val isArabic = ArabicTextUtils.containsArabicLetters(submitted)
                    emit(
                        InstantTranslationResult(
                            query = submitted,
                            isArabicSource = isArabic,
                            isLoading = true
                        )
                    )
                    try {
                        emit(repository.instantTranslateForSearch(submitted))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emit(
                            InstantTranslationResult(
                                query = submitted,
                                isArabicSource = isArabic,
                                isLoading = false
                            )
                        )
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    fun submitSearch() {
        val trimmed = _query.value.trim()
        if (trimmed.isNotEmpty()) {
            _submittedQuery.value = trimmed
        }
    }

    fun onQueryChange(newValue: String) {
        _query.value = newValue
        if (newValue.trim() != _submittedQuery.value) {
            _submittedQuery.value = null
        }
        if (newValue.isNotBlank()) {
            // Section 15.1: Auto-detect direction from script
            _direction.value = if (ArabicTextUtils.containsArabicLetters(newValue)) {
                LanguageDirection.AR_TO_EN
            } else {
                LanguageDirection.EN_TO_AR
            }
        } else {
            _submittedQuery.value = null
            _isLoading.value = false
            _isLoadingMore.value = false
            _hasMoreSuggestions.value = false
            _suggestions.value = emptyList()
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

    companion object {
        const val PAGE_SIZE = 50
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
