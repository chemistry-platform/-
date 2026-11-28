package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.Accent
import com.example.core.AppLanguage
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.LinguisticRanker
import com.example.core.LocalizedLabels
import com.example.core.StaticLabels
import com.example.domain.model.ArabicVocalizedGroup
import com.example.domain.model.ConfusableCandidate
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.PhraseDetailItem
import com.example.domain.model.PosSenseGroup
import com.example.domain.model.ReverseEnglishMatch
import com.example.domain.model.SenseItem
import com.example.domain.model.WordEntry
import com.example.ui.theme.LocalSemanticColors
import java.util.Locale

internal const val WORD_DETAIL_INITIAL_VISIBLE = 10

@Composable
internal fun PosBadgeWithArabic(
    partOfSpeech: String,
    partOfSpeechArabic: String = "",
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val enLabel = LinguisticRanker.formatPosBadgeLabel(
        partOfSpeech = partOfSpeech,
        partOfSpeechArabic = partOfSpeechArabic,
        language = AppLanguage.ENGLISH
    )
    val arLabel = StaticLabels.posArabic(partOfSpeech).ifBlank { partOfSpeechArabic }
    Surface(
        modifier = modifier,
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, semantic.divider)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = enLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface
            )
            if (arLabel.isNotBlank()) {
                Text(
                    text = arLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = semantic.wordSubline
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeadwordCard(
    entry: WordEntry,
    isFavorite: Boolean,
    audioManager: AudioPlayerManager,
    audioSpeed: Float,
    showArabicTranslations: Boolean,
    chipMeanings: Map<String, String>,
    onSelectSpeed: (Float) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAwlInfo: () -> Unit,
    onOpenWord: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onSelectVocalizedForm: (String) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val hasAwlTag = entry.tags.any { it.equals("AWL", ignoreCase = true) }
    val freqTag = when {
        entry.frequencyRank in 1..500 -> "#Top 500"
        entry.frequencyRank in 501..1000 -> "#Top 1000"
        entry.frequencyRank in 1001..3000 -> "#Top 3000"
        entry.frequencyRank in 3001..5000 -> "#Top 5000"
        entry.frequencyRank in 5001..10000 -> "#Top 10000"
        else -> null
    }
    val displayTags = remember(entry.tags, freqTag) {
        buildList {
            entry.tags.filter { !it.equals("AWL", ignoreCase = true) && it.isNotBlank() }
                .forEach { t -> add(if (t.startsWith("#")) t else "#$t") }
            if (freqTag != null && none { it.equals(freqTag, ignoreCase = true) }) {
                add(freqTag)
            }
        }
    }

    var wordFormsExpanded by remember(entry.word) { mutableStateOf(false) }

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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (entry.isArabicWord) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = entry.displayWord.ifBlank { entry.word },
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("favorite_star_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star else AppIcons.StarOutline,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) semantic.wordSubline else colors.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            if (entry.isArabicWord && entry.transliteration.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = entry.transliteration,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            if (entry.isArabicWord) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    SentenceSpeakerButton(
                        isPlaying = false,
                        onClick = {
                            audioManager.playSentenceAudio(
                                sentence = entry.displayWord.ifBlank { entry.word },
                                audioUrl = null,
                                key = "head_ar_${entry.word}"
                            )
                        },
                        contentDescription = "Pronounce Arabic word",
                        modifier = Modifier.testTag("pronounce_ar_button")
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PronunciationPillButton(
                        regionLabel = "US",
                        ipa = entry.usIpa,
                        hasAudio = true,
                        onClick = {
                            audioManager.playHumanPronunciation(
                                word = entry.word,
                                accent = Accent.US,
                                audioUrl = entry.usAudioUrl,
                                key = "head_us_${entry.word}"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pronounce_us_button")
                    )
                    PronunciationPillButton(
                        regionLabel = "UK",
                        ipa = entry.ukIpa,
                        hasAudio = true,
                        onClick = {
                            audioManager.playHumanPronunciation(
                                word = entry.word,
                                accent = Accent.UK,
                                audioUrl = entry.ukAudioUrl,
                                key = "head_uk_${entry.word}"
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pronounce_uk_button")
                    )
                }
            }

            if (entry.isArabicWord && entry.vocalizedForms.size > 1) {
                val activeCanonical = ArabicTextUtils.canonicalizeArabicWithDiacritics(entry.displayWord)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Vocalized forms",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        entry.vocalizedForms.forEach { vocalized ->
                            val isSelected = ArabicTextUtils.canonicalizeArabicWithDiacritics(vocalized) == activeCanonical
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onSelectVocalizedForm(vocalized) },
                                color = if (isSelected) colors.primaryContainer else colors.surfaceVariant,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, if (isSelected) colors.primary else semantic.divider)
                            ) {
                                Text(
                                    text = vocalized,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) colors.primary else colors.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // POS-grouped quick translations (EN -> AR and AR -> EN symmetrically)
            val posSummaries = remember(entry) {
                LinguisticRanker.extractPosHeadwordMeanings(
                    entry = entry,
                    maxPerPos = Int.MAX_VALUE,
                    language = AppLanguage.ENGLISH
                )
            }
            if ((posSummaries.isNotEmpty() || entry.machineTranslations.isNotEmpty()) && (entry.isArabicWord || showArabicTranslations)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("headword_translations_box"),
                    color = colors.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, semantic.divider)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionHeaderWithArabic(
                            englishTitle = "Translation",
                            titleColor = colors.onSurface
                        )

                        if (posSummaries.isNotEmpty()) {
                            posSummaries.forEachIndexed { idx, posSummary ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    PosBadgeWithArabic(
                                        partOfSpeech = posSummary.partOfSpeech,
                                        partOfSpeechArabic = StaticLabels.posArabic(posSummary.partOfSpeech),
                                        modifier = Modifier.testTag("headword_pos_${posSummary.partOfSpeech}")
                                    )

                                    CompositionLocalProvider(
                                        LocalLayoutDirection provides if (entry.isArabicWord) LayoutDirection.Ltr else LayoutDirection.Rtl
                                    ) {
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            posSummary.meanings.take(12).forEach { meaning ->
                                                Surface(
                                                    modifier = Modifier
                                                        .defaultMinSize(minHeight = 40.dp)
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .clickable { onOpenWord(meaning) },
                                                    color = colors.surface,
                                                    shape = RoundedCornerShape(999.dp),
                                                    border = BorderStroke(1.dp, semantic.divider)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = meaning,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = colors.onSurface,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (idx < posSummaries.lastIndex) {
                                    HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                                }
                            }
                        } else if (entry.isArabicWord) {
                            ChipWrapRow(
                                items = entry.machineTranslations,
                                chipMeanings = chipMeanings,
                                onChipClick = onOpenWord,
                                onSpeak = onSpeak
                            )
                        } else {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    entry.machineTranslations.forEach { arText ->
                                        Surface(
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 40.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .clickable { onOpenWord(arText) },
                                            color = colors.surface,
                                            shape = RoundedCornerShape(999.dp),
                                            border = BorderStroke(1.dp, semantic.divider)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = arText,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = colors.onSurface,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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

            // Word forms (English + Arabic words) with Arabic form label underneath in #F2B94B + 10-item cap
            if (entry.wordForms.isNotEmpty()) {
                val visibleForms = if (wordFormsExpanded) {
                    entry.wordForms
                } else {
                    entry.wordForms.take(WORD_DETAIL_INITIAL_VISIBLE)
                }
                SectionHeaderWithArabic(
                    englishTitle = "Word Forms",
                    count = entry.wordForms.size,
                    titleColor = colors.onSurface
                )
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("headword_word_forms"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    visibleForms.forEach { formItem ->
                        val labelAr = StaticLabels.arabicFor(formItem.label)
                        val formIsArabic = ArabicTextUtils.containsArabicLetters(formItem.form)
                        Surface(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            color = colors.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, semantic.divider)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .clickable { onOpenWord(formItem.form) }
                                        .padding(vertical = 6.dp, horizontal = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = formItem.form,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = colors.onSurface
                                        )
                                        if (formItem.label.isNotBlank()) {
                                            Text(
                                                text = formItem.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (labelAr.isNotBlank()) {
                                        Text(
                                            text = labelAr,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = semantic.wordSubline
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (formIsArabic) {
                                            audioManager.playSentenceAudio(
                                                sentence = formItem.form,
                                                audioUrl = null,
                                                key = "form_${formItem.form}"
                                            )
                                        } else {
                                            audioManager.playHumanPronunciation(
                                                word = formItem.form,
                                                accent = Accent.US,
                                                audioUrl = null,
                                                key = "form_${formItem.form}"
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.VolumeUp,
                                        contentDescription = "Pronounce ${formItem.form}",
                                        tint = colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (entry.wordForms.size > WORD_DETAIL_INITIAL_VISIBLE) {
                    FullWidthSeeMoreFooter(
                        isExpanded = wordFormsExpanded,
                        remainingCount = entry.wordForms.size - WORD_DETAIL_INITIAL_VISIBLE,
                        onClick = { wordFormsExpanded = !wordFormsExpanded },
                        onDoubleTapCollapse = if (wordFormsExpanded) {
                            { wordFormsExpanded = false }
                        } else null,
                        testTag = "word_forms_see_more"
                    )
                }
            }

            if (hasAwlTag || displayTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("headword_frequency_tags"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasAwlTag) {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onOpenAwlInfo)
                                .testTag("awl_badge_chip"),
                            color = colors.secondaryContainer,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "#AWL",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    displayTags.forEach { tagText ->
                        Surface(
                            color = colors.surfaceVariant,
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, semantic.divider)
                        ) {
                            Text(
                                text = tagText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = semantic.wordSubline,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Speech speed",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                AudioSpeedSelector(
                    currentSpeed = audioSpeed,
                    onSelectSpeed = onSelectSpeed
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PosGroupCard(
    word: String,
    group: PosSenseGroup,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onCollapseToTen: () -> Unit,
    showArabicTranslations: Boolean,
    playingKey: String?,
    chipMeanings: Map<String, String>,
    onSpeakSentence: (sentence: String, audioUrl: String?, key: String, accentOverride: Accent?) -> Unit,
    onStopAudio: () -> Unit,
    onChipClick: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    onSpeakChip: ((String) -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val visibleSenses = if (showAll) group.senses else group.senses.take(WORD_DETAIL_INITIAL_VISIBLE)
    val onDoubleCollapse: (() -> Unit)? = if (showAll && group.senses.size > WORD_DETAIL_INITIAL_VISIBLE) {
        onCollapseToTen
    } else {
        null
    }
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, semantic.divider)
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (onDoubleCollapse != null) {
                        Modifier.combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = {},
                            onDoubleClick = onDoubleCollapse
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PosBadgeWithArabic(
                    partOfSpeech = group.partOfSpeech,
                    partOfSpeechArabic = group.partOfSpeechArabic
                )
                SectionHeaderWithArabic(
                    englishTitle = "Meanings",
                    count = group.senses.size,
                    titleColor = colors.onSurfaceVariant
                )
            }

            HorizontalDivider(color = semantic.divider, thickness = 1.dp)

            visibleSenses.forEachIndexed { idx, sense ->
                SenseCardContent(
                    word = word,
                    sense = sense,
                    showArabicTranslations = showArabicTranslations,
                    playingKey = playingKey,
                    chipMeanings = chipMeanings,
                    onSpeakSentence = onSpeakSentence,
                    onStopAudio = onStopAudio,
                    onChipClick = onChipClick,
                    onOpenWord = onOpenWord,
                    onSpeakChip = onSpeakChip,
                    onDoubleCollapse = onDoubleCollapse
                )
                if (idx < visibleSenses.lastIndex) {
                    HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                }
            }

            if (group.senses.size > WORD_DETAIL_INITIAL_VISIBLE) {
                FullWidthSeeMoreFooter(
                    isExpanded = showAll,
                    remainingCount = group.senses.size - WORD_DETAIL_INITIAL_VISIBLE,
                    onClick = onToggleShowAll,
                    onDoubleTapCollapse = onDoubleCollapse,
                    testTag = "pos_see_more_${group.partOfSpeech}"
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SenseCardContent(
    word: String,
    sense: SenseItem,
    showArabicTranslations: Boolean,
    playingKey: String?,
    chipMeanings: Map<String, String>,
    onSpeakSentence: (sentence: String, audioUrl: String?, key: String, accentOverride: Accent?) -> Unit,
    onStopAudio: () -> Unit,
    onChipClick: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    onSpeakChip: ((String) -> Unit)? = null,
    onDoubleCollapse: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val defIsArabic = ArabicTextUtils.containsArabicLetters(sense.definition)
    val translatedMeaning = sense.definitionArabic?.takeIf { it.isNotBlank() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sense.usageLabels.isNotEmpty() || sense.spellingVariantLabel != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sense.usageLabels.forEach { label ->
                    UsageLabelChip(text = LocalizedLabels.usageLabel(label, AppLanguage.ENGLISH))
                }
                sense.spellingVariantLabel?.let { variantLabel ->
                    UsageLabelChip(text = LocalizedLabels.usageLabel(variantLabel, AppLanguage.ENGLISH))
                }
                sense.otherSpellingWord?.let { otherWord ->
                    InteractiveWordChip(
                        word = otherWord,
                        arabicSubtext = chipMeanings[otherWord.trim().lowercase(Locale.US)],
                        onClick = { onOpenWord(otherWord) },
                        onSpeak = onSpeakChip,
                        onDoubleClick = onDoubleCollapse
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${sense.index}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!defIsArabic) {
                    if (showArabicTranslations && translatedMeaning != null) {
                        ArabicTranslatedText(
                            text = translatedMeaning,
                            isMachineTranslated = sense.definitionArabicIsMachine,
                            showMachineLabel = false,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = semantic.definitionTranslation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sense_arabic_def_${sense.partOfSpeech}_${sense.index}")
                        )
                    }
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = sense.definition,
                            style = if (showArabicTranslations && translatedMeaning != null) {
                                MaterialTheme.typography.bodyMedium
                            } else {
                                MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            },
                            color = colors.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            text = sense.definition,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (translatedMeaning != null) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = translatedMeaning,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = semantic.definitionTranslation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sense_english_def_${sense.partOfSpeech}_${sense.index}")
                            )
                        }
                    }
                }
                if (!sense.usageNote.isNullOrBlank()) {
                    Text(
                        text = LocalizedLabels.usageNote(sense.usageNote, AppLanguage.ENGLISH),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        sense.examples.take(1).forEachIndexed { exIdx, example ->
            val exPrefix = "ex_${sense.partOfSpeech}_${sense.index}_$exIdx"
            val exIsArabic = ArabicTextUtils.containsArabicLetters(example)
            val exTranslation = sense.exampleTranslations[example]?.takeIf { it.isNotBlank() }
            val exAudioUrl = sense.exampleAudioUrls[example]

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, semantic.divider)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (exIsArabic) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    text = example,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SentenceSpeakerButton(
                                isPlaying = playingKey == exPrefix,
                                onClick = {
                                    if (playingKey == exPrefix) onStopAudio()
                                    else onSpeakSentence(example, exAudioUrl, exPrefix, null)
                                },
                                onDoubleClick = onDoubleCollapse,
                                modifier = Modifier.testTag("speaker_$exPrefix")
                            )
                        }
                        if (exTranslation != null) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = exTranslation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = semantic.exampleTranslation,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("arabic_$exPrefix")
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HighlightedEnglishText(
                                sentence = example,
                                targetWord = word,
                                modifier = Modifier.weight(1f)
                            )
                            SentenceDualSpeakerButtons(
                                playingKey = playingKey,
                                keyPrefix = exPrefix,
                                onPlayUs = { onSpeakSentence(example, exAudioUrl, "${exPrefix}_us", Accent.US) },
                                onPlayUk = { onSpeakSentence(example, exAudioUrl, "${exPrefix}_uk", Accent.UK) },
                                onStop = onStopAudio,
                                onDoubleClick = onDoubleCollapse
                            )
                        }

                        if (showArabicTranslations && exTranslation != null) {
                            ArabicTranslatedText(
                                text = exTranslation,
                                isMachineTranslated = sense.exampleIsMachine[example] ?: true,
                                showMachineLabel = false,
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.exampleTranslation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("arabic_$exPrefix")
                            )
                        }
                    }
                }
            }
        }

        if (sense.synonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Synonyms",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                ChipWrapRow(
                    items = sense.synonyms,
                    chipMeanings = chipMeanings,
                    onChipClick = onChipClick,
                    onSpeak = onSpeakChip,
                    onDoubleClick = onDoubleCollapse
                )
            }
        }
        if (sense.antonyms.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Antonyms",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
                ChipWrapRow(
                    items = sense.antonyms,
                    chipMeanings = chipMeanings,
                    onChipClick = onChipClick,
                    onSpeak = onSpeakChip,
                    onDoubleClick = onDoubleCollapse
                )
            }
        }
    }
}

@Composable
internal fun PhrasalIdiomDetailCard(
    item: PhraseDetailItem,
    showArabicTranslations: Boolean,
    playingKey: String?,
    onTapPhrase: () -> Unit,
    onSpeak: (text: String, audioUrl: String?, key: String, accentOverride: Accent?) -> Unit,
    onStop: () -> Unit,
    onDoubleCollapse: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val phrasePrefix = "phrasal_${item.phrase.hashCode()}"
    val phraseIsArabic = ArabicTextUtils.containsArabicLetters(item.phrase)
    val badgeText = LocalizedLabels.phraseBadge(item.category, item.isIdiom, AppLanguage.ENGLISH)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickableOrDoubleTap(onClick = onTapPhrase, onDoubleClick = onDoubleCollapse)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (phraseIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Text(
                        text = item.phrase,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                UsageLabelChip(text = badgeText)
            }

            if (phraseIsArabic) {
                SentenceSpeakerButton(
                    isPlaying = playingKey == "${phrasePrefix}_ar",
                    onClick = { onSpeak(item.phrase, null, "${phrasePrefix}_ar", null) },
                    onDoubleClick = onDoubleCollapse
                )
            } else {
                SentenceDualSpeakerButtons(
                    playingKey = playingKey,
                    keyPrefix = phrasePrefix,
                    onPlayUs = { onSpeak(item.phrase, null, "${phrasePrefix}_us", Accent.US) },
                    onPlayUk = { onSpeak(item.phrase, null, "${phrasePrefix}_uk", Accent.UK) },
                    onStop = onStop,
                    onDoubleClick = onDoubleCollapse
                )
            }
        }

        if (!item.grammarStructure.isNullOrBlank()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = LocalizedLabels.formatGrammarStructure(item.grammarStructure, AppLanguage.ENGLISH),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!phraseIsArabic) {
            if (showArabicTranslations && !item.arabicMeaning.isNullOrBlank()) {
                ArabicTranslatedText(
                    text = item.arabicMeaning,
                    isMachineTranslated = item.isArabicMachine,
                    showMachineLabel = false,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = semantic.definitionTranslation,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (item.englishMeaning.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = item.englishMeaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            if (item.englishMeaning.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = item.englishMeaning,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = semantic.definitionTranslation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (showArabicTranslations && !item.arabicMeaning.isNullOrBlank()) {
                ArabicTranslatedText(
                    text = item.arabicMeaning,
                    isMachineTranslated = item.isArabicMachine,
                    showMachineLabel = false,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (!item.usageExplanation.isNullOrBlank()) {
            Text(
                text = item.usageExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (item.example.isNotBlank()) {
            val exPrefix = "phrasal_ex_${item.phrase.hashCode()}"
            val exIsArabic = ArabicTextUtils.containsArabicLetters(item.example)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, semantic.divider)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (exIsArabic) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                Text(
                                    text = item.example,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SentenceSpeakerButton(
                                isPlaying = playingKey == "${exPrefix}_ar",
                                onClick = { onSpeak(item.example, item.exampleAudioUrl, "${exPrefix}_ar", null) },
                                onDoubleClick = onDoubleCollapse
                            )
                        } else {
                            HighlightedEnglishText(
                                sentence = item.example,
                                targetWord = item.phrase,
                                modifier = Modifier.weight(1f)
                            )
                            SentenceDualSpeakerButtons(
                                playingKey = playingKey,
                                keyPrefix = exPrefix,
                                onPlayUs = { onSpeak(item.example, item.exampleAudioUrl, "${exPrefix}_us", Accent.US) },
                                onPlayUk = { onSpeak(item.example, item.exampleAudioUrl, "${exPrefix}_uk", Accent.UK) },
                                onStop = onStop,
                                onDoubleClick = onDoubleCollapse
                            )
                        }
                    }
                    if (showArabicTranslations && !item.exampleArabic.isNullOrBlank()) {
                        val transIsArabic = ArabicTextUtils.containsArabicLetters(item.exampleArabic)
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (transIsArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
                        ) {
                            Text(
                                text = item.exampleArabic,
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.exampleTranslation,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfusableCard(
    detail: ConfusableWordDetail,
    wordArabicMeaning: String?,
    audioManager: AudioPlayerManager,
    onOpenWord: () -> Unit,
    onDoubleCollapse: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val hasUs = audioManager.hasAudioOrCache(detail.word, Accent.US, detail.usAudioUrl)
    val hasUk = audioManager.hasAudioOrCache(detail.word, Accent.UK, detail.ukAudioUrl)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, semantic.divider)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickableOrDoubleTap(onClick = onOpenWord, onDoubleClick = onDoubleCollapse)
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = detail.word,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.secondary
                            )
                        }
                        if (detail.partOfSpeech.isNotBlank()) {
                            PosBadgeWithArabic(partOfSpeech = detail.partOfSpeech)
                        }
                    }
                    if (!wordArabicMeaning.isNullOrBlank()) {
                        Text(
                            text = wordArabicMeaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.wordSubline
                        )
                    }
                }

                CompactWordAudioButtons(
                    hasUsAudio = hasUs,
                    hasUkAudio = hasUk,
                    onPlayUs = {
                        audioManager.playHumanPronunciation(
                            word = detail.word,
                            accent = Accent.US,
                            audioUrl = detail.usAudioUrl,
                            key = "conf_us_${detail.word}"
                        )
                    },
                    onPlayUk = {
                        audioManager.playHumanPronunciation(
                            word = detail.word,
                            accent = Accent.UK,
                            audioUrl = detail.ukAudioUrl,
                            key = "conf_uk_${detail.word}"
                        )
                    },
                    onDoubleClick = onDoubleCollapse
                )
            }

            if (detail.labels.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    detail.labels.forEach { label ->
                        UsageLabelChip(text = LocalizedLabels.usageLabel(label, AppLanguage.ENGLISH))
                    }
                }
            }

            if (detail.shortDefinition.isNotBlank()) {
                if (!detail.shortDefinitionArabic.isNullOrBlank()) {
                    ArabicTranslatedText(
                        text = detail.shortDefinitionArabic,
                        isMachineTranslated = false,
                        showMachineLabel = false,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = semantic.definitionTranslation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = detail.shortDefinition,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface
                    )
                }
            }

            if (detail.example.isNotBlank()) {
                HighlightedEnglishText(
                    sentence = detail.example,
                    targetWord = detail.word
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConfusableCategorySection(
    title: String,
    candidates: List<ConfusableCandidate>,
    expanded: Boolean,
    onToggle: () -> Unit,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onDoubleCollapse: (() -> Unit)?,
    confusableDetails: Map<String, ConfusableWordDetail>,
    chipMeanings: Map<String, String>,
    audioManager: AudioPlayerManager,
    onLoadNeeded: () -> Unit,
    onTapCandidate: (String) -> Unit,
    onSpeakChip: (String) -> Unit
) {
    CollapsibleSectionCard(
        title = title,
        count = candidates.size,
        expanded = expanded,
        onToggle = onToggle,
        onDoubleCollapse = onDoubleCollapse
    ) {
        LaunchedEffect(expanded) {
            if (expanded) onLoadNeeded()
        }
        val visibleCandidates = if (showAll) candidates else candidates.take(WORD_DETAIL_INITIAL_VISIBLE)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val loadedList = visibleCandidates.mapNotNull { candidate ->
                confusableDetails[candidate.word.lowercase(Locale.US)]
            }
            val unloadedList = visibleCandidates.filter { candidate ->
                confusableDetails[candidate.word.lowercase(Locale.US)] == null
            }
            loadedList.forEach { detail ->
                ConfusableCard(
                    detail = detail,
                    wordArabicMeaning = chipMeanings[detail.word.trim().lowercase(Locale.US)],
                    audioManager = audioManager,
                    onOpenWord = { onTapCandidate(detail.word) },
                    onDoubleCollapse = onDoubleCollapse
                )
            }
            if (unloadedList.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    unloadedList.forEach { candidate ->
                        InteractiveWordChip(
                            word = candidate.word,
                            arabicSubtext = chipMeanings[candidate.word.trim().lowercase(Locale.US)],
                            onClick = { onTapCandidate(candidate.word) },
                            onSpeak = onSpeakChip,
                            onDoubleClick = onDoubleCollapse
                        )
                    }
                }
            }
            if (candidates.size > WORD_DETAIL_INITIAL_VISIBLE) {
                FullWidthSeeMoreFooter(
                    isExpanded = showAll,
                    remainingCount = candidates.size - WORD_DETAIL_INITIAL_VISIBLE,
                    onClick = onToggleShowAll,
                    onDoubleTapCollapse = onDoubleCollapse,
                    testTag = "confusable_see_more_${title.lowercase(Locale.US)}"
                )
            }
        }
    }
}

@Composable
internal fun CollapsibleChipsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    showAll: Boolean,
    onToggleShowAll: () -> Unit,
    onDoubleCollapse: (() -> Unit)?,
    items: List<String>,
    chipMeanings: Map<String, String> = emptyMap(),
    highlightedChip: String? = null,
    onChipClick: (String) -> Unit,
    onSpeak: ((String) -> Unit)? = null
) {
    val visibleItems = if (showAll) items else items.take(WORD_DETAIL_INITIAL_VISIBLE)
    CollapsibleSectionCard(
        title = title,
        count = items.size,
        expanded = expanded,
        onToggle = onToggle,
        onDoubleCollapse = onDoubleCollapse
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChipWrapRow(
                items = visibleItems,
                chipMeanings = chipMeanings,
                highlightedChip = highlightedChip,
                onChipClick = onChipClick,
                onSpeak = onSpeak,
                onDoubleClick = onDoubleCollapse
            )
            if (items.size > WORD_DETAIL_INITIAL_VISIBLE) {
                FullWidthSeeMoreFooter(
                    isExpanded = showAll,
                    remainingCount = items.size - WORD_DETAIL_INITIAL_VISIBLE,
                    onClick = onToggleShowAll,
                    onDoubleTapCollapse = onDoubleCollapse,
                    testTag = "chips_see_more_${title.lowercase(Locale.US)}"
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollapsibleSectionCard(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDoubleCollapse: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, semantic.divider)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeaderWithArabic(
                    englishTitle = title,
                    count = count,
                    titleColor = colors.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = colors.onSurfaceVariant
                )
            }

            if (expanded) {
                HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onDoubleCollapse != null) {
                                Modifier.combinedClickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = {},
                                    onDoubleClick = onDoubleCollapse
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(14.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReverseArabicGroupsCard(
    groups: List<ArabicVocalizedGroup>,
    audioManager: AudioPlayerManager,
    onOpenEnglishWord: (String) -> Unit,
    onScrollToSection: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    var showAllMatches by remember(groups) { mutableStateOf(false) }
    val flatTotal = groups.sumOf { it.matches.size }
    val onDoubleCollapse: (() -> Unit)? = if (showAllMatches && flatTotal > WORD_DETAIL_INITIAL_VISIBLE) {
        {
            showAllMatches = false
            onScrollToSection()
        }
    } else {
        null
    }
    val interaction = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reverse_arabic_groups_card"),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, semantic.divider)
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (onDoubleCollapse != null) {
                        Modifier.combinedClickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = {},
                            onDoubleClick = onDoubleCollapse
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeaderWithArabic(
                englishTitle = "English Matches",
                count = flatTotal,
                titleColor = colors.onSurface
            )

            var remainingBudget = if (showAllMatches) Int.MAX_VALUE else WORD_DETAIL_INITIAL_VISIBLE
            groups.forEachIndexed { groupIndex, group ->
                if (remainingBudget <= 0) return@forEachIndexed
                val visibleGroupMatches = group.matches.take(remainingBudget)
                remainingBudget -= visibleGroupMatches.size

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reverse_arabic_group_$groupIndex"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = colors.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                            Text(
                                text = "${group.vocalizedForm} (${group.matches.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    visibleGroupMatches.forEach { match ->
                        ReverseArabicMatchRow(
                            match = match,
                            audioManager = audioManager,
                            onOpenEnglishWord = { onOpenEnglishWord(match.englishWord) },
                            onDoubleCollapse = onDoubleCollapse
                        )
                    }

                    if (groupIndex < groups.lastIndex && remainingBudget > 0) {
                        HorizontalDivider(color = semantic.divider, thickness = 1.dp)
                    }
                }
            }

            if (flatTotal > WORD_DETAIL_INITIAL_VISIBLE) {
                FullWidthSeeMoreFooter(
                    isExpanded = showAllMatches,
                    remainingCount = flatTotal - WORD_DETAIL_INITIAL_VISIBLE,
                    onClick = {
                        if (showAllMatches) {
                            showAllMatches = false
                            onScrollToSection()
                        } else {
                            showAllMatches = true
                        }
                    },
                    onDoubleTapCollapse = onDoubleCollapse,
                    testTag = "reverse_arabic_see_more"
                )
            }
        }
    }
}

@Composable
private fun ReverseArabicMatchRow(
    match: ReverseEnglishMatch,
    audioManager: AudioPlayerManager,
    onOpenEnglishWord: () -> Unit,
    onDoubleCollapse: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val semantic = LocalSemanticColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickableOrDoubleTap(onClick = onOpenEnglishWord, onDoubleClick = onDoubleCollapse)
            .testTag("reverse_match_${match.englishWord}"),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = match.englishWord,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (match.partOfSpeech.isNotBlank()) {
                        PosBadgeWithArabic(partOfSpeech = match.partOfSpeech)
                    }
                }

                CompactWordAudioButtons(
                    hasUsAudio = true,
                    hasUkAudio = true,
                    onPlayUs = {
                        audioManager.playHumanPronunciation(
                            word = match.englishWord,
                            accent = Accent.US,
                            audioUrl = match.usAudioUrl,
                            key = "rev_us_${match.englishWord}"
                        )
                    },
                    onPlayUk = {
                        audioManager.playHumanPronunciation(
                            word = match.englishWord,
                            accent = Accent.UK,
                            audioUrl = match.ukAudioUrl,
                            key = "rev_uk_${match.englishWord}"
                        )
                    },
                    onDoubleClick = onDoubleCollapse
                )
            }

            if (match.englishDefinition.isNotBlank()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = match.englishDefinition,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.definitionTranslation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
