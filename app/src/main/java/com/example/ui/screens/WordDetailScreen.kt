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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.Accent
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.data.repository.WordLoadState
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.PhraseDetailItem
import com.example.domain.model.PosSenseGroup
import com.example.domain.model.SenseItem
import com.example.domain.model.WordEntry
import com.example.ui.components.AppIcons
import com.example.ui.components.ArabicTranslatedText
import com.example.ui.components.AudioSpeedSelector
import com.example.ui.components.ChipWrapRow
import com.example.ui.components.CompactWordAudioButtons
import com.example.ui.components.DirectionToggleChip
import com.example.ui.components.HighlightedEnglishText
import com.example.ui.components.InstantTranslationCard
import com.example.ui.components.InteractiveWordChip
import com.example.ui.components.LoadingCard
import com.example.ui.components.MissingArabicPackTopBanner
import com.example.ui.components.MissingEnglishTtsVoiceBanner
import com.example.ui.components.PhraseFallbackBottomSheet
import com.example.ui.components.PosChip
import com.example.ui.components.PronunciationPillButton
import com.example.ui.components.SentenceDualSpeakerButtons
import com.example.ui.components.SentenceSpeakerButton
import com.example.ui.components.UsageLabelChip
import com.example.ui.viewmodel.UsageFilterOption
import com.example.ui.viewmodel.WordDetailViewModel
import java.util.Locale

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val currentQuery by viewModel.currentQuery.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val usageFilter by viewModel.usageFilter.collectAsStateWithLifecycle()
    val highlightedChip by viewModel.highlightedChip.collectAsStateWithLifecycle()
    val confusableDetails by viewModel.confusableDetails.collectAsStateWithLifecycle()
    val isLoadingConfusables by viewModel.isLoadingConfusables.collectAsStateWithLifecycle()
    val chipMeanings by viewModel.chipMeanings.collectAsStateWithLifecycle()
    val phrasalDetails by viewModel.phrasalDetails.collectAsStateWithLifecycle()
    val isLoadingPhrasals by viewModel.isLoadingPhrasals.collectAsStateWithLifecycle()
    val phraseSheet by viewModel.phraseSheet.collectAsStateWithLifecycle()
    val inlineTranslation by viewModel.inlineTranslation.collectAsStateWithLifecycle()
    val showArabicTranslations by viewModel.showArabicTranslations.collectAsStateWithLifecycle()
    val showMachineLabel by viewModel.showMachineTranslationLabel.collectAsStateWithLifecycle()
    val audioSpeed by viewModel.audioSpeed.collectAsStateWithLifecycle()
    val isDownloadingPack by viewModel.isDownloadingPack.collectAsStateWithLifecycle()
    val showCellularConfirm by viewModel.showCellularConfirm.collectAsStateWithLifecycle()
    val packDownloadMessage by viewModel.packDownloadMessage.collectAsStateWithLifecycle()
    val playingKey by audioManager.playingKey.collectAsStateWithLifecycle()
    val ttsMissingEnglishVoice by audioManager.ttsMissingEnglishVoice.collectAsStateWithLifecycle()
    val ttsMissingAccentLabel by audioManager.ttsMissingAccentLabel.collectAsStateWithLifecycle()

    var inlineQuery by remember(currentQuery) { mutableStateOf(currentQuery) }
    var showAwlDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        audioManager.prepareTtsCheckIfNeeded()
    }

    LaunchedEffect(Unit) {
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
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            // Top bar + inline search
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
                        .testTag("word_detail_search_field"),
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
                                    onOpenSentence(trimmed)
                                } else {
                                    viewModel.loadWord(trimmed)
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

            // BUG 6: Live instant translation directly under the search bar while typing
            inlineTranslation?.let { instant ->
                InstantTranslationCard(
                    result = instant,
                    showMachineLabel = showMachineLabel,
                    audioManager = audioManager,
                    playingKey = playingKey,
                    onOpenFull = {
                        val trimmed = instant.query.trim()
                        if (ArabicTextUtils.isMultiWordQuery(trimmed)) {
                            onOpenSentence(trimmed)
                        } else {
                            viewModel.loadWord(trimmed)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            when (val state = uiState) {
                is WordLoadState.Loading -> {
                    LoadingCard(message = "جاري جلب المداخل من المصادر المفتوحة...")
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
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onBackground
                        )
                        if (state.needsLanguagePack) {
                            MissingArabicPackTopBanner(
                                isDownloading = isDownloadingPack,
                                errorMessage = packDownloadMessage,
                                onDownloadClick = { viewModel.requestDownloadArabicPack() },
                                onOpenLanguages = onOpenLanguages
                            )
                        } else {
                            Button(
                                onClick = { viewModel.loadWord(currentQuery, forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إعادة المحاولة", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                is WordLoadState.Success -> {
                    val entry = state.entry
                    val expandedPosGroups = remember(entry.word) { mutableStateMapOf<String, Boolean>() }

                    var synonymsExpanded by remember(entry.word) { mutableStateOf(true) }
                    var antonymsExpanded by remember(entry.word) { mutableStateOf(false) }
                    var collocationsExpanded by remember(entry.word) { mutableStateOf(false) }
                    var phrasalVerbsExpanded by remember(entry.word) { mutableStateOf(true) }
                    var idiomsExpanded by remember(entry.word) { mutableStateOf(true) }
                    var compoundsExpanded by remember(entry.word) { mutableStateOf(false) }
                    var phrasesExpanded by remember(entry.word, highlightedChip) {
                        mutableStateOf(!highlightedChip.isNullOrBlank())
                    }
                    var familyExpanded by remember(entry.word) { mutableStateOf(false) }
                    var confusablesExpanded by remember(entry.word) { mutableStateOf(false) }
                    var generalSpecExpanded by remember(entry.word) { mutableStateOf(false) }
                    var relatedExpanded by remember(entry.word) { mutableStateOf(false) }
                    var tatoebaExpanded by remember(entry.word) { mutableStateOf(true) }

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

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (state.isUpdating) {
                            item(key = "updating_bar", contentType = "progress") {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = colors.secondary,
                                    trackColor = colors.outline
                                )
                            }
                        }

                        // Section 19.11: Missing Arabic pack banner with direct Download button at top
                        if (entry.missingTranslationPack) {
                            item(key = "missing_ar_pack_top", contentType = "banner") {
                                MissingArabicPackTopBanner(
                                    isDownloading = isDownloadingPack,
                                    errorMessage = packDownloadMessage,
                                    onDownloadClick = { viewModel.requestDownloadArabicPack() },
                                    onOpenLanguages = onOpenLanguages
                                )
                            }
                        }

                        // Section 19.7: Missing English TTS voice prompt when needed
                        if (ttsMissingEnglishVoice && hasEnglishSentencesRequiringTts) {
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

                        // Headword Card with Human US/UK Audio Buttons, Speed Selector, AWL chip & Quick Translations
                        item(key = "headword_${entry.word}", contentType = "headword") {
                            HeadwordCard(
                                entry = entry,
                                isFavorite = isFavorite,
                                audioManager = audioManager,
                                audioSpeed = audioSpeed,
                                showArabicTranslations = showArabicTranslations,
                                showMachineLabel = showMachineLabel,
                                chipMeanings = chipMeanings,
                                onSelectSpeed = { audioManager.setAudioSpeed(it) },
                                onToggleFavorite = onToggleFavorite,
                                onOpenAwlInfo = { showAwlDialog = true },
                                onChipClick = { w -> viewModel.onChipTapped(w) },
                                onSelectVocalizedForm = { vocalized -> viewModel.loadWord(vocalized) }
                            )
                        }

                        // Section 13.3: Usage filter row (hidden if NO sense has a label)
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
                                                    text = option.labelAr,
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

                        if (filteredPosGroups.isEmpty() && usageFilter != UsageFilterOption.ALL) {
                            item(key = "empty_filter_notice", contentType = "notice") {
                                Text(
                                    text = "لا توجد معانٍ مطابقة لتصفية \"${usageFilter.labelAr}\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        // Sense-by-sense layout grouped by POS (English/source first, Arabic/target directly BELOW)
                        filteredPosGroups.forEach { group ->
                            item(key = "pos_${group.partOfSpeech}", contentType = "pos_group") {
                                PosGroupCard(
                                    word = entry.word,
                                    group = group,
                                    showAll = expandedPosGroups[group.partOfSpeech] == true,
                                    onToggleShowAll = {
                                        val cur = expandedPosGroups[group.partOfSpeech] == true
                                        expandedPosGroups[group.partOfSpeech] = !cur
                                    },
                                    showArabicTranslations = showArabicTranslations,
                                    showMachineLabel = showMachineLabel,
                                    playingKey = playingKey,
                                    chipMeanings = chipMeanings,
                                    onSpeakSentence = { sentence, url, key, accentOverride ->
                                        audioManager.playSentenceAudio(sentence, url, key, accentOverride)
                                    },
                                    onStopAudio = { audioManager.stopPlayback() },
                                    onChipClick = { viewModel.onChipTapped(it) },
                                    onOpenWord = { onOpenWord(it, null, null) }
                                )
                            }
                        }

                        if (entry.hasWiktionaryAttribution) {
                            item(key = "wiktionary_attribution", contentType = "attribution") {
                                Text(
                                    text = "المصدر: ويكاموس (Wiktionary) • CC BY-SA 4.0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        // Phrasal Verbs section
                        if (phrasalVerbItems.isNotEmpty() || (isLoadingPhrasals && !entry.isArabicWord && displayPhrasals.isEmpty())) {
                            item(key = "sec_phrasal_verbs", contentType = "phrasals_section") {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.phrasal_verbs_title),
                                    count = phrasalVerbItems.size,
                                    expanded = phrasalVerbsExpanded,
                                    onToggle = { phrasalVerbsExpanded = !phrasalVerbsExpanded }
                                ) {
                                    if (isLoadingPhrasals && phrasalVerbItems.isEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                color = colors.secondary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "جاري تحميل معاني الأفعال المركبة...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            phrasalVerbItems.forEachIndexed { idx, item ->
                                                PhrasalIdiomDetailCard(
                                                    item = item,
                                                    showArabicTranslations = showArabicTranslations,
                                                    showMachineLabel = showMachineLabel,
                                                    playingKey = playingKey,
                                                    onTapPhrase = { viewModel.onChipTapped(item.phrase) },
                                                    onSpeak = { text, url, key, accentOverride ->
                                                        if (playingKey == key) audioManager.stopPlayback()
                                                        else audioManager.playSentenceAudio(text, url, key, accentOverride)
                                                    },
                                                    onStop = { audioManager.stopPlayback() }
                                                )
                                                if (idx < phrasalVerbItems.lastIndex) {
                                                    HorizontalDivider(color = colors.outline, thickness = 1.dp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Idioms & Expressions section
                        if (idiomItems.isNotEmpty()) {
                            item(key = "sec_idioms", contentType = "idioms_section") {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.idioms_expressions_title),
                                    count = idiomItems.size,
                                    expanded = idiomsExpanded,
                                    onToggle = { idiomsExpanded = !idiomsExpanded }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        idiomItems.forEachIndexed { idx, item ->
                                            PhrasalIdiomDetailCard(
                                                item = item,
                                                showArabicTranslations = showArabicTranslations,
                                                showMachineLabel = showMachineLabel,
                                                playingKey = playingKey,
                                                onTapPhrase = { viewModel.onChipTapped(item.phrase) },
                                                onSpeak = { text, url, key, accentOverride ->
                                                    if (playingKey == key) audioManager.stopPlayback()
                                                    else audioManager.playSentenceAudio(text, url, key, accentOverride)
                                                },
                                                onStop = { audioManager.stopPlayback() }
                                            )
                                            if (idx < idiomItems.lastIndex) {
                                                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Compound Expressions section
                        if (compoundDetailItems.isNotEmpty() || remainingCompoundChips.isNotEmpty()) {
                            item(key = "sec_compounds", contentType = "compounds_section") {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.compound_expressions_title),
                                    count = compoundDetailItems.size + remainingCompoundChips.size,
                                    expanded = compoundsExpanded,
                                    onToggle = { compoundsExpanded = !compoundsExpanded }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        compoundDetailItems.forEachIndexed { idx, item ->
                                            PhrasalIdiomDetailCard(
                                                item = item,
                                                showArabicTranslations = showArabicTranslations,
                                                showMachineLabel = showMachineLabel,
                                                playingKey = playingKey,
                                                onTapPhrase = { viewModel.onChipTapped(item.phrase) },
                                                onSpeak = { text, url, key, accentOverride ->
                                                    if (playingKey == key) audioManager.stopPlayback()
                                                    else audioManager.playSentenceAudio(text, url, key, accentOverride)
                                                },
                                                onStop = { audioManager.stopPlayback() }
                                            )
                                            if (idx < compoundDetailItems.lastIndex || remainingCompoundChips.isNotEmpty()) {
                                                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                                            }
                                        }
                                        if (remainingCompoundChips.isNotEmpty()) {
                                            ChipWrapRow(
                                                items = remainingCompoundChips,
                                                chipMeanings = chipMeanings,
                                                onChipClick = { viewModel.onChipTapped(it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Collapsible Sections (shown ONLY if real data exists)
                        if (entry.synonyms.isNotEmpty()) {
                            item(key = "sec_synonyms", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.synonyms_title),
                                    expanded = synonymsExpanded,
                                    onToggle = { synonymsExpanded = !synonymsExpanded },
                                    items = entry.synonyms,
                                    chipMeanings = chipMeanings,
                                    onChipClick = { viewModel.onChipTapped(it) }
                                )
                            }
                        }

                        if (entry.antonyms.isNotEmpty()) {
                            item(key = "sec_antonyms", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.antonyms_title),
                                    expanded = antonymsExpanded,
                                    onToggle = { antonymsExpanded = !antonymsExpanded },
                                    items = entry.antonyms,
                                    chipMeanings = chipMeanings,
                                    onChipClick = { viewModel.onChipTapped(it) }
                                )
                            }
                        }

                        if (entry.collocations.isNotEmpty()) {
                            item(key = "sec_collocations", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.collocations_title),
                                    expanded = collocationsExpanded,
                                    onToggle = { collocationsExpanded = !collocationsExpanded },
                                    items = entry.collocations,
                                    chipMeanings = chipMeanings,
                                    onChipClick = { viewModel.onChipTapped(it) }
                                )
                            }
                        }

                        if (entry.prepositionsAndPhrases.isNotEmpty()) {
                            item(key = "sec_phrases", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.phrases_title),
                                    expanded = phrasesExpanded,
                                    onToggle = { phrasesExpanded = !phrasesExpanded },
                                    items = entry.prepositionsAndPhrases,
                                    chipMeanings = chipMeanings,
                                    highlightedChip = highlightedChip,
                                    onChipClick = { phrase ->
                                        viewModel.clearHighlightedChip()
                                        viewModel.onChipTapped(phrase)
                                    }
                                )
                            }
                        }

                        if (entry.wordFamily.isNotEmpty()) {
                            item(key = "sec_word_family", contentType = "word_family_section") {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.similar_words_title),
                                    count = entry.wordFamily.size,
                                    expanded = familyExpanded,
                                    onToggle = { familyExpanded = !familyExpanded }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        entry.wordFamily.forEachIndexed { idx, member ->
                                            val memberArabic = chipMeanings[member.word.trim().lowercase(Locale.US)]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { viewModel.onChipTapped(member.word) }
                                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                                                            color = colors.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                if (member.partOfSpeechArabic.isNotBlank()) {
                                                    PosChip(text = member.partOfSpeechArabic)
                                                }
                                            }
                                            if (idx < entry.wordFamily.lastIndex) {
                                                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (entry.confusableCandidates.isNotEmpty()) {
                            item(key = "sec_confusables", contentType = "confusables_section") {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.confusables_title),
                                    count = entry.confusableCandidates.size,
                                    expanded = confusablesExpanded,
                                    onToggle = {
                                        val next = !confusablesExpanded
                                        confusablesExpanded = next
                                        if (next) {
                                            viewModel.loadConfusablesIfNeeded()
                                        }
                                    }
                                ) {
                                    LaunchedEffect(confusablesExpanded) {
                                        if (confusablesExpanded) {
                                            viewModel.loadConfusablesIfNeeded()
                                        }
                                    }
                                    if (isLoadingConfusables && confusableDetails.isEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 12.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                color = colors.secondary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "جاري تحميل الكلمات المتشابهة...",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    } else if (confusableDetails.isEmpty()) {
                                        Text(
                                            text = "لا توجد مداخل قاموسية مؤكدة لهذه الكلمات.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            entry.confusableCandidates.forEach { candidate ->
                                                val detail = confusableDetails[candidate.word.lowercase(Locale.US)]
                                                if (detail != null) {
                                                    ConfusableCard(
                                                        detail = detail,
                                                        wordArabicMeaning = chipMeanings[detail.word.trim().lowercase(Locale.US)],
                                                        audioManager = audioManager,
                                                        onOpenWord = { viewModel.onChipTapped(detail.word) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (entry.generalTerms.isNotEmpty() || entry.specificTerms.isNotEmpty()) {
                            item(key = "sec_general_specific", contentType = "general_specific_section") {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.hypernyms_hyponyms_title),
                                    count = entry.generalTerms.size + entry.specificTerms.size,
                                    expanded = generalSpecExpanded,
                                    onToggle = { generalSpecExpanded = !generalSpecExpanded }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (entry.generalTerms.isNotEmpty()) {
                                            Text(
                                                text = "مصطلحات أعم",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.onSurfaceVariant
                                            )
                                            ChipWrapRow(
                                                items = entry.generalTerms,
                                                chipMeanings = chipMeanings,
                                                onChipClick = { viewModel.onChipTapped(it) }
                                            )
                                        }
                                        if (entry.specificTerms.isNotEmpty()) {
                                            Text(
                                                text = "مصطلحات أخص",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.onSurfaceVariant
                                            )
                                            ChipWrapRow(
                                                items = entry.specificTerms,
                                                chipMeanings = chipMeanings,
                                                onChipClick = { viewModel.onChipTapped(it) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (entry.relatedWords.isNotEmpty()) {
                            item(key = "sec_related", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.related_terms_title),
                                    expanded = relatedExpanded,
                                    onToggle = { relatedExpanded = !relatedExpanded },
                                    items = entry.relatedWords,
                                    chipMeanings = chipMeanings,
                                    onChipClick = { viewModel.onChipTapped(it) }
                                )
                            }
                        }

                        // Tatoeba More Example Sentences (Human Translations + Standalone Speaker Button for EN & AR)
                        if (entry.tatoebaSentences.isNotEmpty()) {
                            item(key = "sec_tatoeba", contentType = "tatoeba_section") {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.more_examples_title),
                                    count = entry.tatoebaSentences.size,
                                    expanded = tatoebaExpanded,
                                    onToggle = { tatoebaExpanded = !tatoebaExpanded }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        entry.tatoebaSentences.forEachIndexed { idx, pair ->
                                            val tatKey = "tat_${entry.word}_$idx"
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (entry.isArabicWord) {
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
                                                            isPlaying = playingKey == "${tatKey}_ar",
                                                            onClick = {
                                                                if (playingKey == "${tatKey}_ar") {
                                                                    audioManager.stopPlayback()
                                                                } else {
                                                                    audioManager.playSentenceAudio(
                                                                        sentence = pair.arabic,
                                                                        audioUrl = null,
                                                                        key = "${tatKey}_ar"
                                                                    )
                                                                }
                                                            },
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
                                                                color = colors.onSurfaceVariant,
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
                                                            onStop = { audioManager.stopPlayback() }
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
                                                            onStop = { audioManager.stopPlayback() }
                                                        )
                                                    }
                                                    Text(
                                                        text = pair.arabic,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (idx < entry.tatoebaSentences.lastIndex) {
                                                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                                            }
                                        }
                                        if (entry.hasTatoebaAttribution) {
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
            }
        }
    }

    phraseSheet?.let { sheetData ->
        PhraseFallbackBottomSheet(
            sheetData = sheetData,
            showMachineLabel = showMachineLabel,
            audioManager = audioManager,
            playingKey = playingKey,
            onOpenSentenceLookup = { phrase -> onOpenSentence(phrase) },
            onDismiss = { viewModel.dismissPhraseSheet() }
        )
    }

    // Section 12.3: Academic Word List (AWL) attribution dialog
    if (showAwlDialog) {
        AlertDialog(
            onDismissRequest = { showAwlDialog = false },
            containerColor = colors.surface,
            title = {
                Text("كلمة أكاديمية (AWL)", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(
                    text = "من قائمة الكلمات الأكاديمية (Academic Word List) الشائعة في التحضير للتوفل والآيلتس. ليست قائمة رسمية من ETS أو IELTS.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showAwlDialog = false }) {
                    Text("حسناً", color = colors.primary)
                }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeadwordCard(
    entry: WordEntry,
    isFavorite: Boolean,
    audioManager: AudioPlayerManager,
    audioSpeed: Float,
    showArabicTranslations: Boolean,
    showMachineLabel: Boolean,
    chipMeanings: Map<String, String>,
    onSelectSpeed: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAwlInfo: () -> Unit,
    onChipClick: (String) -> Unit,
    onSelectVocalizedForm: (String) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val hasUs = !entry.isArabicWord && audioManager.hasAudioOrCache(entry.word, Accent.US, entry.usAudioUrl)
    val hasUk = !entry.isArabicWord && audioManager.hasAudioOrCache(entry.word, Accent.UK, entry.ukAudioUrl)
    val hasAwlTag = entry.tags.any { it.equals("AWL", ignoreCase = true) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (entry.isArabicWord) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = entry.displayWord.ifBlank { entry.word },
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isFromOfflinePack) {
                        Surface(
                            color = colors.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "أوفلاين",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    } else if (entry.isFromCache) {
                        Surface(
                            color = colors.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "محفوظ",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("favorite_star_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Star else AppIcons.StarOutline,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = if (isFavorite) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            if (entry.isArabicWord && entry.transliteration.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = entry.transliteration,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (entry.isArabicWord && entry.vocalizedForms.size > 1) {
                val activeCanonical = ArabicTextUtils.canonicalizeArabicWithDiacritics(entry.displayWord)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "الصيغ المشكولة:",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        entry.vocalizedForms.forEach { vocalized ->
                            val isSelected = ArabicTextUtils.canonicalizeArabicWithDiacritics(vocalized) == activeCanonical
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onSelectVocalizedForm(vocalized) },
                                color = if (isSelected) colors.primaryContainer else colors.surfaceVariant,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outline)
                            ) {
                                Text(
                                    text = vocalized,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) colors.primary else colors.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section 12.3: Tappable AWL chip if offline pack tagged this word with "AWL"
            if (hasAwlTag) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onOpenAwlInfo)
                        .testTag("awl_badge_chip"),
                    color = colors.secondaryContainer,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "كلمة أكاديمية (AWL)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // US / UK word pronunciations (human recording when available, TTS fallback otherwise)
            if (!entry.isArabicWord) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PronunciationPillButton(
                        regionLabel = "US",
                        ipa = entry.usIpa,
                        hasAudio = true,
                        onClick = {
                            audioManager.playHumanPronunciation(
                                word = entry.word,
                                accent = Accent.US,
                                audioUrl = entry.usAudioUrl,
                                key = "head_us_${entry.word}"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pronounce_us_button")
                    )
                    PronunciationPillButton(
                        regionLabel = "UK",
                        ipa = entry.ukIpa,
                        hasAudio = true,
                        onClick = {
                            audioManager.playHumanPronunciation(
                                word = entry.word,
                                accent = Accent.UK,
                                audioUrl = entry.ukAudioUrl,
                                key = "head_uk_${entry.word}"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pronounce_uk_button")
                    )
                }
            }

            // Quick translations directly under the word & US/UK speaker row (BUG 6 / BUG 8)
            if (entry.machineTranslations.isNotEmpty() && (entry.isArabicWord || showArabicTranslations)) {
                if (entry.isArabicWord) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (showMachineLabel) {
                            Text(
                                text = "ترجمة آلية",
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                color = colors.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        ChipWrapRow(
                            items = entry.machineTranslations,
                            chipMeanings = chipMeanings,
                            onChipClick = onChipClick
                        )
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        entry.machineTranslations.forEach { arText ->
                            Surface(
                                color = colors.surfaceVariant,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                ArabicTranslatedText(
                                    text = arText,
                                    isMachineTranslated = true,
                                    showMachineLabel = showMachineLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // BUG 8: Compact ~28dp speed selector as a separate small control
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
                    onSelectSpeed = onSelectSpeed
                )
            }
        }
    }
}

@Composable
private fun PosGroupCard(
    word: String,
    group: PosSenseGroup,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    showArabicTranslations: Boolean,
    showMachineLabel: Boolean,
    playingKey: String?,
    chipMeanings: Map<String, String>,
    onSpeakSentence: (sentence: String, audioUrl: String?, key: String, accentOverride: Accent?) -> Unit,
    onStopAudio: () -> Unit,
    onChipClick: (String) -> Unit,
    onOpenWord: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val visibleSenses = if (showAll) group.senses else group.senses.take(10)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PosChip(text = group.partOfSpeechArabic)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = group.partOfSpeech,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = colors.outline, thickness = 1.dp)

            visibleSenses.forEachIndexed { idx, sense ->
                SenseCardContent(
                    word = word,
                    sense = sense,
                    showArabicTranslations = showArabicTranslations,
                    showMachineLabel = showMachineLabel,
                    playingKey = playingKey,
                    chipMeanings = chipMeanings,
                    onSpeakSentence = onSpeakSentence,
                    onStopAudio = onStopAudio,
                    onChipClick = onChipClick,
                    onOpenWord = onOpenWord
                )
                if (idx < visibleSenses.lastIndex) {
                    HorizontalDivider(color = colors.outline, thickness = 1.dp)
                }
            }

            if (group.senses.size > 10) {
                TextButton(
                    onClick = onToggleShowAll,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (showAll) "عرض أقل" else "عرض المزيد (${group.senses.size - 10})",
                        color = colors.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenseCardContent(
    word: String,
    sense: SenseItem,
    showArabicTranslations: Boolean,
    showMachineLabel: Boolean,
    playingKey: String?,
    chipMeanings: Map<String, String>,
    onSpeakSentence: (sentence: String, audioUrl: String?, key: String, accentOverride: Accent?) -> Unit,
    onStopAudio: () -> Unit,
    onChipClick: (String) -> Unit,
    onOpenWord: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val defIsArabic = ArabicTextUtils.containsArabicLetters(sense.definition)
    val translatedMeaning = sense.definitionArabic?.takeIf { it.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Section 13.1 & 13.2: Per-sense usage labels & spelling variant chips
        if (sense.usageLabels.isNotEmpty() || sense.spellingVariantLabel != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sense.usageLabels.forEach { label ->
                    UsageLabelChip(text = label)
                }
                sense.spellingVariantLabel?.let { variantLabel ->
                    UsageLabelChip(text = variantLabel)
                }
                sense.otherSpellingWord?.let { otherWord ->
                    InteractiveWordChip(
                        word = otherWord,
                        arabicSubtext = chipMeanings[otherWord.trim().lowercase(Locale.US)],
                        onClick = { onOpenWord(otherWord) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${sense.index}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!defIsArabic) {
                    // EN -> AR: Arabic meaning ABOVE, English definition directly BELOW
                    if (showArabicTranslations && translatedMeaning != null) {
                        ArabicTranslatedText(
                            text = translatedMeaning,
                            isMachineTranslated = sense.definitionArabicIsMachine,
                            showMachineLabel = showMachineLabel,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sense_arabic_def_${sense.partOfSpeech}_${sense.index}")
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = sense.definition,
                            style = if (showArabicTranslations && translatedMeaning != null) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            },
                            color = colors.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // AR -> EN: Arabic source ABOVE, English meaning directly BELOW
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            text = sense.definition,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (translatedMeaning != null) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = translatedMeaning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sense_english_def_${sense.partOfSpeech}_${sense.index}")
                            )
                        }
                    }
                }
                if (!sense.usageNote.isNullOrBlank()) {
                    Text(
                        text = sense.usageNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Concise example (1 per sense) with dual US/UK speakers (EN) or speaker (AR) + translation below
        sense.examples.take(1).forEachIndexed { exIdx, example ->
            val exPrefix = "ex_${sense.partOfSpeech}_${sense.index}_$exIdx"
            val exIsArabic = ArabicTextUtils.containsArabicLetters(example)
            val exTranslation = sense.exampleTranslations[example]?.takeIf { it.isNotBlank() }
            val exAudioUrl = sense.exampleAudioUrls[example]

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (exIsArabic) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = example,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            SentenceSpeakerButton(
                                isPlaying = playingKey == exPrefix,
                                onClick = {
                                    if (playingKey == exPrefix) onStopAudio()
                                    else onSpeakSentence(example, exAudioUrl, exPrefix, null)
                                },
                                modifier = Modifier.testTag("speaker_$exPrefix")
                            )
                        }
                        if (exTranslation != null) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = exTranslation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("arabic_$exPrefix")
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HighlightedEnglishText(
                                sentence = example,
                                targetWord = word,
                                modifier = Modifier.weight(1f)
                            )
                            SentenceDualSpeakerButtons(
                                playingKey = playingKey,
                                keyPrefix = exPrefix,
                                onPlayUs = { onSpeakSentence(example, exAudioUrl, "${exPrefix}_us", Accent.US) },
                                onPlayUk = { onSpeakSentence(example, exAudioUrl, "${exPrefix}_uk", Accent.UK) },
                                onStop = onStopAudio
                            )
                        }

                        if (showArabicTranslations && exTranslation != null) {
                            ArabicTranslatedText(
                                text = exTranslation,
                                isMachineTranslated = sense.exampleIsMachine[example] ?: true,
                                showMachineLabel = showMachineLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("arabic_$exPrefix")
                            )
                        }
                    }
                }
            }
        }

        // Per-sense synonyms/antonyms ONLY if source provided them for this sense
        if (sense.synonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "مرادفات المعنى:",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                ChipWrapRow(
                    items = sense.synonyms,
                    chipMeanings = chipMeanings,
                    onChipClick = onChipClick
                )
            }
        }
        if (sense.antonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "أضداد المعنى:",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                ChipWrapRow(
                    items = sense.antonyms,
                    chipMeanings = chipMeanings,
                    onChipClick = onChipClick
                )
            }
        }
    }
}

@Composable
private fun PhrasalIdiomDetailCard(
    item: PhraseDetailItem,
    showArabicTranslations: Boolean,
    showMachineLabel: Boolean,
    playingKey: String?,
    onTapPhrase: () -> Unit,
    onSpeak: (text: String, audioUrl: String?, key: String, accentOverride: Accent?) -> Unit,
    onStop: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val phrasePrefix = "phrasal_${item.phrase.hashCode()}"
    val phraseIsArabic = ArabicTextUtils.containsArabicLetters(item.phrase)
    val badgeText = when {
        item.category == "COMPOUND" -> "تعبير مركب"
        item.isIdiom || item.category == "IDIOM" -> "تعبير اصطلاحي"
        else -> "فعل مركب"
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onTapPhrase)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (phraseIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = item.phrase,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                UsageLabelChip(text = badgeText)
            }

            if (phraseIsArabic) {
                SentenceSpeakerButton(
                    isPlaying = playingKey == "${phrasePrefix}_ar",
                    onClick = { onSpeak(item.phrase, null, "${phrasePrefix}_ar", null) }
                )
            } else {
                SentenceDualSpeakerButtons(
                    playingKey = playingKey,
                    keyPrefix = phrasePrefix,
                    onPlayUs = { onSpeak(item.phrase, null, "${phrasePrefix}_us", Accent.US) },
                    onPlayUk = { onSpeak(item.phrase, null, "${phrasePrefix}_uk", Accent.UK) },
                    onStop = onStop
                )
            }
        }

        if (!item.grammarStructure.isNullOrBlank()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = item.grammarStructure,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showArabicTranslations && !item.arabicMeaning.isNullOrBlank()) {
            ArabicTranslatedText(
                text = item.arabicMeaning,
                isMachineTranslated = item.isArabicMachine,
                showMachineLabel = showMachineLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (item.englishMeaning.isNotBlank()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = item.englishMeaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!item.usageExplanation.isNullOrBlank()) {
            Text(
                text = item.usageExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (item.example.isNotBlank()) {
            val exPrefix = "phrasal_ex_${item.phrase.hashCode()}"
            val exIsArabic = ArabicTextUtils.containsArabicLetters(item.example)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (exIsArabic) {
                            Text(
                                text = item.example,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            SentenceSpeakerButton(
                                isPlaying = playingKey == "${exPrefix}_ar",
                                onClick = { onSpeak(item.example, item.exampleAudioUrl, "${exPrefix}_ar", null) }
                            )
                        } else {
                            HighlightedEnglishText(
                                sentence = item.example,
                                targetWord = item.phrase,
                                modifier = Modifier.weight(1f)
                            )
                            SentenceDualSpeakerButtons(
                                playingKey = playingKey,
                                keyPrefix = exPrefix,
                                onPlayUs = { onSpeak(item.example, item.exampleAudioUrl, "${exPrefix}_us", Accent.US) },
                                onPlayUk = { onSpeak(item.example, item.exampleAudioUrl, "${exPrefix}_uk", Accent.UK) },
                                onStop = onStop
                            )
                        }
                    }
                    if (showArabicTranslations && !item.exampleArabic.isNullOrBlank()) {
                        ArabicTranslatedText(
                            text = item.exampleArabic,
                            isMachineTranslated = item.exampleIsMachine,
                            showMachineLabel = showMachineLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfusableCard(
    detail: ConfusableWordDetail,
    wordArabicMeaning: String?,
    audioManager: AudioPlayerManager,
    onOpenWord: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasUs = audioManager.hasAudioOrCache(detail.word, Accent.US, detail.usAudioUrl)
    val hasUk = audioManager.hasAudioOrCache(detail.word, Accent.UK, detail.ukAudioUrl)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenWord)
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = detail.word,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.secondary
                            )
                        }
                        if (detail.partOfSpeech.isNotBlank()) {
                            PosChip(text = detail.partOfSpeech)
                        }
                    }
                    if (!wordArabicMeaning.isNullOrBlank()) {
                        Text(
                            text = wordArabicMeaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primary
                        )
                    }
                }

                CompactWordAudioButtons(
                    hasUsAudio = hasUs,
                    hasUkAudio = hasUk,
                    onPlayUs = {
                        audioManager.playHumanPronunciation(
                            word = detail.word,
                            accent = Accent.US,
                            audioUrl = detail.usAudioUrl,
                            key = "conf_us_${detail.word}"
                        )
                    },
                    onPlayUk = {
                        audioManager.playHumanPronunciation(
                            word = detail.word,
                            accent = Accent.UK,
                            audioUrl = detail.ukAudioUrl,
                            key = "conf_uk_${detail.word}"
                        )
                    }
                )
            }

            if (detail.labels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    detail.labels.forEach { label ->
                        Surface(
                            color = colors.tertiaryContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.tertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (detail.shortDefinition.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = detail.shortDefinition,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface
                    )
                }
                if (!detail.shortDefinitionArabic.isNullOrBlank()) {
                    Text(
                        text = detail.shortDefinitionArabic,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (detail.example.isNotBlank()) {
                HighlightedEnglishText(
                    sentence = detail.example,
                    targetWord = detail.word
                )
            }
        }
    }
}

@Composable
private fun CollapsibleChipsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    items: List<String>,
    chipMeanings: Map<String, String> = emptyMap(),
    highlightedChip: String? = null,
    onChipClick: (String) -> Unit
) {
    CollapsibleSectionCard(
        title = title,
        count = items.size,
        expanded = expanded,
        onToggle = onToggle
    ) {
        ChipWrapRow(
            items = items,
            chipMeanings = chipMeanings,
            highlightedChip = highlightedChip,
            onChipClick = onChipClick
        )
    }
}

@Composable
private fun CollapsibleSectionCard(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.primary
                    )
                    Text(
                        text = "($count)",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "طي" else "توسيع",
                    tint = colors.onSurfaceVariant
                )
            }

            if (expanded) {
                HorizontalDivider(color = colors.outline, thickness = 1.dp)
                Box(modifier = Modifier.padding(14.dp)) {
                    content()
                }
            }
        }
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
