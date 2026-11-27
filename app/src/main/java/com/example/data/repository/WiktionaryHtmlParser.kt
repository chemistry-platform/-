package com.example.data.repository

import com.example.core.ArabicTextUtils
import com.example.domain.model.SenseItem
import com.example.domain.model.SenseSource
import com.example.domain.model.WordFormItem
import org.json.JSONObject
import java.util.Locale

data class WiktionaryArabicGloss(
    val senseGloss: String,
    val arabicTerms: List<String>
)

data class WiktionaryEnglishSectionData(
    val arabicGlosses: List<WiktionaryArabicGloss> = emptyList(),
    val phrasalVerbs: List<String> = emptyList(),
    val idioms: List<String> = emptyList(),
    val compounds: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val derivedWords: List<String> = emptyList(),
    val relatedTerms: List<String> = emptyList(),
    val wordForms: List<WordFormItem> = emptyList()
)

data class WiktionaryArabicPageData(
    val vocalizedHeadwords: List<String> = emptyList(),
    val transliteration: String = "",
    val senses: List<SenseItem> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val derivedTerms: List<String> = emptyList(),
    val relatedTerms: List<String> = emptyList(),
    val idiomsAndPhrases: List<String> = emptyList()
)

/**
 * Pure Kotlin parser for Wiktionary `w/api.php?action=parse&prop=text` JSON/HTML payloads.
 * Extracts:
 *  1) Curated Arabic translations (with full tashkeel & sense context glosses) for English entries.
 *  2) Rich English section lists (Phrasal verbs, Idioms, Collocations, Compound terms, etc.).
 *  3) Multi-vocalization Arabic headwords (e.g., "مُرّ" vs "مَرَّ"), transliterations, POS senses,
 *     examples, synonyms, antonyms, derived words, and Arabic idioms/expressions.
 */
object WiktionaryHtmlParser {

    private val NAV_HEAD_REGEX = Regex(
        "<div[^>]*class=\"[^\"]*NavHead[^\"]*\"[^>]*>(.*?)</div>(.*?)(?=<div[^>]*class=\"[^\"]*NavFrame[^\"]*\"|\\z)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val LI_REGEX = Regex(
        "<li[^>]*>(.*?)</li>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ARAB_SPAN_REGEX = Regex(
        "<span[^>]*lang=\"ar\"[^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val TRANSLIT_SPAN_REGEX = Regex(
        "<span[^>]*class=\"[^\"]*\\btr\\b[^\"]*\"[^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HEADING_REGEX = Regex(
        "<h([2-6])[^>]*>(.*?)</h\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val HEADWORD_STRONG_REGEX = Regex(
        "<strong[^>]*class=\"[^\"]*\\bArab\\b[^\"]*\\bheadword\\b[^\"]*\"[^>]*>(.*?)</strong>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val OL_REGEX = Regex(
        "<ol[^>]*>(.*?)</ol>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val DL_OR_EXAMPLE_REGEX = Regex(
        "<div[^>]*class=\"[^\"]*h-usage-example[^\"]*\"[^>]*>(.*?)</div>|<dd[^>]*>(.*?)</dd>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ARABIC_EXAMPLE_SPAN_REGEX = Regex(
        "<(?:span|i)[^>]*class=\"[^\"]*e-example[^\"]*\"[^>]*>(.*?)</(?:span|i)>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val EXAMPLE_TRANS_SPAN_REGEX = Regex(
        "<span[^>]*class=\"[^\"]*e-translation[^\"]*\"[^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val LINK_TEXT_REGEX = Regex(
        "<a[^>]*href=\"/wiki/([^\"#]+)[^\"]*\"[^>]*>(.*?)</a>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val TAG_REGEX = Regex("<[^>]+>")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun extractHtmlFromParseJson(rawJson: String): String {
        if (rawJson.isBlank()) return ""
        return runCatching {
            val parseObj = JSONObject(rawJson).optJSONObject("parse") ?: return ""
            val textObj = parseObj.optJSONObject("text")
            when {
                textObj != null -> textObj.optString("*", "")
                else -> parseObj.optString("text", "")
            }
        }.getOrDefault("")
    }

