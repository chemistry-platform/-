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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.domain.model.LanguageDirection

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
 * Section 3: Standalone human-audio speaker pill button (US / UK).
 * Disabled gracefully if no real recording URL exists.
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
            .then(
                if (hasAudio) Modifier.clickable(onClick = onClick) else Modifier
            ),
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
 * Section 3: Standalone US / UK buttons next to words in search results & confusable cards.
 * Only renders buttons for accents that have real human recordings.
 * Ripple is scoped strictly to each button (never the enclosing card/row).
 */
@Composable
fun CompactWordAudioButtons(
    hasUsAudio: Boolean,
    hasUkAudio: Boolean,
    onPlayUs: () -> Unit,
    onPlayUk: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!hasUsAudio && !hasUkAudio) return
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
    highlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = if (highlighted) colors.primaryContainer else colors.surfaceVariant,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (highlighted) colors.primary else colors.outline)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (highlighted) colors.primary else colors.secondary,
                maxLines = 1
            )
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
    highlightedChip: String? = null,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                InteractiveWordChip(
                    word = item,
                    highlighted = highlightedChip != null && item.equals(highlightedChip, ignoreCase = true),
                    onClick = { onChipClick(item) }
                )
            }
        }
    }
}
