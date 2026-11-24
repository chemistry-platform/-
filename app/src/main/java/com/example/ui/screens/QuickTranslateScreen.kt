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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.data.local.SearchHistoryEntity
import com.example.domain.model.SearchSuggestion
import com.example.ui.components.AppIcons
import com.example.ui.components.CompactWordAudioButtons
import com.example.ui.components.DirectionToggleChip
import com.example.ui.components.InstantTranslationCard
import com.example.ui.components.LanguageSwapBar
import com.example.ui.components.NoInternetBanner
import com.example.ui.components.PosChip
import com.example.ui.viewmodel.QuickTranslateViewModel

@Composable
fun QuickTranslateScreen(
    viewModel: QuickTranslateViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenSentence: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val query by viewModel.query.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val instantTranslation by viewModel.instantTranslation.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val playingKey by audioManager.playingKey.collectAsStateWithLifecycle()

    var isFieldFocused by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
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
                        contentDescription = stringResource(R.string.cd_back),
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
                            text = stringResource(R.string.search_placeholder_translate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.secondary
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onQueryChange("") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
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
                                if (ArabicTextUtils.isMultiWordQuery(trimmed)) {
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isOnline) {
                    item(key = "offline_banner", contentType = "banner") { NoInternetBanner() }
                }

                if (query.isNotBlank()) {
                    val trimmedQuery = query.trim()
                    val isSentence = ArabicTextUtils.isMultiWordQuery(trimmedQuery)

                    // BUG 6: Live translation directly below search bar & speaker buttons row
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
                                        "ترجمة وتحليل الجملة: \"$trimmedQuery\""
                                    } else {
                                        "فتح صفحة المعجم الكاملة لـ \"$trimmedQuery\""
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.secondary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isSentence) "ترجمة الجملة" else "بحث",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.primary
                                )
                            }
                        }
                    }

                    // Live suggestions (Section 6 & 15)
                    if (suggestions.isNotEmpty()) {
                        item(key = "suggestions_card", contentType = "suggestions") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                Column {
                                    suggestions.forEachIndexed { index, item ->
                                        SuggestionResultRow(
                                            suggestion = item,
                                            audioManager = audioManager,
                                            onOpenWord = { onOpenWord(item.word) }
                                        )
                                        if (index < suggestions.lastIndex) {
                                            HorizontalDivider(color = colors.outline, thickness = 1.dp)
                                        }
                                    }
                                }
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
                                    text = stringResource(R.string.recent_searches),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onBackground
                                )
                            }
                            TextButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier.testTag("clear_search_history_button")
                            ) {
                                Text(
                                    text = "مسح السجل",
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
            title = { Text("مسح السجل", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("هل تريد مسح سجل البحث بالكامل؟", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearConfirm = false
                }) {
                    Text("مسح", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("إلغاء", color = colors.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun SuggestionResultRow(
    suggestion: SearchSuggestion,
    audioManager: AudioPlayerManager,
    onOpenWord: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasUs = !suggestion.isArabic && audioManager.hasAudioOrCache(suggestion.word, Accent.US, suggestion.usAudioUrl)
    val hasUk = !suggestion.isArabic && audioManager.hasAudioOrCache(suggestion.word, Accent.UK, suggestion.ukAudioUrl)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenWord)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("suggestion_${suggestion.word}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (suggestion.isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Text(
                    text = suggestion.word,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (suggestion.summaryDefinition.isNotBlank()) {
                Text(
                    text = suggestion.summaryDefinition,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (suggestion.partOfSpeech.isNotBlank()) {
                PosChip(text = suggestion.partOfSpeech)
            }
            if (!suggestion.isArabic) {
                CompactWordAudioButtons(
                    hasUsAudio = hasUs,
                    hasUkAudio = hasUk,
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
                contentDescription = "حذف من السجل",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
