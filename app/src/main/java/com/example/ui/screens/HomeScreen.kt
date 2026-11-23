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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.SearchHistoryEntity
import com.example.domain.model.LanguageDirection
import com.example.ui.components.LanguageSwapBar
import com.example.ui.components.NoInternetBanner
import com.example.ui.components.RegisterBadge
import com.example.ui.components.rememberVoiceInputLauncher
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.DividerBorder
import com.example.ui.theme.LinkBlue
import com.example.ui.theme.RegisterGold
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenQuickTranslate: (initialQuery: String, isArabicSource: Boolean) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val direction by viewModel.languageDirection.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    var comingSoonFeature by remember { mutableStateOf<String?>(null) }

    val launchVoice = rememberVoiceInputLauncher { spokenText ->
        onOpenQuickTranslate(spokenText, direction == LanguageDirection.AR_TO_EN)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "قاموس ومترجم عربي - إنجليزي ذكي",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, DividerBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = RegisterGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "مفتوح المصدر",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            if (!isOnline) {
                item {
                    NoInternetBanner()
                }
            }

            // Rounded card with blue border (#6AA6FF) containing language switcher, search input placeholder, and mic button
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, LinkBlue)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LanguageSwapBar(
                            direction = direction,
                            onSwap = { viewModel.swapLanguages() }
                        )
                        HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenQuickTranslate("", direction == LanguageDirection.AR_TO_EN)
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                                .testTag("home_search_card"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.search_placeholder_home),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextMuted
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceElevated)
                                    .clickable { launchVoice(direction) }
                                    .testTag("home_voice_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = stringResource(R.string.cd_voice_input),
                                    tint = AccentRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Three feature tiles: كاميرا, محادثة, قواعد (showing "قريباً")
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ComingSoonTile(
                        title = stringResource(R.string.tile_camera),
                        icon = Icons.Rounded.CameraAlt,
                        onClick = { comingSoonFeature = "ترجمة الكاميرا الفورية" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_camera")
                    )
                    ComingSoonTile(
                        title = stringResource(R.string.tile_conversation),
                        icon = Icons.Rounded.Forum,
                        onClick = { comingSoonFeature = "المحادثة الصوتية الثنائية" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_conversation")
                    )
                    ComingSoonTile(
                        title = stringResource(R.string.tile_grammar),
                        icon = Icons.Rounded.MenuBook,
                        onClick = { comingSoonFeature = "دليل القواعد والتصريف" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tile_grammar")
                    )
                }
            }

            // Recent searches header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.recent_searches),
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                    }
                    if (recentHistory.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = "مسح الكل",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            if (recentHistory.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DividerBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.empty_recent),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                            TextButton(
                                onClick = { onOpenWord("run") },
                                modifier = Modifier.testTag("try_run_button")
                            ) {
                                Text(
                                    text = "جرّب البحث عن كلمة: run",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LinkBlue
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DividerBorder)
                    ) {
                        Column {
                            recentHistory.forEachIndexed { index, item ->
                                RecentSearchRow(
                                    item = item,
                                    onClick = { onOpenWord(item.resolvedEnglishWord) },
                                    onDelete = { viewModel.deleteHistoryItem(item.queryKey) }
                                )
                                if (index < recentHistory.lastIndex) {
                                    HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    comingSoonFeature?.let { featureName ->
        AlertDialog(
            onDismissRequest = { comingSoonFeature = null },
            containerColor = SurfaceCard,
            titleContentColor = TextPrimary,
            textContentColor = TextMuted,
            title = {
                Text(
                    text = featureName,
                    style = MaterialTheme.typography.headlineMedium
                )
            },
            text = {
                Text(
                    text = "هذه الميزة قيد التطوير وستتوفر قريباً في التحديث القادم من معجم المخزنجي.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { comingSoonFeature = null }) {
                    Text("حسناً", color = LinkBlue, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun ComingSoonTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, DividerBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = LinkBlue,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            RegisterBadge(text = stringResource(R.string.coming_soon))
        }
    }
}

@Composable
private fun RecentSearchRow(
    item: SearchHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("recent_item_${item.resolvedEnglishWord}"),
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
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.arabicPreview.isNotBlank()) {
                Text(
                    text = item.arabicPreview,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "حذف من السجل",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
