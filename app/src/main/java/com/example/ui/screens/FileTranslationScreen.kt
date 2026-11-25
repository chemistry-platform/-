package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.Accent
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.InputDocumentType
import com.example.core.LanguagePackInfo
import com.example.core.TranslatedPageResult
import com.example.ui.components.AppIcons
import com.example.ui.components.AudioSpeedSelector
import com.example.ui.components.HighlightableSentencesText
import com.example.ui.components.LongTextAudioBar
import com.example.ui.components.UsageLabelChip
import com.example.ui.viewmodel.FileTranslationViewModel
import java.util.Locale

@Composable
fun FileTranslationScreen(
    viewModel: FileTranslationViewModel,
    audioManager: AudioPlayerManager,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val playingKey by audioManager.playingKey.collectAsStateWithLifecycle()
    val ttsProgress by audioManager.ttsProgress.collectAsStateWithLifecycle()
    val readerState by audioManager.readerState.collectAsStateWithLifecycle()
    val audioSpeed by audioManager.audioSpeed.collectAsStateWithLifecycle()

    var pickingForSource by remember { mutableStateOf<Boolean?>(null) }

    val sourceLangInfo = remember(languages, uiState.sourceCode) {
        languages.firstOrNull { it.code == uiState.sourceCode }
    }
    val targetLangInfo = remember(languages, uiState.targetCode) {
        languages.firstOrNull { it.code == uiState.targetCode }
    }

    // Section 18.5: derive progress label smoothly without recomposing the entire page list
    val progressLabel by remember {
        derivedStateOf {
            val total = uiState.totalPages
            val current = uiState.currentPage
            val pct = uiState.progressPercent
            if (total > 0) {
                "صفحة $current من $total • $pct%"
            } else {
                uiState.statusMessage ?: "جاري المعالجة..."
            }
        }
    }

    // Zero broad storage permissions: SAF + Android Photo Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryFileName(context, uri) ?: "document.pdf"
            viewModel.onInputSelected(uri, InputDocumentType.PDF, name)
        }
    }

    val txtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = queryFileName(context, uri) ?: "text.txt"
            viewModel.onInputSelected(uri, InputDocumentType.TXT, name)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val name = queryFileName(context, uri) ?: "image"
            viewModel.onInputSelected(uri, InputDocumentType.IMAGE, name)
        }
    }

    val createTxtOutputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { outputUri ->
        if (outputUri != null) {
            val outName = queryFileName(context, outputUri) ?: "translated.txt"
            viewModel.onOutputUriChosenAndStart(outputUri, outName)
        }
    }

    val onSwap = remember(viewModel) { { viewModel.swapLanguages() } }
    val onCancel = remember(viewModel) { { viewModel.cancelTranslation() } }
    val onStartPreviewOnly = remember(viewModel) { { viewModel.startTranslation(null) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header", contentType = "files_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.file_translation_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.onBackground
                        )
                        Text(
                            text = "PDF • صور (OCR) • ملفات نصية .txt",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onOpenLanguages,
                        modifier = Modifier.testTag("files_open_languages")
                    ) {
                        Icon(
                            imageVector = AppIcons.Language,
                            contentDescription = null,
                            tint = colors.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اللغات", color = colors.secondary)
                    }
                }
            }

            // Language pair selector
            item(key = "lang_pair", contentType = "language_pair_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FileLangSelectorPill(
                            label = "من",
                            langName = sourceLangInfo?.arabicName ?: uiState.sourceCode,
                            downloaded = sourceLangInfo?.isDownloaded == true,
                            onClick = { pickingForSource = true },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onSwap,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("files_swap_languages")
                        ) {
                            Icon(
                                imageVector = AppIcons.SwapHoriz,
                                contentDescription = stringResource(R.string.cd_swap_languages),
                                tint = colors.secondary
                            )
                        }
                        FileLangSelectorPill(
                            label = "إلى",
                            langName = targetLangInfo?.arabicName ?: uiState.targetCode,
                            downloaded = targetLangInfo?.isDownloaded == true,
                            onClick = { pickingForSource = false },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Missing language pack banner
            if (uiState.missingPacks) {
                item(key = "missing_pack_banner", contentType = "banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "حزمة اللغة المختارة غير محملة بعد للعمل دون إنترنت.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onErrorContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.requestDownloadMissingPacks() },
                                    enabled = !uiState.isDownloadingPacks,
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                                ) {
                                    if (uiState.isDownloadingPacks) {
                                        CircularProgressIndicator(
                                            color = colors.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text("تحميل حزمة اللغة")
                                }
                                OutlinedButton(onClick = onOpenLanguages) {
                                    Text("كل اللغات", color = colors.onErrorContainer)
                                }
                            }
                        }
                    }
                }
            }

            // Section 16.4 & 16.8 honest notice card
            item(key = "honest_notice", contentType = "notice_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colors.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "تعتمد سرعة المعالجة على حجم الملف وقدرات الجهاز؛ استخراج النص عبر OCR للصور أو صفحات PDF المصوّرة أبطأ من استخراج الطبقة النصية المباشرة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Text(
                                text = "التعرف على النص العربي في الصور غير مدعوم حالياً (ملفات PDF العربية ذات النص القابل للنسخ تعمل بشكل طبيعي).",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.tertiary
                            )
                        }
                    }
                }
            }

            // 3 File picker actions
            item(key = "pickers", contentType = "picker_buttons") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FileInputTypeButton(
                        title = "ملف PDF",
                        icon = AppIcons.PictureAsPdf,
                        enabled = !uiState.isProcessing,
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_pdf_button")
                    )
                    FileInputTypeButton(
                        title = "صورة (OCR)",
                        icon = AppIcons.ImageFile,
                        enabled = !uiState.isProcessing,
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_image_button")
                    )
                    FileInputTypeButton(
                        title = "ملف نصي",
                        icon = AppIcons.Description,
                        enabled = !uiState.isProcessing,
                        onClick = { txtPickerLauncher.launch(arrayOf("text/plain")) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_txt_button")
                    )
                }
            }

            // Selected file & start buttons
            if (uiState.selectedInputUri != null) {
                item(key = "selected_file", contentType = "selected_file_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.selectedFileName ?: "ملف محدد",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                uiState.selectedInputType?.let { type ->
                                    UsageLabelChip(text = type.labelAr)
                                }
                            }

                            if (!uiState.isProcessing) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val suggestedOut = buildSuggestedOutputName(
                                                uiState.selectedFileName,
                                                uiState.targetCode
                                            )
                                            createTxtOutputLauncher.launch(suggestedOut)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("start_save_txt_button")
                                    ) {
                                        Text("ترجمة وحفظ .txt", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = onStartPreviewOnly,
                                        border = BorderStroke(1.dp, colors.outline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("start_preview_only_button")
                                    ) {
                                        Text("ترجمة وعرض هنا", color = colors.onSurface, maxLines = 1)
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = progressLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.secondary
                                    )
                                    if (uiState.totalPages > 0) {
                                        LinearProgressIndicator(
                                            progress = { (uiState.progressPercent.coerceIn(0, 100)) / 100f },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = colors.secondary,
                                            trackColor = colors.outline
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = colors.secondary,
                                            trackColor = colors.outline
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onCancel,
                                        border = BorderStroke(1.dp, colors.primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("cancel_file_translation_button")
                                    ) {
                                        Text("إيقاف الترجمة فوراً", color = colors.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Status or error message
            if (!uiState.errorMessage.isNullOrBlank()) {
                item(key = "error_banner", contentType = "error_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else if (!uiState.isProcessing && !uiState.statusMessage.isNullOrBlank()) {
                item(key = "status_banner", contentType = "status_banner") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.secondaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = uiState.statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Translated page results (Section 16.5 & 18.2 + BUG 7 long text audio)
            items(
                items = uiState.pages,
                key = { it.pageNumber },
                contentType = { "translated_page_card" }
            ) { page ->
                TranslatedPageCard(
                    page = page,
                    sourceIsArabic = ArabicTextUtils.containsArabicLetters(page.originalText),
                    targetIsArabic = ArabicTextUtils.containsArabicLetters(page.translatedText),
                    playingKey = playingKey,
                    ttsProgress = ttsProgress,
                    readerState = readerState,
                    audioSpeed = audioSpeed,
                    onSelectSpeed = { audioManager.setAudioSpeed(it) },
                    onSpeakText = { text, key -> audioManager.startLongReading(key = key, text = text) },
                    onPauseSpeech = { audioManager.pauseLongReading() },
                    onResumeSpeech = { audioManager.resumeLongReading() },
                    onSkipPrevious = { audioManager.skipPrevious() },
                    onSkipNext = { audioManager.skipNext() },
                    onSelectAccent = { audioManager.switchReaderAccent(it) },
                    onStopSpeech = { audioManager.stopPlayback() }
                )
            }
        }
    }

    if (pickingForSource != null) {
        FileLanguagePickerDialog(
            languages = languages,
            onSelect = { code ->
                if (pickingForSource == true) {
                    viewModel.selectSource(code)
                } else {
                    viewModel.selectTarget(code)
                }
                pickingForSource = null
            },
            onDismiss = { pickingForSource = null }
        )
    }

    if (uiState.showCellularConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCellularDownload() },
            containerColor = colors.surface,
            title = { Text("التحميل عبر بيانات الجوال", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "أنت متصل حالياً ببيانات الجوال فقط. هل تريد متابعة تحميل حزم الترجمة الآن؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCellularDownload() }) {
                    Text("متابعة", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCellularDownload() }) {
                    Text("إلغاء", color = colors.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun FileLangSelectorPill(
    label: String,
    langName: String,
    downloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                if (downloaded) {
                    Text(
                        text = "• جاهز",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondary
                    )
                }
            }
            Text(
                text = langName,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileInputTypeButton(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(82.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        color = colors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) colors.secondary else colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TranslatedPageCard(
    page: TranslatedPageResult,
    sourceIsArabic: Boolean,
    targetIsArabic: Boolean,
    playingKey: String?,
    ttsProgress: AudioPlayerManager.TtsProgress?,
    readerState: AudioPlayerManager.ReaderState,
    audioSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onSpeakText: (text: String, key: String) -> Unit,
    onPauseSpeech: () -> Unit,
    onResumeSpeech: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSelectAccent: (Accent) -> Unit,
    onStopSpeech: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (page.totalPages > 1) {
                        "صفحة ${page.pageNumber} من ${page.totalPages}"
                    } else {
                        "النتيجة"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AudioSpeedSelector(
                        currentSpeed = audioSpeed,
                        onSelectSpeed = onSelectSpeed
                    )
                    if (page.usedOcr) {
                        UsageLabelChip(text = "OCR")
                    }
                    UsageLabelChip(text = "ترجمة آلية")
                }
            }

            if (!page.warningMessage.isNullOrBlank()) {
                Text(
                    text = page.warningMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.tertiary
                )
            }

            if (page.originalText.isNotBlank() || page.translatedText.isNotBlank()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 600.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PageTextColumn(
                                header = "النص الأصلي",
                                text = page.originalText,
                                isArabic = sourceIsArabic,
                                audioKey = "file_orig_${page.pageNumber}",
                                playingKey = playingKey,
                                ttsProgress = ttsProgress,
                                readerState = readerState,
                                onSpeak = { onSpeakText(page.originalText, "file_orig_${page.pageNumber}") },
                                onPause = onPauseSpeech,
                                onResume = onResumeSpeech,
                                onSkipPrevious = onSkipPrevious,
                                onSkipNext = onSkipNext,
                                onSelectAccent = onSelectAccent,
                                onStop = onStopSpeech,
                                modifier = Modifier.weight(1f)
                            )
                            PageTextColumn(
                                header = "الترجمة",
                                text = page.translatedText,
                                isArabic = targetIsArabic,
                                audioKey = "file_trans_${page.pageNumber}",
                                playingKey = playingKey,
                                ttsProgress = ttsProgress,
                                readerState = readerState,
                                onSpeak = { onSpeakText(page.translatedText, "file_trans_${page.pageNumber}") },
                                onPause = onPauseSpeech,
                                onResume = onResumeSpeech,
                                onSkipPrevious = onSkipPrevious,
                                onSkipNext = onSkipNext,
                                onSelectAccent = onSelectAccent,
                                onStop = onStopSpeech,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (page.originalText.isNotBlank()) {
                                PageTextColumn(
                                    header = "النص الأصلي",
                                    text = page.originalText,
                                    isArabic = sourceIsArabic,
                                    audioKey = "file_orig_${page.pageNumber}",
                                    playingKey = playingKey,
                                    ttsProgress = ttsProgress,
                                    readerState = readerState,
                                    onSpeak = { onSpeakText(page.originalText, "file_orig_${page.pageNumber}") },
                                    onPause = onPauseSpeech,
                                    onResume = onResumeSpeech,
                                    onSkipPrevious = onSkipPrevious,
                                    onSkipNext = onSkipNext,
                                    onSelectAccent = onSelectAccent,
                                    onStop = onStopSpeech,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (page.translatedText.isNotBlank()) {
                                PageTextColumn(
                                    header = "الترجمة",
                                    text = page.translatedText,
                                    isArabic = targetIsArabic,
                                    audioKey = "file_trans_${page.pageNumber}",
                                    playingKey = playingKey,
                                    ttsProgress = ttsProgress,
                                    readerState = readerState,
                                    onSpeak = { onSpeakText(page.translatedText, "file_trans_${page.pageNumber}") },
                                    onPause = onPauseSpeech,
                                    onResume = onResumeSpeech,
                                    onSkipPrevious = onSkipPrevious,
                                    onSkipNext = onSkipNext,
                                    onSelectAccent = onSelectAccent,
                                    onStop = onStopSpeech,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTextColumn(
    header: String,
    text: String,
    isArabic: Boolean,
    audioKey: String,
    playingKey: String?,
    ttsProgress: AudioPlayerManager.TtsProgress?,
    readerState: AudioPlayerManager.ReaderState,
    onSpeak: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSelectAccent: (Accent) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isThisPlaying = playingKey == audioKey
    val isActiveReader = isThisPlaying && readerState.isActive
    Surface(
        modifier = modifier,
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = header,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.secondary
            )
            if (text.isNotBlank()) {
                LongTextAudioBar(
                    title = if (isArabic) "استماع بالعربية" else "استماع للنص",
                    isPlaying = isThisPlaying,
                    ttsProgress = ttsProgress,
                    onPlayClick = onSpeak,
                    onStopClick = onStop,
                    readerState = readerState,
                    onPauseClick = onPause,
                    onResumeClick = onResume,
                    onSkipPreviousClick = onSkipPrevious,
                    onSkipNextClick = onSkipNext,
                    onSelectAccent = onSelectAccent,
                    isEnglishContent = !isArabic
                )
            }
            HighlightableSentencesText(
                text = text,
                isArabic = isArabic,
                isActiveReader = isActiveReader,
                activeSentenceIndex = readerState.currentIndex,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FileLanguagePickerDialog(
    languages: List<LanguagePackInfo>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var search by remember { mutableStateOf("") }
    val filtered = remember(languages, search) {
        val q = search.trim()
        if (q.isEmpty()) languages
        else languages.filter {
            it.arabicName.contains(q, ignoreCase = true) ||
                it.nativeName.contains(q, ignoreCase = true) ||
                it.code.contains(q, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text("اختر اللغة", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("ابحث عن لغة...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "مسح")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.code },
                        contentType = { "lang_picker_item" }
                    ) { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(lang.code) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.arabicName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "${lang.nativeName} (${lang.code})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            if (lang.isDownloaded) {
                                Text(
                                    text = "محملة",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.secondary
                                )
                            }
                        }
                        HorizontalDivider(color = colors.outline)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = colors.onSurfaceVariant)
            }
        }
    )
}

private fun queryFileName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }.getOrNull()
}

private fun buildSuggestedOutputName(inputName: String?, targetCode: String): String {
    val base = inputName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "document"
    return "${base}_translated_${targetCode.lowercase(Locale.US)}.txt"
}
