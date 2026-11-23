package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.WifiOff
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.theme.AccentRed
import com.example.ui.theme.AccentRedContainer
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.DividerBorder
import com.example.ui.theme.LinkBlue
import com.example.ui.theme.RegisterGold
import com.example.ui.theme.RegisterGoldContainer
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.util.Locale

@Composable
fun rememberVoiceInputLauncher(
    onResult: (String) -> Unit
): (LanguageDirection) -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        if (activityResult.resultCode == Activity.RESULT_OK) {
            val matches = activityResult.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                onResult(spoken)
            }
        }
    }

    return { direction ->
        val langTag = if (direction == LanguageDirection.AR_TO_EN) "ar-SA" else "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن...")
        }
        runCatching {
            launcher.launch(intent)
        }.onFailure {
            Toast.makeText(
                context,
                "ميزة الإدخال الصوتي غير مدعومة على هذا الجهاز",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Composable
fun LanguageSwapBar(
    direction: LanguageDirection,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            color = TextPrimary,
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
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = stringResource(R.string.cd_swap_languages),
                tint = LinkBlue,
                modifier = Modifier.size(22.dp)
            )
        }

        Text(
            text = targetLabel,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PronunciationPillButton(
    regionLabel: String,
    ipa: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayIpa = ipa.ifBlank { "/.../" }.let {
        if (it.startsWith("/")) it else "/$it/"
    }
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = SurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, DividerBorder)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = "$regionLabel pronunciation",
                    tint = AccentRed,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = regionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimary
                )
                Text(
                    text = displayIpa,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun UkUsMiniButtons(
    onPlayUk: () -> Unit,
    onPlayUs: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MiniVoiceButton(
                label = "UK",
                onClick = onPlayUk,
                testTag = "play_uk_sentence"
            )
            MiniVoiceButton(
                label = "US",
                onClick = onPlayUs,
                testTag = "play_us_sentence"
            )
        }
    }
}

@Composable
private fun MiniVoiceButton(
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .size(width = 44.dp, height = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = label,
            tint = AccentRed,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )
    }
}

@Composable
fun RegisterBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = RegisterGoldContainer,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, RegisterGold.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = RegisterGold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PosChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = DividerBorder,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun InteractiveWordChip(
    word: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        color = SurfaceElevated,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, DividerBorder)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.bodySmall,
                color = LinkBlue,
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
                        color = AccentRed,
                        fontWeight = FontWeight.SemiBold
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
            color = TextPrimary,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
fun NoInternetBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = AccentRedContainer,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = stringResource(R.string.no_internet_title),
                tint = AccentRed,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "وضع عدم الاتصال: يتم عرض الكلمات المحفوظة والترجمة المحلية فقط",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun LoadingCard(message: String = "جاري البحث والترجمة...", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            color = AccentRed,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}

enum class BottomNavTab(val labelRes: Int, val testTag: String) {
    HOME(R.string.nav_home, "nav_home"),
    DISCOVER(R.string.nav_discover, "nav_discover"),
    FAVORITES(R.string.nav_favorites, "nav_favorites"),
    MORE(R.string.nav_more, "nav_more")
}

@Composable
fun AlMakhzanjiBottomBar(
    currentTab: BottomNavTab,
    onSelectTab: (BottomNavTab) -> Unit
) {
    Column(modifier = Modifier.background(SurfaceCard)) {
        HorizontalDivider(color = DividerBorder, thickness = 1.dp)
        NavigationBar(
            containerColor = SurfaceCard,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding()
        ) {
            BottomNavTab.entries.forEach { tab ->
                val selected = tab == currentTab
                val icon = when (tab) {
                    BottomNavTab.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
                    BottomNavTab.DISCOVER -> if (selected) Icons.Rounded.Explore else Icons.Outlined.Explore
                    BottomNavTab.FAVORITES -> if (selected) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder
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
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentRed,
                        selectedTextColor = AccentRed,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = AccentRedContainer
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
                    onClick = { onChipClick(item) }
                )
            }
        }
    }
}
