package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.ui.components.ArabicTranslatedText
import com.example.ui.components.AudioSpeedSelector
import com.example.ui.components.DirectionToggleChip
import com.example.ui.components.HighlightedEnglishText
import com.example.ui.components.HighlightedSentenceWithIdioms
import com.example.ui.components.InstantTranslationCard
import com.example.ui.components.InteractiveWordChip
import com.example.ui.components.LongTextAudioBar
import com.example.ui.components.MissingArabicPackTopBanner
import com.example.ui.components.MissingEnglishTtsVoiceBanner
import com.example.ui.components.PhraseFallbackBottomSheet
import com.example.ui.components.SentenceSpeakerButton
import com.example.ui.components.UsageLabelChip
import com.example.ui.viewmodel.SentenceLookupViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceLookupScreen(
    viewModel: SentenceLookupViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenDirectPhrase: (String) -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val query by viewModel.query.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inlineTranslation by viewModel.inlineTranslation.collectAsStateWithLifecycle()
    val chipMeanings by viewModel.chipMeanings.collectAsStateWithLifecycle()
    val phraseSheet by viewModel.phraseSheet.collectAsStateWithLifecycle()
    val showArabicTranslations by viewModel.showArabicTranslations.collectAsStateWithLifecycle()
    val showMachineLabel by viewModel.showMachineTranslationLabel.collectAsStateWithLifecycle()
    val audioSpeed by viewModel.audioSpeed.collectAsStateWithLifecycle()
    val isDownloadingPack by viewModel.isDownloadingPack.collectAsStateWithLifecycle()
    val showCellularConfirm by viewModel.showCellularConfirm.collectAsStateWithLifecycle()
    val packDownloadMessage by viewModel.packDownloadMessage.collectAsStateWithLifecycle()
    val playingKey by audioManager.playingKey.collectAsStateWithLifecycle()
    val ttsProgress by audioManager.ttsProgress.collectAsStateWithLifecycle()
    val ttsMissingEnglishVoice by audioManager.ttsMissingEnglishVoice.collectAsStateWithLifecycle()

    var inlineQuery by remember(query) { mutableStateOf(query) }

    LaunchedEffect(Unit) {
        audioManager.prepareTtsCheckIfNeeded()
    }

    LaunchedEffect(Unit) {
        viewModel.openWordEvent.collect { target ->
            onOpenWord(target)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                audioManager.refreshTtsVoiceAvailability()
                viewModel.refreshIfPackBecameAvailable()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Section 14.3: If the whole query is a known phrase or phrasal verb in the dictionary, redirect cleanly
    LaunchedEffect(state.directPhraseEntryAvailable, state.originalText) {
        if (state.directPhraseEntryAvailable && state.originalText.isNotBlank()) {
            onOpenDirectPhrase(state.originalText)
        }
    }

    val onToggleDirection = remember(viewModel) { { viewModel.toggleDirectionManual() } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("sentence_lookup_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.onBackground
                    )
                }

                OutlinedTextField(
                    value = inlineQuery,
                    onValueChange = {
                        inlineQuery = it
                        viewModel.onInlineQueryChanged(it)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sentence_lookup_search_field"),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder_translate),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.secondary,
                        unfocusedBorderColor = colors.outline,
                        cursorColor = colors.secondary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val trimmed = inlineQuery.trim()
                            if (trimmed.isNotEmpty()) {
                                if (ArabicTextUtils.isMultiWordQuery(trimmed)) {
                                    viewModel.lookupSentence(trimmed)
                                } else {
                                    onOpenWord(trimmed)
                                }
                            }
                        }
                    )
                )

                DirectionToggleChip(
                    direction = direction,
                    onToggle = onToggleDirection
                )
            }

            // BUG 6: Live instant translation while typing
            inlineTranslation?.let { instant ->
                InstantTranslationCard(
                    result = instant,
                    showMachineLabel = showMachineLabel,
                    audioManager = audioManager,
                    playingKey = playingKey,
                    onOpenFull = {
                        val trimmed = instant.query.trim()
                        if (ArabicTextUtils.isMultiWordQuery(trimmed)) {
                            viewModel.lookupSentence(trimmed)
                        } else {
                            onOpenWord(trimmed)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (state.isUpdating) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = colors.secondary,
                    trackColor = colors.outline
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Section 19.11: Missing Arabic pack banner with direct Download button at top of results
                if (state.missingTranslationPack) {
                    item(key = "sentence_missing_pack_top", contentType = "banner") {
                        MissingArabicPackTopBanner(
                            isDownloading = isDownloadingPack,
                            errorMessage = packDownloadMessage,
                            onDownloadClick = { viewModel.requestDownloadArabicPack() },
                            onOpenLanguages = onOpenLanguages
                        )
                    }
                }

                // Section 19.7: Missing English TTS voice banner if needed
                if (ttsMissingEnglishVoice) {
                    item(key = "sentence_missing_tts_voice", contentType = "banner") {
                        MissingEnglishTtsVoiceBanner(
                            onInstallVoiceClick = {
                                runCatching {
                                    context.startActivity(audioManager.buildInstallTtsDataIntent())
                                }
                            }
                        )
                    }
                }

                // Card 1: BUG 8 & BUG 1 & BUG 7 — Original sentence ON TOP, full translation directly BELOW, with EN & AR TTS + long-text controls
                item(key = "sentence_translation", contentType = "sentence_translation_card") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "الجملة الأصلية",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.secondary
                            )

                            val origAudioKey = if (state.isArabicInput) "sent_original_ar" else "sent_original_en"
                            val isOrigLong = state.originalText.length > 250 || playingKey == origAudioKey
                            if (state.isArabicInput) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                        Text(
                                            text = state.originalText,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    SentenceSpeakerButton(
                                        isPlaying = playingKey == origAudioKey,
                                        onClick = {
                                            if (playingKey == origAudioKey) {
                                                audioManager.stopPlayback()
                                            } else {
                                                audioManager.playSentenceAudio(
                                                    sentence = state.originalText,
                                                    audioUrl = null,
                                                    key = origAudioKey
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("speaker_sent_original")
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HighlightedSentenceWithIdioms(
                                        sentence = state.originalText,
                                        idiomPhrases = state.detectedIdioms.map { it.phrase },
                                        modifier = Modifier.weight(1f)
                                    )
                                    SentenceSpeakerButton(
                                        isPlaying = playingKey == origAudioKey,
                                        onClick = {
                                            if (playingKey == origAudioKey) {
                                                audioManager.stopPlayback()
                                            } else {
                                                audioManager.playSentenceAudio(
                                                    sentence = state.originalText,
                                                    audioUrl = null,
                                                    key = origAudioKey
                                                )
                                            }
                                        },
                                        modifier = Modifier.testTag("speaker_sent_original")
                                    )
                                }
                            }

                            if (isOrigLong) {
                                LongTextAudioBar(
                                    title = "قراءة النص الأصلي",
                                    isPlaying = playingKey == origAudioKey,
                                    ttsProgress = ttsProgress,
                                    onPlayClick = {
                                        audioManager.playSentenceAudio(
                                            sentence = state.originalText,
                                            audioUrl = null,
                                            key = origAudioKey
                                        )
                                    },
                                    onStopClick = { audioManager.stopPlayback() },
                                    playTag = "sent_original_long_play",
                                    stopTag = "sent_original_long_stop"
                                )
                            }

                            if (state.isArabicInput || showArabicTranslations) {
                                HorizontalDivider(color = colors.outline, thickness = 1.dp)

                                Text(
                                    text = if (state.isArabicInput) "الترجمة إلى الإنجليزية" else "الترجمة العربية",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary
                                )

                                val mt = state.machineTranslation
                                if (!mt.isNullOrBlank()) {
                                    val transAudioKey = if (state.isArabicInput) "sent_translated_en" else "sent_translated_ar"
                                    val isTransLong = mt.length > 250 || playingKey == transAudioKey
                                    if (state.isArabicInput) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = mt,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                    color = colors.primary,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("sentence_machine_translation")
                                                )
                                            }
                                            SentenceSpeakerButton(
                                                isPlaying = playingKey == transAudioKey,
                                                onClick = {
                                                    if (playingKey == transAudioKey) {
                                                        audioManager.stopPlayback()
                                                    } else {
                                                        audioManager.playSentenceAudio(
                                                            sentence = mt,
                                                            audioUrl = null,
                                                            key = transAudioKey
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.testTag("speaker_sent_translated")
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            ArabicTranslatedText(
                                                text = mt,
                                                isMachineTranslated = true,
                                                showMachineLabel = showMachineLabel,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.primary,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("sentence_machine_translation")
                                            )
                                            SentenceSpeakerButton(
                                                isPlaying = playingKey == transAudioKey,
                                                onClick = {
                                                    if (playingKey == transAudioKey) {
                                                        audioManager.stopPlayback()
                                                    } else {
                                                        audioManager.playSentenceAudio(
                                                            sentence = mt,
                                                            audioUrl = null,
                                                            key = transAudioKey
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.testTag("speaker_sent_translated")
                                            )
                                        }
                                    }

                                    if (isTransLong) {
                                        LongTextAudioBar(
                                            title = "قراءة الترجمة كاملة",
                                            isPlaying = playingKey == transAudioKey,
                                            ttsProgress = ttsProgress,
                                            onPlayClick = {
                                                audioManager.playSentenceAudio(
                                                    sentence = mt,
                                                    audioUrl = null,
                                                    key = transAudioKey
                                                )
                                            },
                                            onStopClick = { audioManager.stopPlayback() },
                                            playTag = "sent_trans_long_play",
                                            stopTag = "sent_trans_long_stop"
                                        )
                                    }
                                } else if (!state.missingTranslationPack) {
                                    Text(
                                        text = "جاري الترجمة...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "سرعة الصوت",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                                AudioSpeedSelector(
                                    currentSpeed = audioSpeed,
                                    onSelectSpeed = { audioManager.setAudioSpeed(it) }
                                )
                            }
                        }
                    }
                }

                // BUG 4: Recognized Idioms inside the sentence
                if (state.detectedIdioms.isNotEmpty()) {
                    item(key = "sentence_detected_idioms", contentType = "detected_idioms_card") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "التعابير الاصطلاحية المكتشفة",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.primary
                                )
                                state.detectedIdioms.forEachIndexed { idx, idiom ->
                                    val idiomKey = "sent_idiom_${idiom.phrase.hashCode()}"
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { viewModel.onChipTapped(idiom.phrase) }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Text(
                                                        text = idiom.phrase,
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = colors.secondary
                                                    )
                                                }
                                                UsageLabelChip(text = "تعبير اصطلاحي")
                                            }
                                            SentenceSpeakerButton(
                                                isPlaying = playingKey == idiomKey,
                                                onClick = {
                                                    if (playingKey == idiomKey) audioManager.stopPlayback()
                                                    else audioManager.playSentenceAudio(idiom.phrase, null, idiomKey)
                                                }
                                            )
                                        }
                                        if (idiom.englishMeaning.isNotBlank()) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = idiom.englishMeaning,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurface
                                                )
                                            }
                                        }
                                        if (showArabicTranslations && !idiom.arabicMeaning.isNullOrBlank()) {
                                            ArabicTranslatedText(
                                                text = idiom.arabicMeaning,
                                                isMachineTranslated = idiom.isArabicMachine,
                                                showMachineLabel = showMachineLabel,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.primary,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        if (idiom.example.isNotBlank()) {
                                            HighlightedEnglishText(
                                                sentence = idiom.example,
                                                targetWord = idiom.phrase
                                            )
                                        }
                                    }
                                    if (idx < state.detectedIdioms.lastIndex) {
                                        HorizontalDivider(color = colors.outline, thickness = 1.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 2: Tappable Word Chips with subtext translations (BUG 3 & BUG 8)
                if (state.wordChips.isNotEmpty()) {
                    item(key = "sentence_word_chips", contentType = "word_chips_card") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "كلمات الجملة (اضغط لفتح المعجم)",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onSurface
                                )
                                CompositionLocalProvider(
                                    LocalLayoutDirection provides if (state.isArabicInput) LayoutDirection.Rtl else LayoutDirection.Ltr
                                ) {
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        state.wordChips.forEach { chip ->
                                            InteractiveWordChip(
                                                word = chip,
                                                arabicSubtext = chipMeanings[chip.trim().lowercase(Locale.US)],
                                                onClick = { viewModel.onChipTapped(chip) },
                                                modifier = Modifier.testTag("sentence_chip_$chip")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 3: Real Tatoeba bilingual examples with standalone speaker button (EN & AR)
                if (state.tatoebaSentences.isNotEmpty()) {
                    item(key = "sentence_tatoeba", contentType = "tatoeba_examples_card") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.more_examples_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.primary
                                )
                                state.tatoebaSentences.forEachIndexed { idx, pair ->
                                    val exKey = "sent_tat_$idx"
                                    val exKeyAr = "sent_tat_ar_$idx"
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (state.isArabicInput) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = pair.arabic,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colors.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                SentenceSpeakerButton(
                                                    isPlaying = playingKey == exKeyAr,
                                                    onClick = {
                                                        if (playingKey == exKeyAr) audioManager.stopPlayback()
                                                        else audioManager.playSentenceAudio(
                                                            sentence = pair.arabic,
                                                            audioUrl = null,
                                                            key = exKeyAr
                                                        )
                                                    }
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Text(
                                                        text = pair.english,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                SentenceSpeakerButton(
                                                    isPlaying = playingKey == exKey,
                                                    onClick = {
                                                        if (playingKey == exKey) audioManager.stopPlayback()
                                                        else audioManager.playSentenceAudio(
                                                            sentence = pair.english,
                                                            audioUrl = pair.audioUrl,
                                                            key = exKey
                                                        )
                                                    }
                                                )
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                HighlightedEnglishText(
                                                    sentence = pair.english,
                                                    targetWord = state.wordChips.firstOrNull().orEmpty(),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                SentenceSpeakerButton(
                                                    isPlaying = playingKey == exKey,
                                                    onClick = {
                                                        if (playingKey == exKey) audioManager.stopPlayback()
                                                        else audioManager.playSentenceAudio(
                                                            sentence = pair.english,
                                                            audioUrl = pair.audioUrl,
                                                            key = exKey
                                                        )
                                                    }
                                                )
                                            }
                                            Text(
                                                text = pair.arabic,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (idx < state.tatoebaSentences.lastIndex) {
                                        HorizontalDivider(color = colors.outline, thickness = 1.dp)
                                    }
                                }
                                Text(
                                    text = "المصدر: Tatoeba • CC BY 2.0 FR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    phraseSheet?.let { sheetData ->
        PhraseFallbackBottomSheet(
            sheetData = sheetData,
            showMachineLabel = showMachineLabel,
            audioManager = audioManager,
            playingKey = playingKey,
            onOpenSentenceLookup = { phrase -> viewModel.lookupSentence(phrase) },
            onDismiss = { viewModel.dismissPhraseSheet() }
        )
    }

    if (showCellularConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCellularDownload() },
            containerColor = colors.surface,
            title = { Text("التحميل عبر بيانات الهاتف؟", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "أنت تستخدم بيانات الهاتف المحمول حالياً. هل تريد المتابعة لتحميل حزمة العربية والإنجليزية؟",
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
