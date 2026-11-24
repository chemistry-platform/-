package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.ArabicTextUtils
import com.example.ui.components.DirectionToggleChip
import com.example.ui.components.HighlightedEnglishText
import com.example.ui.components.InteractiveWordChip
import com.example.ui.components.UsageLabelChip
import com.example.ui.viewmodel.SentenceLookupViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceLookupScreen(
    viewModel: SentenceLookupViewModel,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenDirectPhrase: (String) -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val query by viewModel.query.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var inlineQuery by remember(query) { mutableStateOf(query) }

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
                    onValueChange = { inlineQuery = it },
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
                // Card 1: Full sentence & ML Kit Machine Translation
                item(key = "sentence_translation", contentType = "sentence_translation_card") {
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
                                text = "الجملة الأصلية",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                            CompositionLocalProvider(
                                LocalLayoutDirection provides if (state.isArabicInput) LayoutDirection.Rtl else LayoutDirection.Ltr
                            ) {
                                Text(
                                    text = state.originalText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            HorizontalDivider(color = colors.outline, thickness = 1.dp)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "الترجمة الكاملة",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.primary
                                )
                                UsageLabelChip(text = "ترجمة آلية")
                            }

                            if (!state.machineTranslation.isNullOrBlank()) {
                                CompositionLocalProvider(
                                    LocalLayoutDirection provides if (state.isArabicInput) LayoutDirection.Ltr else LayoutDirection.Rtl
                                ) {
                                    Text(
                                        text = state.machineTranslation.orEmpty(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colors.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("sentence_machine_translation")
                                    )
                                }
                            } else if (state.missingTranslationPack) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colors.errorContainer,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "حمّل حزمة العربية والإنجليزية من شاشة اللغات للترجمة الآلية.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = onOpenLanguages) {
                                            Text("اللغات", color = colors.primary)
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "جاري الترجمة...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Card 2: Tappable Word Chips (Section 14.1)
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
                                                onClick = { onOpenWord(chip) },
                                                modifier = Modifier.testTag("sentence_chip_$chip")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 3: Real Tatoeba bilingual examples (Section 14.2)
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
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (state.isArabicInput) {
                                            Text(
                                                text = pair.arabic,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colors.onSurface
                                            )
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = pair.english,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        } else {
                                            HighlightedEnglishText(
                                                sentence = pair.english,
                                                targetWord = state.wordChips.firstOrNull().orEmpty()
                                            )
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
}