    /**
     * Parses a Wiktionary `Translations` section HTML block into `(senseGloss -> Arabic terms)`.
     */
    fun parseEnglishTranslationsHtml(sectionHtml: String): List<WiktionaryArabicGloss> {
        if (sectionHtml.isBlank()) return emptyList()
        val result = ArrayList<WiktionaryArabicGloss>()

        NAV_HEAD_REGEX.findAll(sectionHtml).forEach { match ->
            val rawGloss = stripTagsClean(match.groupValues[1])
                .removeSuffix("±")
                .removeSuffix("show")
                .removeSuffix("hide")
                .trim()
            val bodyHtml = match.groupValues[2]
            if (rawGloss.isBlank()) return@forEach

            val arabicTerms = LinkedHashSet<String>()
            LI_REGEX.findAll(bodyHtml).forEach { liMatch ->
                val liHtml = liMatch.groupValues[1]
                val liPlainPrefix = stripTagsClean(liHtml.substringBefore("<span").take(40)).lowercase(Locale.US)
                if (liPlainPrefix.contains("arabic") || liHtml.contains("lang=\"ar\"")) {
                    // Only capture Egyptian/Standard Arabic entries, skip unrelated languages that happen to have Arabic script like Persian/Urdu
                    if (!liPlainPrefix.startsWith("persian") &&
                        !liPlainPrefix.startsWith("urdu") &&
                        !liPlainPrefix.startsWith("pashto") &&
                        !liPlainPrefix.startsWith("kurdish") &&
                        !liPlainPrefix.startsWith("ottoman")
                    ) {
                        ARAB_SPAN_REGEX.findAll(liHtml).forEach { arMatch ->
                            val cleanAr = stripTagsClean(arMatch.groupValues[1])
                                .replace(Regex("\\s*\\([^)]*\\)"), "")
                                .trim()
                            if (cleanAr.isNotBlank() && ArabicTextUtils.containsArabicLetters(cleanAr)) {
                                arabicTerms.add(cleanAr)
                            }
                        }
                    }
                }
            }

            if (arabicTerms.isNotEmpty()) {
                result.add(
                    WiktionaryArabicGloss(
                        senseGloss = rawGloss,
                        arabicTerms = arabicTerms.toList()
                    )
                )
            }
        }
        return result
    }

    /**
     * Parses English Wiktionary section HTML (Phrasal verbs, Idioms, Collocations, Compound terms)
     * to reliably pull terms even when `prop=links` omits or adds template links.
     */
    fun parseEnglishListSectionTerms(sectionHtml: String, baseWord: String): List<String> {
        if (sectionHtml.isBlank()) return emptyList()
        val out = LinkedHashSet<String>()
        val baseLower = baseWord.trim().lowercase(Locale.US)
        LI_REGEX.findAll(sectionHtml).forEach { liMatch ->
            val liHtml = liMatch.groupValues[1]
            // Prefer first link inside list item
            val firstLink = LINK_TEXT_REGEX.find(liHtml)
            val candidate = if (firstLink != null) {
                stripTagsClean(firstLink.groupValues[2])
            } else {
                stripTagsClean(liHtml).substringBefore("—").substringBefore("-").substringBefore("(").trim()
            }
            if (candidate.isNotBlank() &&
                candidate.length in 2..56 &&
                !candidate.equals(baseLower, ignoreCase = true) &&
                !candidate.contains(":") &&
                !candidate.startsWith("Appendix", ignoreCase = true) &&
                !candidate.startsWith("Category", ignoreCase = true) &&
                !ArabicTextUtils.containsArabicLetters(candidate)
            ) {
                out.add(candidate)
            }
        }
        return out.toList()
    }

