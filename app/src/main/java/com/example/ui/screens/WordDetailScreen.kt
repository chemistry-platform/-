package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AudioPlayerManager
import com.example.domain.model.ArabicSearchResolution
import com.example.domain.model.SenseItem
import com.example.domain.model.WordEntry
import com.example.ui.components.ChipWrapRow
import com.example.ui.components.HighlightedEnglishText
import com.example.ui.components.InteractiveWordChip
import com.example.ui.components.LanguageSwapBar
import com.example.ui.components.LoadingCard
import com.example.ui.components.PosChip
import com.example.ui.components.PronunciationPillButton
import com.example.ui.components.RegisterBadge
import com.example.ui.components.UkUsMiniButtons
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AccentRedContainer
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.DividerBorder
import com.example.ui.theme.LinkBlue
import com.example.ui.theme.LinkBlueContainer
import com.example.ui.theme.RegisterGold
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.WordDetailTab
import com.example.ui.viewmodel.WordDetailUiState
import com.example.ui.viewmodel.WordDetailViewModel
import java.util.Locale

@Composable
fun WordDetailScreen(
    viewModel: WordDetailViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenPhrases: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val direction by viewModel.languageDirection.collectAsStateWithLifecycle()
    val currentQuery by viewModel.currentQuery.collectAsStateWithLifecycle()

    var inlineQuery by remember(currentQuery) { mutableStateOf(currentQuery) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            // Top Language Bar + Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
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
                        tint = TextPrimary
                    )
                }
                LanguageSwapBar(
                    direction = direction,
                    onSwap = { viewModel.swapLanguages() },
                    modifier = Modifier.weight(1f)
                )
            }

            // Top Inline Search Field
            OutlinedTextField(
                value = inlineQuery,
                onValueChange = { inlineQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("word_detail_search_field"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder_translate),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = LinkBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    focusedBorderColor = LinkBlue,
                    unfocusedBorderColor = DividerBorder,
                    cursorColor = LinkBlue
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        val trimmed = inlineQuery.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.loadWord(trimmed)
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            when (val state = uiState) {
                is WordDetailUiState.Loading -> {
                    LoadingCard(message = "جاري جلب المعاني والأمثلة الذكية...")
                }

                is WordDetailUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.isOffline) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        Button(
                            onClick = { viewModel.loadWord(currentQuery, forceRefresh = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
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

                is WordDetailUiState.Success -> {
                    val entry = state.entry
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 28.dp)
                    ) {
                        // Arabic Resolution candidates banner when searched in Arabic
                        state.arabicResolution?.let { resolution ->
                            item {
                                ArabicCandidateBanner(
                                    resolution = resolution,
                                    activeWord = entry.word,
                                    onSelectWord = { viewModel.loadWord(it) }
                                )
                            }
                        }

                        // Headword Lexicon Card
                        item {
                            HeadwordCard(
                                entry = entry,
                                isFavorite = isFavorite,
                                onToggleFavorite = { viewModel.toggleFavorite() },
                                onPlayUs = {
                                    audioManager.speakWord(
                                        text = entry.word,
                                        audioUrl = entry.usAudioUrl,
                                        locale = Locale.US,
                                        key = "head_us_${entry.word}"
                                    )
                                },
                                onPlayUk = {
                                    audioManager.speakWord(
                                        text = entry.word,
                                        audioUrl = entry.ukAudioUrl,
                                        locale = Locale.UK,
                                        key = "head_uk_${entry.word}"
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        if (!entry.aiStatusNote.isNullOrBlank()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = SurfaceElevated,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, DividerBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = RegisterGold,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = entry.aiStatusNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        // Tabs: الموجزة | قاموس | وردنيت
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            WordTabsBar(
                                selectedTab = selectedTab,
                                onSelectTab = { viewModel.selectTab(it) }
                            )
                        }

                        when (selectedTab) {
                            WordDetailTab.SUMMARY -> {
                                item {
                                    SummaryTabContent(
                                        entry = entry,
                                        audioManager = audioManager,
                                        onOpenWord = onOpenWord,
                                        onOpenPhrases = { onOpenPhrases(entry.word) }
                                    )
                                }
                            }

                            WordDetailTab.DICTIONARY -> {
                                item {
                                    DictionaryTabContent(
                                        entry = entry,
                                        audioManager = audioManager,
                                        onOpenWord = onOpenWord
                                    )
                                }
                            }

                            WordDetailTab.WORDNET -> {
                                item {
                                    WordNetTabContent(
                                        entry = entry,
                                        onOpenWord = onOpenWord
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

@Composable
private fun ArabicCandidateBanner(
    resolution: ArabicSearchResolution,
    activeWord: String,
    onSelectWord: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "نتائج المعجم العربي لـ \"${resolution.arabicQuery}\":",
            style = MaterialTheme.typography.labelMedium,
            color = LinkBlue
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            resolution.candidateWords.forEach { candidate ->
                val isSelected = candidate.englishWord.equals(activeWord, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelectWord(candidate.englishWord) },
                    color = if (isSelected) AccentRedContainer else SurfaceCard,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, if (isSelected) AccentRed else DividerBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = candidate.englishWord,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) AccentRed else TextPrimary
                        )
                        if (candidate.partOfSpeechArabic.isNotBlank()) {
                            Text(
                                text = "(${candidate.partOfSpeechArabic})",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadwordCard(
    entry: WordEntry,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayUs: () -> Unit,
    onPlayUk: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DividerBorder)
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = entry.word,
                        style = MaterialTheme.typography.displayLarge,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.isFromCache) {
                        Surface(
                            color = LinkBlueContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "محفوظ",
                                style = MaterialTheme.typography.labelSmall,
                                color = LinkBlue,
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
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = if (isFavorite) AccentRed else TextMuted,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PronunciationPillButton(
                    regionLabel = "US",
                    ipa = entry.usIpa,
                    onClick = onPlayUs,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pronounce_us_button")
                )
                PronunciationPillButton(
                    regionLabel = "UK",
                    ipa = entry.ukIpa,
                    onClick = onPlayUk,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pronounce_uk_button")
                )
            }
        }
    }
}

@Composable
private fun WordTabsBar(
    selectedTab: WordDetailTab,
    onSelectTab: (WordDetailTab) -> Unit
) {
    val tabs = listOf(
        WordDetailTab.SUMMARY to stringResource(R.string.tab_summary),
        WordDetailTab.DICTIONARY to stringResource(R.string.tab_dictionary),
        WordDetailTab.WORDNET to stringResource(R.string.tab_wordnet)
    )
    val selectedIndex = tabs.indexOfFirst { it.first == selectedTab }.coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedIndex,
        containerColor = CanvasBackground,
        contentColor = TextPrimary,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                height = 2.5.dp,
                color = AccentRed
            )
        },
        divider = {
            HorizontalDivider(color = DividerBorder, thickness = 1.dp)
        }
    ) {
        tabs.forEach { (tab, title) ->
            val selected = tab == selectedTab
            Tab(
                selected = selected,
                onClick = { onSelectTab(tab) },
                modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (selected) TextPrimary else TextMuted
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryTabContent(
    entry: WordEntry,
    audioManager: AudioPlayerManager,
    onOpenWord: (String) -> Unit,
    onOpenPhrases: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // a) One line of ALL Arabic meanings separated by "؛"
        if (entry.arabicMeaningsSummary.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DividerBorder)
            ) {
                Text(
                    text = entry.arabicMeaningsSummary.joinToString("؛ "),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // b) Chips: Part of speech and register
        if (entry.partsOfSpeech.isNotEmpty() || entry.registerTags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entry.partsOfSpeech.forEach { pos ->
                    PosChip(text = pos)
                }
                entry.registerTags.forEach { reg ->
                    RegisterBadge(text = reg)
                }
            }
        }

        // c) "المترادفات": synonyms as chips, grouped by meaning when possible
        if (entry.synonymGroups.isNotEmpty() || entry.allSynonyms.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.synonyms_title)) {
                if (entry.synonymGroups.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        entry.synonymGroups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = group.meaningLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted
                                )
                                ChipWrapRow(items = group.synonyms, onChipClick = onOpenWord)
                            }
                        }
                    }
                } else {
                    ChipWrapRow(items = entry.allSynonyms, onChipClick = onOpenWord)
                }
            }
        }

        // d) "المتضادات": antonyms as chips
        if (entry.allAntonyms.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.antonyms_title)) {
                ChipWrapRow(items = entry.allAntonyms, onChipClick = onOpenWord)
            }
        }

        // e) "المعاني والسياقات" + f) UK/US sound buttons next to EVERY example sentence
        SectionCard(title = stringResource(R.string.meanings_contexts_title)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                entry.senses.forEachIndexed { idx, sense ->
                    SenseContextRow(
                        sense = sense,
                        targetWord = entry.word,
                        onPlayUk = {
                            audioManager.speakSentence(
                                text = sense.exampleEnglish,
                                locale = Locale.UK,
                                key = "sense_uk_${sense.index}"
                            )
                        },
                        onPlayUs = {
                            audioManager.speakSentence(
                                text = sense.exampleEnglish,
                                locale = Locale.US,
                                key = "sense_us_${sense.index}"
                            )
                        }
                    )
                    if (idx < entry.senses.lastIndex) {
                        HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                    }
                }
            }
        }

        // g) "العبارات": common phrases / phrasal verbs with "More" ("المزيد") button
        if (entry.phrases.isNotEmpty()) {
            SectionCard(
                title = stringResource(R.string.phrases_title),
                trailingAction = {
                    TextButton(
                        onClick = onOpenPhrases,
                        modifier = Modifier.testTag("more_phrases_button")
                    ) {
                        Text(
                            text = stringResource(R.string.more_button),
                            style = MaterialTheme.typography.labelLarge,
                            color = LinkBlue
                        )
                    }
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val preview = entry.phrases.take(4)
                    preview.forEachIndexed { idx, phrase ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = phrase.phrase,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = LinkBlue
                                    )
                                }
                                Text(
                                    text = phrase.arabicMeaning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            if (phrase.exampleEnglish.isNotBlank()) {
                                UkUsMiniButtons(
                                    onPlayUk = {
                                        audioManager.speakSentence(phrase.exampleEnglish, Locale.UK, "ph_uk_$idx")
                                    },
                                    onPlayUs = {
                                        audioManager.speakSentence(phrase.exampleEnglish, Locale.US, "ph_us_$idx")
                                    }
                                )
                            }
                        }
                        if (idx < preview.lastIndex) {
                            HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }

        // h) "الكلمات المتشابهة": word family with part of speech on the opposite side
        if (entry.wordFamily.isNotEmpty()) {
            SectionCard(title = stringResource(R.string.similar_words_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.wordFamily.forEachIndexed { idx, member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenWord(member.word) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = member.word,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = LinkBlue
                                    )
                                }
                                if (member.arabicMeaning.isNotBlank()) {
                                    Text(
                                        text = member.arabicMeaning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                            }
                            PosChip(text = member.partOfSpeechArabic.ifBlank { member.partOfSpeech })
                        }
                        if (idx < entry.wordFamily.lastIndex) {
                            HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SenseContextRow(
    sense: SenseItem,
    targetWord: String,
    onPlayUk: () -> Unit,
    onPlayUs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(AccentRedContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${sense.index}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentRed
                )
            }
            Text(
                text = sense.arabicMeaning,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            PosChip(text = sense.partOfSpeechArabic)
            if (sense.registerTag.isNotBlank()) {
                RegisterBadge(text = sense.registerTag)
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(
                text = sense.englishDefinition,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (sense.exampleEnglish.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceElevated,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HighlightedEnglishText(
                            sentence = sense.exampleEnglish,
                            targetWord = targetWord
                        )
                        if (sense.exampleArabic.isNotBlank()) {
                            Text(
                                text = sense.exampleArabic,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    UkUsMiniButtons(
                        onPlayUk = onPlayUk,
                        onPlayUs = onPlayUs
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryTabContent(
    entry: WordEntry,
    audioManager: AudioPlayerManager,
    onOpenWord: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val groupedByPos = entry.senses.groupBy { it.partOfSpeech to it.partOfSpeechArabic }
        groupedByPos.forEach { (posPair, posSenses) ->
            val (posEn, posAr) = posPair
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DividerBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Longman-style headword + IPA + POS header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = entry.word,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimary
                                )
                                if (entry.usIpa.isNotBlank()) {
                                    Text(
                                        text = entry.usIpa,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted
                                    )
                                }
                                Text(
                                    text = posEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AccentRed
                                )
                            }
                        }
                        PosChip(text = posAr)
                    }

                    HorizontalDivider(color = DividerBorder, thickness = 1.dp)

                    posSenses.forEachIndexed { idx, sense ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AccentRed
                                )
                                if (sense.grammarLabel.isNotBlank()) {
                                    Text(
                                        text = sense.grammarLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LinkBlue
                                    )
                                }
                                Text(
                                    text = sense.arabicMeaning,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                            }
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = sense.englishDefinition,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            }
                            if (sense.exampleEnglish.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceElevated, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        HighlightedEnglishText(
                                            sentence = sense.exampleEnglish,
                                            targetWord = entry.word
                                        )
                                        if (sense.exampleArabic.isNotBlank()) {
                                            Text(
                                                text = sense.exampleArabic,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted
                                            )
                                        }
                                    }
                                    UkUsMiniButtons(
                                        onPlayUk = {
                                            audioManager.speakSentence(sense.exampleEnglish, Locale.UK, "dict_uk_${sense.index}")
                                        },
                                        onPlayUs = {
                                            audioManager.speakSentence(sense.exampleEnglish, Locale.US, "dict_us_${sense.index}")
                                        }
                                    )
                                }
                            }
                        }
                        if (idx < posSenses.lastIndex) {
                            HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }

        // THESAURUS Box
        if (entry.allSynonyms.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, LinkBlue.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "THESAURUS • المكنز اللغوي",
                        style = MaterialTheme.typography.labelLarge,
                        color = LinkBlue
                    )
                    ChipWrapRow(items = entry.allSynonyms.take(12), onChipClick = onOpenWord)
                }
            }
        }

        // COLLOCATIONS Box
        if (entry.collocations.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, RegisterGold.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "COLLOCATIONS • المتلازمات اللفظية",
                        style = MaterialTheme.typography.labelLarge,
                        color = RegisterGold
                    )
                    entry.collocations.forEachIndexed { idx, col ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = col.collocation,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                            }
                            Text(
                                text = col.arabicMeaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        if (idx < entry.collocations.lastIndex) {
                            HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                        }
                    }
                }
            }
        }

        // ORIGIN line
        if (!entry.origin.isNullOrBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ORIGIN • أصل الكلمة",
                        style = MaterialTheme.typography.labelMedium,
                        color = AccentRed
                    )
                    Text(
                        text = entry.origin,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun WordNetTabContent(
    entry: WordEntry,
    onOpenWord: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (entry.wordNetSynsets.isEmpty()) {
            Text(
                text = "لا تتوفر شبكة دلالية لهذه الكلمة حالياً.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            return
        }

        entry.wordNetSynsets.forEachIndexed { index, synset ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, DividerBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "حزمة دلالية #${index + 1}: ${synset.arabicMeaning}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        PosChip(text = synset.partOfSpeechArabic)
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = synset.definition,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }

                    if (synset.synonyms.isNotEmpty()) {
                        Text(
                            text = "المترادفات المرتبطة:",
                            style = MaterialTheme.typography.labelMedium,
                            color = LinkBlue
                        )
                        ChipWrapRow(items = synset.synonyms, onChipClick = onOpenWord)
                    }

                    if (synset.antonyms.isNotEmpty()) {
                        Text(
                            text = "الأضداد:",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentRed
                        )
                        ChipWrapRow(items = synset.antonyms, onChipClick = onOpenWord)
                    }

                    if (synset.relatedTerms.isNotEmpty()) {
                        Text(
                            text = "مفاهيم قريبة في الشبكة:",
                            style = MaterialTheme.typography.labelMedium,
                            color = RegisterGold
                        )
                        ChipWrapRow(items = synset.relatedTerms, onChipClick = onOpenWord)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    trailingAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DividerBorder)
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
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AccentRed
                )
                trailingAction?.invoke()
            }
            content()
        }
    }
}
