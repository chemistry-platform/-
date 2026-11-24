package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AppIcons
import com.example.ui.components.InteractiveWordChip

private data class DiscoverWord(
    val word: String,
    val hintArabic: String
)

private val curatedWords = listOf(
    DiscoverWord("run", "يجري • يدير • يشتغل • يترشح"),
    DiscoverWord("set", "يضبط • يحدد • مجموعة • غروب"),
    DiscoverWord("eloquent", "فصيح • بليغ اللسان"),
    DiscoverWord("resilient", "مرن • سريع التعافي"),
    DiscoverWord("pragmatic", "عملي • واقعي"),
    DiscoverWord("ambiguous", "غامض • يحتمل أكثر من معنى")
)

private val topicSets = listOf(
    "أفعال متعددة المعاني" to listOf("run", "take", "make", "break", "turn", "get"),
    "مفردات الأعمال والتقنية" to listOf("strategy", "deploy", "scalable", "benchmark", "revenue"),
    "صفات دقيقة في التعبير" to listOf("subtle", "vivid", "coherent", "profound", "concise")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(
    onOpenWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
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
                        text = stringResource(R.string.nav_discover),
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.onBackground
                    )
                    Text(
                        text = "كلمات مختارة لتوسيع حصيلتك اللغوية وسياقاتها",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenWord("run") },
                    color = colors.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.secondary.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.Lightbulb,
                                contentDescription = null,
                                tint = colors.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "كلمة اليوم متعددة السياقات",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.tertiary
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "run",
                                style = MaterialTheme.typography.displayLarge,
                                color = colors.onSurface
                            )
                        }
                        Text(
                            text = "يجري؛ يدير؛ يشتغل؛ يسيل؛ يترشح — اضغط لاستعراض كافة المعاني والأمثلة الصوتية.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "مفردات مقترحة",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.primary
                )
            }

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
                        curatedWords.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenWord(item.word) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.hintArabic,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = item.word,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = colors.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            topicSets.forEach { (title, words) ->
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
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = colors.onSurface
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    words.forEach { w ->
                                        InteractiveWordChip(
                                            word = w,
                                            onClick = { onOpenWord(w) }
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
}
