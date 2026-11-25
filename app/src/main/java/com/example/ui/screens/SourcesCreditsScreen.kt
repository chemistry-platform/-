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
import androidx.compose.ui.unit.dp
import com.example.core.LocalAppLanguage
import com.example.core.tr
import com.example.ui.components.AppIcons

private data class CreditSource(
    val titleEn: String,
    val titleAr: String,
    val descriptionEn: String,
    val descriptionAr: String,
    val licenseEn: String?,
    val licenseAr: String?,
    val url: String?
)

private val openSources = listOf(
    CreditSource(
        titleEn = "Wiktionary",
        titleAr = "ويكاموس (Wiktionary)",
        descriptionEn = "Detailed definitions, parts of speech, idioms, and contextual examples via the open REST API.",
        descriptionAr = "مصدر التعريفات المفصلة، أقسام الكلام، والأمثلة السياقية عبر واجهة REST المفتوحة.",
        licenseEn = "License: CC BY-SA 4.0",
        licenseAr = "الترخيص: CC BY-SA 4.0",
        url = "https://creativecommons.org/licenses/by-sa/4.0/"
    ),
    CreditSource(
        titleEn = "Tatoeba",
        titleAr = "Tatoeba",
        descriptionEn = "Bilingual English–Arabic example sentence corpus in real usage contexts.",
        descriptionAr = "مجموعة جمل الأمثلة الثنائية (الإنجليزية والعربية) في سياقات استخدام حقيقية.",
        licenseEn = "License: CC BY 2.0 FR",
        licenseAr = "الترخيص: CC BY 2.0 FR",
        url = "https://creativecommons.org/licenses/by/2.0/fr/"
    ),
    CreditSource(
        titleEn = "Datamuse API",
        titleAr = "Datamuse API",
        descriptionEn = "Synonyms, antonyms, collocations, prepositions, word families, and confusable words.",
        descriptionAr = "المترادفات، المتضادات، المتلازمات اللفظية، حروف الجر، عائلة الكلمة، والكلمات المتشابهة في النطق أو الكتابة.",
        licenseEn = null,
        licenseAr = null,
        url = "https://www.datamuse.com/api/"
    ),
    CreditSource(
        titleEn = "Free Dictionary API",
        titleAr = "Free Dictionary API",
        descriptionEn = "Human US/UK audio pronunciations, IPA transcriptions, and core definitions.",
        descriptionAr = "النطق الصوتي البشري (US / UK)، الألفبائية الصوتية الدولية (IPA)، والتعريفات.",
        licenseEn = null,
        licenseAr = null,
        url = "https://dictionaryapi.dev/"
    ),
    CreditSource(
        titleEn = "Princeton WordNet",
        titleAr = "Princeton WordNet",
        descriptionEn = "Semantic relations, hypernyms, and hyponyms powering the Datamuse lexical engine.",
        descriptionAr = "الروابط الدلالية والمفاهيم الأعم والأخص التي يعتمد عليها محرك Datamuse.",
        licenseEn = null,
        licenseAr = null,
        url = "https://wordnet.princeton.edu/"
    ),
    CreditSource(
        titleEn = "PDFBox-Android (Apache PDFBox)",
        titleAr = "PDFBox-Android (Apache PDFBox)",
        descriptionEn = "Efficient on-device page-by-page PDF text extraction with low memory overhead.",
        descriptionAr = "استخراج النصوص من ملفات PDF صفحة بصفحة محلياً على الجهاز بكفاءة ومن دون استهلاك مفرط للذاكرة.",
        licenseEn = "License: Apache License 2.0",
        licenseAr = "الترخيص: Apache License 2.0",
        url = "https://github.com/TomRoush/PdfBox-Android"
    ),
    CreditSource(
        titleEn = "Google ML Kit (On-Device Translation & Text Recognition)",
        titleAr = "Google ML Kit (الترجمة والتعرف الضوئي محلياً)",
        descriptionEn = "Offline machine translation across 59+ languages and Latin-script OCR for images and scanned PDFs.",
        descriptionAr = "الترجمة الآلية المحلية لأكثر من 59 لغة، والتعرف الضوئي على الحروف اللاتينية في الصور وملفات PDF المصوّرة دون اتصال.",
        licenseEn = "Google ML Kit Terms",
        licenseAr = "تراخيص Google ML Kit",
        url = "https://developers.google.com/ml-kit/terms"
    ),
    CreditSource(
        titleEn = "Coxhead (2000) Academic Word List",
        titleAr = "قائمة الكلمات الأكاديمية (Coxhead 2000)",
        descriptionEn = "Academic Word List (AWL) used to highlight high-frequency academic vocabulary in offline packs. Not an official ETS or IELTS list.",
        descriptionAr = "قائمة الكلمات الأكاديمية (AWL) المستخدمة في تمييز المفردات الأكاديمية الشائعة ضمن حزم الأوفلاين. ليست قائمة رسمية من ETS أو IELTS.",
        licenseEn = "Academic Word List Source",
        licenseAr = "مصدر القائمة الأكاديمية",
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
    val lang = LocalAppLanguage.current

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
                        contentDescription = tr(lang, "Back", "رجوع"),
                        tint = colors.onBackground
                    )
                }
                Text(
                    text = tr(lang, "Sources & Licenses", "عن المصادر"),
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
                                    text = tr(lang, source.titleEn, source.titleAr),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = tr(lang, source.descriptionEn, source.descriptionAr),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                                val licenseText = if (source.licenseEn != null && source.licenseAr != null) {
                                    tr(lang, source.licenseEn, source.licenseAr)
                                } else null
                                if (licenseText != null && source.url != null) {
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
                                            text = licenseText,
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
