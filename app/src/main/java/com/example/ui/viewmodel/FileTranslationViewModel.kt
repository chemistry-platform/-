package com.example.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.FileTranslationManager
import com.example.core.InputDocumentType
import com.example.core.LanguagePackInfo
import com.example.core.MissingLanguagePackException
import com.example.core.TranslatedPageResult
import com.example.core.TranslationManager
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class FileTranslationUiState(
    val sourceCode: String = TranslateLanguage.ENGLISH,
    val targetCode: String = TranslateLanguage.ARABIC,
    val selectedInputUri: Uri? = null,
    val selectedInputType: InputDocumentType? = null,
    val selectedFileName: String? = null,
    val selectedOutputUri: Uri? = null,
    val selectedOutputFileName: String? = null,
    val isProcessing: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val progressPercent: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val missingPacks: Boolean = false,
    val isDownloadingPacks: Boolean = false,
    val showCellularConfirm: Boolean = false,
    val pages: List<TranslatedPageResult> = emptyList()
)

class FileTranslationViewModel(
    private val translationManager: TranslationManager,
    private val fileTranslationManager: FileTranslationManager
) : ViewModel() {

    val languages: StateFlow<List<LanguagePackInfo>> = translationManager.languages

    private val _uiState = MutableStateFlow(FileTranslationUiState())
    val uiState: StateFlow<FileTranslationUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            translationManager.refreshDownloadedLanguages()
            checkCurrentPairStatus()
        }
    }

    fun selectSource(code: String) {
        _uiState.value = _uiState.value.copy(sourceCode = code, errorMessage = null, statusMessage = null)
        viewModelScope.launch { checkCurrentPairStatus() }
    }

    fun selectTarget(code: String) {
        _uiState.value = _uiState.value.copy(targetCode = code, errorMessage = null, statusMessage = null)
        viewModelScope.launch { checkCurrentPairStatus() }
    }

    fun swapLanguages() {
        val current = _uiState.value
        _uiState.value = current.copy(
            sourceCode = current.targetCode,
            targetCode = current.sourceCode,
            errorMessage = null,
            statusMessage = null
        )
        viewModelScope.launch { checkCurrentPairStatus() }
    }

    private suspend fun checkCurrentPairStatus() {
        val st = _uiState.value
        val ok = translationManager.isPairDownloaded(st.sourceCode, st.targetCode)
        _uiState.value = _uiState.value.copy(missingPacks = !ok)
    }

    fun requestDownloadMissingPacks() {
        if (!translationManager.isOnline()) {
            _uiState.value = _uiState.value.copy(errorMessage = "لا يوجد اتصال بالإنترنت")
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
            _uiState.value = _uiState.value.copy(
                isDownloadingPacks = true,
                errorMessage = null,
                statusMessage = "جاري تحميل حزم الترجمة..."
            )
            val res = translationManager.downloadPair(st.sourceCode, st.targetCode)
            _uiState.value = _uiState.value.copy(
                isDownloadingPacks = false,
                missingPacks = res.isFailure,
                statusMessage = if (res.isSuccess) "تم تحميل حزم الترجمة بنجاح" else null,
                errorMessage = if (res.isFailure) "تعذر تحميل حزمة اللغة حالياً" else null
            )
        }
    }

    fun onInputSelected(uri: Uri, type: InputDocumentType, displayName: String?) {
        _uiState.value = _uiState.value.copy(
            selectedInputUri = uri,
            selectedInputType = type,
            selectedFileName = displayName ?: type.labelAr,
            errorMessage = null,
            statusMessage = null,
            pages = emptyList(),
            currentPage = 0,
            totalPages = 0,
            progressPercent = 0
        )
    }

    fun onOutputUriChosenAndStart(outputUri: Uri, outputName: String?) {
        _uiState.value = _uiState.value.copy(
            selectedOutputUri = outputUri,
            selectedOutputFileName = outputName ?: "translated.txt"
        )
        startTranslation(outputUri)
    }

    fun startTranslation(outputTxtUri: Uri? = _uiState.value.selectedOutputUri) {
        val st = _uiState.value
        val inUri = st.selectedInputUri ?: return
        val inType = st.selectedInputType ?: return

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            if (!translationManager.isPairDownloaded(st.sourceCode, st.targetCode)) {
                _uiState.value = _uiState.value.copy(missingPacks = true)
                return@launch
            }

            if (inType == InputDocumentType.IMAGE && st.sourceCode == TranslateLanguage.ARABIC) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "التعرف على النص العربي في الصور غير مدعوم حالياً",
                    isProcessing = false
                )
                return@launch
            }

            val accumulatedPages = ArrayList<TranslatedPageResult>()
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                errorMessage = null,
                statusMessage = "جاري بدء المعالجة...",
                pages = emptyList(),
                currentPage = 0,
                totalPages = 0,
                progressPercent = 0
            )

            try {
                val res = fileTranslationManager.translateDocumentStreaming(
                    inputUri = inUri,
                    inputType = inType,
                    outputTxtUri = outputTxtUri,
                    sourceCode = st.sourceCode,
                    targetCode = st.targetCode,
                    onProgress = { progress ->
                        _uiState.value = _uiState.value.copy(
                            currentPage = progress.currentPage,
                            totalPages = progress.totalPages,
                            progressPercent = progress.percent,
                            statusMessage = progress.statusText
                        )
                    },
                    onPageCompleted = { pageResult ->
                        accumulatedPages.add(pageResult)
                        _uiState.value = _uiState.value.copy(
                            pages = accumulatedPages.toList()
                        )
                    }
                )

                val err = res.exceptionOrNull()
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    missingPacks = err is MissingLanguagePackException,
                    errorMessage = if (res.isFailure && err !is MissingLanguagePackException) {
                        err?.message ?: "تعذر إتمام ترجمة الملف"
                    } else null,
                    statusMessage = if (res.isSuccess) {
                        if (outputTxtUri != null) "اكتملت الترجمة وتم حفظها تدريجياً في ملف .txt"
                        else "اكتملت الترجمة"
                    } else null
                )
            } catch (_: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    statusMessage = "تم إيقاف الترجمة."
                )
            }
        }
    }

    fun cancelTranslation() {
        activeJob?.cancel()
        activeJob = null
        _uiState.value = _uiState.value.copy(
            isProcessing = false,
            statusMessage = "تم الإلغاء"
        )
    }

    class Factory(
        private val translationManager: TranslationManager,
        private val fileTranslationManager: FileTranslationManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FileTranslationViewModel(translationManager, fileTranslationManager) as T
        }
    }
}
