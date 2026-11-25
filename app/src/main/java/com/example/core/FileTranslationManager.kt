package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "FileTranslationManager"
private const val OCR_MAX_DIMENSION_PX = 1600
private const val TXT_PAGE_CHAR_LIMIT = 2400
private const val PROGRESS_MIN_INTERVAL_MS = 350L

enum class InputDocumentType(val labelAr: String, val labelEn: String) {
    PDF("PDF", "PDF"),
    IMAGE("صورة", "Image"),
    TXT("ملف نصي", "Text File");

    fun labelFor(language: AppLanguage): String =
        if (language == AppLanguage.ARABIC) labelAr else labelEn
}

@Immutable
data class TranslatedPageResult(
    val pageNumber: Int,
    val totalPages: Int,
    val originalText: String,
    val translatedText: String,
    val usedOcr: Boolean = false,
    val warningMessage: String? = null
)

data class FileTranslationProgress(
    val currentPage: Int,
    val totalPages: Int,
    val percent: Int,
    val statusText: String
)

class FileTranslationManager(
    context: Context,
    private val translationManager: TranslationManager
) {
    private val appContext = context.applicationContext

    @Volatile
    private var pdfBoxInitialized = false

    private val latinTextRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private fun ensurePdfBoxInitialized() {
        if (!pdfBoxInitialized) {
            synchronized(this) {
                if (!pdfBoxInitialized) {
                    PDFBoxResourceLoader.init(appContext)
                    pdfBoxInitialized = true
                }
            }
        }
    }

    /**
     * Section 16: Processes PDF (1 page at a time), Image (Photo Picker + OCR), or TXT (bounded line batches),
     * translating via the streaming chunk pipeline and writing results incrementally page-by-page to [outputTxtUri].
     */
    suspend fun translateDocumentStreaming(
        inputUri: Uri,
        inputType: InputDocumentType,
        outputTxtUri: Uri?,
        sourceCode: String,
        targetCode: String,
        onProgress: (FileTranslationProgress) -> Unit,
        onPageCompleted: (TranslatedPageResult) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!translationManager.isPairDownloaded(sourceCode, targetCode)) {
            return@withContext Result.failure(MissingLanguagePackException(sourceCode, targetCode))
        }

        val outWriter = if (outputTxtUri != null) {
            runCatching {
                val os = appContext.contentResolver.openOutputStream(outputTxtUri, "w")
                    ?: appContext.contentResolver.openOutputStream(outputTxtUri)
                    ?: throw IllegalStateException("تعذر فتح ملف الحفظ")
                BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8))
            }.getOrElse { err ->
                return@withContext Result.failure(err)
            }
        } else {
            null
        }

        try {
            val result = when (inputType) {
                InputDocumentType.PDF -> processPdfIncremental(
                    inputUri = inputUri,
                    sourceCode = sourceCode,
                    targetCode = targetCode,
                    outWriter = outWriter,
                    onProgress = onProgress,
                    onPageCompleted = onPageCompleted
                )
                InputDocumentType.IMAGE -> processImageIncremental(
                    inputUri = inputUri,
                    sourceCode = sourceCode,
                    targetCode = targetCode,
                    outWriter = outWriter,
                    onProgress = onProgress,
                    onPageCompleted = onPageCompleted
                )
                InputDocumentType.TXT -> processTxtIncremental(
                    inputUri = inputUri,
                    sourceCode = sourceCode,
                    targetCode = targetCode,
                    outWriter = outWriter,
                    onProgress = onProgress,
                    onPageCompleted = onPageCompleted
                )
            }
            outWriter?.flush()
            result
        } finally {
            runCatching { outWriter?.close() }
        }
    }

    private suspend fun processPdfIncremental(
        inputUri: Uri,
        sourceCode: String,
        targetCode: String,
        outWriter: BufferedWriter?,
        onProgress: (FileTranslationProgress) -> Unit,
        onPageCompleted: (TranslatedPageResult) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempPdfFile = File(appContext.cacheDir, "active_translate_doc_${System.nanoTime()}.pdf")
        try {
            onProgress(FileTranslationProgress(0, 0, 0, "جاري تجهيز ملف PDF..."))
            appContext.contentResolver.openInputStream(inputUri)?.use { input ->
                FileOutputStream(tempPdfFile).use { out ->
                    input.copyTo(out, bufferSize = 32 * 1024)
                    out.flush()
                }
            } ?: return@withContext Result.failure(IllegalStateException("تعذر قراءة ملف PDF"))

            if (tempPdfFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("ملف PDF فارغ"))
            }

            ensurePdfBoxInitialized()
            val document: PDDocument = try {
                PDDocument.load(tempPdfFile, MemoryUsageSetting.setupTempFileOnly())
            } catch (_: InvalidPasswordException) {
                return@withContext Result.failure(IllegalStateException("ملف PDF محمي بكلمة مرور ولا يمكن فتحه."))
            } catch (e: Exception) {
                Log.w(TAG, "Corrupted or unreadable PDF", e)
                return@withContext Result.failure(IllegalStateException("ملف PDF تالف أو غير صالح للقراءة."))
            }

            var pfd: ParcelFileDescriptor? = null
            var pdfRenderer: PdfRenderer? = null
            try {
                val totalPages = document.numberOfPages
                if (totalPages <= 0) {
                    return@withContext Result.failure(IllegalStateException("ملف PDF لا يحتوي على صفحات."))
                }

                val stripper = PDFTextStripper()
                var lastProgressMs = 0L

                for (pageOneBased in 1..totalPages) {
                    kotlin.coroutines.coroutineContext.ensureActive()

                    val nowMs = SystemClock.elapsedRealtime()
                    if (pageOneBased == 1 || pageOneBased == totalPages || (nowMs - lastProgressMs) >= PROGRESS_MIN_INTERVAL_MS) {
                        lastProgressMs = nowMs
                        val pct = ((pageOneBased - 1) * 100 / totalPages).coerceIn(0, 99)
                        onProgress(
                            FileTranslationProgress(
                                currentPage = pageOneBased,
                                totalPages = totalPages,
                                percent = pct,
                                statusText = "صفحة $pageOneBased من $totalPages"
                            )
                        )
                    }

                    // Step 1: Try extracting the embedded text layer for ONE page only
                    stripper.startPage = pageOneBased
                    stripper.endPage = pageOneBased
                    val extractedLayerText = runCatching {
                        stripper.getText(document).trim()
                    }.getOrDefault("")

                    var usedOcr = false
                    var pageWarning: String? = null
                    var pageOriginal = extractedLayerText

                    // Step 2: If page has no text layer, render ONLY this page via PdfRenderer + OCR, then recycle bitmap immediately
                    if (pageOriginal.isBlank()) {
                        if (sourceCode == TranslateLanguage.ARABIC) {
                            pageWarning = "التعرف على النص العربي في الصور غير مدعوم حالياً"
                        } else {
                            val renderer = pdfRenderer ?: run {
                                val openedPfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                                pfd = openedPfd
                                PdfRenderer(openedPfd).also { pdfRenderer = it }
                            }
                            val pageIndex = pageOneBased - 1
                            if (pageIndex < renderer.pageCount) {
                                val ocrText = renderPdfPageAndRunOcr(renderer, pageIndex)
                                usedOcr = true
                                if (ocrText.isBlank()) {
                                    pageWarning = "الصفحة $pageOneBased فارغة أو لا تحتوي على نص لاتيني واضح."
                                } else {
                                    pageOriginal = ocrText
                                }
                            }
                        }
                    }

                    val translatedPageText = if (pageOriginal.isNotBlank()) {
                        val tr = translationManager.translatePageTextStreaming(
                            pageText = pageOriginal,
                            sourceCode = sourceCode,
                            targetCode = targetCode
                        )
                        tr.getOrElse { err ->
                            if (err is MissingLanguagePackException) throw err
                            pageWarning = "تعذر ترجمة هذه الصفحة."
                            ""
                        }
                    } else {
                        if (pageWarning == null) {
                            pageWarning = "صفحة فارغة لا تحتوي على نص."
                        }
                        ""
                    }

                    writePageToOutput(
                        writer = outWriter,
                        pageNumber = pageOneBased,
                        totalPages = totalPages,
                        translatedText = translatedPageText,
                        warning = pageWarning
                    )

                    val pageResult = TranslatedPageResult(
                        pageNumber = pageOneBased,
                        totalPages = totalPages,
                        originalText = pageOriginal,
                        translatedText = translatedPageText,
                        usedOcr = usedOcr,
                        warningMessage = pageWarning
                    )
                    onPageCompleted(pageResult)
                }

                onProgress(
                    FileTranslationProgress(
                        currentPage = totalPages,
                        totalPages = totalPages,
                        percent = 100,
                        statusText = "اكتملت ترجمة $totalPages صفحة"
                    )
                )
                Result.success(Unit)
            } finally {
                runCatching { pdfRenderer?.close() }
                runCatching { pfd?.close() }
                runCatching { document.close() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: MissingLanguagePackException) {
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "PDF translation failed", e)
            Result.failure(IllegalStateException("تعذر معالجة ملف PDF: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
        } finally {
            runCatching { if (tempPdfFile.exists()) tempPdfFile.delete() }
        }
    }

    private suspend fun renderPdfPageAndRunOcr(
        renderer: PdfRenderer,
        pageIndex: Int
    ): String {
        var bitmap: Bitmap? = null
        return try {
            val page = renderer.openPage(pageIndex)
            try {
                val rawW = page.width.coerceAtLeast(1)
                val rawH = page.height.coerceAtLeast(1)
                val scale = minOf(
                    2.0f,
                    OCR_MAX_DIMENSION_PX.toFloat() / maxOf(rawW, rawH).toFloat()
                ).coerceAtLeast(1.0f)
                val targetW = (rawW * scale).toInt().coerceIn(200, OCR_MAX_DIMENSION_PX)
                val targetH = (rawH * scale).toInt().coerceIn(200, OCR_MAX_DIMENSION_PX)
                val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap = bmp
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val image = InputImage.fromBitmap(bmp, 0)
                val result = latinTextRecognizer.process(image).awaitTask()
                result.text.trim()
            } finally {
                runCatching { page.close() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "PDF page $pageIndex OCR failed", e)
            ""
        } finally {
            runCatching { bitmap?.recycle() }
        }
    }

    private suspend fun processImageIncremental(
        inputUri: Uri,
        sourceCode: String,
        targetCode: String,
        outWriter: BufferedWriter?,
        onProgress: (FileTranslationProgress) -> Unit,
        onPageCompleted: (TranslatedPageResult) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (sourceCode == TranslateLanguage.ARABIC) {
            return@withContext Result.failure(
                IllegalStateException("التعرف على النص العربي في الصور غير مدعوم حالياً")
            )
        }

        var bitmap: Bitmap? = null
        try {
            onProgress(FileTranslationProgress(1, 1, 15, "جاري استخراج النص من الصورة (OCR)..."))
            bitmap = decodeSampledOrientedBitmap(inputUri)
                ?: return@withContext Result.failure(IllegalStateException("تعذر قراءة ملف الصورة."))

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val recognizedText = latinTextRecognizer.process(inputImage).awaitTask().text.trim()
            // Recycle bitmap immediately after OCR (Section 16.2 / memory safety)
            runCatching { bitmap.recycle() }
            bitmap = null

            if (recognizedText.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("لم يتم العثور على نص لاتيني واضح في الصورة (التعرف على النص العربي في الصور غير مدعوم حالياً).")
                )
            }

            kotlin.coroutines.coroutineContext.ensureActive()
            onProgress(FileTranslationProgress(1, 1, 60, "جاري ترجمة النص المستخرج..."))

            val translated = translationManager.translatePageTextStreaming(
                pageText = recognizedText,
                sourceCode = sourceCode,
                targetCode = targetCode
            ).getOrElse { err ->
                return@withContext Result.failure(err)
            }

            writePageToOutput(
                writer = outWriter,
                pageNumber = 1,
                totalPages = 1,
                translatedText = translated,
                warning = null
            )

            onPageCompleted(
                TranslatedPageResult(
                    pageNumber = 1,
                    totalPages = 1,
                    originalText = recognizedText,
                    translatedText = translated,
                    usedOcr = true
                )
            )

            onProgress(FileTranslationProgress(1, 1, 100, "اكتملت ترجمة الصورة"))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Image OCR/translation failed", e)
            Result.failure(IllegalStateException("تعذر معالجة الصورة: ${e.localizedMessage ?: "خطأ غير متوقع"}"))
        } finally {
            runCatching { bitmap?.recycle() }
        }
    }

    private suspend fun processTxtIncremental(
        inputUri: Uri,
        sourceCode: String,
        targetCode: String,
        outWriter: BufferedWriter?,
        onProgress: (FileTranslationProgress) -> Unit,
        onPageCompleted: (TranslatedPageResult) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            onProgress(FileTranslationProgress(0, 0, 0, "جاري قراءة الملف النصي..."))
            var totalPages = 0
            appContext.contentResolver.openInputStream(inputUri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val section = readNextTxtSection(reader) ?: break
                        if (section.isNotBlank()) totalPages++
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("تعذر فتح الملف النصي."))

            if (totalPages == 0) {
                return@withContext Result.failure(IllegalStateException("الملف النصي فارغ."))
            }

            var currentPage = 0
            var lastProgressMs = 0L

            appContext.contentResolver.openInputStream(inputUri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val section = readNextTxtSection(reader) ?: break
                        if (section.isBlank()) continue
                        currentPage++

                        val nowMs = SystemClock.elapsedRealtime()
                        if (currentPage == 1 || currentPage == totalPages || (nowMs - lastProgressMs) >= PROGRESS_MIN_INTERVAL_MS) {
                            lastProgressMs = nowMs
                            val pct = ((currentPage - 1) * 100 / totalPages).coerceIn(0, 99)
                            onProgress(
                                FileTranslationProgress(
                                    currentPage = currentPage,
                                    totalPages = totalPages,
                                    percent = pct,
                                    statusText = "قسم $currentPage من $totalPages"
                                )
                            )
                        }

                        val translated = translationManager.translatePageTextStreaming(
                            pageText = section,
                            sourceCode = sourceCode,
                            targetCode = targetCode
                        ).getOrElse { err ->
                            return@withContext Result.failure(err)
                        }

                        writePageToOutput(
                            writer = outWriter,
                            pageNumber = currentPage,
                            totalPages = totalPages,
                            translatedText = translated,
                            warning = null
                        )

                        onPageCompleted(
                            TranslatedPageResult(
                                pageNumber = currentPage,
                                totalPages = totalPages,
                                originalText = section,
                                translatedText = translated,
                                usedOcr = false
                            )
                        )
                    }
                }
            }

            onProgress(
                FileTranslationProgress(
                    currentPage = totalPages,
                    totalPages = totalPages,
                    percent = 100,
                    statusText = "اكتملت ترجمة الملف ($totalPages أقسام)"
                )
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "TXT file translation failed", e)
            Result.failure(IllegalStateException("تعذر قراءة الملف النصي."))
        }
    }

    private fun readNextTxtSection(reader: BufferedReader): String? {
        val sb = StringBuilder()
        while (sb.length < TXT_PAGE_CHAR_LIMIT) {
            val line = reader.readLine() ?: break
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
        }
        return if (sb.isEmpty()) null else sb.toString()
    }

    private fun writePageToOutput(
        writer: BufferedWriter?,
        pageNumber: Int,
        totalPages: Int,
        translatedText: String,
        warning: String?
    ) {
        if (writer == null) return
        if (totalPages > 1) {
            writer.write("--- صفحة $pageNumber من $totalPages ---\n")
        }
        if (translatedText.isNotBlank()) {
            writer.write(translatedText)
            writer.write("\n\n")
        } else if (!warning.isNullOrBlank()) {
            writer.write("[$warning]\n\n")
        }
        writer.flush()
    }

    private fun decodeSampledOrientedBitmap(uri: Uri): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val origW = boundsOpts.outWidth
        val origH = boundsOpts.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while ((origW / sampleSize) > OCR_MAX_DIMENSION_PX || (origH / sampleSize) > OCR_MAX_DIMENSION_PX) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val rotationDegrees = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { exifStream ->
                val exif = ExifInterface(exifStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (rotationDegrees == 0f) return decoded

        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated != decoded) {
            decoded.recycle()
        }
        return rotated
    }

    fun close() {
        runCatching { latinTextRecognizer.close() }
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
