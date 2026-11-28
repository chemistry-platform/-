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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.core.Accent
import com.example.core.AppLanguage
import com.example.core.AudioDebugEntry
import com.example.core.OfflinePackState
import com.example.core.OfflineVoicePackState
import com.example.core.StaticLabels
import com.example.core.UpdateUiState
import com.example.ui.components.AppIcons
import com.example.ui.components.AudioSpeedSelector
import com.example.ui.components.SectionHeaderWithArabic
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.MoreViewModel
import java.util.Locale

@Composable
fun MoreScreen(
    viewModel: MoreViewModel,
    onOpenLanguages: () -> Unit,
    onOpenTranslation: () -> Unit,
    onOpenFileTranslation: () -> Unit,
    onOpenToeflIelts: () -> Unit,
    onOpenSourcesCredits: () -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val cachedCount by viewModel.cachedWordCount.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val offlinePackState by viewModel.offlinePackState.collectAsStateWithLifecycle()
    val offlineVoicePackState by viewModel.offlineVoicePackState.collectAsStateWithLifecycle()
    val audioDebugHistory by viewModel.audioDebugHistory.collectAsStateWithLifecycle()
    val lastAudioBytes by viewModel.lastAudioNetworkBytes.collectAsStateWithLifecycle()
    val totalAudioBytes by viewModel.totalAudioNetworkBytes.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val audioSpeed by viewModel.audioSpeed.collectAsStateWithLifecycle()
    val sentenceAccent by viewModel.sentenceAccent.collectAsStateWithLifecycle()
    val showArabicTranslations by viewModel.showArabicTranslations.collectAsStateWithLifecycle()
    val literalTranslation by viewModel.literalTranslation.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAudioDebugDialog by remember { mutableStateOf(false) }
    var debugTapCount by remember { mutableIntStateOf(0) }

    val onVersionOrHeaderTap = {
        val next = debugTapCount + 1
        if (next >= 5) {
            debugTapCount = 0
            showAudioDebugDialog = true
        } else {
            debugTapCount = next
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onVersionOrHeaderTap() }
                        .testTag("more_settings_header"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Settings & More",
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onBackground
                    )
                    Text(
                        text = StaticLabels.arabicFor("Settings"),
                        style = MaterialTheme.typography.labelLarge,
                        color = semantic.wordSubline
                    )
                    Text(
                        text = "Language, theme, Pro Pack, Voice Pack, history, and updates",
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

            // App Language Switcher
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, semantic.divider)
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
                                imageVector = AppIcons.Language,
                                contentDescription = "App Language",
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            SectionHeaderWithArabic(
                                englishTitle = "App Language",
                                arabicSublineOverride = "لغة التطبيق",
                                titleColor = colors.onSurface
                            )
                        }
                        Text(
                            text = "Choose default helper language preference",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = appLanguage == AppLanguage.ENGLISH,
                                onClick = { viewModel.setAppLanguage(AppLanguage.ENGLISH) },
                                label = {
                                    Text(text = "English", style = MaterialTheme.typography.labelMedium)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.primaryContainer,
                                    selectedLabelColor = colors.primary
                                ),
                                modifier = Modifier.testTag("app_lang_en")
                            )
                            FilterChip(
                                selected = appLanguage == AppLanguage.ARABIC,
                                onClick = { viewModel.setAppLanguage(AppLanguage.ARABIC) },
                                label = {
                                    Text(text = "Arabic", style = MaterialTheme.typography.labelMedium)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.primaryContainer,
                                    selectedLabelColor = colors.primary
                                ),
                                modifier = Modifier.testTag("app_lang_ar")
                            )
                        }
                    }
                }
            }

            // Theme Switcher (Dark / Light / System)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, semantic.divider)
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
                                contentDescription = "Appearance",
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            SectionHeaderWithArabic(
                                englishTitle = "Appearance",
                                arabicSublineOverride = "المظهر",
                                titleColor = colors.onSurface
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
                                            text = mode.labelFor(AppLanguage.ENGLISH),
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

            // Audio & Translation Settings (includes Literal Translation toggle - OFF by default)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, semantic.divider)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.VolumeUp,
                                contentDescription = "Audio & Translation Settings",
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            SectionHeaderWithArabic(
                                englishTitle = "Audio & Translation",
                                arabicSublineOverride = "إعدادات الصوت والترجمة",
                                titleColor = colors.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Speech Speed",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "سرعة النطق",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.wordSubline
                                )
                            }
                            AudioSpeedSelector(
                                currentSpeed = audioSpeed,
                                onSelectSpeed = { viewModel.setAudioSpeed(it) }
                            )
                        }

                        HorizontalDivider(color = semantic.divider)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Sentence Speech Accent",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface
                            )
                            Text(
                                text = "لهجة نطق الجمل (US / UK)",
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.wordSubline
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = sentenceAccent == Accent.US,
                                    onClick = { viewModel.setSentenceAccent(Accent.US) },
                                    label = {
                                        Text(text = "US English", style = MaterialTheme.typography.labelMedium)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.primaryContainer,
                                        selectedLabelColor = colors.primary
                                    ),
                                    modifier = Modifier.testTag("accent_pref_us")
                                )
                                FilterChip(
                                    selected = sentenceAccent == Accent.UK,
                                    onClick = { viewModel.setSentenceAccent(Accent.UK) },
                                    label = {
                                        Text(text = "UK English", style = MaterialTheme.typography.labelMedium)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.primaryContainer,
                                        selectedLabelColor = colors.primary
                                    ),
                                    modifier = Modifier.testTag("accent_pref_uk")
                                )
                            }
                        }

                        HorizontalDivider(color = semantic.divider)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Show Translations",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "إظهار المعاني والترجمات أسفل كل تعريف ومثال",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.wordSubline
                                )
                            }
                            Switch(
                                checked = showArabicTranslations,
                                onCheckedChange = { viewModel.setShowArabicTranslations(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                                modifier = Modifier.testTag("setting_show_arabic_switch")
                            )
                        }

                        HorizontalDivider(color = semantic.divider)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Literal Translation",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = StaticLabels.arabicFor("Literal Translation"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.wordSubline
                                )
                                Text(
                                    text = "Word-by-word instead of contextual translation (default OFF)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = literalTranslation,
                                onCheckedChange = { viewModel.setLiteralTranslation(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                                modifier = Modifier.testTag("setting_literal_translation_switch")
                            )
                        }
                    }
                }
            }

            // Tools navigation
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, semantic.divider)
                ) {
                    Column {
                        SettingsNavRow(
                            icon = AppIcons.Language,
                            iconTint = colors.secondary,
                            title = "Languages",
                            arabicTitle = "اللغات",
                            subtitle = "Download and manage offline ML Kit language packs",
                            onClick = onOpenLanguages,
                            testTag = "more_open_languages"
                        )
                        HorizontalDivider(color = semantic.divider)
                        SettingsNavRow(
                            icon = AppIcons.Translate,
                            iconTint = colors.primary,
                            title = "Text Translation",
                            arabicTitle = "ترجمة النصوص",
                            subtitle = "Translate sentences and long text offline",
                            onClick = onOpenTranslation,
                            testTag = "more_open_translation"
                        )
                        HorizontalDivider(color = semantic.divider)
                        SettingsNavRow(
                            icon = AppIcons.Description,
                            iconTint = colors.secondary,
                            title = "File Translation",
                            arabicTitle = "ترجمة الملفات",
                            subtitle = "PDF • Images • TXT with page-by-page OCR",
                            onClick = onOpenFileTranslation,
                            testTag = "more_open_file_translation"
                        )
                        HorizontalDivider(color = semantic.divider)
                        SettingsNavRow(
                            icon = AppIcons.Lightbulb,
                            iconTint = colors.primary,
                            title = "TOEFL & IELTS",
                            arabicTitle = "التوفل والآيلتس",
                            subtitle = "Exam guide and Academic Word List (AWL)",
                            onClick = onOpenToeflIelts,
                            testTag = "more_open_toefl_ielts"
                        )
                    }
                }
            }

            // Item 2: Pro Pack (Offline Dictionary Pack with Pause/Resume & Check Updates)
            item {
                ProPackCard(
                    state = offlinePackState,
                    onDownload = { viewModel.startOfflinePackDownload() },
                    onPause = { viewModel.pauseOfflinePackDownload() },
                    onCheckUpdates = { viewModel.checkOfflinePackForUpdates() },
                    onCancel = { viewModel.cancelOfflinePackDownload() },
                    onDelete = { viewModel.deleteOfflinePack() }
                )
            }

            // Item 4: Optional Offline Voice Pack (~30 MB, top 5,000 English words US/UK Kokoro .opus)
            item {
                OfflineVoicePackCard(
                    state = offlineVoicePackState,
                    onDownload = { viewModel.startVoicePackDownload() },
                    onPause = { viewModel.pauseVoicePackDownload() },
                    onDelete = { viewModel.deleteVoicePack() }
                )
            }

            // App Self-Update Card
            item {
                val isChecking = updateUiState is UpdateUiState.Checking
                val updateStatusText = (updateUiState as? UpdateUiState.Message)?.text
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, semantic.divider)
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
                                    contentDescription = "App Update",
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                SectionHeaderWithArabic(
                                    englishTitle = "App Update",
                                    arabicSublineOverride = "تحديث التطبيق",
                                    titleColor = colors.onSurface
                                )
                            }
                            if (!updateStatusText.isNullOrBlank()) {
                                Text(
                                    text = updateStatusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.exampleTranslation,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .testTag("update_status_message")
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.checkForUpdateNow() },
                            enabled = !isChecking,
                            border = BorderStroke(1.dp, semantic.divider),
                            modifier = Modifier.testTag("check_update_button")
                        ) {
                            Text(
                                text = if (isChecking) "Checking..." else "Check Updates",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurface
                            )
                        }
                    }
                }
            }

            // Search History Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, semantic.divider)
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
                                    contentDescription = "Search History",
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                SectionHeaderWithArabic(
                                    englishTitle = "History",
                                    count = recentHistory.size,
                                    titleColor = colors.onSurface
                                )
                            }
                            if (recentHistory.isNotEmpty()) {
                                TextButton(
                                    onClick = { showClearHistoryDialog = true },
                                    modifier = Modifier.testTag("more_clear_history_button")
                                ) {
                                    Text(
                                        text = "Clear",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primary
                                    )
                                }
                            }
                        }

                        if (recentHistory.isEmpty()) {
                            Text(
                                text = "Search history is empty.",
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
                                            contentDescription = "Delete",
                                            tint = colors.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                if (idx < recentHistory.take(15).lastIndex) {
                                    HorizontalDivider(color = semantic.divider)
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
                    border = BorderStroke(1.dp, semantic.divider)
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
                                contentDescription = "Dictionary Cache",
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                SectionHeaderWithArabic(
                                    englishTitle = "Saved Entries",
                                    arabicSublineOverride = "الكلمات المحفوظة في الجهاز",
                                    titleColor = colors.onSurface
                                )
                                Text(
                                    text = "$cachedCount entries stored locally",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearCache() },
                            border = BorderStroke(1.dp, semantic.divider)
                        ) {
                            Icon(
                                imageVector = AppIcons.DeleteOutline,
                                contentDescription = "Clear",
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", color = colors.onSurface)
                        }
                    }
                }
            }

            // Sources & Licenses Screen Trigger
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSourcesCredits)
                        .testTag("more_open_sources"),
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, semantic.divider)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Sources & Licenses",
                                tint = colors.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                SectionHeaderWithArabic(
                                    englishTitle = "Sources & Licenses",
                                    titleColor = colors.onSurface
                                )
                                Text(
                                    text = "Wiktionary, FreeDict, Lingua Libre, Kokoro-82M, Tatoeba & WordNet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                        Text("Open", color = colors.secondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Version footer (tap 5x to open Audio Debug Dialog)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onVersionOrHeaderTap() }
                        .padding(vertical = 8.dp)
                        .testTag("more_version_footer"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Almakhzanji Dictionary v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = colors.surface,
            title = { Text("Clear History", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Clear your entire search history?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text("Clear", color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = colors.onSurfaceVariant)
                }
            }
        )
    }

    if (showAudioDebugDialog) {
        AudioDebugDialog(
            entries = audioDebugHistory,
            lastAudioBytes = lastAudioBytes,
            totalAudioBytes = totalAudioBytes,
            onDismiss = { showAudioDebugDialog = false }
        )
    }
}

