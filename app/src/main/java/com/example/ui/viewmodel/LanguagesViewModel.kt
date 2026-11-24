package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.LanguagePackInfo
import com.example.core.TranslationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LanguagesViewModel(
    private val translationManager: TranslationManager
) : ViewModel() {

    val languages: StateFlow<List<LanguagePackInfo>> = translationManager.languages

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _pendingCellularCode = MutableStateFlow<String?>(null)
    val pendingCellularCode: StateFlow<String?> = _pendingCellularCode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            translationManager.refreshDownloadedLanguages()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun requestDownload(code: String) {
        if (!translationManager.isOnline()) {
            _message.value = "لا يوجد اتصال بالإنترنت"
            return
        }
        if (translationManager.isOnCellularOnly()) {
            _pendingCellularCode.value = code
        } else {
            performDownload(code)
        }
    }

    fun confirmCellularDownload() {
        val code = _pendingCellularCode.value ?: return
        _pendingCellularCode.value = null
        performDownload(code)
    }

    fun dismissCellularDialog() {
        _pendingCellularCode.value = null
    }

    private fun performDownload(code: String) {
        viewModelScope.launch {
            _message.value = null
            val res = translationManager.downloadLanguage(code)
            if (res.isFailure) {
                _message.value = "تعذر تحميل حزمة اللغة"
            }
        }
    }

    fun deleteLanguage(code: String) {
        viewModelScope.launch {
            _message.value = null
            val res = translationManager.deleteLanguage(code)
            if (res.isFailure) {
                _message.value = "تعذر حذف حزمة اللغة"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    class Factory(private val translationManager: TranslationManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LanguagesViewModel(translationManager) as T
        }
    }
}
