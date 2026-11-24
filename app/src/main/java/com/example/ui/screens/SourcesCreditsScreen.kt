package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.AppIcons

private data class CreditSource(
    val title: String,
    val description: String,
    val licenseLabel: String?,
    val url: String?
)

private val openSources = listOf(
    CreditSource(
        title = "Wiktionary (ويكاموس)",
        description = "مصدر التعريفات المفصلة، أقسام الكلام، والأمثلة السياقية عبر واجهة REST المفتوحة.",
        licenseLabel = "الترخيص: CC BY-SA 4.0",
        url = "https://creativecommons.org/licenses/by-sa/4.0/"
    ),
    CreditSource(
        title = "Tatoeba",
        description = "مجموعة جمل الأمثلة الثنائية (الإنجليزية والعربية) في سياقات استخدام حقيقية.",
        licenseLabel = "الترخيص: CC BY 2.0 FR",
        url = "https://creativecommons.org/licenses/by/2.0/fr/"
    ),
    CreditSource(
        title = "Datamuse API",
        description = "المترادفات، المتضادات، المتلازمات اللفظية، حروف الجر، عائلة الكلمة، والكلمات المتشابهة في النطق أو الكتابة.",
        licenseLabel = null,
        url = "https://www.datamuse.com/api/"
    ),
    CreditSource(
        title = "Free Dictionary API",
        description = "النطق الصوتي البشري (US / UK)، الألفبائية الصوتية الدولية (IPA)، والتعريفات.",
        licenseLabel = null,
        url = "https://dictionaryapi.dev/"
    ),
    CreditSource(
        title = "Princeton WordNet",
        description = "الروابط الدلالية والمفاهيم الأعم والأخص التي يعتمد عليها محرك Datamuse.",
        licenseLabel = null,
        url = "https://wordnet.princeton.edu/"
    ),
    CreditSource(
        title = "PDFBox-Android (Apache PDFBox)",
        description = "استخراج النصوص من ملفات PDF صفحة بصفحة محلياً على الجهاز بكفاءة ومن دون استهلاك مفرط للذاكرة.",
        licenseLabel = "الترخيص: Apache License 2.0",
        url = "https://github.com/TomRoush/PdfBox-Android"
    ),
    CreditSource(
        title = "Google ML Kit (On-Device Translation & Text Recognition)",
        description = "الترجمة الآلية المحلية لأكثر من 59 لغة، والتعرف الضوئي على الحروف اللاتينية في الصور وملفات PDF المصوّرة دون اتصال.",
        licenseLabel = "تراخيص Google ML Kit",
        url = "https://developers.google.com/ml-kit/terms"
    ),
    CreditSource(
        title = "Coxhead (2000) Academic Word List",
        description = "قائمة الكلمات الأكاديمية (AWL) المستخدمة في تمييز المفردات الأكاديمية الشائعة ضمن حزم الأوفلاين. ليست قائمة رسمية من ETS أو IELTS.",
        licenseLabel = "مصدر القائمة الأكاديمية",
        url = "https://www.wgtn.ac.nz/lals/resources/academicwordlist"
    )
)

@Composable
fun SourcesCreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

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
                        .testTag("sources_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.onBackground
                    )
                }
                Text(
                    text = "عن المصادر",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onBackground
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                openSources.forEach { source ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, colors.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = source.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = source.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                                if (source.licenseLabel != null && source.url != null) {
                                    Row(
                                        modifier = Modifier
                                            .clickable {
                                                val uri = Uri.parse(source.url)
                                                runCatching {
                                                    CustomTabsIntent.Builder().build().launchUrl(context, uri)
                                                }.onFailure {
                                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = source.licenseLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.secondary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = AppIcons.OpenInNew,
                                            contentDescription = null,
                                            tint = colors.secondary,
                                            modifier = Modifier.size(14.dp)
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
