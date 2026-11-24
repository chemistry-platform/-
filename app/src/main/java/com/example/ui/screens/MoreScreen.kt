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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.OfflinePackState
import com.example.core.UpdateUiState
import com.example.ui.components.AppIcons
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.MoreViewModel
import java.util.Locale

@Composable
fun MoreScreen(
    viewModel: MoreViewModel,
    onOpenLanguages: () -> Unit,
    onOpenTranslation: () -> Unit,
    onOpenSourcesCredits: () -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val cachedCount by viewModel.cachedWordCount.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val offlinePackState by viewModel.offlinePackState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    var showClearHistoryDialog by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.nav_more),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onBackground
                    )
                    Text(
                        text = "المظهر، حزم الأوفلاين، اللغات، السجل، والتحديثات",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (!actionMessage.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colors.secondary)
                    ) {
                        Text(
                            text = actionMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Section 8: Theme Switcher (فاتح / داكن / حسب النظام)
            item {
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Palette,
                                contentDescription = null,
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "المظهر",
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                val selected = mode == themeMode
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = {
                                        Text(
                                            text = mode.labelAr,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.primaryContainer,
                                        selectedLabelColor = colors.primary
                                    ),
                                    modifier = Modifier.testTag("theme_${mode.key}")
                                )
                            }
                        }
                    }
                }
            }

            // Section 2 Navigation: اللغات & الترجمة
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenLanguages)
                                .padding(16.dp)
                                .testTag("more_open_languages"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Language,
                                    contentDescription = null,
                                    tint = colors.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "اللغات",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = "تحميل وإدارة حزم الترجمة بدون إنترنت",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            Text("فتح", color = colors.secondary, style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider(color = colors.outline)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenTranslation)
                                .padding(16.dp)
                                .testTag("more_open_translation"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.Translate,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "ترجمة النصوص والملفات",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = "ترجمة جمل وملفات .txt طويلة دون اتصال",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            Text("فتح", color = colors.secondary, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Section 5: حزم الأوفلاين (Offline Dictionary Pack)
            item {
                OfflinePackCard(
                    state = offlinePackState,
                    onDownload = { viewModel.startOfflinePackDownload() },
                    onCancel = { viewModel.cancelOfflinePackDownload() },
                    onDelete = { viewModel.deleteOfflinePack() }
                )
            }

            // Section 9: Compact App Self-Update Card (not full width button!)
            item {
                val isChecking = updateUiState is UpdateUiState.Checking
                val updateStatusText = (updateUiState as? UpdateUiState.Message)?.text
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.SystemUpdate,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "تحديث التطبيق",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onSurface
                                )
                            }
                            if (!updateStatusText.isNullOrBlank()) {
                                Text(
                                    text = updateStatusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.secondary,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .testTag("update_status_message")
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.checkForUpdateNow() },
                            enabled = !isChecking,
                            border = BorderStroke(1.dp, colors.outline),
                            modifier = Modifier.testTag("check_update_button")
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    color = colors.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isChecking) "جاري التحقق..." else "تحقق من التحديث",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurface
                            )
                        }
                    }
                }
            }

            // Section 7: Search History Management Card
            item {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.History,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "سجل البحث (${recentHistory.size})",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onSurface
                                )
                            }
                            if (recentHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = { showClearHistoryDialog = true },
                                    modifier = Modifier.testTag("more_clear_history_button")
                                ) {
                                    Text(
                                        text = "مسح السجل",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primary
                                    )
                                }
                            }
                        }

                        if (recentHistory.isEmpty()) {
                            Text(
                                text = "سجل البحث فارغ حالياً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        } else {
                            recentHistory.take(15).forEachIndexed { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenWord(item.resolvedEnglishWord) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = item.displayQuery,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = colors.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteHistoryItem(item.queryKey) },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "حذف",
                                            tint = colors.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (idx < recentHistory.take(15).lastIndex) {
                                    HorizontalDivider(color = colors.outline)
                                }
                            }
                        }
                    }
                }
            }

            // Local Cache Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = AppIcons.Storage,
                                contentDescription = null,
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "الذاكرة المؤقتة للقاموس",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "$cachedCount كلمة مخزّنة محلياً",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearCache() },
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Icon(
                                imageVector = AppIcons.DeleteOutline,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح الكاش", color = colors.onSurface)
                        }
                    }
                }
            }

            // Section 4.8: "عن المصادر" screen trigger
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSourcesCredits)
                        .testTag("more_open_sources"),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "عن المصادر",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "تراخيص Wiktionary و Tatoeba و Datamuse و WordNet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        Text("عرض", color = colors.secondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = colors.surface,
            title = { Text("مسح السجل", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("هل تريد مسح سجل البحث بالكامل؟", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text("مسح", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("إلغاء", color = colors.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun OfflinePackCard(
    state: OfflinePackState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.CloudDownload,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "حزم الأوفلاين",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface
                )
            }

            when (state) {
                is OfflinePackState.Ready -> {
                    Text(
                        text = if (state.isInstalled) {
                            "الحالة: مثبّتة (${state.wordCount} كلمة • ${formatBytes(state.diskSizeBytes)})"
                        } else {
                            "الحالة: غير مثبّتة"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isInstalled) colors.secondary else colors.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.secondary,
                                contentColor = colors.onSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_offline_pack_button")
                        ) {
                            Text(if (state.isInstalled) "تحديث الحزمة" else "تحميل الحزمة")
                        }
                        if (state.isInstalled) {
                            OutlinedButton(
                                onClick = onDelete,
                                border = BorderStroke(1.dp, colors.outline),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("delete_offline_pack_button")
                            ) {
                                Text("حذف", color = colors.onSurface)
                            }
                        }
                    }
                }

                is OfflinePackState.Downloading -> {
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.secondary
                    )
                    if (state.indeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.secondary,
                            trackColor = colors.outline
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.secondary,
                            trackColor = colors.outline
                        )
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        border = BorderStroke(1.dp, colors.outline),
                        modifier = Modifier.testTag("cancel_offline_pack_button")
                    ) {
                        Text("إلغاء", color = colors.primary)
                    }
                }

                is OfflinePackState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.secondary,
                                contentColor = colors.onSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إعادة المحاولة")
                        }
                        if (state.isInstalled) {
                            OutlinedButton(
                                onClick = onDelete,
                                border = BorderStroke(1.dp, colors.outline),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("حذف", color = colors.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
