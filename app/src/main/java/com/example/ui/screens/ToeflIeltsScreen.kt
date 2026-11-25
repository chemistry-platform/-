package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.Accent
import com.example.core.AudioPlayerManager
import com.example.core.LocalAppLanguage
import com.example.core.tr
import com.example.domain.model.AwlWordItem
import com.example.ui.components.AppIcons
import com.example.ui.components.CompactWordAudioButtons
import com.example.ui.viewmodel.ToeflIeltsViewModel

@Composable
fun ToeflIeltsScreen(
    viewModel: ToeflIeltsViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    onOpenExamInfo: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val offlinePackCount by viewModel.offlinePackCount.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val awlWords by viewModel.filteredAwlWords.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toefl_ielts_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.onBackground
                    )
                }
                Text(
                    text = tr("TOEFL & IELTS", "التوفل والآيلتس"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 12.1: Two cards "TOEFL" and "IELTS"
                item(key = "exam_cards") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ExamCard(
                            title = "TOEFL",
                            subtitle = tr("TOEFL iBT exam overview", "معلومات اختبار TOEFL iBT"),
                            onClick = { onOpenExamInfo("TOEFL") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("card_toefl")
                        )
                        ExamCard(
                            title = "IELTS",
                            subtitle = tr("IELTS exam overview", "معلومات اختبار IELTS"),
                            onClick = { onOpenExamInfo("IELTS") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("card_ielts")
                        )
                    }
                }

                // Section 12.4: Academic Word List (AWL) section
                item(key = "awl_header") {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = tr("Academic Vocabulary", "الكلمات الأكاديمية"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.onBackground
                        )
                        Text(
                            text = tr(
                                "From the Academic Word List (AWL) when available in the offline pack.",
                                "من قائمة الكلمات الأكاديمية (AWL) عند توفرها في حزمة الأوفلاين."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                if (offlinePackCount == 0) {
                    item(key = "awl_pack_missing") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Text(
                                text = tr(
                                    "Install the offline pack to view this list",
                                    "ثبّت حزمة الأوفلاين لعرض القائمة"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .testTag("awl_install_pack_message")
                            )
                        }
                    }
                } else {
                    item(key = "awl_search") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("awl_search_field"),
                            placeholder = {
                                Text(
                                    text = tr("Search academic words...", "ابحث في الكلمات الأكاديمية..."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = colors.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.onSearchQueryChange("") },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_clear_search),
                                            tint = colors.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                focusedBorderColor = colors.secondary,
                                unfocusedBorderColor = colors.outline,
                                cursorColor = colors.secondary
                            )
                        )
                    }

                    if (awlWords.isEmpty()) {
                        item(key = "awl_empty") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.surface,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colors.outline)
                            ) {
                                Text(
                                    text = if (searchQuery.isBlank()) {
                                        tr(
                                            "No AWL-tagged words found in the installed offline pack.",
                                            "لا توجد كلمات موسومة بـ AWL في حزمة الأوفلاين المثبّتة."
                                        )
                                    } else {
                                        tr(
                                            "No matching results for \"$searchQuery\".",
                                            "لا توجد نتائج مطابقة لـ \"$searchQuery\"."
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(
                            items = awlWords,
                            key = { it.word }
                        ) { item ->
                            AwlWordRow(
                                item = item,
                                audioManager = audioManager,
                                onClick = { onOpenWord(item.word) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clickable(onClick = onClick),
        color = colors.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Text(
                text = tr("View details", "عرض التفاصيل"),
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondary
            )
        }
    }
}

@Composable
private fun AwlWordRow(
    item: AwlWordItem,
    audioManager: AudioPlayerManager,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val hasUs = audioManager.hasAudioOrCache(item.word, Accent.US, item.usAudioUrl)
    val hasUk = audioManager.hasAudioOrCache(item.word, Accent.UK, item.ukAudioUrl)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("awl_word_${item.word}"),
        color = colors.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = item.word,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.phonetic.isNotBlank()) {
                        Text(
                            text = item.phonetic,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.secondary,
                            maxLines = 1
                        )
                    }
                }
                if (item.summaryDefinition.isNotBlank()) {
                    Text(
                        text = item.summaryDefinition,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            CompactWordAudioButtons(
                hasUsAudio = true,
                hasUkAudio = true,
                onPlayUs = {
                    audioManager.playHumanPronunciation(
                        word = item.word,
                        accent = Accent.US,
                        audioUrl = item.usAudioUrl,
                        key = "awl_us_${item.word}"
                    )
                },
                onPlayUk = {
                    audioManager.playHumanPronunciation(
                        word = item.word,
                        accent = Accent.UK,
                        audioUrl = item.ukAudioUrl,
                        key = "awl_uk_${item.word}"
                    )
                }
            )
        }
    }
}

/**
 * Section 12.1: Dedicated TOEFL or IELTS information page using strictly verified facts.
 */
@Composable
fun ExamInfoScreen(
    examId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val isToefl = examId.equals("TOEFL", ignoreCase = true)

    val title = if (isToefl) "TOEFL iBT" else "IELTS"
    val facts = if (isToefl) {
        listOf(
            tr(
                lang,
                "An English proficiency test administered by ETS, widely used for university admissions, especially in the United States.",
                "اختبار كفاءة في اللغة الإنجليزية تقدّمه مؤسسة ETS، ويُستخدم غالباً للقبول الجامعي، خصوصاً في الولايات المتحدة الأمريكية."
            ),
            tr(
                lang,
                "Tested sections: Reading, Listening, Speaking, and Writing.",
                "المهارات المختبرة: القراءة (Reading)، الاستماع (Listening)، التحدث (Speaking)، والكتابة (Writing)."
            ),
            tr(
                lang,
                "The exam is computer-delivered.",
                "يُجرى الاختبار على الحاسوب."
            )
        )
    } else {
        listOf(
            tr(
                lang,
                "An English proficiency test jointly owned by the British Council, IDP, and Cambridge University Press & Assessment.",
                "اختبار كفاءة في اللغة الإنجليزية مملوك بشكل مشترك من قِبل المجلس الثقافي البريطاني (British Council) و IDP و Cambridge University Press & Assessment."
            ),
            tr(
                lang,
                "Available in two versions: Academic for university study, and General Training for migration or work.",
                "يتوفر بنسختين: الأكاديمي (Academic) للدراسة الجامعية، والتدريب العام (General Training) للهجرة أو العمل."
            ),
            tr(
                lang,
                "Tested sections: Listening, Reading, Writing, and Speaking.",
                "المهارات المختبرة: الاستماع (Listening)، القراءة (Reading)، الكتابة (Writing)، والتحدث (Speaking)."
            ),
            tr(
                lang,
                "Scores are reported on a 0 to 9 Band scale.",
                "يُحتسب التقييم بنظام الدرجات (Bands) من 0 إلى 9."
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("exam_info_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.onBackground
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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
                            facts.forEachIndexed { idx, line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface
                                )
                                if (idx < facts.lastIndex) {
                                    HorizontalDivider(color = colors.outline)
                                }
                            }
                        }
                    }
                }

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
                            Text(
                                text = tr(
                                    "For official details, fees, and test dates, visit the official website",
                                    "للتفاصيل الرسمية والأسعار والمواعيد راجع الموقع الرسمي"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = { openExamCustomTab(context, "https://www.ets.org/toefl") },
                                border = BorderStroke(1.dp, colors.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_official_toefl")
                            ) {
                                Icon(
                                    imageVector = AppIcons.OpenInNew,
                                    contentDescription = null,
                                    tint = colors.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("Official TOEFL Site (ETS)", "الموقع الرسمي TOEFL (ETS)"), color = colors.onSurface)
                            }

                            OutlinedButton(
                                onClick = { openExamCustomTab(context, "https://www.ielts.org") },
                                border = BorderStroke(1.dp, colors.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_official_ielts")
                            ) {
                                Icon(
                                    imageVector = AppIcons.OpenInNew,
                                    contentDescription = null,
                                    tint = colors.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tr("Official IELTS Site", "الموقع الرسمي IELTS"), color = colors.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun openExamCustomTab(context: Context, url: String) {
    val uri = Uri.parse(url)
    runCatching {
        CustomTabsIntent.Builder().build().launchUrl(context, uri)
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}
