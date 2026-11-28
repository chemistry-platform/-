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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.core.AppLanguage
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.OfflinePackState
import com.example.data.repository.WordLoadState
import com.example.domain.model.SenseItem
import com.example.ui.components.AppIcons
import com.example.ui.components.ArabicTranslatedText
import com.example.ui.components.ChipWrapRow
import com.example.ui.components.CollapsibleChipsSection
import com.example.ui.components.CollapsibleSectionCard
import com.example.ui.components.ConfusableCategorySection
import com.example.ui.components.DirectionToggleChip
import com.example.ui.components.FullWidthSeeMoreFooter
import com.example.ui.components.HeadwordCard
import com.example.ui.components.HighlightedEnglishText
import com.example.ui.components.LiveSearchSuggestionRow
import com.example.ui.components.MissingArabicPackTopBanner
import com.example.ui.components.MissingEnglishTtsVoiceBanner
import com.example.ui.components.OfflineDictionaryPackBanner
import com.example.ui.components.PhrasalIdiomDetailCard
import com.example.ui.components.PhraseFallbackBottomSheet
import com.example.ui.components.PosBadgeWithArabic
import com.example.ui.components.PosGroupCard
import com.example.ui.components.ReverseArabicGroupsCard
import com.example.ui.components.SentenceDualSpeakerButtons
import com.example.ui.components.SentenceSpeakerButton
import com.example.ui.components.WORD_DETAIL_INITIAL_VISIBLE
import com.example.ui.components.clickableOrDoubleTap
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.viewmodel.UsageFilterOption
import com.example.ui.viewmodel.WordDetailViewModel
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordDetailScreen(
    viewModel: WordDetailViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenWord: (query: String, fallbackBase: String?, highlightChip: String?) -> Unit,
    onOpenSentence: (String) -> Unit,
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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val currentQuery by viewModel.currentQuery.collectAsStateWithLifecycle()
    val inlineQuery by viewModel.inlineQuery.collectAsStateWithLifecycle()
    val isSearchingInline by viewModel.isSearchingInline.collectAsStateWithLifecycle()
    val inlineSuggestions by viewModel.inlineSuggestions.collectAsStateWithLifecycle()
    val hasMoreInlineSuggestions by viewModel.hasMoreInlineSuggestions.collectAsStateWithLifecycle()
    val isExpandedAllInline by viewModel.isExpandedAllInline.collectAsStateWithLifecycle()

    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val usageFilter by viewModel.usageFilter.collectAsStateWithLifecycle()
    val highlightedChip by viewModel.highlightedChip.collectAsStateWithLifecycle()
    val confusableDetails by viewModel.confusableDetails.collectAsStateWithLifecycle()
    val chipMeanings by viewModel.chipMeanings.collectAsStateWithLifecycle()
    val phrasalDetails by viewModel.phrasalDetails.collectAsStateWithLifecycle()
    val phraseSheet by viewModel.phraseSheet.collectAsStateWithLifecycle()
    val showArabicTranslations by viewModel.showArabicTranslations.collectAsStateWithLifecycle()
    val audioSpeed by viewModel.audioSpeed.collectAsStateWithLifecycle()
    val isDownloadingPack by viewModel.isDownloadingPack.collectAsStateWithLifecycle()
    val showCellularConfirm by viewModel.showCellularConfirm.collectAsStateWithLifecycle()
    val packDownloadMessage by viewModel.packDownloadMessage.collectAsStateWithLifecycle()
    val playingKey by audioManager.playingKey.collectAsStateWithLifecycle()
    val ttsMissingEnglishVoice by audioManager.ttsMissingEnglishVoice.collectAsStateWithLifecycle()
    val ttsMissingAccentLabel by audioManager.ttsMissingAccentLabel.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val offlinePackState = viewModel.offlinePackState?.collectAsStateWithLifecycle()?.value

    var showAwlDialog by remember { mutableStateOf(false) }
    val liveListState = rememberLazyListState()
    val resultListState = rememberLazyListState()

    val speakChip: (String) -> Unit = remember(audioManager) {
        { chipWord ->
            if (ArabicTextUtils.containsArabicLetters(chipWord)) {
                audioManager.playSentenceAudio(chipWord, null, "chip_${chipWord.hashCode()}")
            } else if (ArabicTextUtils.isMultiWordQuery(chipWord)) {
                audioManager.playSentenceAudio(chipWord, null, "chip_${chipWord.hashCode()}", Accent.US)
            } else {
                audioManager.playHumanPronunciation(chipWord, Accent.US, null, "chip_${chipWord.hashCode()}")
            }
        }
    }

    LaunchedEffect(Unit) {
        audioManager.prepareTtsCheckIfNeeded()
        viewModel.openWordEvent.collect { target ->
            onOpenWord(target, null, null)
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

    val onToggleDirection = remember(viewModel) { { viewModel.toggleDirectionManual() } }
    val onToggleFavorite = remember(viewModel) { { viewModel.toggleFavorite() } }
    val onSelectUsageFilter = remember(viewModel) { { opt: UsageFilterOption -> viewModel.selectUsageFilter(opt) } }

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
            // Top bar + inline search with (X) clear button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("word_detail_back")
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
                        .testTag("word_detail_search_field"),
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
                                        onOpenSentence(trimmed)
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
                                    .testTag("word_detail_clear")
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
                                    onOpenSentence(trimmed)
                                } else {
                                    viewModel.openWordReady(trimmed)
                                }
                            }
                        }
                    )
                )

                DirectionToggleChip(
                    direction = direction,
                    onToggle = onToggleDirection,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            if (isSearchingInline) {
                // Typing immediately hides old entry and shows ONLY live results
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
                        .testTag("word_detail_live_search_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (inlineQuery.isNotBlank()) {
                        if (ArabicTextUtils.isMultiWordQuery(inlineQuery.trim())) {
                            item(key = "word_direct_sentence_row") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            keyboardController?.hide()
                                            onOpenSentence(inlineQuery.trim())
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
                            key = { idx, item -> "wd_sug_${idx}_${item.word}" }
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
                            item(key = "word_detail_see_more_footer") {
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
                                    testTag = "word_detail_live_see_more",
                                    onDoubleTapCollapse = collapseLiveList
                                )
                            }
                        }
                    }
                }
            } else {
                when (val state = uiState) {
                    is WordLoadState.Loading -> {
                        // Hold silently without any spinner or loading text
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(colors.background)
                        )
                    }

                    is WordLoadState.Error -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (state.isOffline) {
                                Icon(
                                    imageVector = AppIcons.WifiOff,
                                    contentDescription = "Offline",
                                    tint = colors.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onBackground
                            )
                            if (state.needsLanguagePack && !isOnline) {
                                MissingArabicPackTopBanner(
                                    isDownloading = isDownloadingPack,
                                    errorMessage = packDownloadMessage,
                                    onDownloadClick = { viewModel.requestDownloadArabicPack() },
                                    onOpenLanguages = onOpenLanguages
                                )
                            } else {
                                OfflineDictionaryPackBanner(
                                    isOnline = isOnline,
                                    packState = offlinePackState,
                                    onDownloadClick = { viewModel.requestOfflinePackDownload() }
                                )
                                Button(
                                    onClick = { viewModel.loadWord(currentQuery, forceRefresh = true) },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Retry",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }

                    is WordLoadState.Success -> {
                        val entry = state.entry
                        val expandedPosGroups = remember(entry.word) { mutableStateMapOf<String, Boolean>() }

                        // Section card expand/collapse
                        var synonymsExpanded by remember(entry.word) { mutableStateOf(true) }
                        var antonymsExpanded by remember(entry.word) { mutableStateOf(false) }
                        var collocationsExpanded by remember(entry.word) { mutableStateOf(false) }
                        var phrasalVerbsExpanded by remember(entry.word) { mutableStateOf(true) }
                        var idiomsExpanded by remember(entry.word) { mutableStateOf(true) }
                        var homophonesExpanded by remember(entry.word) { mutableStateOf(false) }
                        var lookAlikeExpanded by remember(entry.word) { mutableStateOf(false) }
                        var soundAlikeExpanded by remember(entry.word) { mutableStateOf(false) }
                        var compoundsExpanded by remember(entry.word) { mutableStateOf(false) }
                        var phrasesExpanded by remember(entry.word, highlightedChip) {
                            mutableStateOf(!highlightedChip.isNullOrBlank())
                        }
                        var familyExpanded by remember(entry.word) { mutableStateOf(false) }
                        var generalSpecExpanded by remember(entry.word) { mutableStateOf(false) }
                        var relatedExpanded by remember(entry.word) { mutableStateOf(false) }
                        var tatoebaExpanded by remember(entry.word) { mutableStateOf(true) }

                        // Per-section >10 "See more / See less" state
                        var synonymsShowAll by remember(entry.word) { mutableStateOf(false) }
                        var antonymsShowAll by remember(entry.word) { mutableStateOf(false) }
                        var collocationsShowAll by remember(entry.word) { mutableStateOf(false) }
                        var phrasalVerbsShowAll by remember(entry.word) { mutableStateOf(false) }
                        var idiomsShowAll by remember(entry.word) { mutableStateOf(false) }
                        var homophonesShowAll by remember(entry.word) { mutableStateOf(false) }
                        var lookAlikeShowAll by remember(entry.word) { mutableStateOf(false) }
                        var soundAlikeShowAll by remember(entry.word) { mutableStateOf(false) }
                        var compoundsShowAll by remember(entry.word) { mutableStateOf(false) }
                        var phrasesShowAll by remember(entry.word) { mutableStateOf(false) }
                        var familyShowAll by remember(entry.word) { mutableStateOf(false) }
                        var generalSpecShowAll by remember(entry.word) { mutableStateOf(false) }
                        var relatedShowAll by remember(entry.word) { mutableStateOf(false) }
                        var tatoebaShowAll by remember(entry.word) { mutableStateOf(false) }

                        val homophoneCandidates = remember(entry.confusableCandidates) {
                            entry.confusableCandidates.filter { "نفس النطق" in it.labels }
                        }
                        val lookAlikeCandidates = remember(entry.confusableCandidates) {
                            entry.confusableCandidates.filter { "كتابة مشابهة" in it.labels }
                        }
                        val soundAlikeCandidates = remember(entry.confusableCandidates) {
                            entry.confusableCandidates.filter { "نفس النطق" !in it.labels && "كتابة مشابهة" !in it.labels }
                        }

                        val displayPhrasals = remember(phrasalDetails, entry.phrasalVerbsAndIdioms) {
                            if (phrasalDetails.isEmpty()) {
                                entry.phrasalVerbsAndIdioms
                            } else {
                                (phrasalDetails + entry.phrasalVerbsAndIdioms)
                                    .distinctBy { it.phrase.trim().lowercase(Locale.US) }
                            }
                        }
                        val phrasalVerbItems = remember(displayPhrasals) {
                            displayPhrasals.filter { !it.isIdiom && it.category != "IDIOM" && it.category != "COMPOUND" }
                        }
                        val idiomItems = remember(displayPhrasals) {
                            displayPhrasals.filter { it.isIdiom || it.category == "IDIOM" }
                        }
                        val compoundDetailItems = remember(displayPhrasals) {
                            displayPhrasals.filter { it.category == "COMPOUND" }
                        }
                        val remainingCompoundChips = remember(entry.compoundExpressions, compoundDetailItems) {
                            val detailedKeys = compoundDetailItems.map { it.phrase.trim().lowercase(Locale.US) }.toSet()
                            entry.compoundExpressions.filter { it.trim().lowercase(Locale.US) !in detailedKeys }
                        }

                        val hasAnyUsageLabels by remember(entry) {
                            derivedStateOf {
                                entry.posGroups.any { g ->
                                    g.senses.any { it.usageLabels.isNotEmpty() || it.spellingVariantLabel != null }
                                }
                            }
                        }

                        val filteredPosGroups by remember(entry, usageFilter) {
                            derivedStateOf {
                                if (usageFilter == UsageFilterOption.ALL) {
                                    entry.posGroups
                                } else {
                                    entry.posGroups.mapNotNull { group ->
                                        val kept = group.senses.filter { matchesUsageFilter(it, usageFilter) }
                                        if (kept.isEmpty()) null else group.copy(senses = kept)
                                    }
                                }
                            }
                        }

                        val hasEnglishSentencesRequiringTts by remember(entry) {
                            derivedStateOf {
                                entry.posGroups.any { g ->
                                    g.senses.any { s ->
                                        s.examples.any { ex ->
                                            !ArabicTextUtils.containsArabicLetters(ex) &&
                                                s.exampleAudioUrls[ex].isNullOrBlank()
                                        }
                                    }
                                } || entry.tatoebaSentences.any { it.audioUrl.isNullOrBlank() }
                            }
                        }

                        val showOfflinePackBanner = offlinePackState is OfflinePackState.Downloading ||
                            (!isOnline && ((offlinePackState is OfflinePackState.Ready && !offlinePackState.isInstalled) ||
                                (offlinePackState is OfflinePackState.Paused) ||
                                (offlinePackState is OfflinePackState.Error && !offlinePackState.isInstalled)))
                        val showMissingArBanner = entry.missingTranslationPack && !isOnline
                        val showMissingTtsBanner = ttsMissingEnglishVoice && hasEnglishSentencesRequiringTts
                        val showReverseGroups = entry.isArabicWord && entry.reverseArabicGroups.isNotEmpty()
                        val showEmptyFilterNotice = filteredPosGroups.isEmpty() && usageFilter != UsageFilterOption.ALL

                        // Precompute LazyColumn item indices so double-tap collapse scrolls each section header into view
                        var curIdx = 0
                        if (showOfflinePackBanner) curIdx++
                        if (showMissingArBanner) curIdx++
                        if (showMissingTtsBanner) curIdx++
                        curIdx++ // headword
                        val reverseGroupsIdx = if (showReverseGroups) curIdx++ else -1
                        if (hasAnyUsageLabels) curIdx++
                        if (showEmptyFilterNotice) curIdx++
                        val posStartIdx = curIdx
                        curIdx += filteredPosGroups.size
                        val synonymsIdx = if (entry.synonyms.isNotEmpty()) curIdx++ else -1
                        val antonymsIdx = if (entry.antonyms.isNotEmpty()) curIdx++ else -1
                        val phrasalsIdx = if (phrasalVerbItems.isNotEmpty()) curIdx++ else -1
                        val idiomsIdx = if (idiomItems.isNotEmpty()) curIdx++ else -1
                        val collocationsIdx = if (entry.collocations.isNotEmpty()) curIdx++ else -1
                        val homophonesIdx = if (homophoneCandidates.isNotEmpty()) curIdx++ else -1
                        val lookAlikeIdx = if (lookAlikeCandidates.isNotEmpty()) curIdx++ else -1
                        val soundAlikeIdx = if (soundAlikeCandidates.isNotEmpty()) curIdx++ else -1
                        val compoundsIdx = if (compoundDetailItems.isNotEmpty() || remainingCompoundChips.isNotEmpty()) curIdx++ else -1
                        val phrasesIdx = if (entry.prepositionsAndPhrases.isNotEmpty()) curIdx++ else -1
                        val familyIdx = if (entry.wordFamily.isNotEmpty()) curIdx++ else -1
                        val genSpecIdx = if (entry.generalTerms.isNotEmpty() || entry.specificTerms.isNotEmpty()) curIdx++ else -1
                        val relatedIdx = if (entry.relatedWords.isNotEmpty()) curIdx++ else -1
                        val tatoebaIdx = if (entry.tatoebaSentences.isNotEmpty()) curIdx else -1

                        LazyColumn(
                            state = resultListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .testTag("word_detail_result_list"),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (showOfflinePackBanner) {
                                item(key = "offline_pack_banner", contentType = "banner") {
                                    OfflineDictionaryPackBanner(
                                        isOnline = isOnline,
                                        packState = offlinePackState,
                                        onDownloadClick = { viewModel.requestOfflinePackDownload() }
                                    )
                                }
                            }

                            if (showMissingArBanner) {
                                item(key = "missing_ar_pack_top", contentType = "banner") {
                                    MissingArabicPackTopBanner(
                                        isDownloading = isDownloadingPack,
                                        errorMessage = packDownloadMessage,
                                        onDownloadClick = { viewModel.requestDownloadArabicPack() },
                                        onOpenLanguages = onOpenLanguages
                                    )
                                }
                            }

                            if (showMissingTtsBanner) {
                                item(key = "missing_tts_voice", contentType = "banner") {
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

                            item(key = "headword_${entry.word}", contentType = "headword") {
                                HeadwordCard(
                                    entry = entry,
                                    isFavorite = isFavorite,
                                    audioManager = audioManager,
                                    audioSpeed = audioSpeed,
                                    showArabicTranslations = showArabicTranslations,
                                    chipMeanings = chipMeanings,
                                    onSelectSpeed = { audioManager.setAudioSpeed(it) },
                                    onToggleFavorite = onToggleFavorite,
                                    onOpenAwlInfo = { showAwlDialog = true },
                                    onOpenWord = { w -> viewModel.openWordReady(w) },
                                    onSpeak = speakChip,
                                    onSelectVocalizedForm = { vocalized -> viewModel.loadWord(vocalized) }
                                )
                            }

                            if (showReverseGroups) {
                                item(key = "reverse_arabic_groups_${entry.word}", contentType = "reverse_arabic_groups") {
                                    ReverseArabicGroupsCard(
                                        groups = entry.reverseArabicGroups,
                                        audioManager = audioManager,
                                        onOpenEnglishWord = { engWord -> viewModel.openWordReady(engWord) },
                                        onScrollToSection = {
                                            coroutineScope.launch {
                                                resultListState.animateScrollToItem(reverseGroupsIdx)
                                            }
                                        }
                                    )
                                }
                            }

                            if (hasAnyUsageLabels) {
                                item(key = "usage_filter_row", contentType = "filter_row") {
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            items = UsageFilterOption.entries,
                                            key = { it.name },
                                            contentType = { "usage_filter_chip" }
                                        ) { option ->
                                            val selected = option == usageFilter
                                            FilterChip(
                                                selected = selected,
                                                onClick = { onSelectUsageFilter(option) },
                                                label = {
                                                    Text(
                                                        text = option.labelFor(AppLanguage.ENGLISH),
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = colors.primaryContainer,
                                                    selectedLabelColor = colors.primary
                                                ),
                                                modifier = Modifier.testTag("usage_filter_${option.name}")
                                            )
                                        }
                                    }
                                }
                            }

                            if (showEmptyFilterNotice) {
                                item(key = "empty_filter_notice", contentType = "notice") {
                                    val filterLabel = usageFilter.labelFor(AppLanguage.ENGLISH)
                                    Text(
                                        text = "No meanings match \"$filterLabel\".",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }

                            filteredPosGroups.forEachIndexed { posOffset, group ->
                                val itemIndex = posStartIdx + posOffset
                                item(key = "pos_${group.partOfSpeech}", contentType = "pos_group") {
                                    PosGroupCard(
                                        word = entry.word,
                                        group = group,
                                        showAll = expandedPosGroups[group.partOfSpeech] == true,
                                        onToggleShowAll = {
                                            val cur = expandedPosGroups[group.partOfSpeech] == true
                                            expandedPosGroups[group.partOfSpeech] = !cur
                                            if (cur) {
                                                coroutineScope.launch { resultListState.animateScrollToItem(itemIndex) }
                                            }
                                        },
                                        onCollapseToTen = {
                                            expandedPosGroups[group.partOfSpeech] = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(itemIndex) }
                                        },
                                        showArabicTranslations = showArabicTranslations,
                                        playingKey = playingKey,
                                        chipMeanings = chipMeanings,
                                        onSpeakSentence = { sentence, url, key, accentOverride ->
                                            audioManager.playSentenceAudio(sentence, url, key, accentOverride)
                                        },
                                        onStopAudio = { audioManager.stopPlayback() },
                                        onChipClick = { viewModel.onChipTapped(it) },
                                        onOpenWord = { viewModel.openWordReady(it) },
                                        onSpeakChip = speakChip
                                    )
                                }
                            }

                            // 1. Synonyms
                            if (synonymsIdx >= 0) {
                                item(key = "sec_synonyms", contentType = "chips_section") {
                                    val onCollapse = if (synonymsShowAll && entry.synonyms.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            synonymsShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(synonymsIdx) }
                                            Unit
                                        }
                                    } else null
                                    CollapsibleChipsSection(
                                        title = "Synonyms",
                                        expanded = synonymsExpanded,
                                        onToggle = { synonymsExpanded = !synonymsExpanded },
                                        showAll = synonymsShowAll,
                                        onToggleShowAll = {
                                            val next = !synonymsShowAll
                                            synonymsShowAll = next
                                            if (!next) coroutineScope.launch { resultListState.animateScrollToItem(synonymsIdx) }
                                        },
                                        onDoubleCollapse = onCollapse,
                                        items = entry.synonyms,
                                        chipMeanings = chipMeanings,
                                        onChipClick = { viewModel.onChipTapped(it) },
                                        onSpeak = speakChip
                                    )
                                }
                            }

                            // 2. Antonyms
                            if (antonymsIdx >= 0) {
                                item(key = "sec_antonyms", contentType = "chips_section") {
                                    val onCollapse = if (antonymsShowAll && entry.antonyms.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            antonymsShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(antonymsIdx) }
                                            Unit
                                        }
                                    } else null
                                    CollapsibleChipsSection(
                                        title = "Antonyms",
                                        expanded = antonymsExpanded,
                                        onToggle = { antonymsExpanded = !antonymsExpanded },
                                        showAll = antonymsShowAll,
                                        onToggleShowAll = {
                                            val next = !antonymsShowAll
                                            antonymsShowAll = next
                                            if (!next) coroutineScope.launch { resultListState.animateScrollToItem(antonymsIdx) }
                                        },
                                        onDoubleCollapse = onCollapse,
                                        items = entry.antonyms,
                                        chipMeanings = chipMeanings,
                                        onChipClick = { viewModel.onChipTapped(it) },
                                        onSpeak = speakChip
                                    )
                                }
                            }

                            // 3. Phrasal Verbs
                            if (phrasalsIdx >= 0) {
                                item(key = "sec_phrasal_verbs", contentType = "phrasals_section") {
                                    val onCollapse = if (phrasalVerbsShowAll && phrasalVerbItems.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            phrasalVerbsShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(phrasalsIdx) }
                                            Unit
                                        }
                                    } else null
                                    val visibleItems = if (phrasalVerbsShowAll) phrasalVerbItems else phrasalVerbItems.take(WORD_DETAIL_INITIAL_VISIBLE)
                                    CollapsibleSectionCard(
                                        title = "Phrasal Verbs",
                                        count = phrasalVerbItems.size,
                                        expanded = phrasalVerbsExpanded,
                                        onToggle = { phrasalVerbsExpanded = !phrasalVerbsExpanded },
                                        onDoubleCollapse = onCollapse
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            visibleItems.forEachIndexed { idx, item ->
                                                PhrasalIdiomDetailCard(
                                                    item = item,
                                                    showArabicTranslations = showArabicTranslations,
                                                    playingKey = playingKey,
                                                    onTapPhrase = { viewModel.onChipTapped(item.phrase) },
                                                    onSpeak = { text, url, key, accentOverride ->
                                                        if (playingKey == key) audioManager.stopPlayback()
                                                        else audioManager.playSentenceAudio(text, url, key, accentOverride)
                                                    },
                                                    onStop = { audioManager.stopPlayback() },
                                                    onDoubleCollapse = onCollapse
                                                )
                                                if (idx < visibleItems.lastIndex) {
                                                    HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                                                }
                                            }
                                            if (phrasalVerbItems.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                                FullWidthSeeMoreFooter(
                                                    isExpanded = phrasalVerbsShowAll,
                                                    remainingCount = phrasalVerbItems.size - WORD_DETAIL_INITIAL_VISIBLE,
                                                    onClick = {
                                                        val next = !phrasalVerbsShowAll
                                                        phrasalVerbsShowAll = next
                                                        if (!next) coroutineScope.launch { resultListState.animateScrollToItem(phrasalsIdx) }
                                                    },
                                                    onDoubleTapCollapse = onCollapse,
                                                    testTag = "phrasal_verbs_see_more"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 4. Idioms & Expressions
                            if (idiomsIdx >= 0) {
                                item(key = "sec_idioms", contentType = "idioms_section") {
                                    val onCollapse = if (idiomsShowAll && idiomItems.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            idiomsShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(idiomsIdx) }
                                            Unit
                                        }
                                    } else null
                                    val visibleItems = if (idiomsShowAll) idiomItems else idiomItems.take(WORD_DETAIL_INITIAL_VISIBLE)
                                    CollapsibleSectionCard(
                                        title = "Idioms & Expressions",
                                        count = idiomItems.size,
                                        expanded = idiomsExpanded,
                                        onToggle = { idiomsExpanded = !idiomsExpanded },
                                        onDoubleCollapse = onCollapse
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            visibleItems.forEachIndexed { idx, item ->
                                                PhrasalIdiomDetailCard(
                                                    item = item,
                                                    showArabicTranslations = showArabicTranslations,
                                                    playingKey = playingKey,
                                                    onTapPhrase = { viewModel.onChipTapped(item.phrase) },
                                                    onSpeak = { text, url, key, accentOverride ->
                                                        if (playingKey == key) audioManager.stopPlayback()
                                                        else audioManager.playSentenceAudio(text, url, key, accentOverride)
                                                    },
                                                    onStop = { audioManager.stopPlayback() },
                                                    onDoubleCollapse = onCollapse
                                                )
                                                if (idx < visibleItems.lastIndex) {
                                                    HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                                                }
                                            }
                                            if (idiomItems.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                                FullWidthSeeMoreFooter(
                                                    isExpanded = idiomsShowAll,
                                                    remainingCount = idiomItems.size - WORD_DETAIL_INITIAL_VISIBLE,
                                                    onClick = {
                                                        val next = !idiomsShowAll
                                                        idiomsShowAll = next
                                                        if (!next) coroutineScope.launch { resultListState.animateScrollToItem(idiomsIdx) }
                                                    },
                                                    onDoubleTapCollapse = onCollapse,
                                                    testTag = "idioms_see_more"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Collocations
                            if (collocationsIdx >= 0) {
                                item(key = "sec_collocations", contentType = "chips_section") {
                                    val onCollapse = if (collocationsShowAll && entry.collocations.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            collocationsShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(collocationsIdx) }
                                            Unit
                                        }
                                    } else null
                                    CollapsibleChipsSection(
                                        title = "Collocations",
                                        expanded = collocationsExpanded,
                                        onToggle = { collocationsExpanded = !collocationsExpanded },
                                        showAll = collocationsShowAll,
                                        onToggleShowAll = {
                                            val next = !collocationsShowAll
                                            collocationsShowAll = next
                                            if (!next) coroutineScope.launch { resultListState.animateScrollToItem(collocationsIdx) }
                                        },
                                        onDoubleCollapse = onCollapse,
                                        items = entry.collocations,
                                        chipMeanings = chipMeanings,
                                        onChipClick = { viewModel.onChipTapped(it) },
                                        onSpeak = speakChip
                                    )
                                }
                            }

                            // 6. Homophones
                            if (homophonesIdx >= 0) {
                                item(key = "sec_homophones", contentType = "confusables_section") {
                                    val onCollapse = if (homophonesShowAll && homophoneCandidates.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            homophonesShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(homophonesIdx) }
                                            Unit
                                        }
                                    } else null
                                    ConfusableCategorySection(
                                        title = "Homophones",
                                        candidates = homophoneCandidates,
                                        expanded = homophonesExpanded,
                                        onToggle = {
                                            val next = !homophonesExpanded
                                            homophonesExpanded = next
                                            if (next) viewModel.loadConfusablesIfNeeded()
                                        },
                                        showAll = homophonesShowAll,
                                        onToggleShowAll = {
                                            val next = !homophonesShowAll
                                            homophonesShowAll = next
                                            if (!next) coroutineScope.launch { resultListState.animateScrollToItem(homophonesIdx) }
                                        },
                                        onDoubleCollapse = onCollapse,
                                        confusableDetails = confusableDetails,
                                        chipMeanings = chipMeanings,
                                        audioManager = audioManager,
                                        onLoadNeeded = { viewModel.loadConfusablesIfNeeded() },
                                        onTapCandidate = { viewModel.onChipTapped(it) },
                                        onSpeakChip = speakChip
                                    )
                                }
                            }

                            // 7. Look-alike Words
                            if (lookAlikeIdx >= 0) {
                                item(key = "sec_look_alike", contentType = "confusables_section") {
                                    val onCollapse = if (lookAlikeShowAll && lookAlikeCandidates.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            lookAlikeShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(lookAlikeIdx) }
                                            Unit
                                        }
                                    } else null
                                    ConfusableCategorySection(
                                        title = "Look-alike Words",
                                        candidates = lookAlikeCandidates,
                                        expanded = lookAlikeExpanded,
                                        onToggle = {
                                            val next = !lookAlikeExpanded
                                            lookAlikeExpanded = next
                                            if (next) viewModel.loadConfusablesIfNeeded()
                                        },
                                        showAll = lookAlikeShowAll,
                                        onToggleShowAll = {
                                            val next = !lookAlikeShowAll
                                            lookAlikeShowAll = next
                                            if (!next) coroutineScope.launch { resultListState.animateScrollToItem(lookAlikeIdx) }
                                        },
                                        onDoubleCollapse = onCollapse,
                                        confusableDetails = confusableDetails,
                                        chipMeanings = chipMeanings,
                                        audioManager = audioManager,
                                        onLoadNeeded = { viewModel.loadConfusablesIfNeeded() },
                                        onTapCandidate = { viewModel.onChipTapped(it) },
                                        onSpeakChip = speakChip
                                    )
                                }
                            }

                            // 8. Sound-alike Words
                            if (soundAlikeIdx >= 0) {
                                item(key = "sec_sound_alike", contentType = "confusables_section") {
                                    val onCollapse = if (soundAlikeShowAll && soundAlikeCandidates.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            soundAlikeShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(soundAlikeIdx) }
                                            Unit
                                        }
                                    } else null
                                    ConfusableCategorySection(
                                        title = "Sound-alike Words",
                                        candidates = soundAlikeCandidates,
                                        expanded = soundAlikeExpanded,
                                        onToggle = {
                                            val next = !soundAlikeExpanded
                                            soundAlikeExpanded = next
                                            if (next) viewModel.loadConfusablesIfNeeded()
                                        },
                                        showAll = soundAlikeShowAll,
                                        onToggleShowAll = {
                                            val next = !soundAlikeShowAll
                                            soundAlikeShowAll = next
                                            if (!next) coroutineScope.launch { resultListState.animateScrollToItem(soundAlikeIdx) }
                                        },
                                        onDoubleCollapse = onCollapse,
                                        confusableDetails = confusableDetails,
                                        chipMeanings = chipMeanings,
                                        audioManager = audioManager,
                                        onLoadNeeded = { viewModel.loadConfusablesIfNeeded() },
                                        onTapCandidate = { viewModel.onChipTapped(it) },
                                        onSpeakChip = speakChip
                                    )
                                }
                            }

                            // 9. Compound Expressions
                            if (compoundsIdx >= 0) {
                                item(key = "sec_compounds", contentType = "compounds_section") {
                                    val totalCompounds = compoundDetailItems.size + remainingCompoundChips.size
                                    val onCollapse = if (compoundsShowAll && totalCompounds > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            compoundsShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(compoundsIdx) }
                                            Unit
                                        }
                                    } else null
                                    val visibleDetailCompounds = if (compoundsShowAll) compoundDetailItems else compoundDetailItems.take(WORD_DETAIL_INITIAL_VISIBLE)
                                    val chipCap = if (compoundsShowAll) Int.MAX_VALUE else (WORD_DETAIL_INITIAL_VISIBLE - visibleDetailCompounds.size).coerceAtLeast(0)
                                    val visibleChipCompounds = remainingCompoundChips.take(chipCap)

                                    CollapsibleSectionCard(
                                        title = "Compound Expressions",
                                        count = totalCompounds,
                                        expanded = compoundsExpanded,
                                        onToggle = { compoundsExpanded = !compoundsExpanded },
                                        onDoubleCollapse = onCollapse
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            visibleDetailCompounds.forEachIndexed { idx, item ->
                                                PhrasalIdiomDetailCard(
                                                    item = item,
                                                    showArabicTranslations = showArabicTranslations,
                                                    playingKey = playingKey,
                                                    onTapPhrase = { viewModel.onChipTapped(item.phrase) },
                                                    onSpeak = { text, url, key, accentOverride ->
                                                        if (playingKey == key) audioManager.stopPlayback()
                                                        else audioManager.playSentenceAudio(text, url, key, accentOverride)
                                                    },
                                                    onStop = { audioManager.stopPlayback() },
                                                    onDoubleCollapse = onCollapse
                                                )
                                                if (idx < visibleDetailCompounds.lastIndex || visibleChipCompounds.isNotEmpty()) {
                                                    HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                                                }
                                            }
                                            if (visibleChipCompounds.isNotEmpty()) {
                                                ChipWrapRow(
                                                    items = visibleChipCompounds,
                                                    chipMeanings = chipMeanings,
                                                    onChipClick = { viewModel.onChipTapped(it) },
                                                    onSpeak = speakChip,
                                                    onDoubleClick = onCollapse
                                                )
                                            }
                                            if (totalCompounds > WORD_DETAIL_INITIAL_VISIBLE) {
                                                FullWidthSeeMoreFooter(
                                                    isExpanded = compoundsShowAll,
                                                    remainingCount = totalCompounds - WORD_DETAIL_INITIAL_VISIBLE,
                                                    onClick = {
                                                        val next = !compoundsShowAll
                                                        compoundsShowAll = next
                                                        if (!next) coroutineScope.launch { resultListState.animateScrollToItem(compoundsIdx) }
                                                    },
                                                    onDoubleTapCollapse = onCollapse,
                                                    testTag = "compounds_see_more"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 10. Phrases
                            if (phrasesIdx >= 0) {
                                item(key = "sec_phrases", contentType = "chips_section") {
                                    val onCollapse = if (phrasesShowAll && entry.prepositionsAndPhrases.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            phrasesShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(phrasesIdx) }
                                            Unit
                                        }
                                    } else null
                                    CollapsibleChipsSection(
                                        title = "Phrases",
                                        expanded = phrasesExpanded,
                                        onToggle = { phrasesExpanded = !phrasesExpanded },
                                        showAll = phrasesShowAll,
                                        onToggleShowAll = {
                                            val next = !phrasesShowAll
                                            phrasesShowAll = next
                                            if (!next) coroutineScope.launch { resultListState.animateScrollToItem(phrasesIdx) }
                                        },
                                        onDoubleCollapse = onCollapse,
                                        items = entry.prepositionsAndPhrases,
                                        chipMeanings = chipMeanings,
                                        highlightedChip = highlightedChip,
                                        onChipClick = { phrase ->
                                            viewModel.clearHighlightedChip()
                                            viewModel.onChipTapped(phrase)
                                        },
                                        onSpeak = speakChip
                                    )
                                }
                            }

                            // 11. Word Family
                            if (familyIdx >= 0) {
                                item(key = "sec_word_family", contentType = "word_family_section") {
                                    val onCollapse = if (familyShowAll && entry.wordFamily.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            familyShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(familyIdx) }
                                            Unit
                                        }
                                    } else null
                                    val visibleFamily = if (familyShowAll) entry.wordFamily else entry.wordFamily.take(WORD_DETAIL_INITIAL_VISIBLE)
                                    CollapsibleSectionCard(
                                        title = "Word Family",
                                        count = entry.wordFamily.size,
                                        expanded = familyExpanded,
                                        onToggle = { familyExpanded = !familyExpanded },
                                        onDoubleCollapse = onCollapse
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            visibleFamily.forEachIndexed { idx, member ->
                                                val memberArabic = chipMeanings[member.word.trim().lowercase(Locale.US)]
                                                val memberIsArabic = ArabicTextUtils.containsArabicLetters(member.word)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .clickableOrDoubleTap(
                                                            onClick = { viewModel.onChipTapped(member.word) },
                                                            onDoubleClick = onCollapse
                                                        )
                                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        CompositionLocalProvider(
                                                            LocalLayoutDirection provides if (memberIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                                                        ) {
                                                            Text(
                                                                text = member.word,
                                                                style = MaterialTheme.typography.labelLarge,
                                                                color = colors.secondary
                                                            )
                                                        }
                                                        if (!memberArabic.isNullOrBlank()) {
                                                            Text(
                                                                text = memberArabic,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = semantic.wordSubline
                                                            )
                                                        }
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        if (member.partOfSpeechArabic.isNotBlank()) {
                                                            PosBadgeWithArabic(
                                                                partOfSpeech = member.partOfSpeechArabic,
                                                                partOfSpeechArabic = member.partOfSpeechArabic
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = { speakChip(member.word) },
                                                            modifier = Modifier.size(40.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = AppIcons.VolumeUp,
                                                                contentDescription = "Pronounce ${member.word}",
                                                                tint = colors.primary,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                if (idx < visibleFamily.lastIndex) {
                                                    HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                                                }
                                            }
                                            if (entry.wordFamily.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                                FullWidthSeeMoreFooter(
                                                    isExpanded = familyShowAll,
                                                    remainingCount = entry.wordFamily.size - WORD_DETAIL_INITIAL_VISIBLE,
                                                    onClick = {
                                                        val next = !familyShowAll
                                                        familyShowAll = next
                                                        if (!next) coroutineScope.launch { resultListState.animateScrollToItem(familyIdx) }
                                                    },
                                                    onDoubleTapCollapse = onCollapse,
                                                    testTag = "word_family_see_more"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 12. Broader & Narrower Terms
                            if (genSpecIdx >= 0) {
                                item(key = "sec_general_specific", contentType = "general_specific_section") {
                                    val totalTerms = entry.generalTerms.size + entry.specificTerms.size
                                    val onCollapse = if (generalSpecShowAll && totalTerms > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            generalSpecShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(genSpecIdx) }
                                            Unit
                                        }
                                    } else null
                                    val visibleGen = if (generalSpecShowAll) entry.generalTerms else entry.generalTerms.take(WORD_DETAIL_INITIAL_VISIBLE)
                                    val specBudget = if (generalSpecShowAll) Int.MAX_VALUE else (WORD_DETAIL_INITIAL_VISIBLE - visibleGen.size).coerceAtLeast(0)
                                    val visibleSpec = entry.specificTerms.take(specBudget)

                                    CollapsibleSectionCard(
                                        title = "Related Terms",
                                        count = totalTerms,
                                        expanded = generalSpecExpanded,
                                        onToggle = { generalSpecExpanded = !generalSpecExpanded },
                                        onDoubleCollapse = onCollapse
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            if (visibleGen.isNotEmpty()) {
                                                Text(
                                                    text = "Broader terms",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.onSurfaceVariant
                                                )
                                                ChipWrapRow(
                                                    items = visibleGen,
                                                    chipMeanings = chipMeanings,
                                                    onChipClick = { viewModel.onChipTapped(it) },
                                                    onSpeak = speakChip,
                                                    onDoubleClick = onCollapse
                                                )
                                            }
                                            if (visibleSpec.isNotEmpty()) {
                                                Text(
                                                    text = "Narrower terms",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.onSurfaceVariant
                                                )
                                                ChipWrapRow(
                                                    items = visibleSpec,
                                                    chipMeanings = chipMeanings,
                                                    onChipClick = { viewModel.onChipTapped(it) },
                                                    onSpeak = speakChip,
                                                    onDoubleClick = onCollapse
                                                )
                                            }
                                            if (totalTerms > WORD_DETAIL_INITIAL_VISIBLE) {
                                                FullWidthSeeMoreFooter(
                                                    isExpanded = generalSpecShowAll,
                                                    remainingCount = totalTerms - WORD_DETAIL_INITIAL_VISIBLE,
                                                    onClick = {
                                                        val next = !generalSpecShowAll
                                                        generalSpecShowAll = next
                                                        if (!next) coroutineScope.launch { resultListState.animateScrollToItem(genSpecIdx) }
                                                    },
                                                    onDoubleTapCollapse = onCollapse,
                                                    testTag = "general_specific_see_more"
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 13. Related Terms
                            if (relatedIdx >= 0) {
                                item(key = "sec_related", contentType = "chips_section") {
                                    val onCollapse = if (relatedShowAll && entry.relatedWords.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            relatedShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(relatedIdx) }
                                            Unit
                                        }
                                    } else null
                                    CollapsibleChipsSection(
                                        title = "Related Terms",
                                        expanded = relatedExpanded,
                                        onToggle = { relatedExpanded = !relatedExpanded },
                                        showAll = relatedShowAll,
                                        onToggleShowAll = {
                                            val next = !relatedShowAll
                                            relatedShowAll = next
                                            if (!next) coroutineScope.launch { resultListState.animateScrollToItem(relatedIdx) }
                                        },
                                        onDoubleCollapse = onCollapse,
                                        items = entry.relatedWords,
                                        chipMeanings = chipMeanings,
                                        onChipClick = { viewModel.onChipTapped(it) },
                                        onSpeak = speakChip
                                    )
                                }
                            }

                            // 14. More Examples (Tatoeba)
                            if (tatoebaIdx >= 0) {
                                item(key = "sec_tatoeba", contentType = "tatoeba_section") {
                                    val onCollapse = if (tatoebaShowAll && entry.tatoebaSentences.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                        {
                                            tatoebaShowAll = false
                                            coroutineScope.launch { resultListState.animateScrollToItem(tatoebaIdx) }
                                            Unit
                                        }
                                    } else null
                                    val visibleTat = if (tatoebaShowAll) entry.tatoebaSentences else entry.tatoebaSentences.take(WORD_DETAIL_INITIAL_VISIBLE)
                                    CollapsibleSectionCard(
                                        title = "More Examples",
                                        count = entry.tatoebaSentences.size,
                                        expanded = tatoebaExpanded,
                                        onToggle = { tatoebaExpanded = !tatoebaExpanded },
                                        onDoubleCollapse = onCollapse
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            visibleTat.forEachIndexed { idx, pair ->
                                                val tatKey = "tat_${entry.word}_$idx"
                                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    if (entry.isArabicWord) {
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
                                                                isPlaying = playingKey == "${tatKey}_ar",
                                                                onClick = {
                                                                    if (playingKey == "${tatKey}_ar") audioManager.stopPlayback()
                                                                    else audioManager.playSentenceAudio(
                                                                        sentence = pair.arabic,
                                                                        audioUrl = null,
                                                                        key = "${tatKey}_ar"
                                                                    )
                                                                },
                                                                onDoubleClick = onCollapse,
                                                                modifier = Modifier.testTag("tatoeba_speak_ar_$idx")
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
                                                                keyPrefix = tatKey,
                                                                onPlayUs = {
                                                                    audioManager.playSentenceAudio(
                                                                        sentence = pair.english,
                                                                        audioUrl = pair.audioUrl,
                                                                        key = "${tatKey}_us",
                                                                        accentOverride = Accent.US
                                                                    )
                                                                },
                                                                onPlayUk = {
                                                                    audioManager.playSentenceAudio(
                                                                        sentence = pair.english,
                                                                        audioUrl = pair.audioUrl,
                                                                        key = "${tatKey}_uk",
                                                                        accentOverride = Accent.UK
                                                                    )
                                                                },
                                                                onStop = { audioManager.stopPlayback() },
                                                                onDoubleClick = onCollapse
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
                                                                targetWord = entry.word,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            SentenceDualSpeakerButtons(
                                                                playingKey = playingKey,
                                                                keyPrefix = tatKey,
                                                                onPlayUs = {
                                                                    audioManager.playSentenceAudio(
                                                                        sentence = pair.english,
                                                                        audioUrl = pair.audioUrl,
                                                                        key = "${tatKey}_us",
                                                                        accentOverride = Accent.US
                                                                    )
                                                                },
                                                                onPlayUk = {
                                                                    audioManager.playSentenceAudio(
                                                                        sentence = pair.english,
                                                                        audioUrl = pair.audioUrl,
                                                                        key = "${tatKey}_uk",
                                                                        accentOverride = Accent.UK
                                                                    )
                                                                },
                                                                onStop = { audioManager.stopPlayback() },
                                                                onDoubleClick = onCollapse
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

                                            if (entry.tatoebaSentences.size > WORD_DETAIL_INITIAL_VISIBLE) {
                                                FullWidthSeeMoreFooter(
                                                    isExpanded = tatoebaShowAll,
                                                    remainingCount = entry.tatoebaSentences.size - WORD_DETAIL_INITIAL_VISIBLE,
                                                    onClick = {
                                                        val next = !tatoebaShowAll
                                                        tatoebaShowAll = next
                                                        if (!next) coroutineScope.launch { resultListState.animateScrollToItem(tatoebaIdx) }
                                                    },
                                                    onDoubleTapCollapse = onCollapse,
                                                    testTag = "tatoeba_see_more"
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
        }
    }

    phraseSheet?.let { sheetData ->
        PhraseFallbackBottomSheet(
            sheetData = sheetData,
            showMachineLabel = false,
            audioManager = audioManager,
            playingKey = playingKey,
            onOpenSentenceLookup = { phrase -> onOpenSentence(phrase) },
            onDismiss = { viewModel.dismissPhraseSheet() }
        )
    }

    if (showAwlDialog) {
        AlertDialog(
            onDismissRequest = { showAwlDialog = false },
            containerColor = colors.surface,
            title = { Text("Academic Word (AWL)", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    text = "From the Academic Word List (AWL), commonly studied for TOEFL and IELTS preparation. Not an official ETS or IELTS list.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showAwlDialog = false }) {
                    Text("OK", color = colors.primary)
                }
            }
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

private fun matchesUsageFilter(sense: SenseItem, filter: UsageFilterOption): Boolean {
    return when (filter) {
        UsageFilterOption.ALL -> true
        UsageFilterOption.FORMAL -> "رسمي" in sense.usageLabels
        UsageFilterOption.INFORMAL -> "غير رسمي" in sense.usageLabels
        UsageFilterOption.US -> "أمريكي" in sense.usageLabels || sense.spellingVariantLabel == "إملاء أمريكي"
        UsageFilterOption.UK -> "بريطاني" in sense.usageLabels || sense.spellingVariantLabel == "إملاء بريطاني"
    }
}
