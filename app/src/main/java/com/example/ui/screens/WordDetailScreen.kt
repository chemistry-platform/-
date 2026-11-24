package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.Accent
import com.example.core.AudioPlayerManager
import com.example.data.repository.WordLoadState
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.PosSenseGroup
import com.example.domain.model.SenseItem
import com.example.domain.model.WordEntry
import com.example.ui.components.AppIcons
import com.example.ui.components.ChipWrapRow
import com.example.ui.components.CompactWordAudioButtons
import com.example.ui.components.HighlightedEnglishText
import com.example.ui.components.LoadingCard
import com.example.ui.components.PosChip
import com.example.ui.components.PronunciationPillButton
import com.example.ui.viewmodel.WordDetailViewModel
import java.net.URLEncoder
import java.util.Locale

@Composable
fun WordDetailScreen(
    viewModel: WordDetailViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenWord: (query: String, fallbackBase: String?, highlightChip: String?) -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val currentQuery by viewModel.currentQuery.collectAsStateWithLifecycle()
    val highlightedChip by viewModel.highlightedChip.collectAsStateWithLifecycle()
    val confusableDetails by viewModel.confusableDetails.collectAsStateWithLifecycle()
    val isLoadingConfusables by viewModel.isLoadingConfusables.collectAsStateWithLifecycle()
    val arabicTranslations by viewModel.arabicTranslations.collectAsStateWithLifecycle()
    val missingLanguagePackBanner by viewModel.missingLanguagePackBanner.collectAsStateWithLifecycle()

    var inlineQuery by remember(currentQuery) { mutableStateOf(currentQuery) }

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
                    onValueChange = { inlineQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
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
                                viewModel.loadWord(trimmed)
                            }
                        }
                    )
                )
            }

            if (missingLanguagePackBanner) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = colors.errorContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "حمّل حزمة العربية والإنجليزية من شاشة اللغات للترجمة الفورية",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            viewModel.dismissMissingPackBanner()
                            onOpenLanguages()
                        }) {
                            Text("اللغات", color = colors.primary)
                        }
                    }
                }
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
                            Button(onClick = onOpenLanguages) {
                                Text("فتح شاشة اللغات")
                            }
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
                    var phrasesExpanded by remember(entry.word, highlightedChip) {
                        mutableStateOf(!highlightedChip.isNullOrBlank())
                    }
                    var familyExpanded by remember(entry.word) { mutableStateOf(false) }
                    var confusablesExpanded by remember(entry.word) { mutableStateOf(false) }
                    var generalSpecExpanded by remember(entry.word) { mutableStateOf(false) }
                    var relatedExpanded by remember(entry.word) { mutableStateOf(false) }
                    var tatoebaExpanded by remember(entry.word) { mutableStateOf(true) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (state.isUpdating) {
                            item {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = colors.secondary,
                                    trackColor = colors.outline
                                )
                            }
                        }

                        if (!state.arabicQuery.isNullOrBlank()) {
                            item {
                                Text(
                                    text = "نتائج البحث لـ \"${state.arabicQuery}\"",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.secondary
                                )
                            }
                        }

                        // Headword Card with Standalone Human US/UK Audio Buttons (Section 3)
                        item {
                            HeadwordCard(
                                entry = entry,
                                isFavorite = isFavorite,
                                audioManager = audioManager,
                                onToggleFavorite = { viewModel.toggleFavorite() }
                            )
                        }

                        // Sense-by-sense layout grouped by POS (Section 4.2)
                        entry.posGroups.forEach { group ->
                            item(key = "pos_${group.partOfSpeech}") {
                                PosGroupCard(
                                    word = entry.word,
                                    group = group,
                                    showAll = expandedPosGroups[group.partOfSpeech] == true,
                                    onToggleShowAll = {
                                        val cur = expandedPosGroups[group.partOfSpeech] == true
                                        expandedPosGroups[group.partOfSpeech] = !cur
                                    },
                                    arabicTranslations = arabicTranslations,
                                    onToggleArabicHelp = { key, text ->
                                        viewModel.toggleArabicHelp(key, text)
                                    },
                                    onOpenWord = { onOpenWord(it, null, null) }
                                )
                            }
                        }

                        if (entry.hasWiktionaryAttribution) {
                            item {
                                Text(
                                    text = "المصدر: ويكاموس (Wiktionary) • CC BY-SA 4.0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        // 4.3 Collapsible Sections (shown ONLY if real data exists)
                        if (entry.synonyms.isNotEmpty()) {
                            item {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.synonyms_title),
                                    expanded = synonymsExpanded,
                                    onToggle = { synonymsExpanded = !synonymsExpanded },
                                    items = entry.synonyms,
                                    onChipClick = { onOpenWord(it, null, null) }
                                )
                            }
                        }

                        if (entry.antonyms.isNotEmpty()) {
                            item {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.antonyms_title),
                                    expanded = antonymsExpanded,
                                    onToggle = { antonymsExpanded = !antonymsExpanded },
                                    items = entry.antonyms,
                                    onChipClick = { onOpenWord(it, null, null) }
                                )
                            }
                        }

                        if (entry.collocations.isNotEmpty()) {
                            item {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.collocations_title),
                                    expanded = collocationsExpanded,
                                    onToggle = { collocationsExpanded = !collocationsExpanded },
                                    items = entry.collocations,
                                    onChipClick = { onOpenWord(it, entry.word, it) }
                                )
                            }
                        }

                        if (entry.prepositionsAndPhrases.isNotEmpty()) {
                            item {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.phrases_title),
                                    expanded = phrasesExpanded,
                                    onToggle = { phrasesExpanded = !phrasesExpanded },
                                    items = entry.prepositionsAndPhrases,
                                    highlightedChip = highlightedChip,
                                    onChipClick = { phrase ->
                                        viewModel.clearHighlightedChip()
                                        onOpenWord(phrase, entry.word, phrase)
                                    }
                                )
                            }
                        }

                        if (entry.wordFamily.isNotEmpty()) {
                            item {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.similar_words_title),
                                    count = entry.wordFamily.size,
                                    expanded = familyExpanded,
                                    onToggle = { familyExpanded = !familyExpanded }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        entry.wordFamily.forEachIndexed { idx, member ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable { onOpenWord(member.word, null, null) }
                                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Text(
                                                        text = member.word,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = colors.secondary
                                                    )
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

                        // 4.10 Easily Confused Words (lazy fetch ONLY when expanded)
                        if (entry.confusableCandidates.isNotEmpty()) {
                            item {
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
                                                        audioManager = audioManager,
                                                        arabicTranslations = arabicTranslations,
                                                        onToggleArabicHelp = { key, txt ->
                                                            viewModel.toggleArabicHelp(key, txt)
                                                        },
                                                        onOpenWord = { onOpenWord(detail.word, null, null) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // More general & specific terms
                        if (entry.generalTerms.isNotEmpty() || entry.specificTerms.isNotEmpty()) {
                            item {
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
                                                onChipClick = { onOpenWord(it, null, null) }
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
                                                onChipClick = { onOpenWord(it, null, null) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Related words (ml + rel_trg)
                        if (entry.relatedWords.isNotEmpty()) {
                            item {
                                CollapsibleChipsSection(
                                    title = stringResource(R.string.related_terms_title),
                                    expanded = relatedExpanded,
                                    onToggle = { relatedExpanded = !relatedExpanded },
                                    items = entry.relatedWords,
                                    onChipClick = { onOpenWord(it, null, null) }
                                )
                            }
                        }

                        // Tatoeba More Example Sentences
                        if (entry.tatoebaSentences.isNotEmpty()) {
                            item {
                                CollapsibleSectionCard(
                                    title = stringResource(R.string.more_examples_title),
                                    count = entry.tatoebaSentences.size,
                                    expanded = tatoebaExpanded,
                                    onToggle = { tatoebaExpanded = !tatoebaExpanded }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        entry.tatoebaSentences.forEachIndexed { idx, pair ->
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                HighlightedEnglishText(
                                                    sentence = pair.english,
                                                    targetWord = entry.word
                                                )
                                                Text(
                                                    text = pair.arabic,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant
                                                )
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

                        // 4.7 Links Only (Longman + Cambridge via Custom Tabs)
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val slug = encodeDictSlug(entry.word)
                                        openInCustomTab(
                                            context,
                                            "https://www.ldoceonline.com/dictionary/$slug"
                                        )
                                    },
                                    border = BorderStroke(1.dp, colors.outline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("open_longman_button")
                                ) {
                                    Icon(
                                        imageVector = AppIcons.OpenInNew,
                                        contentDescription = null,
                                        tint = colors.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Longman", color = colors.onSurface)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val slug = encodeDictSlug(entry.word)
                                        openInCustomTab(
                                            context,
                                            "https://dictionary.cambridge.org/dictionary/english/$slug"
                                        )
                                    },
                                    border = BorderStroke(1.dp, colors.outline),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("open_cambridge_button")
                                ) {
                                    Icon(
                                        imageVector = AppIcons.OpenInNew,
                                        contentDescription = null,
                                        tint = colors.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Cambridge", color = colors.onSurface)
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
private fun HeadwordCard(
    entry: WordEntry,
    isFavorite: Boolean,
    audioManager: AudioPlayerManager,
    onToggleFavorite: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasUs = audioManager.hasAudioOrCache(entry.word, Accent.US, entry.usAudioUrl)
    val hasUk = audioManager.hasAudioOrCache(entry.word, Accent.UK, entry.ukAudioUrl)

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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = entry.word,
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

            if (hasUs || hasUk || entry.usIpa.isNotBlank() || entry.ukIpa.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasUs || entry.usIpa.isNotBlank()) {
                        PronunciationPillButton(
                            regionLabel = "US",
                            ipa = entry.usIpa,
                            hasAudio = hasUs,
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
                    }
                    if (hasUk || entry.ukIpa.isNotBlank()) {
                        PronunciationPillButton(
                            regionLabel = "UK",
                            ipa = entry.ukIpa,
                            hasAudio = hasUk,
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
    arabicTranslations: Map<String, String>,
    onToggleArabicHelp: (key: String, text: String) -> Unit,
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = group.partOfSpeech,
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.primary
                    )
                }
                PosChip(text = group.partOfSpeechArabic)
            }

            HorizontalDivider(color = colors.outline, thickness = 1.dp)

            visibleSenses.forEachIndexed { idx, sense ->
                SenseCardContent(
                    word = word,
                    sense = sense,
                    arabicTranslations = arabicTranslations,
                    onToggleArabicHelp = onToggleArabicHelp,
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

@Composable
private fun SenseCardContent(
    word: String,
    sense: SenseItem,
    arabicTranslations: Map<String, String>,
    onToggleArabicHelp: (key: String, text: String) -> Unit,
    onOpenWord: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val defKey = "def_${sense.partOfSpeech}_${sense.index}"
    val translatedDef = arabicTranslations[defKey]

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = sense.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ArabicHelpToggle(
                    translatedText = translatedDef,
                    onToggle = { onToggleArabicHelp(defKey, sense.definition) }
                )
            }
        }

        // Examples from the SAME sense
        sense.examples.forEachIndexed { exIdx, example ->
            val exKey = "ex_${sense.partOfSpeech}_${sense.index}_$exIdx"
            val translatedEx = arabicTranslations[exKey]

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HighlightedEnglishText(
                        sentence = example,
                        targetWord = word
                    )
                    ArabicHelpToggle(
                        translatedText = translatedEx,
                        onToggle = { onToggleArabicHelp(exKey, example) }
                    )
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
                ChipWrapRow(items = sense.synonyms, onChipClick = onOpenWord)
            }
        }
        if (sense.antonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "أضداد المعنى:",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                ChipWrapRow(items = sense.antonyms, onChipClick = onOpenWord)
            }
        }
    }
}

@Composable
private fun ArabicHelpToggle(
    translatedText: String?,
    onToggle: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (translatedText != null) "إخفاء الترجمة العربية" else "ترجمة عربية",
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
        if (translatedText != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "ترجمة آلية",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.tertiary
                )
                Text(
                    text = translatedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfusableCard(
    detail: ConfusableWordDetail,
    audioManager: AudioPlayerManager,
    arabicTranslations: Map<String, String>,
    onToggleArabicHelp: (key: String, text: String) -> Unit,
    onOpenWord: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasUs = audioManager.hasAudioOrCache(detail.word, Accent.US, detail.usAudioUrl)
    val hasUk = audioManager.hasAudioOrCache(detail.word, Accent.UK, detail.ukAudioUrl)
    val defKey = "conf_${detail.word}"

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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onOpenWord)
                        .padding(vertical = 2.dp),
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
                ArabicHelpToggle(
                    translatedText = arabicTranslations[defKey],
                    onToggle = { onToggleArabicHelp(defKey, detail.shortDefinition) }
                )
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

private fun encodeDictSlug(word: String): String {
    val dashed = word.trim().lowercase(Locale.US).replace(Regex("\\s+"), "-")
    return URLEncoder.encode(dashed, "UTF-8").replace("+", "-")
}

private fun openInCustomTab(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
