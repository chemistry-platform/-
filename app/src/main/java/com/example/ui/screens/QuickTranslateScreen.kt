package com.example.ui.screens

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.Accent
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.OfflinePackState
import com.example.core.tr
import com.example.data.local.SearchHistoryEntity
import com.example.domain.model.SearchSuggestion
import com.example.ui.components.AppIcons
import com.example.ui.components.CompactWordAudioButtons
import com.example.ui.components.DirectionToggleChip
import com.example.ui.components.InstantTranslationCard
import com.example.ui.components.LanguageSwapBar
import com.example.ui.components.NoInternetBanner
import com.example.ui.viewmodel.QuickTranslateViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun QuickTranslateScreen(
    viewModel: QuickTranslateViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenSentence: (String) -> Unit,
    modifier: Modifier = Modifier,
    offlinePackState: OfflinePackState? = null
) {
    val colors = MaterialTheme.colorScheme
    val query by viewModel.query.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val hasMoreSuggestions by viewModel.hasMoreSuggestions.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val instantTranslation by viewModel.instantTranslation.collectAsStateWithLifecycle()
    val isSearchSubmitted by viewModel.isSearchSubmitted.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val playingKey by audioManager.playingKey.collectAsStateWithLifecycle()

    var isFieldFocused by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(listState, hasMoreSuggestions, isLoadingMore, suggestions.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total to lastVisible
        }
            .distinctUntilChanged()
            .collect { (total, lastVisible) ->
                if (hasMoreSuggestions && !isLoadingMore && total > 0 && lastVisible >= total - 5) {
                    viewModel.loadMoreSuggestions()
                }
            }
    }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("quick_translate_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = tr("Back", "رجوع"),
                        tint = colors.onBackground
                    )
                }
                LanguageSwapBar(
                    direction = direction,
                    onSwap = { viewModel.swapLanguages() },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { viewModel.onQueryChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFieldFocused = it.isFocused }
                        .testTag("quick_translate_input"),
                    placeholder = {
                        Text(
                            text = tr("Search a word or sentence to translate...", "ابحث عن كلمة أو جملة لترجمتها..."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        IconButton(
                            onClick = {
                                if (query.isNotBlank()) {
                                    viewModel.submitSearch()
                                    keyboardController?.hide()
                                } else {
                                    runCatching { focusRequester.requestFocus() }
                                    keyboardController?.show()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("quick_translate_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = tr("Search", "بحث"),
                                tint = if (query.isNotBlank()) colors.primary else colors.secondary
                            )
                        }
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.onQueryChange("")
                                    runCatching { focusRequester.requestFocus() }
                                    keyboardController?.show()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("quick_translate_clear")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = tr("Clear search", "مسح البحث"),
                                    tint = colors.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
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
                            val trimmed = query.trim()
                            if (trimmed.isNotEmpty()) {
                                if (!isSearchSubmitted) {
                                    viewModel.submitSearch()
                                    keyboardController?.hide()
                                } else if (ArabicTextUtils.isMultiWordQuery(trimmed)) {
                                    onOpenSentence(trimmed)
                                } else {
                                    onOpenWord(trimmed)
                                }
                            }
                        }
                    )
                )

                DirectionToggleChip(
                    direction = direction,
                    onToggle = { viewModel.swapLanguages() }
                )
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(2.dp),
                    color = colors.primary,
                    trackColor = colors.outline
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (offlinePackState is OfflinePackState.Downloading) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("pack_download_banner"),
                    color = colors.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = offlinePackState.statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (offlinePackState.indeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = colors.primary,
                                trackColor = colors.outline
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { offlinePackState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = colors.primary,
                                trackColor = colors.outline
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("quick_translate_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isOnline) {
                    item(key = "offline_banner", contentType = "banner") { NoInternetBanner() }
                }

                if (query.isNotBlank()) {
                    val trimmedQuery = query.trim()
                    val isSentence = ArabicTextUtils.isMultiWordQuery(trimmedQuery)

                    // Show instant box & direct lookup ONLY after the user taps the Search icon / submits search
                    if (isSearchSubmitted) {
                        instantTranslation?.let { instant ->
                            item(key = "instant_translation", contentType = "instant_translation") {
                                InstantTranslationCard(
                                    result = instant,
                                    showMachineLabel = true,
                                    audioManager = audioManager,
                                    playingKey = playingKey,
                                    onOpenFull = {
                                        if (ArabicTextUtils.isMultiWordQuery(instant.query)) {
                                            onOpenSentence(instant.query)
                                        } else {
                                            onOpenWord(instant.query)
                                        }
                                    }
                                )
                            }
                        }

                        // Direct lookup row (multi-word -> SentenceLookupScreen, single word -> WordDetailScreen)
                        item(key = "direct_lookup", contentType = "direct_lookup") {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSentence) onOpenSentence(trimmedQuery)
                                        else onOpenWord(trimmedQuery)
                                    }
                                    .testTag("direct_lookup_button"),
                                color = colors.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isSentence) {
                                            tr(
                                                "Translate & analyze sentence: \"$trimmedQuery\"",
                                                "ترجمة وتحليل الجملة: \"$trimmedQuery\""
                                            )
                                        } else {
                                            tr(
                                                "Open full dictionary entry for \"$trimmedQuery\"",
                                                "فتح صفحة المعجم الكاملة لـ \"$trimmedQuery\""
                                            )
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.secondary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isSentence) tr("Translate", "ترجمة الجملة") else tr("Search", "بحث"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primary
                                    )
                                }
                            }
                        }
                    }

                    // Live prefix suggestions (screenshot-style POS-grouped single-line preview rows, 40/page)
                    itemsIndexed(
                        items = suggestions,
                        key = { idx, item -> "sug_${idx}_${item.word}" },
                        contentType = { _, _ -> "suggestion_row" }
                    ) { index, item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SuggestionResultRow(
                                suggestion = item,
                                audioManager = audioManager,
                                onOpenWord = { onOpenWord(item.word) }
                            )
                            if (index < suggestions.lastIndex) {
                                HorizontalDivider(color = colors.outline.copy(alpha = 0.6f), thickness = 1.dp)
                            }
                        }
                    }

                    if (isLoadingMore) {
                        item(key = "loading_more_suggestions", contentType = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = colors.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                } else if (isFieldFocused && recentHistory.isNotEmpty()) {
                    // Section 7: History shown ONLY when search field is tapped/focused while empty
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.History,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = tr("Recent Searches", "عمليات البحث الأخيرة"),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onBackground
                                )
                            }
                            TextButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier.testTag("clear_search_history_button")
                            ) {
                                Text(
                                    text = tr("Clear History", "مسح السجل"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary
                                )
                            }
                        }
                    }
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column {
                                recentHistory.forEachIndexed { index, item ->
                                    HistoryRow(
                                        item = item,
                                        onClick = { onOpenWord(item.resolvedEnglishWord) },
                                        onDelete = { viewModel.deleteHistoryItem(item.queryKey) }
                                    )
                                    if (index < recentHistory.lastIndex) {
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

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = colors.surface,
            title = { Text(tr("Clear History", "مسح السجل"), style = MaterialTheme.typography.headlineSmall) },
            text = { Text(tr("Clear your entire search history?", "هل تريد مسح سجل البحث بالكامل؟"), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearConfirm = false
                }) {
                    Text(tr("Clear", "مسح"), color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(tr("Cancel", "إلغاء"), color = colors.onSurfaceVariant)
                }
            }
        )
    }
}

