package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ChunkSlice
import com.example.core.LanguagePackInfo
import com.example.core.MissingLanguagePackException
import com.example.core.TranslationManager
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TranslationUiState(
    val sourceCode: String = TranslateLanguage.ENGLISH,
    val targetCode: String = TranslateLanguage.ARABIC,
    val inputText: String = "",
    val shortOutput: String = "",
    val slices: List<ChunkSlice> = emptyList(),
    val isTranslating: Boolean = false,
    val progressPercent: Int = 0,
    val missingPacks: Boolean = false,
    val isDownloadingPacks: Boolean = false,
    val showCellularConfirm: Boolean = false,
    val statusMessage: String? = null,
    val importedFileName: String? = null
)

class TranslationViewModel(
    private val translationManager: TranslationManager
) : ViewModel() {

    val languages: StateFlow<List<LanguagePackInfo>> = translationManager.languages

    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            translationManager.refreshDownloadedLanguages()
            checkCurrentPairStatus()
        }
    }

    fun selectSource(code: String) {
        _uiState.value = _uiState.value.copy(sourceCode = code, statusMessage = null)
        viewModelScope.launch { checkCurrentPairStatus() }
    }

    fun selectTarget(code: String) {
        _uiState.value = _uiState.value.copy(targetCode = code, statusMessage = null)
        viewModelScope.launch { checkCurrentPairStatus() }
    }

    fun swapLanguages() {
        val current = _uiState.value
        _uiState.value = current.copy(
            sourceCode = current.targetCode,
            targetCode = current.sourceCode,
            shortOutput = "",
            slices = emptyList(),
            statusMessage = null
        )
        viewModelScope.launch { checkCurrentPairStatus() }
    }

    fun onInputTextChange(text: String) {
        _uiState.value = _uiState.value.copy(
            inputText = text,
            importedFileName = null,
            statusMessage = null
        )
    }

    suspend fun checkCurrentPairStatus() {
        val st = _uiState.value
        val ok = translationManager.isPairDownloaded(st.sourceCode, st.targetCode)
        _uiState.value = _uiState.value.copy(missingPacks = !ok)
    }

    fun requestDownloadMissingPacks() {
        if (!translationManager.isOnline()) {
            _uiState.value = _uiState.value.copy(statusMessage = "لا يوجد اتصال بالإنترنت")
            return
        }
        if (translationManager.isOnCellularOnly()) {
            _uiState.value = _uiState.value.copy(showCellularConfirm = true)
        } else {
            performDownloadMissingPacks()
        }
    }

    fun confirmCellularDownload() {
        _uiState.value = _uiState.value.copy(showCellularConfirm = false)
        performDownloadMissingPacks()
    }

    fun dismissCellularDownload() {
        _uiState.value = _uiState.value.copy(showCellularConfirm = false)
    }

    private fun performDownloadMissingPacks() {
        val st = _uiState.value
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDownloadingPacks = true, statusMessage = null)
            val result = translationManager.downloadPair(st.sourceCode, st.targetCode)
            _uiState.value = _uiState.value.copy(
                isDownloadingPacks = false,
                missingPacks = result.isFailure,
                statusMessage = if (result.isFailure) "تعذر تحميل حزمة اللغة حالياً" else null
            )
        }
    }

    fun translateInputText() {
        val st = _uiState.value
        val text = st.inputText
        if (text.isBlank()) return

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            if (!translationManager.isPairDownloaded(st.sourceCode, st.targetCode)) {
                _uiState.value = _uiState.value.copy(missingPacks = true)
                return@launch
            }
            if (text.length <= 500) {
                _uiState.value = _uiState.value.copy(
                    isTranslating = true,
                    slices = emptyList(),
                    shortOutput = "",
                    progressPercent = 0,
                    statusMessage = null
                )
                val res = translationManager.translateShortIfReady(text, st.sourceCode, st.targetCode)
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    shortOutput = res.getOrNull().orEmpty(),
                    missingPacks = res.exceptionOrNull() is MissingLanguagePackException,
                    statusMessage = if (res.isFailure && res.exceptionOrNull() !is MissingLanguagePackException) {
                        "تعذر إتمام الترجمة"
                    } else null
                )
            } else {
                val sliceAccumulator = ArrayList<ChunkSlice>()
                _uiState.value = _uiState.value.copy(
                    isTranslating = true,
                    shortOutput = "",
                    slices = emptyList(),
                    progressPercent = 0,
                    statusMessage = null
                )
                try {
                    val res = translationManager.streamTranslateText(
                        text = text,
                        sourceCode = st.sourceCode,
                        targetCode = st.targetCode,
                        onProgress = { done, total ->
                            val pct = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0
                            _uiState.value = _uiState.value.copy(progressPercent = pct)
                        },
                        onNewSliceWritten = { slice ->
                            sliceAccumulator.add(slice)
                            _uiState.value = _uiState.value.copy(slices = sliceAccumulator.toList())
                        }
                    )
                    _uiState.value = _uiState.value.copy(
                        isTranslating = false,
                        missingPacks = res.exceptionOrNull() is MissingLanguagePackException,
                        statusMessage = if (res.isFailure && res.exceptionOrNull() !is MissingLanguagePackException) {
                            "تعذر إتمام ترجمة النص"
                        } else null
                    )
                } catch (_: CancellationException) {
                    _uiState.value = _uiState.value.copy(
                        isTranslating = false,
                        statusMessage = "تم إلغاء الترجمة"
                    )
                }
            }
        }
    }

    fun translateImportedFile(uri: Uri, fileName: String?) {
        val st = _uiState.value
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            if (!translationManager.isPairDownloaded(st.sourceCode, st.targetCode)) {
                _uiState.value = _uiState.value.copy(missingPacks = true)
                return@launch
            }
            val sliceAccumulator = ArrayList<ChunkSlice>()
            _uiState.value = _uiState.value.copy(
                isTranslating = true,
                shortOutput = "",
                slices = emptyList(),
                progressPercent = 0,
                importedFileName = fileName,
                statusMessage = null
            )
            try {
                val res = translationManager.streamTranslateTxtUri(
                    uri = uri,
                    sourceCode = st.sourceCode,
                    targetCode = st.targetCode,
                    onProgress = { done, total ->
                        val pct = if (total > 0) (done * 100 / total).coerceIn(0, 100) else 0
                        _uiState.value = _uiState.value.copy(progressPercent = pct)
                    },
                    onNewSliceWritten = { slice ->
                        sliceAccumulator.add(slice)
                        _uiState.value = _uiState.value.copy(slices = sliceAccumulator.toList())
                    }
                )
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    statusMessage = if (res.isSuccess) "اكتملت ترجمة الملف" else "تعذر ترجمة الملف"
                )
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isTranslating = false,
                    statusMessage = "تم إلغاء ترجمة الملف"
                )
            }
        }
    }

    fun cancelTranslation() {
        activeJob?.cancel()
        activeJob = null
        _uiState.value = _uiState.value.copy(isTranslating = false, statusMessage = "تم الإلغاء")
    }

    suspend fun loadSliceText(slice: ChunkSlice): String = translationManager.readSliceText(slice)

    fun exportToTxtUri(uri: Uri) {
        viewModelScope.launch {
            val res = translationManager.exportStreamedTranslationToUri(uri)
            _uiState.value = _uiState.value.copy(
                statusMessage = if (res.isSuccess) "تم تصدير الترجمة بنجاح" else "تعذر حفظ الملف"
            )
        }
    }

    class Factory(private val translationManager: TranslationManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TranslationViewModel(translationManager) as T
        }
    }
}
