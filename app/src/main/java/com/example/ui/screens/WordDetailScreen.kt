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
import androidx.compose.foundation.layout.defaultMinSize
import com.example.R
import com.example.core.Accent
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.LinguisticRanker
import com.example.core.LocalAppLanguage
import com.example.core.LocalizedLabels
import com.example.core.OfflinePackState
import com.example.core.tr
import com.example.data.repository.WordLoadState
import com.example.domain.model.ArabicVocalizedGroup
import com.example.domain.model.ConfusableCandidate
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.PhraseDetailItem
import com.example.domain.model.PosSenseGroup
import com.example.domain.model.ReverseEnglishMatch
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
import com.example.ui.components.OfflineDictionaryPackBanner
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
    val lang = LocalAppLanguage.current

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
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val offlinePackState = viewModel.offlinePackState?.collectAsStateWithLifecycle()?.value

    var inlineQuery by remember(currentQuery) { mutableStateOf(currentQuery) }
    var showAwlDialog by remember { mutableStateOf(false) }
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
                        contentDescription = tr("Back", "رجوع"),
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
                            text = tr("Search a word or sentence to translate...", "ابحث عن كلمة أو جملة لترجمتها..."),
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
                    LoadingCard(message = tr("Fetching entries from open sources...", "جاري جلب المداخل من المصادر المفتوحة..."))
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
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("Retry", "إعادة المحاولة"), style = MaterialTheme.typography.labelLarge)
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

                        // Show offline dictionary pack prompt ONLY when offline and missing, or while downloading
                        if (offlinePackState is OfflinePackState.Downloading ||
                            (!isOnline && ((offlinePackState is OfflinePackState.Ready && !offlinePackState.isInstalled) ||
                                (offlinePackState is OfflinePackState.Error && !offlinePackState.isInstalled)))
                        ) {
                            item(key = "offline_pack_banner", contentType = "banner") {
                                OfflineDictionaryPackBanner(
                                    isOnline = isOnline,
                                    packState = offlinePackState,
                                    onDownloadClick = { viewModel.requestOfflinePackDownload() }
                                )
                            }
                        }

                        // Missing offline ML Kit Arabic pack banner (shown ONLY when offline)
                        if (entry.missingTranslationPack && !isOnline) {
                            item(key = "missing_ar_pack_top", contentType = "banner") {
                                MissingArabicPackTopBanner(
                                    isDownloading = isDownloadingPack,
                                    errorMessage = packDownloadMessage,
                                    onDownloadClick = { viewModel.requestDownloadArabicPack() },
                                    onOpenLanguages = onOpenLanguages
                                )
                            }
                        }

                        // Missing English TTS voice prompt when needed
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

                        // Headword Card with Human US/UK Audio Buttons, POS Meanings, Word Forms, Frequency Tags
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
                                onChipClick = { w -> viewModel.onChipTapped(w) },
                                onOpenWord = { w -> onOpenWord(w, null, null) },
                                onSpeak = speakChip,
                                onSelectVocalizedForm = { vocalized -> viewModel.loadWord(vocalized) }
                            )
                        }

                        if (entry.isArabicWord && entry.reverseArabicGroups.isNotEmpty()) {
                            item(key = "reverse_arabic_groups_${entry.word}", contentType = "reverse_arabic_groups") {
                                ReverseArabicGroupsCard(
                                    groups = entry.reverseArabicGroups,
                                    audioManager = audioManager,
                                    onOpenEnglishWord = { engWord -> onOpenWord(engWord, null, null) }
                                )
                            }
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
                                                    text = option.labelFor(lang),
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
                                val filterLabel = usageFilter.labelFor(lang)
                                Text(
                                    text = tr(
                                        "No meanings match \"$filterLabel\".",
                                        "لا توجد معانٍ مطابقة لتصفية \"$filterLabel\"."
                                    ),
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
                                    onOpenWord = { onOpenWord(it, null, null) },
                                    onSpeakChip = speakChip
                                )
                            }
                        }

                        // 1. Synonyms
                        if (entry.synonyms.isNotEmpty()) {
                            item(key = "sec_synonyms", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = tr("Synonyms", "المترادفات"),
                                    expanded = synonymsExpanded,
                                    onToggle = { synonymsExpanded = !synonymsExpanded },
                                    items = entry.synonyms,
                                    chipMeanings = chipMeanings,
                                    onChipClick = { viewModel.onChipTapped(it) },
                                    onSpeak = speakChip
                                )
                            }
                        }

                        // 2. Antonyms
                        if (entry.antonyms.isNotEmpty()) {
                            item(key = "sec_antonyms", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = tr("Antonyms", "المتضادات"),
                                    expanded = antonymsExpanded,
                                    onToggle = { antonymsExpanded = !antonymsExpanded },
                                    items = entry.antonyms,
                                    chipMeanings = chipMeanings,
                                    onChipClick = { viewModel.onChipTapped(it) },
                                    onSpeak = speakChip
                                )
                            }
                        }

                        // 3. Phrasal Verbs
                        if (phrasalVerbItems.isNotEmpty() || (isLoadingPhrasals && !entry.isArabicWord && displayPhrasals.isEmpty())) {
                            item(key = "sec_phrasal_verbs", contentType = "phrasals_section") {
                                CollapsibleSectionCard(
                                    title = tr("Phrasal Verbs", "الأفعال المركبة"),
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
                                                text = tr("Loading phrasal verbs...", "جاري تحميل معاني الأفعال المركبة..."),
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

                        // 4. Idioms & Expressions
                        if (idiomItems.isNotEmpty()) {
                            item(key = "sec_idioms", contentType = "idioms_section") {
                                CollapsibleSectionCard(
                                    title = tr("Idioms & Expressions", "التعابير الاصطلاحية والعبارات"),
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

                        // 5. Collocations
                        if (entry.collocations.isNotEmpty()) {
                            item(key = "sec_collocations", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = tr("Collocations", "المتلازمات اللفظية"),
                                    expanded = collocationsExpanded,
                                    onToggle = { collocationsExpanded = !collocationsExpanded },
                                    items = entry.collocations,
                                    chipMeanings = chipMeanings,
                                    onChipClick = { viewModel.onChipTapped(it) },
                                    onSpeak = speakChip
                                )
                            }
                        }

                        // 6. Homophones
                        if (homophoneCandidates.isNotEmpty()) {
                            item(key = "sec_homophones", contentType = "confusables_section") {
                                ConfusableCategorySection(
                                    title = tr("Homophones", "كلمات بنفس النطق"),
                                    candidates = homophoneCandidates,
                                    expanded = homophonesExpanded,
                                    onToggle = {
                                        val next = !homophonesExpanded
                                        homophonesExpanded = next
                                        if (next) viewModel.loadConfusablesIfNeeded()
                                    },
                                    isLoading = isLoadingConfusables,
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
                        if (lookAlikeCandidates.isNotEmpty()) {
                            item(key = "sec_look_alike", contentType = "confusables_section") {
                                ConfusableCategorySection(
                                    title = tr("Look-alike Words", "كلمات متشابهة في الكتابة"),
                                    candidates = lookAlikeCandidates,
                                    expanded = lookAlikeExpanded,
                                    onToggle = {
                                        val next = !lookAlikeExpanded
                                        lookAlikeExpanded = next
                                        if (next) viewModel.loadConfusablesIfNeeded()
                                    },
                                    isLoading = isLoadingConfusables,
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
                        if (soundAlikeCandidates.isNotEmpty()) {
                            item(key = "sec_sound_alike", contentType = "confusables_section") {
                                ConfusableCategorySection(
                                    title = tr("Sound-alike Words", "كلمات متشابهة في النطق"),
                                    candidates = soundAlikeCandidates,
                                    expanded = soundAlikeExpanded,
                                    onToggle = {
                                        val next = !soundAlikeExpanded
                                        soundAlikeExpanded = next
                                        if (next) viewModel.loadConfusablesIfNeeded()
                                    },
                                    isLoading = isLoadingConfusables,
                                    confusableDetails = confusableDetails,
                                    chipMeanings = chipMeanings,
                                    audioManager = audioManager,
                                    onLoadNeeded = { viewModel.loadConfusablesIfNeeded() },
                                    onTapCandidate = { viewModel.onChipTapped(it) },
                                    onSpeakChip = speakChip
                                )
                            }
                        }

                        // Compound Expressions section
                        if (compoundDetailItems.isNotEmpty() || remainingCompoundChips.isNotEmpty()) {
                            item(key = "sec_compounds", contentType = "compounds_section") {
                                CollapsibleSectionCard(
                                    title = tr("Compound Expressions", "التعابير المركبة والمتلازمات"),
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
                                                onChipClick = { viewModel.onChipTapped(it) },
                                                onSpeak = speakChip
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (entry.prepositionsAndPhrases.isNotEmpty()) {
                            item(key = "sec_phrases", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = tr("Prepositions & Phrases", "حروف الجر والتراكيب"),
                                    expanded = phrasesExpanded,
                                    onToggle = { phrasesExpanded = !phrasesExpanded },
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

                        if (entry.wordFamily.isNotEmpty()) {
                            item(key = "sec_word_family", contentType = "word_family_section") {
                                CollapsibleSectionCard(
                                    title = tr("Word Family", "عائلة الكلمة"),
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
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (member.partOfSpeechArabic.isNotBlank()) {
                                                        PosChip(
                                                            text = LinguisticRanker.formatPosBadgeLabel(
                                                                partOfSpeech = member.partOfSpeechArabic,
                                                                partOfSpeechArabic = member.partOfSpeechArabic,
                                                                language = lang
                                                            )
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { speakChip(member.word) },
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = AppIcons.VolumeUp,
                                                            contentDescription = tr("Pronounce ${member.word}", "نطق ${member.word}"),
                                                            tint = colors.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
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

                        if (entry.generalTerms.isNotEmpty() || entry.specificTerms.isNotEmpty()) {
                            item(key = "sec_general_specific", contentType = "general_specific_section") {
                                CollapsibleSectionCard(
                                    title = tr("Broader & Narrower Terms", "مصطلحات أعم وأخص"),
                                    count = entry.generalTerms.size + entry.specificTerms.size,
                                    expanded = generalSpecExpanded,
                                    onToggle = { generalSpecExpanded = !generalSpecExpanded }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (entry.generalTerms.isNotEmpty()) {
                                            Text(
                                                text = tr("Broader terms", "مصطلحات أعم"),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.onSurfaceVariant
                                            )
                                            ChipWrapRow(
                                                items = entry.generalTerms,
                                                chipMeanings = chipMeanings,
                                                onChipClick = { viewModel.onChipTapped(it) },
                                                onSpeak = speakChip
                                            )
                                        }
                                        if (entry.specificTerms.isNotEmpty()) {
                                            Text(
                                                text = tr("Narrower terms", "مصطلحات أخص"),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.onSurfaceVariant
                                            )
                                            ChipWrapRow(
                                                items = entry.specificTerms,
                                                chipMeanings = chipMeanings,
                                                onChipClick = { viewModel.onChipTapped(it) },
                                                onSpeak = speakChip
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (entry.relatedWords.isNotEmpty()) {
                            item(key = "sec_related", contentType = "chips_section") {
                                CollapsibleChipsSection(
                                    title = tr("Related Terms", "كلمات ذات صلة"),
                                    expanded = relatedExpanded,
                                    onToggle = { relatedExpanded = !relatedExpanded },
                                    items = entry.relatedWords,
                                    chipMeanings = chipMeanings,
                                    onChipClick = { viewModel.onChipTapped(it) },
                                    onSpeak = speakChip
                                )
                            }
                        }

                        // More Example Sentences
                        if (entry.tatoebaSentences.isNotEmpty()) {
                            item(key = "sec_tatoeba", contentType = "tatoeba_section") {
                                CollapsibleSectionCard(
                                    title = tr("More Examples", "أمثلة إضافية"),
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
                Text(tr("Academic Word (AWL)", "كلمة أكاديمية (AWL)"), style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(
                    text = tr(
                        "From the Academic Word List (AWL), commonly studied for TOEFL and IELTS preparation. Not an official ETS or IELTS list.",
                        "من قائمة الكلمات الأكاديمية (Academic Word List) الشائعة في التحضير للتوفل والآيلتس. ليست قائمة رسمية من ETS أو IELTS."
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showAwlDialog = false }) {
                    Text(tr("OK", "حسناً"), color = colors.primary)
                }
            }
        )
    }

    if (showCellularConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCellularDownload() },
            containerColor = colors.surface,
            title = { Text(tr("Download over mobile data?", "التحميل عبر بيانات الهاتف؟"), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    tr(
                        "You are currently on mobile data. Continue downloading the Arabic & English packs?",
                        "أنت تستخدم بيانات الهاتف المحمول حالياً. هل تريد المتابعة لتحميل حزمة العربية والإنجليزية؟"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmCellularDownload() }) {
                    Text(tr("Continue", "متابعة"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCellularDownload() }) {
                    Text(tr("Cancel", "إلغاء"))
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
    chipMeanings: Map<String, String>,
    onSelectSpeed: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAwlInfo: () -> Unit,
    onChipClick: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onSelectVocalizedForm: (String) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalAppLanguage.current
    val hasAwlTag = entry.tags.any { it.equals("AWL", ignoreCase = true) }
    val freqTag = when {
        entry.frequencyRank in 1..500 -> "#Top 500"
        entry.frequencyRank in 501..1000 -> "#Top 1000"
        entry.frequencyRank in 1001..3000 -> "#Top 3000"
        entry.frequencyRank in 3001..5000 -> "#Top 5000"
        entry.frequencyRank in 5001..10000 -> "#Top 10000"
        else -> null
    }
    val displayTags = remember(entry.tags, freqTag) {
        buildList {
            entry.tags.filter { !it.equals("AWL", ignoreCase = true) && it.isNotBlank() }
                .forEach { t -> add(if (t.startsWith("#")) t else "#$t") }
            if (freqTag != null && none { it.equals(freqTag, ignoreCase = true) }) {
                add(freqTag)
            }
        }
    }

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
                                text = tr("Offline", "أوفلاين"),
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
                                text = tr("Cached", "محفوظ"),
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
                            contentDescription = tr("Favorite", "المفضلة"),
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
                        text = tr("Vocalized forms:", "الصيغ المشكولة:"),
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

            // Comprehensive quick meanings grouped by Part of Speech directly under word & US/UK audio
            val posSummaries = remember(entry, lang) {
                LinguisticRanker.extractPosHeadwordMeanings(entry, maxPerPos = Int.MAX_VALUE, language = lang)
            }
            if ((posSummaries.isNotEmpty() || entry.machineTranslations.isNotEmpty()) && (entry.isArabicWord || showArabicTranslations)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("headword_translations_box"),
                    color = colors.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.75f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tr("Translation", "الترجمة"),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary
                        )

                        if (posSummaries.isNotEmpty()) {
                            posSummaries.forEachIndexed { idx, posSummary ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        modifier = Modifier.testTag("headword_pos_${posSummary.partOfSpeech}"),
                                        color = colors.primaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = posSummary.posBadgeLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    CompositionLocalProvider(
                                        LocalLayoutDirection provides if (entry.isArabicWord) LayoutDirection.Ltr else LayoutDirection.Rtl
                                    ) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            posSummary.meanings.forEach { meaning ->
                                                Surface(
                                                    modifier = Modifier
                                                        .defaultMinSize(minHeight = 40.dp)
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .clickable { onOpenWord(meaning) },
                                                    color = colors.surface,
                                                    shape = RoundedCornerShape(999.dp),
                                                    border = BorderStroke(1.dp, colors.outline)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = meaning,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = colors.onSurface,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (idx < posSummaries.lastIndex) {
                                    HorizontalDivider(
                                        color = colors.outline.copy(alpha = 0.5f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        } else if (entry.isArabicWord) {
                            ChipWrapRow(
                                items = entry.machineTranslations,
                                chipMeanings = chipMeanings,
                                onChipClick = onOpenWord,
                                onSpeak = onSpeak
                            )
                        } else {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    entry.machineTranslations.forEach { arText ->
                                        Surface(
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 40.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .clickable { onOpenWord(arText) },
                                            color = colors.surface,
                                            shape = RoundedCornerShape(999.dp),
                                            border = BorderStroke(1.dp, colors.outline)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = arText,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.primary,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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

            // Word forms (e.g. run pa. pple., ran pa. t.) with speaker buttons
            if (!entry.isArabicWord && entry.wordForms.isNotEmpty()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("headword_word_forms"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        entry.wordForms.forEach { formItem ->
                            Surface(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                color = colors.surfaceVariant.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clickable { onOpenWord(formItem.form) }
                                            .padding(vertical = 8.dp, horizontal = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = formItem.form,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onSurface
                                        )
                                        if (formItem.label.isNotBlank()) {
                                            Text(
                                                text = formItem.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            audioManager.playHumanPronunciation(
                                                word = formItem.form,
                                                accent = Accent.US,
                                                audioUrl = null,
                                                key = "form_${formItem.form}"
                                            )
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = AppIcons.VolumeUp,
                                            contentDescription = tr("Pronounce ${formItem.form}", "نطق ${formItem.form}"),
                                            tint = colors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Frequency & Academic tags (#Top 500, #AWL, etc.)
            if (hasAwlTag || displayTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("headword_frequency_tags"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
                                text = tr("#AWL", "كلمة أكاديمية (AWL)"),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    displayTags.forEach { tagText ->
                        Surface(
                            color = colors.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = tagText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
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
                    text = tr("Speech speed", "سرعة الصوت"),
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
    onOpenWord: (String) -> Unit,
    onSpeakChip: ((String) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalAppLanguage.current
    val visibleSenses = if (showAll) group.senses else group.senses.take(10)
    val posBadge = LinguisticRanker.formatPosBadgeLabel(
        partOfSpeech = group.partOfSpeech,
        partOfSpeechArabic = group.partOfSpeechArabic,
        language = lang
    )

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
                PosChip(text = posBadge)
                Text(
                    text = tr("${group.senses.size} meanings", "${group.senses.size} معنى"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
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
                    onOpenWord = onOpenWord,
                    onSpeakChip = onSpeakChip
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
                        text = if (showAll) {
                            tr("Show less", "عرض أقل")
                        } else {
                            tr("Show more (${group.senses.size - 10})", "عرض المزيد (${group.senses.size - 10})")
                        },
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
    onOpenWord: (String) -> Unit,
    onSpeakChip: ((String) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalAppLanguage.current
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
                    UsageLabelChip(text = LocalizedLabels.usageLabel(label, lang))
                }
                sense.spellingVariantLabel?.let { variantLabel ->
                    UsageLabelChip(text = LocalizedLabels.usageLabel(variantLabel, lang))
                }
                sense.otherSpellingWord?.let { otherWord ->
                    InteractiveWordChip(
                        word = otherWord,
                        arabicSubtext = chipMeanings[otherWord.trim().lowercase(Locale.US)],
                        onClick = { onOpenWord(otherWord) },
                        onSpeak = onSpeakChip
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
                        text = LocalizedLabels.usageNote(sense.usageNote, lang),
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
                    text = tr("Sense Synonyms:", "مرادفات المعنى:"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                ChipWrapRow(
                    items = sense.synonyms,
                    chipMeanings = chipMeanings,
                    onChipClick = onChipClick,
                    onSpeak = onSpeakChip
                )
            }
        }
        if (sense.antonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = tr("Sense Antonyms:", "أضداد المعنى:"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                ChipWrapRow(
                    items = sense.antonyms,
                    chipMeanings = chipMeanings,
                    onChipClick = onChipClick,
                    onSpeak = onSpeakChip
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
    val lang = LocalAppLanguage.current
    val phrasePrefix = "phrasal_${item.phrase.hashCode()}"
    val phraseIsArabic = ArabicTextUtils.containsArabicLetters(item.phrase)
    val badgeText = LocalizedLabels.phraseBadge(item.category, item.isIdiom, lang)

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
                    text = LocalizedLabels.formatGrammarStructure(item.grammarStructure, lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (item.noMeaningFound) {
            Text(
                text = tr(
                    "no idiom meaning found",
                    "لم يُعثر على معنى للتعبير الاصطلاحي"
                ),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phrase_item_no_idiom_meaning")
            )
        } else {
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
    val lang = LocalAppLanguage.current
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
                            PosChip(
                                text = LinguisticRanker.formatPosBadgeLabel(
                                    partOfSpeech = detail.partOfSpeech,
                                    partOfSpeechArabic = "",
                                    language = lang
                                )
                            )
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
                                text = LocalizedLabels.usageLabel(label, lang),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfusableCategorySection(
    title: String,
    candidates: List<ConfusableCandidate>,
    expanded: Boolean,
    onToggle: () -> Unit,
    isLoading: Boolean,
    confusableDetails: Map<String, ConfusableWordDetail>,
    chipMeanings: Map<String, String>,
    audioManager: AudioPlayerManager,
    onLoadNeeded: () -> Unit,
    onTapCandidate: (String) -> Unit,
    onSpeakChip: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    CollapsibleSectionCard(
        title = title,
        count = candidates.size,
        expanded = expanded,
        onToggle = onToggle
    ) {
        LaunchedEffect(expanded) {
            if (expanded) onLoadNeeded()
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = colors.secondary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Loading meanings...", "جاري تحميل المعاني..."),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            val loadedList = candidates.mapNotNull { candidate ->
                confusableDetails[candidate.word.lowercase(Locale.US)]
            }
            val unloadedList = candidates.filter { candidate ->
                confusableDetails[candidate.word.lowercase(Locale.US)] == null
            }
            loadedList.forEach { detail ->
                ConfusableCard(
                    detail = detail,
                    wordArabicMeaning = chipMeanings[detail.word.trim().lowercase(Locale.US)],
                    audioManager = audioManager,
                    onOpenWord = { onTapCandidate(detail.word) }
                )
            }
            if (unloadedList.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    unloadedList.forEach { candidate ->
                        InteractiveWordChip(
                            word = candidate.word,
                            arabicSubtext = chipMeanings[candidate.word.trim().lowercase(Locale.US)],
                            onClick = { onTapCandidate(candidate.word) },
                            onSpeak = onSpeakChip
                        )
                    }
                }
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
    onChipClick: (String) -> Unit,
    onSpeak: ((String) -> Unit)? = null
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
            onChipClick = onChipClick,
            onSpeak = onSpeak
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
                    contentDescription = if (expanded) tr("Collapse", "طي") else tr("Expand", "توسيع"),
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

@Composable
private fun ReverseArabicGroupsCard(
    groups: List<ArabicVocalizedGroup>,
    audioManager: AudioPlayerManager,
    onOpenEnglishWord: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalAppLanguage.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reverse_arabic_groups_card"),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = tr("English Meanings by Vocalized Form", "المعاني الإنجليزية حسب التشكيل"),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primary
            )

            groups.forEachIndexed { groupIndex, group ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reverse_arabic_group_$groupIndex"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = colors.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = "${group.vocalizedForm} (${group.matches.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    group.matches.forEach { match ->
                        ReverseArabicMatchRow(
                            match = match,
                            posBadgeText = if (match.partOfSpeech.isNotBlank()) {
                                LinguisticRanker.formatPosBadgeLabel(match.partOfSpeech, "", lang)
                            } else {
                                ""
                            },
                            audioManager = audioManager,
                            onOpenEnglishWord = { onOpenEnglishWord(match.englishWord) }
                        )
                    }

                    if (groupIndex < groups.lastIndex) {
                        HorizontalDivider(color = colors.outline, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReverseArabicMatchRow(
    match: ReverseEnglishMatch,
    posBadgeText: String,
    audioManager: AudioPlayerManager,
    onOpenEnglishWord: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpenEnglishWord)
            .testTag("reverse_match_${match.englishWord}"),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = match.englishWord,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (posBadgeText.isNotBlank()) {
                        PosChip(text = posBadgeText)
                    }
                }

                CompactWordAudioButtons(
                    hasUsAudio = true,
                    hasUkAudio = true,
                    onPlayUs = {
                        audioManager.playHumanPronunciation(
                            word = match.englishWord,
                            accent = Accent.US,
                            audioUrl = match.usAudioUrl,
                            key = "rev_us_${match.englishWord}"
                        )
                    },
                    onPlayUk = {
                        audioManager.playHumanPronunciation(
                            word = match.englishWord,
                            accent = Accent.UK,
                            audioUrl = match.ukAudioUrl,
                            key = "rev_uk_${match.englishWord}"
                        )
                    }
                )
            }

            if (match.englishDefinition.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = match.englishDefinition,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