    private val HEADWORD_LINE_REGEX = Regex(
        "<span[^>]*class=\"[^\"]*headword-line[^\"]*\"[^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val FORM_SPAN_PAIR_REGEX = Regex(
        "<i[^>]*>([^<]+)</i>\\s*(?:<span[^>]*>)?\\s*<(?:b|strong|a)[^>]*class=\"[^\"]*form-of[^\"]*\"[^>]*>(?:<a[^>]*>)?([^<]+)(?:</a>)?</(?:b|strong|a)>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val FORM_PLAIN_PAIR_REGEX = Regex(
        "<i[^>]*>([^<]+)</i>\\s*<b[^>]*>(?:<a[^>]*>)?([^<]+)(?:</a>)?</b>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /**
     * Parses Wiktionary `<span class="headword-line">` inflections (e.g., `ran pa. t.`, `run pa. pple.`).
     */
    fun parseEnglishHeadwordForms(sectionHtml: String, baseWord: String): List<WordFormItem> {
        if (sectionHtml.isBlank()) return emptyList()
        val seen = LinkedHashMap<String, WordFormItem>()
        val baseLower = baseWord.trim().lowercase(Locale.US)

        fun mapFormLabel(rawLabel: String): Pair<String, String>? {
            val l = rawLabel.lowercase(Locale.US)
            return when {
                "past participle" in l -> "pa. pple." to "اسم المفعول"
                "simple past" in l || "past tense" in l -> "pa. t." to "الماضي"
                "third-person singular" in l -> "3rd sg." to "المضارع المفرد"
                "present participle" in l -> "pres. pple." to "اسم الفاعل"
                "plural" in l -> "pl." to "الجمع"
                "comparative" in l -> "comp." to "المقارنة"
                "superlative" in l -> "sup." to "التفضيل"
                else -> null
            }
        }

        val lines = HEADWORD_LINE_REGEX.findAll(sectionHtml).map { it.groupValues[1] }.ifEmpty {
            sequenceOf(sectionHtml)
        }
        for (lineHtml in lines) {
            for (regex in listOf(FORM_SPAN_PAIR_REGEX, FORM_PLAIN_PAIR_REGEX)) {
                regex.findAll(lineHtml).forEach { m ->
                    val rawLabel = stripTagsClean(m.groupValues[1])
                    val mapped = mapFormLabel(rawLabel) ?: return@forEach
                    val formWord = stripTagsClean(m.groupValues[2])
                        .trim(',', ';', '(', ')', ' ')
                    if (formWord.isBlank() || formWord.length > 40 || ArabicTextUtils.containsArabicLetters(formWord)) {
                        return@forEach
                    }
                    // Include past participle even if identical to base (e.g. "run pa. pple.")
                    val key = "${formWord.lowercase(Locale.US)}|${mapped.first}"
                    if (!seen.containsKey(key) && (formWord.lowercase(Locale.US) != baseLower || mapped.first == "pa. pple.")) {
                        seen[key] = WordFormItem(
                            form = formWord,
                            label = mapped.first,
                            labelArabic = mapped.second
                        )
                    }
                }
            }
        }
        return seen.values.toList().take(12)
    }

    /**
     * Parses the Arabic language block (`id="Arabic"`) from an English Wiktionary page HTML.
     * Extracts ALL vocalized headwords (e.g., "مُرّ" & "مَرَّ"), POS senses, examples,
     * synonyms, antonyms, derived terms, and idioms/phrases.
     */
    fun parseArabicSectionFromPageHtml(fullPageHtml: String, searchedQuery: String): WiktionaryArabicPageData {
        if (fullPageHtml.isBlank()) return WiktionaryArabicPageData()

        val arBlock = extractLanguageBlock(fullPageHtml, "Arabic") ?: return WiktionaryArabicPageData()
        val headings = HEADING_REGEX.findAll(arBlock).toList()

        val vocalizedHeadwords = LinkedHashSet<String>()
        var firstTranslit = ""
        val senses = ArrayList<SenseItem>()
        val synonyms = LinkedHashSet<String>()
        val antonyms = LinkedHashSet<String>()
        val derived = LinkedHashSet<String>()
        val related = LinkedHashSet<String>()
        val idioms = LinkedHashSet<String>()

        var currentVocalized = ArabicTextUtils.canonicalizeArabicWithDiacritics(searchedQuery)
        var senseCounter = 1

        for (i in headings.indices) {
            val hMatch = headings[i]
            val titleRaw = stripTagsClean(hMatch.groupValues[2])
                .replace("[edit]", "")
                .trim()
            val titleLower = titleRaw.lowercase(Locale.US)
            val sectionStart = hMatch.range.last + 1
            val sectionEnd = if (i + 1 < headings.size) headings[i + 1].range.first else arBlock.length
            if (sectionStart >= sectionEnd) continue
            val chunk = arBlock.substring(sectionStart, sectionEnd)

            // Detect headword inside this block if present
            val headwordMatch = HEADWORD_STRONG_REGEX.find(chunk)
            if (headwordMatch != null) {
                val hw = stripTagsClean(headwordMatch.groupValues[1])
                if (hw.isNotBlank() && ArabicTextUtils.containsArabicLetters(hw)) {
                    currentVocalized = ArabicTextUtils.canonicalizeArabicWithDiacritics(hw)
                    vocalizedHeadwords.add(currentVocalized)
                }
            }
            if (firstTranslit.isBlank()) {
                val trMatch = TRANSLIT_SPAN_REGEX.find(chunk)
                if (trMatch != null) {
                    firstTranslit = stripTagsClean(trMatch.groupValues[1]).trim()
                }
            }

            val mappedPos = mapHeadingToPos(titleLower)
            if (mappedPos != null) {
                val olMatch = OL_REGEX.find(chunk)
                if (olMatch != null) {
                    val olBody = olMatch.groupValues[1]
                    LI_REGEX.findAll(olBody).forEach { liMatch ->
                        val liHtml = liMatch.groupValues[1]
                        // Extract example before stripping definition text
                        val exList = ArrayList<String>()
                        val exTrans = HashMap<String, String>()
                        val exIsMachine = HashMap<String, Boolean>()

                        ARABIC_EXAMPLE_SPAN_REGEX.findAll(liHtml).forEach { exSpan ->
                            val arEx = stripTagsClean(exSpan.groupValues[1])
                            if (arEx.isNotBlank()) exList.add(arEx)
                        }
                        val firstExTrans = EXAMPLE_TRANS_SPAN_REGEX.find(liHtml)?.let { stripTagsClean(it.groupValues[1]) }
                        if (exList.isNotEmpty() && !firstExTrans.isNullOrBlank()) {
                            exTrans[exList.first()] = firstExTrans
                            exIsMachine[exList.first()] = false
                        }

                        // Remove nested <ul>/<dl>/<div class="h-usage-example"> so definition is clean English gloss
                        val defOnlyHtml = liHtml
                            .replace(Regex("<ul[^>]*>.*?</ul>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                            .replace(DL_OR_EXAMPLE_REGEX, "")
                        val engDef = stripTagsClean(defOnlyHtml).trim()
                        if (engDef.isNotBlank() && engDef.length >= 2) {
                            val usageInfo = ArabicTextUtils.extractUsageFromWiktionaryHtml(defOnlyHtml, engDef)
                            val labels = LinkedHashSet(usageInfo.usageLabels)
                            val displayHw = currentVocalized.ifBlank { searchedQuery }
                            if (ArabicTextUtils.hasArabicDiacritics(displayHw)) {
                                labels.add(displayHw)
                            }
                            senses.add(
                                SenseItem(
                                    index = senseCounter++,
                                    partOfSpeech = mappedPos,
                                    partOfSpeechArabic = mapPosToArabicStatic(mappedPos),
                                    definition = displayHw,
                                    definitionArabic = engDef,
                                    definitionArabicIsMachine = false,
                                    examples = exList.take(2),
                                    exampleTranslations = exTrans,
                                    exampleIsMachine = exIsMachine,
                                    usageLabels = labels.toList(),
                                    headwordVariant = displayHw,
                                    source = SenseSource.WIKTIONARY
                                )
                            )
                        }
                    }
                }
            } else {
                // Relation lists inside Arabic section (Synonyms, Antonyms, Derived terms, Related terms, Idioms)
                val targetSet = when {
                    titleLower.contains("synonym") -> synonyms
                    titleLower.contains("antonym") -> antonyms
                    titleLower.contains("derived") -> derived
                    titleLower.contains("related") -> related
                    titleLower.contains("idiom") || titleLower.contains("phrase") || titleLower.contains("proverb") -> idioms
                    else -> null
                }
                if (targetSet != null) {
                    extractArabicTermsFromChunk(chunk, targetSet, exclude = currentVocalized)
                }
            }
        }

        return WiktionaryArabicPageData(
            vocalizedHeadwords = vocalizedHeadwords.toList(),
            transliteration = firstTranslit,
            senses = senses,
            synonyms = synonyms.toList().take(25),
            antonyms = antonyms.toList().take(25),
            derivedTerms = derived.toList().take(25),
            relatedTerms = related.toList().take(25),
            idiomsAndPhrases = idioms.toList().take(20)
        )
    }

    /**
     * Matches curated Wiktionary English->Arabic translations onto a SenseItem definition.
     */
    fun matchBestArabicTranslation(
        englishDefinition: String,
        glosses: List<WiktionaryArabicGloss>
    ): String? {
        if (glosses.isEmpty() || englishDefinition.isBlank()) return null
        val defTokens = tokenizeEnglish(englishDefinition)
        if (defTokens.isEmpty()) return null

        var bestScore = 0.0
        var bestTerms: List<String>? = null
        for (gloss in glosses) {
            val gTokens = tokenizeEnglish(gloss.senseGloss)
            if (gTokens.isEmpty()) continue
            val overlap = defTokens.count { it in gTokens }
            if (overlap == 0) continue
            val union = (defTokens + gTokens).size
            val score = overlap.toDouble() / minOf(defTokens.size, gTokens.size).toDouble() +
                (overlap.toDouble() / union.toDouble())
            if (score > bestScore && overlap >= 1) {
                bestScore = score
                bestTerms = gloss.arabicTerms
            }
        }
        return bestTerms?.take(25)?.joinToString("؛ ")
    }

    private fun extractLanguageBlock(html: String, langName: String): String? {
        val marker = Regex("<h2[^>]*>\\s*(?:<span[^>]*id=\"$langName\"[^>]*>)?$langName", RegexOption.IGNORE_CASE)
        val match = marker.find(html) ?: Regex("id=\"$langName\"").find(html) ?: return null
        val start = match.range.first
        val nextH2 = Regex("<h2[^>]*>", RegexOption.IGNORE_CASE).find(html, start + 10)
        val end = nextH2?.range?.first ?: html.length
        return html.substring(start, end)
    }

    private fun extractArabicTermsFromChunk(chunk: String, out: MutableSet<String>, exclude: String) {
        val normExcl = ArabicTextUtils.normalizeArabicForMatching(exclude)
        ARAB_SPAN_REGEX.findAll(chunk).forEach { m ->
            val term = stripTagsClean(m.groupValues[1])
                .replace(Regex("\\s*\\([^)]*\\)"), "")
                .trim()
            if (term.isNotBlank() &&
                ArabicTextUtils.containsArabicLetters(term) &&
                ArabicTextUtils.normalizeArabicForMatching(term) != normExcl &&
                term.length <= 60
            ) {
                out.add(term)
            }
        }
        LINK_TEXT_REGEX.findAll(chunk).forEach { m ->
            val term = stripTagsClean(m.groupValues[2]).trim()
            if (term.isNotBlank() &&
                ArabicTextUtils.containsArabicLetters(term) &&
                ArabicTextUtils.normalizeArabicForMatching(term) != normExcl &&
                term.length <= 60
            ) {
                out.add(term)
            }
        }
    }

    private fun mapHeadingToPos(lower: String): String? = when {
        lower == "noun" || lower.startsWith("noun ") -> "noun"
        lower == "verb" || lower.startsWith("verb ") -> "verb"
        lower == "adjective" || lower.startsWith("adjective ") -> "adjective"
        lower == "adverb" || lower.startsWith("adverb ") -> "adverb"
        lower == "preposition" -> "preposition"
        lower == "pronoun" -> "pronoun"
        lower == "conjunction" -> "conjunction"
        lower == "interjection" -> "interjection"
        lower == "particle" -> "particle"
        lower == "determiner" -> "determiner"
        lower == "numeral" -> "numeral"
        lower == "phrase" || lower == "idiom" || lower == "proverb" -> lower
        else -> null
    }

    private fun mapPosToArabicStatic(pos: String): String = when (pos.lowercase(Locale.US)) {
        "noun" -> "اسم"
        "verb" -> "فعل"
        "adjective" -> "صفة"
        "adverb" -> "ظرف"
        "pronoun" -> "ضمير"
        "preposition" -> "حرف جر"
        "conjunction" -> "أداة ربط"
        "interjection" -> "صيغة تعجب"
        "determiner" -> "محدد"
        "particle" -> "أداة"
        "numeral" -> "عدد"
        "idiom" -> "تعبير اصطلاحي"
        "phrase" -> "عبارة"
        "proverb" -> "مثل"
        else -> pos
    }

    private fun stripTagsClean(html: String): String {
        if (html.isBlank()) return ""
        return TAG_REGEX.replace(html, " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    private val STOP_WORDS = setOf(
        "to", "of", "the", "a", "an", "in", "on", "at", "for", "with", "by", "or", "and", "be", "something", "someone"
    )

    private fun tokenizeEnglish(text: String): Set<String> {
        return text.lowercase(Locale.US)
            .split(Regex("[^a-z]+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()
    }
}