private val POS_SEGMENT_SPLIT_REGEX =
    Regex("؛\\s+(?=(?:v\\.|n\\.|adj\\.|adv\\.|prep\\.|conj\\.|pron\\.|interj\\.|det\\.|part\\.|num\\.|idiom\\s|phr\\.|prov\\.))")

private val POS_LEADING_TAG_REGEX =
    Regex("^((?:v\\.|n\\.|adj\\.|adv\\.|prep\\.|conj\\.|pron\\.|interj\\.|det\\.|part\\.|num\\.|idiom|phr\\.|prov\\.)\\s*)(.*)$")

private fun splitPreviewByPos(preview: String): List<String> {
    if (preview.isBlank()) return emptyList()
    return preview.split(POS_SEGMENT_SPLIT_REGEX)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

@Composable
private fun SuggestionResultRow(
    suggestion: SearchSuggestion,
    audioManager: AudioPlayerManager,
    onOpenWord: () -> Unit
    val colors = MaterialTheme.colorScheme
    val previewText = remember(suggestion.posPreview, suggestion.summaryDefinition, suggestion.isArabic) {
        if (suggestion.posPreview.isNotBlank()) {
            suggestion.posPreview
        } else if (suggestion.isArabic) {
            suggestion.summaryDefinition.takeIf { it.any { c -> c in 'a'..'z' || c in 'A'..'Z' } }.orEmpty()
        } else {
            suggestion.summaryDefinition.takeIf { ArabicTextUtils.containsArabicLetters(it) }.orEmpty()
        }
    }
    val posLines = remember(previewText) { splitPreviewByPos(previewText) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onOpenWord)
            .padding(horizontal = 6.dp, vertical = 10.dp)
            .testTag("suggestion_${suggestion.word}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Inline status badge ("local" for offline pack / Room, "on" for network suggestion)
            Surface(
                color = if (suggestion.isLocal) colors.primaryContainer else colors.errorContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = if (suggestion.isLocal) "local" else "on",
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (suggestion.isLocal) colors.primary else colors.error,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            // Headword
            CompositionLocalProvider(
                LocalLayoutDirection provides if (suggestion.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Text(
                    text = suggestion.word,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            if (!suggestion.isArabic && !suggestion.isIdiom) {
                CompactWordAudioButtons(
                    hasUsAudio = true,
                    hasUkAudio = true,
                    onPlayUs = {
                        audioManager.playHumanPronunciation(
                            word = suggestion.word,
                            accent = Accent.US,
                            audioUrl = suggestion.usAudioUrl,
                            key = "sug_us_${suggestion.word}"
                        )
                    },
                    onPlayUk = {
                        audioManager.playHumanPronunciation(
                            word = suggestion.word,
                            accent = Accent.UK,
                            audioUrl = suggestion.ukAudioUrl,
                            key = "sug_uk_${suggestion.word}"
                        )
                    }
                )
            }
        }

        // All meanings written underneath the headword grouped by POS, never truncated to 1 line
        if (posLines.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                posLines.forEach { segment ->
                    val segIsArabic = ArabicTextUtils.containsArabicLetters(segment)
                    val match = POS_LEADING_TAG_REGEX.find(segment)
                    val styledText = buildAnnotatedString {
                        if (match != null) {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primary
                                )
                            ) {
                                append(match.groupValues[1])
                            }
                            append(match.groupValues[2])
                        } else {
                            append(segment)
                        }
                    }
                    CompositionLocalProvider(
                        LocalLayoutDirection provides if (segIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                    ) {
                        Text(
                            text = styledText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: SearchHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("history_item_${item.resolvedEnglishWord}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = item.displayQuery,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.previewSummary.isNotBlank()) {
                Text(
                    text = item.previewSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = tr("Remove from history", "حذف من السجل"),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
