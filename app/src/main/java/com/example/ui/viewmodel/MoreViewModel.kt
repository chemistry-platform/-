package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.remote.OfflineModelStatus
import com.example.data.repository.LexiconRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoreViewModel(
    private val repository: LexiconRepository
) : ViewModel() {

    val cachedWordCount: StateFlow<Int> = repository.cachedCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = 0
        )

    val mlKitStatus: StateFlow<OfflineModelStatus> = repository.mlKitModelStatus

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = true
        )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refreshMlKitStatus()
        }
    }

    fun downloadOfflineModels() {
        viewModelScope.launch {
            val result = repository.downloadOfflineModels()
            _actionMessage.value = if (result.isSuccess) {
                "تم تحميل حزمة الترجمة بدون إنترنت بنجاح"
            } else {
                "تعذر تحميل الحزمة. تأكد من اتصالك بالإنترنت"
            }
        }
    }

    fun deleteOfflineModels() {
        viewModelScope.launch {
            repository.deleteOfflineModels()
            _actionMessage.value = "تم حذف حزمة اللغة العربية المحلية"
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            _actionMessage.value = "تم مسح ذاكرة التخزين المؤقت للقاموس"
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _actionMessage.value = "تم مسح سجل البحث"
        }
    }

    fun dismissMessage() {
        _actionMessage.value = null
    }

    class Factory(private val repository: LexiconRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MoreViewModel(repository) as T
        }
    }
}
