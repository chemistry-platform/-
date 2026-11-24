package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.Accent
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.AudioSpeedOptions
import com.example.domain.model.InstantTranslationResult
import com.example.domain.model.LanguageDirection
import com.example.domain.model.PhraseSheetData
import java.util.Locale

@Composable
fun LanguageSwapBar(
    direction: LanguageDirection,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val sourceLabel = if (direction == LanguageDirection.EN_TO_AR) {
        stringResource(R.string.lang_english)
    } else {
        stringResource(R.string.lang_arabic)
    }
    val targetLabel = if (direction == LanguageDirection.EN_TO_AR) {
        stringResource(R.string.lang_arabic)
    } else {
        stringResource(R.string.lang_english)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = sourceLabel,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onSwap)
                .testTag("swap_language_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.SwapHoriz,
                contentDescription = stringResource(R.string.cd_swap_languages),
                tint = colors.secondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = targetLabel,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Human-audio speaker pill button (US / UK).
 * Remains clickable even when no recording exists so tapping provides clear feedback ("لا يوجد صوت متاح").
 */
@Composable
fun PronunciationPillButton(
    regionLabel: String,
    ipa: String,
    hasAudio: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val displayIpa = ipa.ifBlank { "" }.let {
        if (it.isNotEmpty() && !it.startsWith("/")) "/$it/" else it
    }
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = AppIcons.VolumeUp,
                    contentDescription = if (regionLabel == "US") {
                        stringResource(R.string.cd_speak_us)
                    } else {
                        stringResource(R.string.cd_speak_uk)
                    },
                    tint = if (hasAudio) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = regionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasAudio) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f)
                )
                if (displayIpa.isNotEmpty()) {
                    Text(
                        text = displayIpa,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Standalone US / UK buttons next to words in search results & confusable cards.
 * Only renders buttons for accents that have real human recordings, with optional unavailable notice.
 */
@Composable
fun CompactWordAudioButtons(
    hasUsAudio: Boolean,
    hasUkAudio: Boolean,
    onPlayUs: () -> Unit,
    onPlayUk: () -> Unit,
    showUnavailableLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!hasUsAudio && !hasUkAudio) {
        if (showUnavailableLabel) {
            Text(
                text = "لا يوجد صوت متاح",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier
            )
        }
        return
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (hasUsAudio) {
                MiniSpeakerButton(label = "US", onClick = onPlayUs, testTag = "audio_btn_us")
            }
            if (hasUkAudio) {
                MiniSpeakerButton(label = "UK", onClick = onPlayUk, testTag = "audio_btn_uk")
            }
        }
    }
}

