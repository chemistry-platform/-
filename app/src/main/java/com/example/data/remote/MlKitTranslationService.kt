package com.example.data.remote

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "MlKitTranslation"

data class OfflineModelStatus(
    val isArabicDownloaded: Boolean = false,
    val isEnglishDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val statusMessage: String? = null
)

class MlKitTranslationService {
    private val enToArTranslator: Translator by lazy {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.ARABIC)
            .build()
        Translation.getClient(options)
    }

    private val arToEnTranslator: Translator by lazy {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ARABIC)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        Translation.getClient(options)
    }

    private val _modelStatus = MutableStateFlow(OfflineModelStatus())
    val modelStatus: StateFlow<OfflineModelStatus> = _modelStatus.asStateFlow()

    suspend fun refreshModelStatus() {
        runCatching {
            val manager = RemoteModelManager.getInstance()
            val arModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
            val enModel = TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build()
            val arReady = manager.isModelDownloaded(arModel).awaitTask()
            val enReady = manager.isModelDownloaded(enModel).awaitTask()
            _modelStatus.value = _modelStatus.value.copy(
                isArabicDownloaded = arReady,
                isEnglishDownloaded = enReady
            )
        }.onFailure { e ->
            Log.w(TAG, "Failed to check ML Kit model status", e)
        }
    }

    suspend fun ensureModelsDownloaded(): Result<Unit> {
        _modelStatus.value = _modelStatus.value.copy(
            isDownloading = true,
            statusMessage = "جاري تجهيز حزمة الترجمة بدون إنترنت..."
        )
        return try {
            val conditions = DownloadConditions.Builder().build()
            withTimeout(60_000L) {
                enToArTranslator.downloadModelIfNeeded(conditions).awaitTask()
                arToEnTranslator.downloadModelIfNeeded(conditions).awaitTask()
            }
            refreshModelStatus()
            _modelStatus.value = _modelStatus.value.copy(
                isDownloading = false,
                statusMessage = "الترجمة الفورية بدون إنترنت جاهزة"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading ML Kit models", e)
            _modelStatus.value = _modelStatus.value.copy(
                isDownloading = false,
                statusMessage = "تعذر تحميل حزمة الترجمة حالياً"
            )
            Result.failure(e)
        }
    }

    suspend fun deleteOfflineModels(): Result<Unit> {
        return try {
            val manager = RemoteModelManager.getInstance()
            val arModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
            manager.deleteDownloadedModel(arModel).awaitTask()
            refreshModelStatus()
            _modelStatus.value = _modelStatus.value.copy(
                statusMessage = "تم حذف الحزمة العربية من الجهاز"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting ML Kit model", e)
            Result.failure(e)
        }
    }

    suspend fun translateEnToAr(text: String): Result<String> {
        if (text.isBlank()) return Result.success("")
        return translateWith(enToArTranslator, text)
    }

    suspend fun translateArToEn(text: String): Result<String> {
        if (text.isBlank()) return Result.success("")
        return translateWith(arToEnTranslator, text)
    }

    private suspend fun translateWith(translator: Translator, text: String): Result<String> {
        return try {
            val conditions = DownloadConditions.Builder().build()
            withTimeout(45_000L) {
                translator.downloadModelIfNeeded(conditions).awaitTask()
                val translated = translator.translate(text).awaitTask()
                refreshModelStatus()
                Result.success(translated.trim())
            }
        } catch (e: Exception) {
            Log.e(TAG, "ML Kit translation error for '$text'", e)
            Result.failure(e)
        }
    }

    fun close() {
        runCatching { enToArTranslator.close() }
        runCatching { arToEnTranslator.close() }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result)
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }
