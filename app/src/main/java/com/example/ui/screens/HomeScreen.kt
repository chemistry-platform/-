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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.model.LanguageDirection
import com.example.ui.components.AppIcons
import com.example.ui.components.ChipWrapRow
import com.example.ui.components.LanguageSwapBar
import com.example.ui.components.NoInternetBanner
import com.example.ui.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenQuickTranslate: (initialQuery: String, isArabicSource: Boolean) -> Unit,
    onOpenTranslation: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val direction by viewModel.languageDirection.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

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
            item {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onBackground
                    )
                    Text(
                        text = "معجم ومترجم عربي - إنجليزي مفتوح المصدر",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (!isOnline) {
                item {
                    NoInternetBanner()
                }
            }

            // Rounded card with blue border containing language switcher & search trigger
            item {
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
                        HorizontalDivider(color = colors.outline, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenQuickTranslate("", direction == LanguageDirection.AR_TO_EN)
                                }
                                .padding(horizontal = 14.dp, vertical = 16.dp)
                                .testTag("home_search_card"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.search_placeholder_home),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Two functional tool tiles: الترجمة (Text & Files) + اللغات (Offline Packs)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HomeActionTile(
                        title = "ترجمة النصوص والملفات",
                        subtitle = "تدفق سريع بدون إنترنت",
                        icon = AppIcons.Translate,
                        onClick = onOpenTranslation,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_translation_tile")
                    )
                    HomeActionTile(
                        title = "اللغات",
                        subtitle = "حزم الترجمة المحلية",
                        icon = AppIcons.Language,
                        onClick = onOpenLanguages,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_languages_tile")
                    )
                }
            }

            // Quick dictionary entry shortcuts (no search history on Home per Section 7)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "مداخل قاموسية سريعة",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onSurface
                        )
                        ChipWrapRow(
                            items = listOf("run", "look after", "resilient", "affect", "effect", "eloquent"),
                            onChipClick = onOpenWord
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
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(98.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = colors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.outline)
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
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurface,
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
