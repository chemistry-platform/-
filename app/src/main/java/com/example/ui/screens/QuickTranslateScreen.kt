package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AudioPlayerManager
import com.example.domain.model.LanguageDirection
import com.example.domain.model.SearchSuggestion
import com.example.ui.components.LanguageSwapBar
import com.example.ui.components.NoInternetBanner
import com.example.ui.components.PosChip
import com.example.ui.components.UkUsMiniButtons
import com.example.ui.components.rememberVoiceInputLauncher
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.DividerBorder
import com.example.ui.theme.LinkBlue
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.QuickTranslateViewModel
import java.util.Locale

@Composable
fun QuickTranslateScreen(
    viewModel: QuickTranslateViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val direction by viewModel.direction.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val sentenceTranslation by viewModel.sentenceTranslation.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val launchVoice = rememberVoiceInputLauncher { spoken ->
        viewModel.onQueryChange(spoken)
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .statusBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            // Top bar with Back + Language Switcher
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
                        tint = TextPrimary
                    )
                }
                LanguageSwapBar(
                    direction = direction,
                    onSwap = { viewModel.swapLanguages() },
                    modifier = Modifier.weight(1f)
                )
            }

            // Search / input field
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester)
                    .testTag("quick_translate_input"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_placeholder_translate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = LinkBlue
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onQueryChange("") },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.cd_clear_search),
                                    tint = TextMuted
                                )
                            }
                        }
                        IconButton(
                            onClick = { launchVoice(direction) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.cd_voice_input),
                                tint = AccentRed
                            )
                        }
                    }
                },
                singleLine = false,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
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
                        val trimmed = query.trim()
                        if (trimmed.isNotEmpty()) {
                            onOpenWord(trimmed)
                        }
                    }
                )
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(2.dp),
                    color = AccentRed,
                    trackColor = DividerBorder
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
                    item {
                        NoInternetBanner()
                    }
                }

                // Dual Translation Card when translating phrases/sentences
                if (!sentenceTranslation.isNullOrBlank()) {
                    item {
                        DualTranslationCard(
                            sourceText = query.trim(),
                            translatedText = sentenceTranslation.orEmpty(),
                            isArabicResult = !sentenceTranslation.orEmpty().any { it in 'a'..'z' || it in 'A'..'Z' },
                            onCopy = {
                                clipboard.setText(AnnotatedString(sentenceTranslation.orEmpty()))
                            },
                            onShare = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, "${query.trim()}\n${sentenceTranslation.orEmpty()}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "مشاركة الترجمة"))
                            },
                            onPlayUk = {
                                audioManager.speakSentence(query.trim(), Locale.UK, "qt_uk")
                            },
                            onPlayUs = {
                                audioManager.speakSentence(query.trim(), Locale.US, "qt_us")
                            },
                            onPlayArabic = {
                                audioManager.speakArabic(sentenceTranslation.orEmpty(), "qt_ar")
                            }
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    item {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentRed
                        )
                    }
                }

                if (query.isNotBlank() && !isLoading) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenWord(query.trim()) }
                                .testTag("direct_lookup_button"),
                            color = SurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DividerBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "فتح صفحة المعجم الكاملة لـ \"${query.trim()}\"",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LinkBlue,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "عرض",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AccentRed
                                )
                            }
                        }
                    }
                }

                // Live suggestion rows: English word (bold) + Arabic meanings (muted), separated by thin dividers
                if (suggestions.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = SurfaceCard,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, DividerBorder)
                        ) {
                            Column {
                                suggestions.forEachIndexed { index, item ->
                                    SuggestionResultRow(
                                        suggestion = item,
                                        onClick = { onOpenWord(item.englishWord) }
                                    )
                                    if (index < suggestions.lastIndex) {
                                        HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                                    }
                                }
                            }
                        }
                    }
                } else if (query.isBlank()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "اكتب كلمة إنجليزية أو عربية أو جملة كاملة للترجمة الفورية",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("run", "resilient", "brightness", "ذهب").forEach { sample ->
                                    TextButton(onClick = { viewModel.onQueryChange(sample) }) {
                                        Text(
                                            text = sample,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = LinkBlue
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

@Composable
private fun DualTranslationCard(
    sourceText: String,
    translatedText: String,
    isArabicResult: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onPlayUk: () -> Unit,
    onPlayUs: () -> Unit,
    onPlayArabic: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DividerBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "الترجمة الفورية (بدون إنترنت عبر ML Kit)",
                style = MaterialTheme.typography.labelMedium,
                color = LinkBlue
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = sourceText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            HorizontalDivider(
                color = DividerBorder,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Text(
                text = translatedText,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isArabicResult) {
                        UkUsMiniButtons(onPlayUk = onPlayUk, onPlayUs = onPlayUs)
                    } else {
                        IconButton(onClick = onPlayArabic, modifier = Modifier.size(44.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "استماع",
                                tint = AccentRed
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "نسخ",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "مشاركة",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionResultRow(
    suggestion: SearchSuggestion,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("suggestion_${suggestion.englishWord}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = suggestion.englishWord,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = suggestion.arabicMeaning,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (suggestion.partOfSpeechArabic.isNotBlank()) {
            Spacer(modifier = Modifier.size(8.dp))
            PosChip(text = suggestion.partOfSpeechArabic)
        }
    }
}