@Composable
private fun MiniSpeakerButton(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .size(width = 44.dp, height = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = AppIcons.VolumeUp,
                contentDescription = label,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RegisterBadge(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = colors.tertiaryContainer,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, colors.tertiary.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.tertiary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun UsageLabelChip(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = colors.tertiaryContainer,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, colors.tertiary.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = colors.tertiary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DirectionToggleChip(
    direction: LanguageDirection,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val desc = if (direction == LanguageDirection.AR_TO_EN) {
        "عربي ⇄ إنجليزي"
    } else {
        "إنجليزي ⇄ عربي"
    }
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .testTag("manual_direction_toggle"),
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.6f))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.secondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun PosChip(text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun InteractiveWordChip(
    word: String,
    onClick: () -> Unit,
    arabicSubtext: String? = null,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val wordIsArabic = ArabicTextUtils.containsArabicLetters(word)
    val subTrimmed = arabicSubtext?.trim().orEmpty()
    val hasSub = subTrimmed.isNotEmpty()
    val subIsArabic = ArabicTextUtils.containsArabicLetters(subTrimmed)

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = if (hasSub) 44.dp else 36.dp)
            .clip(RoundedCornerShape(if (hasSub) 14.dp else 999.dp))
            .clickable(onClick = onClick),
        color = if (highlighted) colors.primaryContainer else colors.surfaceVariant,
        shape = RoundedCornerShape(if (hasSub) 14.dp else 999.dp),
        border = BorderStroke(1.dp, if (highlighted) colors.primary else colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = if (hasSub) 6.dp else 8.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(
                LocalLayoutDirection provides if (wordIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Text(
                    text = word,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (highlighted) colors.primary else colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasSub) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (subIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = subTrimmed,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun HighlightedEnglishText(
    sentence: String,
    targetWord: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val annotated = buildAnnotatedString {
        if (targetWord.isBlank()) {
            append(sentence)
        } else {
            val regex = Regex("\\b${Regex.escape(targetWord)}\\w*", RegexOption.IGNORE_CASE)
            var lastIndex = 0
            regex.findAll(sentence).forEach { match ->
                if (match.range.first > lastIndex) {
                    append(sentence.substring(lastIndex, match.range.first))
                }
                withStyle(
                    SpanStyle(
                        color = colors.primary,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(match.value)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < sentence.length) {
                append(sentence.substring(lastIndex))
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = modifier.fillMaxWidth()
        )
    }
}

/**
 * Highlights recognized multi-word idioms inside a sentence (BUG 4).
 */
@Composable
fun HighlightedSentenceWithIdioms(
    sentence: String,
    idiomPhrases: List<String>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val annotated = buildAnnotatedString {
        val activePhrases = idiomPhrases.map { it.trim() }.filter { it.length >= 3 }
        if (activePhrases.isEmpty()) {
            append(sentence)
        } else {
            val pattern = activePhrases
                .sortedByDescending { it.length }
                .joinToString("|") { Regex.escape(it) }
            val regex = Regex("\\b($pattern)\\b", RegexOption.IGNORE_CASE)
            var lastIndex = 0
            regex.findAll(sentence).forEach { match ->
                if (match.range.first > lastIndex) {
                    append(sentence.substring(lastIndex, match.range.first))
                }
                withStyle(
                    SpanStyle(
                        color = colors.primary,
                        background = colors.primaryContainer.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(match.value)
                }
                lastIndex = match.range.last + 1
            }
            if (lastIndex < sentence.length) {
                append(sentence.substring(lastIndex))
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            modifier = modifier
        )
    }
}

@Composable
fun NoInternetBanner(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.errorContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = AppIcons.WifiOff,
                contentDescription = stringResource(R.string.no_internet_title),
                tint = colors.error,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "وضع عدم الاتصال: يتم عرض الكلمات المحفوظة والترجمة المحلية فقط",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onErrorContainer
            )
        }
    }
}

@Composable
fun LoadingCard(message: String = "جاري التحميل...", modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            color = colors.primary,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
    }
}

enum class BottomNavTab(val labelRes: Int, val testTag: String) {
    HOME(R.string.nav_home, "nav_home"),
    DISCOVER(R.string.nav_discover, "nav_discover"),
    FILES(R.string.file_translation_title, "nav_files"),
    FAVORITES(R.string.nav_favorites, "nav_favorites"),
    MORE(R.string.nav_more, "nav_more")
}

@Composable
fun AlMakhzanjiBottomBar(
    currentTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.background(colors.surface)) {
        HorizontalDivider(color = colors.outline, thickness = 1.dp)
        NavigationBar(
            containerColor = colors.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            BottomNavTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val icon = when (tab) {
                    BottomNavTab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
                    BottomNavTab.DISCOVER -> AppIcons.Explore
                    BottomNavTab.FILES -> AppIcons.Description
                    BottomNavTab.FAVORITES -> if (selected) AppIcons.Bookmark else AppIcons.BookmarkBorder
                    BottomNavTab.MORE -> if (selected) Icons.Rounded.Menu else Icons.Outlined.Menu
                }
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    modifier = Modifier.testTag(tab.testTag),
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(tab.labelRes),
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                        indicatorColor = colors.primaryContainer
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipWrapRow(
    items: List<String>,
    onChipClick: (String) -> Unit,
    chipMeanings: Map<String, String> = emptyMap(),
    highlightedChip: String? = null,
    modifier: Modifier = Modifier
) {
    val isRtlItems = items.firstOrNull()?.let { ArabicTextUtils.containsArabicLetters(it) } == true
    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtlItems) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                val sub = chipMeanings[item.trim().lowercase(Locale.US)]
                InteractiveWordChip(
                    word = item,
                    arabicSubtext = sub,
                    highlighted = highlightedChip != null && item.equals(highlightedChip, ignoreCase = true),
                    onClick = { onChipClick(item) }
                )
            }
        }
    }
}

/**
 * Renders Arabic translated text with an optional tiny, low-contrast inline "ترجمة آلية" label.
 */
@Composable
fun ArabicTranslatedText(
    text: String,
    isMachineTranslated: Boolean,
    showMachineLabel: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val annotated = buildAnnotatedString {
        append(text)
        if (isMachineTranslated && showMachineLabel) {
            append("  ")
            withStyle(
                SpanStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    color = colors.onSurfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                append("ترجمة آلية")
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Text(
            text = annotated,
            style = style,
            color = color,
            modifier = modifier
        )
    }
}

/**
 * Standalone small clickable speaker button for sentences & examples.
 */
@Composable
fun SentenceSpeakerButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    contentDescription: String = "استماع للجملة",
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isPlaying) colors.primaryContainer else colors.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Close else AppIcons.VolumeUp,
            contentDescription = if (isPlaying) "إيقاف الصوت" else contentDescription,
            tint = if (isPlaying) colors.primary else colors.secondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * BUG 7: Compact long-text audio playback bar with Stop button + live progress indicator.
 */
@Composable
fun LongTextAudioBar(
    title: String,
    isPlaying: Boolean,
    ttsProgress: AudioPlayerManager.TtsProgress?,
    onPlayClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    playTag: String = "long_audio_play",
    stopTag: String = "long_audio_stop"
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SentenceSpeakerButton(
                isPlaying = isPlaying,
                onClick = { if (isPlaying) onStopClick() else onPlayClick() },
                contentDescription = title,
                modifier = Modifier.testTag(playTag)
            )
            Text(
                text = if (isPlaying) {
                    if (ttsProgress != null && ttsProgress.totalChunks > 1) {
                        "جاري القراءة: مقطع ${ttsProgress.currentChunk} من ${ttsProgress.totalChunks}"
                    } else {
                        "جاري القراءة..."
                    }
                } else {
                    title
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isPlaying) colors.primary else colors.secondary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isPlaying) {
                Surface(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onStopClick)
                        .testTag(stopTag),
                    color = colors.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "إيقاف القراءة",
                            tint = colors.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "إيقاف",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.error
                        )
                    }
                }
            }
        }
        if (isPlaying && ttsProgress != null && ttsProgress.totalChunks > 1) {
            val fraction = (ttsProgress.currentChunk.toFloat() / ttsProgress.totalChunks.toFloat())
                .coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = colors.primary,
                trackColor = colors.outline
            )
        }
    }
}

/**
 * BUG 8: Compact ~28dp height separate audio speed button with dropdown picker (0.75x, 1x, 1.25x, 1.5x).
 */
@Composable
fun AudioSpeedSelector(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = AudioSpeedOptions.formatLabel(currentSpeed)

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .testTag("audio_speed_button"),
            color = colors.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "السرعة: $currentLabel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.primary,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "تغيير سرعة الصوت",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AudioSpeedOptions.ALL.forEach { speed ->
                val selected = kotlin.math.abs(currentSpeed - speed) < 0.01f
                val label = AudioSpeedOptions.formatLabel(speed)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selected) colors.primary else colors.onSurface
                        )
                    },
                    onClick = {
                        onSelectSpeed(speed)
                        expanded = false
                    },
                    modifier = Modifier.testTag("audio_speed_$label")
                )
            }
        }
    }
}