@Composable
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    arabicTitle: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface
                )
                Text(
                    text = arabicTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.wordSubline
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        Text("Open", color = colors.secondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ProPackCard(
    state: OfflinePackState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCheckUpdates: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, semantic.divider)
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
                    contentDescription = "Pro Pack",
                    tint = colors.secondary,
                    modifier = Modifier.size(20.dp)
                )
                SectionHeaderWithArabic(
                    englishTitle = "Pro Pack",
                    titleColor = colors.onSurface
                )
            }
            Text(
                text = "Full offline EN↔AR dictionary, reverse meanings, idioms, and phrasal verbs.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )

            when (state) {
                is OfflinePackState.Ready -> {
                    Text(
                        text = if (state.isInstalled) {
                            "Installed (${state.wordCount} words • ${formatBytes(state.diskSizeBytes)})"
                        } else {
                            "Not installed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isInstalled) semantic.exampleTranslation else colors.onSurfaceVariant
                    )
                    if (!state.updateCheckedMessage.isNullOrBlank()) {
                        Text(
                            text = state.updateCheckedMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = semantic.wordSubline,
                            modifier = Modifier.testTag("pro_pack_update_message")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            Text(
                                text = when {
                                    state.updateAvailable -> "Update Pack"
                                    state.isInstalled -> "Reinstall"
                                    else -> "Download"
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            border = BorderStroke(1.dp, semantic.divider),
                            modifier = Modifier.testTag("check_offline_pack_updates_button")
                        ) {
                            Text("Check Updates", color = colors.onSurface)
                        }
                        if (state.isInstalled) {
                            OutlinedButton(
                                onClick = onDelete,
                                border = BorderStroke(1.dp, semantic.divider),
                                modifier = Modifier.testTag("delete_offline_pack_button")
                            ) {
                                Text("Delete", color = colors.primary)
                            }
                        }
                    }
                }

                is OfflinePackState.Downloading -> {
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.exampleTranslation
                    )
                    if (state.indeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.secondary,
                            trackColor = semantic.divider
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.secondary,
                            trackColor = semantic.divider
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPause,
                            border = BorderStroke(1.dp, semantic.divider),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pause_offline_pack_button")
                        ) {
                            Text("Pause", color = semantic.wordSubline)
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            border = BorderStroke(1.dp, semantic.divider),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_offline_pack_button")
                        ) {
                            Text("Cancel", color = colors.primary)
                        }
                    }
                }

                is OfflinePackState.Paused -> {
                    Text(
                        text = "Paused at ${(state.progress * 100).toInt()}% (${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.wordSubline
                    )
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = semantic.wordSubline,
                        trackColor = semantic.divider
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("resume_offline_pack_button")
                        ) {
                            Text("Resume")
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            border = BorderStroke(1.dp, semantic.divider),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = colors.onSurface)
                        }
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
                            Text("Retry")
                        }
                        if (state.isInstalled) {
                            OutlinedButton(
                                onClick = onDelete,
                                border = BorderStroke(1.dp, semantic.divider),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete", color = colors.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineVoicePackCard(
    state: OfflineVoicePackState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("offline_voice_pack_card"),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, semantic.divider)
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
                    imageVector = AppIcons.VolumeUp,
                    contentDescription = "Offline Voice Pack",
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                SectionHeaderWithArabic(
                    englishTitle = "Offline Voice Pack",
                    titleColor = colors.onSurface
                )
            }
            Text(
                text = "Optional ~30 MB pack with US & UK natural voice clips for the top 5,000 English words.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )

            when (state) {
                is OfflineVoicePackState.Ready -> {
                    Text(
                        text = if (state.isInstalled) {
                            "Installed (${state.clipCount} clips • ${formatBytes(state.diskSizeBytes)})"
                        } else {
                            "Not installed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isInstalled) semantic.exampleTranslation else colors.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_voice_pack_button")
                        ) {
                            Text(if (state.isInstalled) "Update Voice Pack" else "Download (~30 MB)")
                        }
                        if (state.isInstalled) {
                            OutlinedButton(
                                onClick = onDelete,
                                border = BorderStroke(1.dp, semantic.divider),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("delete_voice_pack_button")
                            ) {
                                Text("Delete", color = colors.onSurface)
                            }
                        }
                    }
                }

                is OfflineVoicePackState.Downloading -> {
                    Text(
                        text = state.statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.exampleTranslation
                    )
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.primary,
                        trackColor = semantic.divider
                    )
                    OutlinedButton(
                        onClick = onPause,
                        border = BorderStroke(1.dp, semantic.divider),
                        modifier = Modifier.testTag("pause_voice_pack_button")
                    ) {
                        Text("Pause", color = semantic.wordSubline)
                    }
                }

                is OfflineVoicePackState.Paused -> {
                    Text(
                        text = "Paused at ${(state.progress * 100).toInt()}% (${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.wordSubline
                    )
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = semantic.wordSubline,
                        trackColor = semantic.divider
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Resume")
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            border = BorderStroke(1.dp, semantic.divider),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete", color = colors.onSurface)
                        }
                    }
                }

                is OfflineVoicePackState.Error -> {
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
                            colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry")
                        }
                        if (state.isInstalled) {
                            OutlinedButton(
                                onClick = onDelete,
                                border = BorderStroke(1.dp, semantic.divider),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Delete", color = colors.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioDebugDialog(
    entries: List<AudioDebugEntry>,
    lastAudioBytes: Long,
    totalAudioBytes: Long,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Column {
                Text("Audio Source Debug (Last 20)", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Last: ${formatBytes(lastAudioBytes)} • Total session: ${formatBytes(totalAudioBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.wordSubline
                )
            }
        },
        text = {
            if (entries.isEmpty()) {
                Text(
                    text = "No pronunciations played yet in this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries) { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${entry.queryWord} (${entry.accent.code.uppercase()})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurface
                                )
                                Text(
                                    text = "${entry.sourceKind.label} • ${formatBytes(entry.networkBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.exampleTranslation
                                )
                            }
                            if (entry.detail.isNotBlank()) {
                                Text(
                                    text = entry.detail,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        HorizontalDivider(color = semantic.divider)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.primary)
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    }
}
