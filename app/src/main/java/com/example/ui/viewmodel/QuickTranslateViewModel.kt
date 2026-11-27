package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ArabicTextUtils
import com.example.core.OfflinePackManager
import com.example.core.OfflinePackState
import com.example.data.local.SearchHistoryEntity
import com.example.data.repository.LexiconRepository
import com.example.domain.model.InstantTranslationResult
import com.example.domain.model.LanguageDirection
import com.example.domain.model.LiveSearchPage
import com.example.domain.model.SearchSuggestion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class QuickTranslateViewModel(
    private val repository: LexiconRepository,
    initialQuery: String,
    initialDirection: LanguageDirection,
    private val offlinePackManager: OfflinePackManager? = null
) : ViewModel() {

    val offlinePackState: StateFlow<OfflinePackState>? = offlinePackManager?.state

    fun requestOfflinePackDownload() {
        offlinePackManager?.startDownload()
    }

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

    private val _isExpandedAll = MutableStateFlow(false)
    val isExpandedAll: StateFlow<Boolean> = _isExpandedAll.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var cachedFullQuery: String = ""
    private var cachedFullSuggestions: List<SearchSuggestion> = emptyList()

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
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) {
                        _isLoading.value = false
                        _hasMoreSuggestions.value = false
                        flowOf(Triple(trimmed, emptyList<SearchSuggestion>(), false))
                    } else {
                        _isLoading.value = true
                        repository.observeLiveSuggestions(
                            query = trimmed,
                            limit = INITIAL_PAGE_SIZE
                        ).onEach { page ->
                            page.items.firstOrNull()?.word?.let { topWord ->
                                if (!page.items.first().isIdiom) {
                                    repository.prefetchWord(topWord)
                                }
                            }
                            _isLoading.value = false
                        }.catch { e ->
                            if (e is CancellationException) throw e
                            _isLoading.value = false
                            emit(LiveSearchPage(emptyList(), hasMore = false))
                        }.let { sugFlow ->
                            kotlinx.coroutines.flow.flow {
                                sugFlow.collect { page ->
                                    emit(Triple(trimmed, page.items, page.hasMore))
                                }
                            }
                        }
                    }
                }
                .collect { (activeQuery, items, hasMore) ->
                    val currentTrimmed = _query.value.trim()
                    if (currentTrimmed.isEmpty() || currentTrimmed != activeQuery) {
                        return@collect
                    }
                    if (_isExpandedAll.value && cachedFullQuery == currentTrimmed && cachedFullSuggestions.isNotEmpty()) {
                        _suggestions.value = cachedFullSuggestions
                        _hasMoreSuggestions.value = false
                    } else {
                        _suggestions.value = items
                        _hasMoreSuggestions.value = hasMore
                        _isExpandedAll.value = false
                    }
                    _isLoading.value = false
                    _isLoadingMore.value = false
                }
        }
    }

    fun expandAllSuggestions() {
        val trimmed = _query.value.trim()
        if (trimmed.isEmpty() || _isLoadingMore.value) return
        if (cachedFullQuery == trimmed && cachedFullSuggestions.size > INITIAL_PAGE_SIZE) {
            _isExpandedAll.value = true
            _suggestions.value = cachedFullSuggestions
            _hasMoreSuggestions.value = false
            return
        }
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val page = repository.getLiveSuggestionsPaged(
                    query = trimmed,
                    offset = 0,
                    limit = EXPANDED_PAGE_SIZE
                )
                if (_query.value.trim() == trimmed) {
                    val existingKeys = _suggestions.value.mapTo(HashSet()) { it.word.lowercase() }
                    val merged = if (page.items.size >= _suggestions.value.size) {
                        page.items
                    } else {
                        _suggestions.value + page.items.filter { it.word.lowercase() !in existingKeys }
                    }
                    cachedFullQuery = trimmed
                    cachedFullSuggestions = merged
                    _suggestions.value = merged
                    _isExpandedAll.value = true
                    _hasMoreSuggestions.value = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun collapseSuggestions() {
        if (!_isExpandedAll.value) return
        val current = _suggestions.value
        val trimmed = _query.value.trim()
        if (cachedFullQuery != trimmed || cachedFullSuggestions.size < current.size) {
            cachedFullQuery = trimmed
            cachedFullSuggestions = current
        }
        _suggestions.value = current.take(INITIAL_PAGE_SIZE)
        _isExpandedAll.value = false
        _hasMoreSuggestions.value = current.size > INITIAL_PAGE_SIZE
    }

    fun loadMoreSuggestions() {
        expandAllSuggestions()
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
        val changed = newValue != _query.value
        _query.value = newValue
        if (changed) {
            // Immediately remove old results and previous state when even one letter changes
            _submittedQuery.value = null
            _suggestions.value = emptyList()
            _hasMoreSuggestions.value = false
            _isExpandedAll.value = false
            _isLoadingMore.value = false
            cachedFullQuery = ""
            cachedFullSuggestions = emptyList()
        }
        if (newValue.isNotBlank()) {
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
            _isExpandedAll.value = false
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
        const val INITIAL_PAGE_SIZE = 18
        const val EXPANDED_PAGE_SIZE = 500
    }

    class Factory(
        private val repository: LexiconRepository,
        private val initialQuery: String,
        private val initialDirection: LanguageDirection,
        private val offlinePackManager: OfflinePackManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QuickTranslateViewModel(
                repository = repository,
                initialQuery = initialQuery,
                initialDirection = initialDirection,
                offlinePackManager = offlinePackManager
            ) as T
        }
    }
}