/**
 * BUG 6 & BUG 8: Instant live translation card directly below the search bar & US/UK audio row.
 */
@Composable
fun InstantTranslationCard(
    result: InstantTranslationResult,
    showMachineLabel: Boolean,
    audioManager: AudioPlayerManager,
    playingKey: String?,
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isSingleEnglishWord = !result.isArabicSource && !ArabicTextUtils.isMultiWordQuery(result.query)
    val hasUs = isSingleEnglishWord && audioManager.hasAudioOrCache(result.query, Accent.US, result.usAudioUrl)
    val hasUk = isSingleEnglishWord && audioManager.hasAudioOrCache(result.query, Accent.UK, result.ukAudioUrl)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFull)
            .testTag("instant_translation_card"),
        color = colors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row holding query & speaker buttons (US/UK for single English word; TTS for sentences/Arabic)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (result.isArabicSource) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = result.query,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isSingleEnglishWord) {
                    CompactWordAudioButtons(
                        hasUsAudio = hasUs,
                        hasUkAudio = hasUk,
                        onPlayUs = {
                            audioManager.playHumanPronunciation(
                                word = result.query,
                                accent = Accent.US,
                                audioUrl = result.usAudioUrl,
                                key = "inst_us_${result.query}"
                            )
                        },
                        onPlayUk = {
                            audioManager.playHumanPronunciation(
                                word = result.query,
                                accent = Accent.UK,
                                audioUrl = result.ukAudioUrl,
                                key = "inst_uk_${result.query}"
                            )
                        },
                        showUnavailableLabel = true
                    )
                } else {
                    val sentKey = "inst_sent_${result.query.hashCode()}"
                    SentenceSpeakerButton(
                        isPlaying = playingKey == sentKey,
                        onClick = {
                            if (playingKey == sentKey) {
                                audioManager.stopPlayback()
                            } else {
                                audioManager.playSentenceAudio(
                                    sentence = result.query,
                                    audioUrl = null,
                                    key = sentKey
                                )
                            }
                        }
                    )
                }
            }

            HorizontalDivider(color = colors.outline, thickness = 1.dp)

            // Live translation directly under the row holding the speaker buttons
            if (result.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = colors.secondary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "جاري الترجمة الفورية...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            } else if (!result.translation.isNullOrBlank()) {
                if (result.isArabicSource) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = result.translation,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.primary,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("instant_translation_text")
                            )
                        }
                        if (ArabicTextUtils.isMultiWordQuery(result.translation)) {
                            val transKey = "inst_trans_${result.translation.hashCode()}"
                            SentenceSpeakerButton(
                                isPlaying = playingKey == transKey,
                                onClick = {
                                    if (playingKey == transKey) audioManager.stopPlayback()
                                    else audioManager.playSentenceAudio(result.translation, null, transKey)
                                }
                            )
                        }
                    }
                } else {
                    ArabicTranslatedText(
                        text = result.translation,
                        isMachineTranslated = result.isMachine,
                        showMachineLabel = showMachineLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("instant_translation_text")
                    )
                }
            } else if (result.missingPack) {
                Text(
                    text = "حمّل حزمة العربية والإنجليزية لتفعيل الترجمة الفورية دون إنترنت.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.tertiary
                )
            } else {
                Text(
                    text = "تعذر الحصول على ترجمة فورية حالياً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * BUG 3: ModalBottomSheet shown when tapping a chip/phrase that has no full dictionary entry.
 * Shows the phrase, translation directly below, one real example if any, and speaker buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhraseFallbackBottomSheet(
    sheetData: PhraseSheetData,
    showMachineLabel: Boolean,
    audioManager: AudioPlayerManager,
    playingKey: String?,
    onOpenSentenceLookup: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isArabicPhrase = ArabicTextUtils.containsArabicLetters(sheetData.phrase)
    val isSingleEnglishWord = !isArabicPhrase && !ArabicTextUtils.isMultiWordQuery(sheetData.phrase)
    val hasUs = isSingleEnglishWord && audioManager.hasAudioOrCache(sheetData.phrase, Accent.US, sheetData.usAudioUrl)
    val hasUk = isSingleEnglishWord && audioManager.hasAudioOrCache(sheetData.phrase, Accent.UK, sheetData.ukAudioUrl)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (isArabicPhrase) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = sheetData.phrase,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isSingleEnglishWord) {
                    CompactWordAudioButtons(
                        hasUsAudio = hasUs,
                        hasUkAudio = hasUk,
                        onPlayUs = {
                            audioManager.playHumanPronunciation(
                                word = sheetData.phrase,
                                accent = Accent.US,
                                audioUrl = sheetData.usAudioUrl,
                                key = "sheet_us_${sheetData.phrase}"
                            )
                        },
                        onPlayUk = {
                            audioManager.playHumanPronunciation(
                                word = sheetData.phrase,
                                accent = Accent.UK,
                                audioUrl = sheetData.ukAudioUrl,
                                key = "sheet_uk_${sheetData.phrase}"
                            )
                        },
                        showUnavailableLabel = true
                    )
                } else {
                    val sheetKey = "sheet_phrase_${sheetData.phrase.hashCode()}"
                    SentenceSpeakerButton(
                        isPlaying = playingKey == sheetKey,
                        onClick = {
                            if (playingKey == sheetKey) audioManager.stopPlayback()
                            else audioManager.playSentenceAudio(sheetData.phrase, null, sheetKey)
                        }
                    )
                }
            }

            if (sheetData.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = colors.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "جاري جلب المعنى والمثال...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            } else {
                val meaning = sheetData.arabicMeaning?.takeIf { it.isNotBlank() }
                if (meaning != null) {
                    if (isArabicPhrase) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = meaning,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.primary,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        ArabicTranslatedText(
                            text = meaning,
                            isMachineTranslated = sheetData.isArabicMachine,
                            showMachineLabel = showMachineLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (sheetData.missingArabicPack) {
                    Text(
                        text = "حمّل حزمة اللغة العربية لعرض الترجمة دون إنترنت.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.tertiary
                    )
                }

                if (sheetData.example.isNotBlank()) {
                    HorizontalDivider(color = colors.outline)
                    Text(
                        text = "مثال حقيقي",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.secondary
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surfaceVariant,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val exKey = "sheet_ex_${sheetData.phrase.hashCode()}"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HighlightedEnglishText(
                                    sentence = sheetData.example,
                                    targetWord = sheetData.phrase,
                                    modifier = Modifier.weight(1f)
                                )
                                SentenceSpeakerButton(
                                    isPlaying = playingKey == exKey,
                                    onClick = {
                                        if (playingKey == exKey) audioManager.stopPlayback()
                                        else audioManager.playSentenceAudio(
                                            sentence = sheetData.example,
                                            audioUrl = sheetData.exampleAudioUrl,
                                            key = exKey
                                        )
                                    }
                                )
                            }
                            if (!sheetData.exampleArabic.isNullOrBlank()) {
                                ArabicTranslatedText(
                                    text = sheetData.exampleArabic,
                                    isMachineTranslated = sheetData.exampleIsMachine,
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (ArabicTextUtils.isMultiWordQuery(sheetData.phrase)) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenSentenceLookup(sheetData.phrase)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("تحليل العبارة")
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, colors.outline),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("إغلاق", color = colors.onSurface)
                }
            }
        }
    }
}

/**
 * Clear Arabic message + download button at top of results when Arabic pack is missing.
 */
@Composable
fun MissingArabicPackTopBanner(
    isDownloading: Boolean,
    errorMessage: String?,
    onDownloadClick: () -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.errorContainer,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "حزمة اللغة العربية للترجمة غير محمّلة. حمّلها لعرض المعاني والترجمات بالعربية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onDownloadClick,
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    modifier = Modifier.testTag("top_download_arabic_pack_button")
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            color = colors.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text("تحميل", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (!errorMessage.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpenLanguages) {
                        Text("اللغات", color = colors.secondary)
                    }
                }
            }
        }
    }
}

/**
 * Shown when no English TTS voice is installed on device.
 */
@Composable
fun MissingEnglishTtsVoiceBanner(
    onInstallVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ثبّت صوتاً إنجليزياً من إعدادات الجهاز",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onInstallVoiceClick,
                modifier = Modifier.testTag("install_tts_voice_button")
            ) {
                Text("تثبيت", color = colors.secondary)
            }
        }
    }
}
