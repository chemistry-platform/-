package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.AppIcons
import com.example.ui.components.PosChip
import com.example.ui.viewmodel.FavoritesViewModel

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.nav_favorites),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onBackground
                    )
                    Text(
                        text = "الكلمات المحفوظة للمراجعة السريعة بدون إنترنت",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (favorites.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.BookmarkBorder,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = stringResource(R.string.empty_favorites),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface
                            )
                            Text(
                                text = "اضغط على أيقونة النجمة بجانب أي كلمة لحفظها هنا.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.outline)
                    ) {
                        Column {
                            favorites.forEachIndexed { index, fav ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenWord(fav.word) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .testTag("favorite_item_${fav.word}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = fav.word,
                                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = colors.onSurface
                                                )
                                            }
                                            if (fav.usIpa.isNotBlank()) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Text(
                                                        text = fav.usIpa,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            if (fav.partOfSpeech.isNotBlank()) {
                                                PosChip(text = fav.partOfSpeech)
                                            }
                                        }
                                        if (fav.summaryText.isNotBlank()) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                Text(
                                                    text = fav.summaryText,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.removeFavorite(fav.word) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("remove_fav_${fav.word}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = "إزالة من المفضلة",
                                            tint = colors.primary
                                        )
                                    }
                                }
                                if (index < favorites.lastIndex) {
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
