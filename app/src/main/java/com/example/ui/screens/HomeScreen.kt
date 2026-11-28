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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.OfflinePackState
import com.example.core.StaticLabels
import com.example.domain.model.LanguageDirection
import com.example.ui.components.AppIcons
import com.example.ui.components.ChipWrapRow
import com.example.ui.components.LanguageSwapBar
import com.example.ui.components.NoInternetBanner
import com.example.ui.components.OfflineDictionaryPackBanner
import com.example.ui.components.SectionHeaderWithArabic
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenQuickTranslate: (initialQuery: String, isArabicSource: Boolean) -> Unit,
    onOpenTranslation: () -> Unit,
    onOpenFileTranslation: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val direction by viewModel.languageDirection.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val packState = viewModel.offlinePackState?.collectAsStateWithLifecycle()?.value

    LaunchedEffect(Unit) {
        viewModel.openWordEvent.collect { targetWord ->
            onOpenWord(targetWord)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
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
            item(key = "home_header", contentType = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Almakhzanji Dictionary",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onBackground
                    )
                    Text(
                        text = StaticLabels.arabicFor("Almakhzanji Dictionary"),
                        style = MaterialTheme.typography.labelLarge,
                        color = semantic.wordSubline
                    )
                    Text(
                        text = "Open-Source English–Arabic Dictionary & Translator",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (packState is OfflinePackState.Downloading ||
                (!isOnline && ((packState is OfflinePackState.Ready && !packState.isInstalled) ||
                    (packState is OfflinePackState.Paused) ||
                    (packState is OfflinePackState.Error && !packState.isInstalled)))
            ) {
                item(key = "home_pack_download", contentType = "banner") {
                    OfflineDictionaryPackBanner(
                        isOnline = isOnline,
                        packState = packState,
                        onDownloadClick = { viewModel.requestOfflinePackDownload() }
                    )
                }
            }

            if (!isOnline) {
                item(key = "home_offline", contentType = "banner") {
                    NoInternetBanner()
                }
            }

            // Rounded card containing language switcher & search trigger
            item(key = "home_search", contentType = "search_card") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, colors.secondary)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LanguageSwapBar(
                            direction = direction,
                            onSwap = { viewModel.swapLanguages() }
                        )
                        HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenQuickTranslate("", direction == LanguageDirection.AR_TO_EN)
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                                .testTag("home_search_card"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Search a word or sentence...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant
                                )
                                Text(
                                    text = "ابحث عن كلمة أو جملة...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.wordSubline
                                )
                            }
                        }
                    }
                }
            }

            // Functional tool tiles: Text Translation + File Translation + Languages
            item(key = "home_tools_row", contentType = "tools_row") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HomeActionTile(
                            title = "Text Translation",
                            arabicTitle = "ترجمة النصوص",
                            subtitle = "59 offline languages",
                            icon = AppIcons.Translate,
                            onClick = onOpenTranslation,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_translation_tile")
                        )
                        HomeActionTile(
                            title = "File Translation",
                            arabicTitle = "ترجمة الملفات",
                            subtitle = "PDF • Images • TXT",
                            icon = AppIcons.Description,
                            onClick = onOpenFileTranslation,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_files_tile")
                        )
                    }
                    HomeActionTile(
                        title = "Manage Languages & Packs",
                        arabicTitle = "إدارة اللغات وحزم العمل دون إنترنت",
                        subtitle = "Download and update offline translation packs",
                        icon = AppIcons.Language,
                        onClick = onOpenLanguages,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_languages_tile")
                    )
                }
            }

            // Quick dictionary entry shortcuts (English & Arabic)
            item(key = "home_quick_words", contentType = "quick_chips") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, semantic.divider)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeaderWithArabic(
                            englishTitle = "Quick Dictionary Entries",
                            arabicSublineOverride = "مداخل قاموسية سريعة",
                            titleColor = colors.onSurface
                        )
                        ChipWrapRow(
                            items = listOf("run", "look after", "resilient", "affect", "كتاب", "حكمة", "سلام"),
                            onChipClick = { word -> viewModel.openWordReady(word) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeActionTile(
    title: String,
    arabicTitle: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    Surface(
        modifier = modifier
            .height(108.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = colors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, semantic.divider)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = colors.secondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
                maxLines = 1
            )
            Text(
                text = arabicTitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = semantic.wordSubline,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
