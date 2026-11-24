package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.LexiconRepository
import com.example.domain.model.AwlWordItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ToeflIeltsViewModel(
    repository: LexiconRepository
) : ViewModel() {

    val offlinePackCount: StateFlow<Int> = repository.offlinePackCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredAwlWords: StateFlow<List<AwlWordItem>> = combine(
        repository.observeAwlWords(),
        _searchQuery
    ) { words, query ->
        val q = query.trim()
        if (q.isEmpty()) {
            words
        } else {
            words.filter { item ->
                item.word.contains(q, ignoreCase = true) ||
                    item.summaryDefinition.contains(q, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    class Factory(
        private val repository: LexiconRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ToeflIeltsViewModel(repository) as T
        }
    }
}
