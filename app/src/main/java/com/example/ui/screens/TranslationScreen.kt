package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.ChunkSlice
import com.example.core.LanguagePackInfo
import com.example.ui.components.AppIcons
import com.example.ui.components.AudioSpeedSelector
import com.example.ui.components.HighlightableSentencesText
import com.example.ui.components.LongTextAudioBar
import com.example.ui.viewmodel.TranslationViewModel
import kotlinx.coroutines.launch

@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val playingKey by audioManager.playingKey.collectAsStateWithLifecycle()
    val ttsProgress by audioManager.ttsProgress.collectAsStateWithLifecycle()
    val readerState by audioManager.readerState.collectAsStateWithLifecycle()
    val audioSpeed by audioManager.audioSpeed.collectAsStateWithLifecycle()

    var selectingSource by remember { mutableStateOf(false) }
    var selectingTarget by remember { mutableStateOf(false) }

    val openTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveFileName(context, uri)
            viewModel.translateImportedFile(uri, fileName)
        }
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportToTxtUri(uri)
        }
    }

    val sourceInfo = languages.firstOrNull { it.code == state.sourceCode }
    val targetInfo = languages.firstOrNull { it.code == state.targetCode }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("translation_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = colors.onBackground
                        )
                    }
                    Text(
                        text = "الترجمة",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onBackground
                    )
                }

                TextButton(
                    onClick = onOpenLanguages,
                    modifier = Modifier.testTag("open_languages_from_translation")
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

            // Language selector card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = colors.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, colors.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanguageChipSelector(
                        label = sourceInfo?.arabicName ?: state.sourceCode,
                        onClick = { selectingSource = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("source_language_selector")
                    )

                    IconButton(
                        onClick = { viewModel.swapLanguages() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("translation_swap_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.SwapHoriz,
                            contentDescription = stringResource(R.string.cd_swap_languages),
                            tint = colors.secondary
                        )
                    }

                    LanguageChipSelector(
                        label = targetInfo?.arabicName ?: state.targetCode,
                        onClick = { selectingTarget = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("target_language_selector")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Missing language pack banner
            if (state.missingPacks) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = colors.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "حزمة اللغة غير محمّلة",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.requestDownloadMissingPacks() },
                            enabled = !state.isDownloadingPacks,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary,
                                contentColor = colors.onPrimary
                            ),
                            modifier = Modifier.testTag("download_missing_pair_button")
                        ) {
                            if (state.isDownloadingPacks) {
                                CircularProgressIndicator(
                                    color = colors.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text("تحميل", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = { viewModel.onInputTextChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .testTag("translation_input_field"),
                        placeholder = {
                            Text(
                                text = "أدخل نصاً للترجمة أو استورد ملف .txt طويل...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (state.inputText.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onInputTextChange("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "مسح",
                                        tint = colors.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedBorderColor = colors.secondary,
                            unfocusedBorderColor = colors.outline,
                            cursorColor = colors.secondary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                // Action Row: Translate + Import .txt
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.isTranslating) {
                            Button(
                                onClick = { viewModel.cancelTranslation() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.error,
                                    contentColor = colors.onError
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("cancel_translation_button")
                            ) {
                                Text("إلغاء الترجمة (${state.progressPercent}%)")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.translateInputText() },
                                enabled = state.inputText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("translate_action_button")
                            ) {
                                Text("ترجمة", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        OutlinedButton(
                            onClick = { openTxtLauncher.launch(arrayOf("text/plain")) },
                            enabled = !state.isTranslating,
                            border = BorderStroke(1.dp, colors.outline),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("import_txt_button")
                        ) {
                            Icon(
                                imageVector = AppIcons.UploadFile,
                                contentDescription = null,
                                tint = colors.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ملف .txt", color = colors.onSurface)
                        }
                    }
                }

                if (state.inputText.isNotBlank()) {
                    item(key = "input_audio_controls") {
                        val isEnglishInput = !ArabicTextUtils.containsArabicLetters(state.inputText)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                LongTextAudioBar(
                                    title = "قراءة النص المدخل",
                                    isPlaying = playingKey == "tr_input_text",
                                    ttsProgress = ttsProgress,
                                    onPlayClick = {
                                        audioManager.startLongReading(
                                            key = "tr_input_text",
                                            text = state.inputText
                                        )
                                    },
                                    onStopClick = { audioManager.stopPlayback() },
                                    playTag = "tr_input_audio_play",
                                    stopTag = "tr_input_audio_stop",
                                    readerState = readerState,
                                    onPauseClick = { audioManager.pauseLongReading() },
                                    onResumeClick = { audioManager.resumeLongReading() },
                                    onSkipPreviousClick = { audioManager.skipPrevious() },
                                    onSkipNextClick = { audioManager.skipNext() },
                                    onSelectAccent = { audioManager.switchReaderAccent(it) },
                                    isEnglishContent = isEnglishInput
                                )
                            }
                            AudioSpeedSelector(
                                currentSpeed = audioSpeed,
                                onSelectSpeed = { audioManager.setAudioSpeed(it) }
                            )
                        }
                    }
                }

                if (state.isTranslating && (state.progressPercent > 0 || state.importedFileName != null)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { state.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.secondary,
                                trackColor = colors.outline
                            )
                            Text(
                                text = "جاري الترجمة: ${state.progressPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!state.statusMessage.isNullOrBlank()) {
                    item {
                        Text(
                            text = state.statusMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.secondary
                        )
                    }
                }

                // Short output card
                if (state.shortOutput.isNotBlank()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "الترجمة",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.secondary
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AudioSpeedSelector(
                                            currentSpeed = audioSpeed,
                                            onSelectSpeed = { audioManager.setAudioSpeed(it) }
                                        )
                                        IconButton(
                                            onClick = { clipboard.setText(AnnotatedString(state.shortOutput)) },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = AppIcons.ContentCopy,
                                                contentDescription = "نسخ",
                                                tint = colors.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                                val isOutputArabic = ArabicTextUtils.containsArabicLetters(state.shortOutput)
                                val isOutputReading = playingKey == "tr_short_output" && readerState.isActive
                                HighlightableSentencesText(
                                    text = state.shortOutput,
                                    isArabic = isOutputArabic,
                                    isActiveReader = isOutputReading,
                                    activeSentenceIndex = readerState.currentIndex,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                LongTextAudioBar(
                                    title = "استماع للترجمة",
                                    isPlaying = playingKey == "tr_short_output",
                                    ttsProgress = ttsProgress,
                                    onPlayClick = {
                                        audioManager.startLongReading(
                                            key = "tr_short_output",
                                            text = state.shortOutput
                                        )
                                    },
                                    onStopClick = { audioManager.stopPlayback() },
                                    playTag = "tr_short_audio_play",
                                    stopTag = "tr_short_audio_stop",
                                    readerState = readerState,
                                    onPauseClick = { audioManager.pauseLongReading() },
                                    onResumeClick = { audioManager.resumeLongReading() },
                                    onSkipPreviousClick = { audioManager.skipPrevious() },
                                    onSkipNextClick = { audioManager.skipNext() },
                                    onSelectAccent = { audioManager.switchReaderAccent(it) },
                                    isEnglishContent = !isOutputArabic
                                )
                            }
                        }
                    }
                }

                // Streamed chunk output header + Long Text Audio + Export .txt (BUG 7)
                if (state.slices.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "الترجمة المتدفقة (${state.slices.size} مقاطع)",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = colors.onBackground
                                    )
                                    OutlinedButton(
                                        onClick = { exportTxtLauncher.launch("translated_output.txt") },
                                        border = BorderStroke(1.dp, colors.outline),
                                        modifier = Modifier.testTag("export_txt_button")
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.SaveAlt,
                                            contentDescription = null,
                                            tint = colors.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تصدير .txt", color = colors.onSurface)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        LongTextAudioBar(
                                            title = "قراءة كل النص المترجم",
                                            isPlaying = playingKey == "tr_streamed_out",
                                            ttsProgress = ttsProgress,
                                            onPlayClick = {
                                                coroutineScope.launch {
                                                    val fullText = viewModel.readAllTranslatedText()
                                                    if (fullText.isNotBlank()) {
                                                        audioManager.startLongReading(
                                                            key = "tr_streamed_out",
                                                            text = fullText
                                                        )
                                                    }
                                                }
                                            },
                                            onStopClick = { audioManager.stopPlayback() },
                                            playTag = "tr_streamed_audio_play",
                                            stopTag = "tr_streamed_audio_stop",
                                            readerState = readerState,
                                            onPauseClick = { audioManager.pauseLongReading() },
                                            onResumeClick = { audioManager.resumeLongReading() },
                                            onSkipPreviousClick = { audioManager.skipPrevious() },
                                            onSkipNextClick = { audioManager.skipNext() },
                                            onSelectAccent = { audioManager.switchReaderAccent(it) },
                                            isEnglishContent = state.targetCode.equals("en", ignoreCase = true)
                                        )
                                    }
                                    AudioSpeedSelector(
                                        currentSpeed = audioSpeed,
                                        onSelectSpeed = { audioManager.setAudioSpeed(it) }
                                    )
                                }
                            }
                        }
                    }

                    items(
                        items = state.slices,
                        key = { it.index }
                    ) { slice ->
                        StreamedSliceRow(
                            slice = slice,
                            loadText = { viewModel.loadSliceText(slice) }
                        )
                    }
                }
            }
        }
    }

    if (selectingSource) {
        LanguagePickerDialog(
            title = "اختر لغة المصدر",
            languages = languages,
            selectedCode = state.sourceCode,
            onSelect = {
                viewModel.selectSource(it)
                selectingSource = false
            },
            onDismiss = { selectingSource = false }
        )
    }

    if (selectingTarget) {
        LanguagePickerDialog(
            title = "اختر لغة الهدف",
            languages = languages,
            selectedCode = state.targetCode,
            onSelect = {
                viewModel.selectTarget(it)
                selectingTarget = false
            },
            onDismiss = { selectingTarget = false }
        )
    }

    if (state.showCellularConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCellularDownload() },
            containerColor = colors.surface,
            title = { Text("التحميل عبر بيانات الهاتف؟", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "أنت تستخدم بيانات الهاتف المحمول حالياً. هل تريد المتابعة لتحميل حزم الترجمة؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmCellularDownload() }) {
                    Text("متابعة")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCellularDownload() }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun StreamedSliceRow(
    slice: ChunkSlice,
    loadText: suspend () -> String
) {
    val colors = MaterialTheme.colorScheme
    var text by remember(slice.index) { mutableStateOf("") }

    LaunchedEffect(slice.index, slice.byteLength) {
        text = loadText()
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Text(
            text = text.trimEnd(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun LanguageChipSelector(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun LanguagePickerDialog(
    title: String,
    languages: List<LanguagePackInfo>,
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var filter by remember { mutableStateOf("") }
    val filtered = remember(languages, filter) {
        val q = filter.trim()
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
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = { Text("ابحث عن لغة...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(filtered, key = { it.code }) { item ->
                        val isSelected = item.code == selectedCode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item.code) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.arabicName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) colors.primary else colors.onSurface
                                )
                                Text(
                                    text = item.nativeName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (item.isDownloaded) "محمّلة" else "غير محمّلة",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.isDownloaded) colors.secondary else colors.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = colors.outline)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
}
