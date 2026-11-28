package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Accent
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.StaticLabels
import com.example.ui.components.ArabicTranslatedText
import com.example.ui.components.AudioSpeedSelector
import com.example.ui.components.DirectionToggleChip
import com.example.ui.components.FullWidthSeeMoreFooter
import com.example.ui.components.HighlightableSentencesText
import com.example.ui.components.HighlightedEnglishText
import com.example.ui.components.HighlightedSentenceWithIdioms
import com.example.ui.components.InteractiveWordChip
import com.example.ui.components.LiveSearchSuggestionRow
import com.example.ui.components.LongTextAudioBar
import com.example.ui.components.MissingArabicPackTopBanner
import com.example.ui.components.MissingEnglishTtsVoiceBanner
import com.example.ui.components.PhraseFallbackBottomSheet
import com.example.ui.components.SectionHeaderWithArabic
import com.example.ui.components.SentenceDualSpeakerButtons
import com.example.ui.components.SentenceSpeakerButton
import com.example.ui.components.UsageLabelChip
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.viewmodel.SentenceLookupViewModel
import kotlinx.coroutines.launch
import java.util.Locale

private const val SENTENCE_INITIAL_VISIBLE = 10

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SentenceLookupScreen(
    viewModel: SentenceLookupViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenWord: (String, String?) -> Unit,
    onOpenDirectPhrase: (String) -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    val query by viewModel.query.collectAsStateWithLifecycle()
    val inlineQuery by viewModel.inlineQuery.collectAsStateWithLifecycle()
    val isSearchingInline by viewModel.isSearchingInline.collectAsStateWithLifecycle()
    val inlineSuggestions by viewModel.inlineSuggestions.collectAsStateWithLifecycle()
    val hasMoreInlineSuggestions by viewModel.hasMoreInlineSuggestions.collectAsStateWithLifecycle()
    val isExpandedAllInline by viewModel.isExpandedAllInline.collectAsStateWithLifecycle()

    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val longTranslationProgress by viewModel.longTranslationProgress.collectAsStateWithLifecycle()
    val chipMeanings by viewModel.chipMeanings.collectAsStateWithLifecycle()
    val phraseSheet by viewModel.phraseSheet.collectAsStateWithLifecycle()
    val showArabicTranslations by viewModel.showArabicTranslations.collectAsStateWithLifecycle()
    val audioSpeed by viewModel.audioSpeed.collectAsStateWithLifecycle()
    val isDownloadingPack by viewModel.isDownloadingPack.collectAsStateWithLifecycle()
    val showCellularConfirm by viewModel.showCellularConfirm.collectAsStateWithLifecycle()
    val packDownloadMessage by viewModel.packDownloadMessage.collectAsStateWithLifecycle()
    val playingKey by audioManager.playingKey.collectAsStateWithLifecycle()
    val ttsProgress by audioManager.ttsProgress.collectAsStateWithLifecycle()
    val readerState by audioManager.readerState.collectAsStateWithLifecycle()
    val ttsMissingEnglishVoice by audioManager.ttsMissingEnglishVoice.collectAsStateWithLifecycle()
    val ttsMissingAccentLabel by audioManager.ttsMissingAccentLabel.collectAsStateWithLifecycle()

    val liveListState = rememberLazyListState()
    val resultListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        audioManager.prepareTtsCheckIfNeeded()
        viewModel.openWordEvent.collect { req ->
            onOpenWord(req.word, req.preferredSense)
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
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
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
                        contentDescription = "Back",
                        tint = colors.onBackground
                    )
                }

                OutlinedTextField(
                    value = inlineQuery,
                    onValueChange = { viewModel.onInlineQueryChanged(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("sentence_lookup_search_field"),
                    placeholder = {
                        Text(
                            text = "Search a word or sentence...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        IconButton(
                            onClick = {
                                val trimmed = inlineQuery.trim()
                                if (trimmed.isNotEmpty()) {
                                    keyboardController?.hide()
                                    if (ArabicTextUtils.isMultiWordQuery(trimmed)) {
                                        viewModel.lookupSentence(trimmed)
                                    } else {
                                        viewModel.openWordReady(trimmed)
                                    }
                                } else {
                                    runCatching { focusRequester.requestFocus() }
                                    keyboardController?.show()
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = colors.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    trailingIcon = {
                        if (inlineQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.clearInlineSearch()
                                    runCatching { focusRequester.requestFocus() }
                                    keyboardController?.show()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("sentence_lookup_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surface,
                        focusedBorderColor = colors.secondary,
                        unfocusedBorderColor = semantic.divider,
                        cursorColor = colors.secondary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            val trimmed = inlineQuery.trim()
                            if (trimmed.isNotEmpty()) {
                                keyboardController?.hide()
                                if (ArabicTextUtils.isMultiWordQuery(trimmed)) {
                                    viewModel.lookupSentence(trimmed)
                                } else {
                                    viewModel.openWordReady(trimmed)
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

            if (isSearchingInline) {
                val collapseLiveList: (() -> Unit)? = if (isExpandedAllInline) {
                    {
                        viewModel.collapseInlineSuggestions()
                        coroutineScope.launch { liveListState.animateScrollToItem(0) }
                    }
                } else {
                    null
                }
                val liveInteraction = remember { MutableInteractionSource() }
                LazyColumn(
                    state = liveListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(
                            if (collapseLiveList != null) {
                                Modifier.combinedClickable(
                                    interactionSource = liveInteraction,
                                    indication = null,
                                    onClick = {},
                                    onDoubleClick = collapseLiveList
                                )
                            } else {
                                Modifier
                            }
                        )
                        .testTag("sentence_live_search_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (inlineQuery.isNotBlank()) {
                        if (ArabicTextUtils.isMultiWordQuery(inlineQuery.trim())) {
                            item(key = "sentence_direct_translate_row") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            keyboardController?.hide()
                                            viewModel.lookupSentence(inlineQuery.trim())
                                        },
                                    color = colors.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, semantic.divider)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Translate sentence: \"${inlineQuery.trim()}\"",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.secondary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "Translate",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = semantic.wordSubline
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = inlineSuggestions,
                            key = { idx, item -> "s_sug_${idx}_${item.word}" }
                        ) { idx, item ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LiveSearchSuggestionRow(
                                    suggestion = item,
                                    audioManager = audioManager,
                                    onOpenWord = { viewModel.openWordReady(item.word) },
                                    onDoubleTapCollapse = collapseLiveList
                                )
                                if (idx < inlineSuggestions.lastIndex) {
                                    HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                                }
                            }
                        }

                        if (hasMoreInlineSuggestions || isExpandedAllInline) {
                            item(key = "sentence_see_more_footer") {
                                Spacer(modifier = Modifier.height(6.dp))
                                FullWidthSeeMoreFooter(
                                    isExpanded = isExpandedAllInline,
                                    onClick = {
                                        if (isExpandedAllInline) {
                                            viewModel.collapseInlineSuggestions()
                                            coroutineScope.launch { liveListState.animateScrollToItem(0) }
                                        } else {
                                            viewModel.expandAllInlineSuggestions()
                                        }
                                    },
                                    testTag = "sentence_live_see_more",
                                    onDoubleTapCollapse = collapseLiveList
                                )
                            }
                        }
                    }
                }
            } else {
                var idiomsExpandedAll by remember(query) { mutableStateOf(false) }
                var chipsExpandedAll by remember(query) { mutableStateOf(false) }
                var tatoebaExpandedAll by remember(query) { mutableStateOf(false) }

                // Compute item indices so double-tap collapse scrolls each section header into view cleanly
                var nextIdx = 0
                val missingPackIdx = if (state.missingTranslationPack) nextIdx++ else -1
                val missingVoiceIdx = if (ttsMissingEnglishVoice) nextIdx++ else -1
                val translationCardIdx = nextIdx++
                val idiomsCardIdx = if (state.detectedIdioms.isNotEmpty()) nextIdx++ else -1
                val wordChipsCardIdx = if (state.wordChips.isNotEmpty()) nextIdx++ else -1
                val tatoebaCardIdx = if (state.tatoebaSentences.isNotEmpty()) nextIdx else -1

                LazyColumn(
                    state = resultListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (missingPackIdx >= 0) {
                        item(key = "sentence_missing_pack_top", contentType = "banner") {
                            MissingArabicPackTopBanner(
                                isDownloading = isDownloadingPack,
                                errorMessage = packDownloadMessage,
                                onDownloadClick = { viewModel.requestDownloadArabicPack() },
                                onOpenLanguages = onOpenLanguages
                            )
                        }
                    }

                    if (missingVoiceIdx >= 0) {
                        item(key = "sentence_missing_tts_voice", contentType = "banner") {
                            MissingEnglishTtsVoiceBanner(
                                missingAccentLabel = ttsMissingAccentLabel,
                                onInstallVoiceClick = {
                                    runCatching {
                                        context.startActivity(audioManager.buildInstallTtsDataIntent())
                                    }
                                }
                            )
                        }
                    }

                    // Card 1: Original sentence + Full translated sentence (#6CB4FF) + Long text streaming progress
                    item(key = "sentence_translation", contentType = "sentence_translation_card") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, semantic.divider)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    SectionHeaderWithArabic(
                                        englishTitle = "Original Sentence",
                                        arabicSublineOverride = "الجملة الأصلية",
                                        titleColor = colors.secondary
                                    )
                                    if (longTranslationProgress != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = colors.surfaceVariant,
                                                shape = RoundedCornerShape(999.dp),
                                                border = BorderStroke(1.dp, semantic.divider)
                                            ) {
                                                Text(
                                                    text = "${longTranslationProgress}%",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = semantic.wordSubline,
                                                    modifier = Modifier
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                        .testTag("long_translation_progress_pct")
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { viewModel.cancelLongTranslation() },
                                                border = BorderStroke(1.dp, semantic.divider),
                                                modifier = Modifier.testTag("long_translation_cancel")
                                            ) {
                                                Text("Cancel", color = colors.primary)
                                            }
                                        }
                                    }
                                }

                                val origAudioKey = if (state.isArabicInput) "sent_original_ar" else "sent_original_en"
                                val isOrigReading = playingKey == origAudioKey && readerState.isActive
                                val isOrigLong = state.originalText.length > 250 || isOrigReading
                                if (state.isArabicInput) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (isOrigReading) {
                                            HighlightableSentencesText(
                                                text = state.originalText,
                                                isArabic = true,
                                                isActiveReader = true,
                                                activeSentenceIndex = readerState.currentIndex,
                                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                Text(
                                                    text = state.originalText,
                                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = colors.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        SentenceSpeakerButton(
                                            isPlaying = playingKey == origAudioKey,
                                            onClick = {
                                                if (playingKey == origAudioKey) {
                                                    audioManager.stopPlayback()
                                                } else if (state.originalText.length > 250) {
                                                    audioManager.startLongReading(
                                                        key = origAudioKey,
                                                        text = state.originalText
                                                    )
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
                                        if (isOrigReading) {
                                            HighlightableSentencesText(
                                                text = state.originalText,
                                                isArabic = false,
                                                isActiveReader = true,
                                                activeSentenceIndex = readerState.currentIndex,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = colors.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            HighlightedSentenceWithIdioms(
                                                sentence = state.originalText,
                                                idiomPhrases = state.detectedIdioms.map { it.phrase },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        SentenceDualSpeakerButtons(
                                            playingKey = playingKey,
                                            keyPrefix = origAudioKey,
                                            onPlayUs = {
                                                audioManager.playSentenceAudio(
                                                    sentence = state.originalText,
                                                    audioUrl = null,
                                                    key = "${origAudioKey}_us",
                                                    accentOverride = Accent.US
                                                )
                                            },
                                            onPlayUk = {
                                                audioManager.playSentenceAudio(
                                                    sentence = state.originalText,
                                                    audioUrl = null,
                                                    key = "${origAudioKey}_uk",
                                                    accentOverride = Accent.UK
                                                )
                                            },
                                            onStop = { audioManager.stopPlayback() }
                                        )
                                    }
                                }

                                if (isOrigLong) {
                                    LongTextAudioBar(
                                        title = "Read original text",
                                        isPlaying = playingKey == origAudioKey,
                                        ttsProgress = ttsProgress,
                                        onPlayClick = {
                                            audioManager.startLongReading(
                                                key = origAudioKey,
                                                text = state.originalText
                                            )
                                        },
                                        onStopClick = { audioManager.stopPlayback() },
                                        playTag = "sent_original_long_play",
                                        stopTag = "sent_original_long_stop",
                                        readerState = readerState,
                                        onPauseClick = { audioManager.pauseLongReading() },
                                        onResumeClick = { audioManager.resumeLongReading() },
                                        onSkipPreviousClick = { audioManager.skipPrevious() },
                                        onSkipNextClick = { audioManager.skipNext() },
                                        onSelectAccent = { audioManager.switchReaderAccent(it) },
                                        isEnglishContent = !state.isArabicInput
                                    )
                                }

                                val mt = state.machineTranslation
                                if ((state.isArabicInput || showArabicTranslations) && !mt.isNullOrBlank()) {
                                    HorizontalDivider(color = semantic.divider, thickness = 1.dp)

                                    SectionHeaderWithArabic(
                                        englishTitle = if (state.isArabicInput) "English Translation" else "Arabic Translation",
                                        arabicSublineOverride = if (state.isArabicInput) "الترجمة الإنجليزية" else "الترجمة العربية",
                                        titleColor = semantic.exampleTranslation
                                    )

                                    val transAudioKey = if (state.isArabicInput) "sent_translated_en" else "sent_translated_ar"
                                    val isTransReading = playingKey == transAudioKey && readerState.isActive
                                    val isTransLong = mt.length > 250 || isTransReading
                                    if (state.isArabicInput) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            HighlightableSentencesText(
                                                text = mt,
                                                isArabic = false,
                                                isActiveReader = isTransReading,
                                                activeSentenceIndex = readerState.currentIndex,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = semantic.exampleTranslation,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("sentence_machine_translation")
                                            )
                                            SentenceDualSpeakerButtons(
                                                playingKey = playingKey,
                                                keyPrefix = transAudioKey,
                                                onPlayUs = {
                                                    audioManager.playSentenceAudio(
                                                        sentence = mt,
                                                        audioUrl = null,
                                                        key = "${transAudioKey}_us",
                                                        accentOverride = Accent.US
                                                    )
                                                },
                                                onPlayUk = {
                                                    audioManager.playSentenceAudio(
                                                        sentence = mt,
                                                        audioUrl = null,
                                                        key = "${transAudioKey}_uk",
                                                        accentOverride = Accent.UK
                                                    )
                                                },
                                                onStop = { audioManager.stopPlayback() }
                                            )
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isTransReading) {
                                                HighlightableSentencesText(
                                                    text = mt,
                                                    isArabic = true,
                                                    isActiveReader = true,
                                                    activeSentenceIndex = readerState.currentIndex,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                    color = semantic.exampleTranslation,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("sentence_machine_translation")
                                                )
                                            } else {
                                                ArabicTranslatedText(
                                                    text = mt,
                                                    isMachineTranslated = true,
                                                    showMachineLabel = false,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                    color = semantic.exampleTranslation,
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
                                                    } else if (mt.length > 250) {
                                                        audioManager.startLongReading(
                                                            key = transAudioKey,
                                                            text = mt
                                                        )
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
                                            title = "Read full translation",
                                            isPlaying = playingKey == transAudioKey,
                                            ttsProgress = ttsProgress,
                                            onPlayClick = {
                                                audioManager.startLongReading(
                                                    key = transAudioKey,
                                                    text = mt
                                                )
                                            },
                                            onStopClick = { audioManager.stopPlayback() },
                                            playTag = "sent_trans_long_play",
                                            stopTag = "sent_trans_long_stop",
                                            readerState = readerState,
                                            onPauseClick = { audioManager.pauseLongReading() },
                                            onResumeClick = { audioManager.resumeLongReading() },
                                            onSkipPreviousClick = { audioManager.skipPrevious() },
                                            onSkipNextClick = { audioManager.skipNext() },
                                            onSelectAccent = { audioManager.switchReaderAccent(it) },
                                            isEnglishContent = state.isArabicInput
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Speech speed",
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

                    // Card 2: Recognized Idioms (collapsible at 10 + double-tap collapse)
                    if (idiomsCardIdx >= 0) {
                        item(key = "sentence_detected_idioms", contentType = "detected_idioms_card") {
                            val totalIdioms = state.detectedIdioms.size
                            val visibleIdioms = if (idiomsExpandedAll) {
                                state.detectedIdioms
                            } else {
                                state.detectedIdioms.take(SENTENCE_INITIAL_VISIBLE)
                            }
                            val onCollapseIdioms: (() -> Unit)? = if (idiomsExpandedAll && totalIdioms > SENTENCE_INITIAL_VISIBLE) {
                                {
                                    idiomsExpandedAll = false
                                    coroutineScope.launch { resultListState.animateScrollToItem(idiomsCardIdx) }
                                }
                            } else {
                                null
                            }
                            val interaction = remember { MutableInteractionSource() }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, semantic.divider)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .then(
                                            if (onCollapseIdioms != null) {
                                                Modifier.combinedClickable(
                                                    interactionSource = interaction,
                                                    indication = null,
                                                    onClick = {},
                                                    onDoubleClick = onCollapseIdioms
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SectionHeaderWithArabic(
                                        englishTitle = "Idioms & Expressions",
                                        count = totalIdioms,
                                        titleColor = colors.onSurface
                                    )
                                    visibleIdioms.forEachIndexed { idx, idiom ->
                                        val idiomKey = "sent_idiom_${idiom.phrase.hashCode()}"
                                        val phraseIsArabic = ArabicTextUtils.containsArabicLetters(idiom.phrase)
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
                                                        .combinedClickable(
                                                            onClick = { viewModel.onChipTapped(idiom.phrase) },
                                                            onDoubleClick = onCollapseIdioms
                                                        )
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CompositionLocalProvider(
                                                        LocalLayoutDirection provides if (phraseIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                                                    ) {
                                                        Text(
                                                            text = idiom.phrase,
                                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = colors.onSurface
                                                        )
                                                    }
                                                    UsageLabelChip(
                                                        text = if (idiom.isPhrasalVerb) "Phrasal Verb" else "Idiom"
                                                    )
                                                }
                                                if (phraseIsArabic) {
                                                    SentenceSpeakerButton(
                                                        isPlaying = playingKey == "${idiomKey}_ar",
                                                        onClick = {
                                                            if (playingKey == "${idiomKey}_ar") {
                                                                audioManager.stopPlayback()
                                                            } else {
                                                                audioManager.playSentenceAudio(
                                                                    sentence = idiom.phrase,
                                                                    audioUrl = null,
                                                                    key = "${idiomKey}_ar"
                                                                )
                                                            }
                                                        },
                                                        onDoubleClick = onCollapseIdioms
                                                    )
                                                } else {
                                                    SentenceDualSpeakerButtons(
                                                        playingKey = playingKey,
                                                        keyPrefix = idiomKey,
                                                        onPlayUs = {
                                                            audioManager.playSentenceAudio(
                                                                sentence = idiom.phrase,
                                                                audioUrl = null,
                                                                key = "${idiomKey}_us",
                                                                accentOverride = Accent.US
                                                            )
                                                        },
                                                        onPlayUk = {
                                                            audioManager.playSentenceAudio(
                                                                sentence = idiom.phrase,
                                                                audioUrl = null,
                                                                key = "${idiomKey}_uk",
                                                                accentOverride = Accent.UK
                                                            )
                                                        },
                                                        onStop = { audioManager.stopPlayback() },
                                                        onDoubleClick = onCollapseIdioms
                                                    )
                                                }
                                            }
                                            if (!phraseIsArabic) {
                                                if (showArabicTranslations && !idiom.arabicMeaning.isNullOrBlank()) {
                                                    ArabicTranslatedText(
                                                        text = idiom.arabicMeaning,
                                                        isMachineTranslated = idiom.isArabicMachine,
                                                        showMachineLabel = false,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = semantic.definitionTranslation,
                                                        modifier = Modifier.fillMaxWidth()
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
                                            } else {
                                                if (idiom.englishMeaning.isNotBlank()) {
                                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                        Text(
                                                            text = idiom.englishMeaning,
                                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                            color = semantic.definitionTranslation
                                                        )
                                                    }
                                                }
                                                if (showArabicTranslations && !idiom.arabicMeaning.isNullOrBlank()) {
                                                    ArabicTranslatedText(
                                                        text = idiom.arabicMeaning,
                                                        isMachineTranslated = idiom.isArabicMachine,
                                                        showMachineLabel = false,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurface,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                            if (idiom.example.isNotBlank()) {
                                                if (ArabicTextUtils.containsArabicLetters(idiom.example)) {
                                                    Text(
                                                        text = idiom.example,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                } else {
                                                    HighlightedEnglishText(
                                                        sentence = idiom.example,
                                                        targetWord = idiom.phrase
                                                    )
                                                }
                                            }
                                        }
                                        if (idx < visibleIdioms.lastIndex) {
                                            HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                                        }
                                    }

                                    if (totalIdioms > SENTENCE_INITIAL_VISIBLE) {
                                        FullWidthSeeMoreFooter(
                                            isExpanded = idiomsExpandedAll,
                                            remainingCount = totalIdioms - SENTENCE_INITIAL_VISIBLE,
                                            onClick = {
                                                if (idiomsExpandedAll) {
                                                    idiomsExpandedAll = false
                                                    coroutineScope.launch { resultListState.animateScrollToItem(idiomsCardIdx) }
                                                } else {
                                                    idiomsExpandedAll = true
                                                }
                                            },
                                            onDoubleTapCollapse = onCollapseIdioms,
                                            testTag = "sentence_idioms_see_more"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 3: Tappable Word Chips with #F2B94B contextual meanings (collapsible at 10)
                    if (wordChipsCardIdx >= 0) {
                        item(key = "sentence_word_chips", contentType = "word_chips_card") {
                            val totalChips = state.wordChips.size
                            val visibleChips = if (chipsExpandedAll) {
                                state.wordChips
                            } else {
                                state.wordChips.take(SENTENCE_INITIAL_VISIBLE)
                            }
                            val onCollapseChips: (() -> Unit)? = if (chipsExpandedAll && totalChips > SENTENCE_INITIAL_VISIBLE) {
                                {
                                    chipsExpandedAll = false
                                    coroutineScope.launch { resultListState.animateScrollToItem(wordChipsCardIdx) }
                                }
                            } else {
                                null
                            }
                            val interaction = remember { MutableInteractionSource() }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, semantic.divider)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .then(
                                            if (onCollapseChips != null) {
                                                Modifier.combinedClickable(
                                                    interactionSource = interaction,
                                                    indication = null,
                                                    onClick = {},
                                                    onDoubleClick = onCollapseChips
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SectionHeaderWithArabic(
                                        englishTitle = "Words in Sentence",
                                        count = totalChips,
                                        titleColor = colors.onSurface
                                    )
                                    CompositionLocalProvider(
                                        LocalLayoutDirection provides if (state.isArabicInput) LayoutDirection.Rtl else LayoutDirection.Ltr
                                    ) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            visibleChips.forEach { chip ->
                                                InteractiveWordChip(
                                                    word = chip,
                                                    arabicSubtext = chipMeanings[chip.trim().lowercase(Locale.US)],
                                                    onClick = { viewModel.onChipTapped(chip) },
                                                    onDoubleClick = onCollapseChips,
                                                    modifier = Modifier.testTag("sentence_chip_$chip")
                                                )
                                            }
                                        }
                                    }

                                    if (totalChips > SENTENCE_INITIAL_VISIBLE) {
                                        FullWidthSeeMoreFooter(
                                            isExpanded = chipsExpandedAll,
                                            remainingCount = totalChips - SENTENCE_INITIAL_VISIBLE,
                                            onClick = {
                                                if (chipsExpandedAll) {
                                                    chipsExpandedAll = false
                                                    coroutineScope.launch { resultListState.animateScrollToItem(wordChipsCardIdx) }
                                                } else {
                                                    chipsExpandedAll = true
                                                }
                                            },
                                            onDoubleTapCollapse = onCollapseChips,
                                            testTag = "sentence_chips_see_more"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 4: More Examples (Tatoeba) with #6CB4FF translations & 10-item collapse
                    if (tatoebaCardIdx >= 0) {
                        item(key = "sentence_tatoeba", contentType = "tatoeba_examples_card") {
                            val totalTat = state.tatoebaSentences.size
                            val visibleTat = if (tatoebaExpandedAll) {
                                state.tatoebaSentences
                            } else {
                                state.tatoebaSentences.take(SENTENCE_INITIAL_VISIBLE)
                            }
                            val onCollapseTat: (() -> Unit)? = if (tatoebaExpandedAll && totalTat > SENTENCE_INITIAL_VISIBLE) {
                                {
                                    tatoebaExpandedAll = false
                                    coroutineScope.launch { resultListState.animateScrollToItem(tatoebaCardIdx) }
                                }
                            } else {
                                null
                            }
                            val interaction = remember { MutableInteractionSource() }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, semantic.divider)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .then(
                                            if (onCollapseTat != null) {
                                                Modifier.combinedClickable(
                                                    interactionSource = interaction,
                                                    indication = null,
                                                    onClick = {},
                                                    onDoubleClick = onCollapseTat
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    SectionHeaderWithArabic(
                                        englishTitle = "More Examples",
                                        count = totalTat,
                                        titleColor = colors.onSurface
                                    )
                                    visibleTat.forEachIndexed { idx, pair ->
                                        val exKey = "sent_tat_$idx"
                                        val exKeyAr = "sent_tat_ar_$idx"
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (state.isArabicInput) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                                        Text(
                                                            text = pair.arabic,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = colors.onSurface,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    SentenceSpeakerButton(
                                                        isPlaying = playingKey == exKeyAr,
                                                        onClick = {
                                                            if (playingKey == exKeyAr) audioManager.stopPlayback()
                                                            else audioManager.playSentenceAudio(
                                                                sentence = pair.arabic,
                                                                audioUrl = null,
                                                                key = exKeyAr
                                                            )
                                                        },
                                                        onDoubleClick = onCollapseTat
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
                                                            color = semantic.exampleTranslation,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                    SentenceDualSpeakerButtons(
                                                        playingKey = playingKey,
                                                        keyPrefix = exKey,
                                                        onPlayUs = {
                                                            audioManager.playSentenceAudio(
                                                                sentence = pair.english,
                                                                audioUrl = pair.audioUrl,
                                                                key = "${exKey}_us",
                                                                accentOverride = Accent.US
                                                            )
                                                        },
                                                        onPlayUk = {
                                                            audioManager.playSentenceAudio(
                                                                sentence = pair.english,
                                                                audioUrl = pair.audioUrl,
                                                                key = "${exKey}_uk",
                                                                accentOverride = Accent.UK
                                                            )
                                                        },
                                                        onStop = { audioManager.stopPlayback() },
                                                        onDoubleClick = onCollapseTat
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
                                                    SentenceDualSpeakerButtons(
                                                        playingKey = playingKey,
                                                        keyPrefix = exKey,
                                                        onPlayUs = {
                                                            audioManager.playSentenceAudio(
                                                                sentence = pair.english,
                                                                audioUrl = pair.audioUrl,
                                                                key = "${exKey}_us",
                                                                accentOverride = Accent.US
                                                            )
                                                        },
                                                        onPlayUk = {
                                                            audioManager.playSentenceAudio(
                                                                sentence = pair.english,
                                                                audioUrl = pair.audioUrl,
                                                                key = "${exKey}_uk",
                                                                accentOverride = Accent.UK
                                                            )
                                                        },
                                                        onStop = { audioManager.stopPlayback() },
                                                        onDoubleClick = onCollapseTat
                                                    )
                                                }
                                                ArabicTranslatedText(
                                                    text = pair.arabic,
                                                    isMachineTranslated = false,
                                                    showMachineLabel = false,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = semantic.exampleTranslation,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        if (idx < visibleTat.lastIndex) {
                                            HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                                        }
                                    }

                                    if (totalTat > SENTENCE_INITIAL_VISIBLE) {
                                        FullWidthSeeMoreFooter(
                                            isExpanded = tatoebaExpandedAll,
                                            remainingCount = totalTat - SENTENCE_INITIAL_VISIBLE,
                                            onClick = {
                                                if (tatoebaExpandedAll) {
                                                    tatoebaExpandedAll = false
                                                    coroutineScope.launch { resultListState.animateScrollToItem(tatoebaCardIdx) }
                                                } else {
                                                    tatoebaExpandedAll = true
                                                }
                                            },
                                            onDoubleTapCollapse = onCollapseTat,
                                            testTag = "sentence_tatoeba_see_more"
                                        )
                                    }
                                }
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
            showMachineLabel = false,
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
            title = { Text("Download over mobile data?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "You are currently on mobile data. Continue downloading the Arabic & English packs?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmCellularDownload() }) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCellularDownload() }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            }
        )
    }
}
