package com.example.core

import androidx.core.text.HtmlCompat
import java.util.Locale

data class ExtractedUsageInfo(
    val usageLabels: List<String> = emptyList(),
    val spellingVariantLabel: String? = null,
    val otherSpellingWord: String? = null
)

object ArabicTextUtils {

    private val ARABIC_DIACRITICS_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")
    private val ARABIC_HARAKAT_ONLY_REGEX = Regex("[\\u064B-\\u065F\\u0670]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private val USAGE_SPAN_REGEX = Regex(
        "<span[^>]*class=\"[^\"]*(?:usage-label-sense|usage-label-term|ib-content|label-content)[^\"]*\"[^>]*>(.*?)</span>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val LEADING_PARENS_REGEX = Regex("^\\(([^)]{1,64})\\)\\s+")

    private val US_SPELLING_REGEX = Regex(
        "\\b(?:US|U\\.S\\.|American)(?:\\s+standard)?\\s+spelling\\s+of\\s+([A-Za-z][A-Za-z-]{1,40})\\b",
        RegexOption.IGNORE_CASE
    )
    private val UK_SPELLING_REGEX = Regex(
        "\\b(?:UK|U\\.K\\.|British|Commonwealth)(?:\\s+standard)?\\s+spelling\\s+of\\s+([A-Za-z][A-Za-z-]{1,40})\\b",
        RegexOption.IGNORE_CASE
    )

    fun containsArabicLetters(text: String): Boolean {
        for (ch in text) {
            if (ch in '\u0600'..'\u06FF' ||
                ch in '\u0750'..'\u077F' ||
                ch in '\u08A0'..'\u08FF' ||
                ch in '\uFB50'..'\uFDFF' ||
                ch in '\uFE70'..'\uFEFF'
            ) {
                return true
            }
        }
        return false
    }

    fun hasArabicDiacritics(text: String): Boolean =
        ARABIC_HARAKAT_ONLY_REGEX.containsMatchIn(text)

    /**
     * Canonicalizes tashkeel order (shadda before vowel mark) and strips tatweel so
     * two differently ordered vocalizations like "مَرَّ" and "مَرَّ" match identically
     * while preserving distinct vocalizations ("مُرّ" != "مَرَّ").
     */
    fun canonicalizeArabicWithDiacritics(text: String): String {
        val noTatweel = text.trim().replace("\u0640", "").replace(MULTI_SPACE_REGEX, " ")
        val out = StringBuilder(noTatweel.length)
        var i = 0
        while (i < noTatweel.length) {
            val ch = noTatweel[i]
            out.append(ch)
            if (i + 1 < noTatweel.length && isArabicVowelMark(ch) && noTatweel[i + 1] == '\u0651') {
                // Swap vowel mark + shadda -> shadda + vowel mark
                out.setCharAt(out.length - 1, '\u0651')
                out.append(ch)
                i += 2
            } else {
                i++
            }
        }
        return out.toString().trim()
    }

    private fun isArabicVowelMark(ch: Char): Boolean =
        ch in '\u064B'..'\u0650' || ch == '\u0652' || ch == '\u0670'

    /**
     * Section 15.2: Strip tashkeel (diacritics) and tatweel, preserve أ/إ/آ for initial Wiktionary lookup.
     */
    fun stripArabicDiacriticsKeepHamza(text: String): String {
        return ARABIC_DIACRITICS_REGEX.replace(text.trim(), "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    /**
     * Section 15.2: Normalize Arabic for matching only (never display):
     * remove tashkeel & tatweel, unify أ إ آ ٱ -> ا.
     */
    fun normalizeArabicForMatching(text: String): String {
        val stripped = stripArabicDiacriticsKeepHamza(text)
        val sb = StringBuilder(stripped.length)
        for (ch in stripped) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(MULTI_SPACE_REGEX, " ").trim()
    }

    fun normalizeForKey(text: String): String {
        val trimmed = text.trim().replace(MULTI_SPACE_REGEX, " ")
        return if (containsArabicLetters(trimmed)) {
            normalizeArabicForMatching(trimmed)
        } else {
            trimmed.lowercase(Locale.US)
        }
    }

    fun isMultiWordQuery(text: String): Boolean {
        val words = text.trim().split(MULTI_SPACE_REGEX).filter { it.isNotBlank() }
        return words.size > 1
    }

    /**
     * Section 14.1: Split a sentence into tappable word tokens (preserving order & stripping outer punctuation).
     */
    fun extractWordTokens(sentence: String): List<String> {
        val trimChars = charArrayOf(
            '.', ',', '!', '?', '؟', '؛', '،', ':', ';', '"', '\'', '«', '»',
            '(', ')', '[', ']', '{', '}', '“', '”', '‘', '’', '…', '—', '–'
        )
        return sentence.trim()
            .split(MULTI_SPACE_REGEX)
            .map { raw -> raw.trim(*trimChars) }
            .filter { token -> token.any { it.isLetterOrDigit() } }
            .distinctBy { normalizeForKey(it) }
    }

    fun stripHtmlPlain(html: String): String {
        if (html.isBlank()) return ""
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            .toString()
            .replace('\uFFFC', ' ')
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    /**
     * Section 13: Extract ONLY explicit usage labels from Wiktionary HTML usage-label spans.
     * Never guesses from definition prose.
     */
    fun extractUsageFromWiktionaryHtml(defHtml: String, cleanPlainDef: String): ExtractedUsageInfo {
        val labels = LinkedHashSet<String>()

        USAGE_SPAN_REGEX.findAll(defHtml).forEach { match ->
            val rawSpan = stripHtmlPlain(match.groupValues[1])
            collectKnownLabels(rawSpan, labels)
        }

        // Also check explicit spelling variant text stated directly by the source
        var spellingLabel: String? = null
        var otherSpelling: String? = null
        val usMatch = US_SPELLING_REGEX.find(cleanPlainDef)
        val ukMatch = UK_SPELLING_REGEX.find(cleanPlainDef)
        if (usMatch != null) {
            spellingLabel = "إملاء أمريكي"
            otherSpelling = usMatch.groupValues[1].trim()
        } else if (ukMatch != null) {
            spellingLabel = "إملاء بريطاني"
            otherSpelling = ukMatch.groupValues[1].trim()
        }

        return ExtractedUsageInfo(
            usageLabels = labels.toList(),
            spellingVariantLabel = spellingLabel,
            otherSpellingWord = otherSpelling
        )
    }

    /**
     * Section 13: Extract explicit leading parenthetical labels or spelling variants from Free Dictionary text
     * ONLY when the source explicitly wrote them. Never guesses.
     */
    fun extractUsageFromPlainDefinition(plainDef: String): ExtractedUsageInfo {
        val labels = LinkedHashSet<String>()
        val leadingMatch = LEADING_PARENS_REGEX.find(plainDef.trim())
        if (leadingMatch != null) {
            val inner = leadingMatch.groupValues[1]
            val parts = inner.split(Regex("[,;/]")).map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) {
                val mapped = parts.mapNotNull { mapTokenToArabicLabel(it) }
                // Only treat parenthetical as usage labels if EVERY part inside parentheses is a recognized label
                if (mapped.size == parts.size) {
                    labels.addAll(mapped)
                }
            }
        }

        var spellingLabel: String? = null
        var otherSpelling: String? = null
        val usMatch = US_SPELLING_REGEX.find(plainDef)
        val ukMatch = UK_SPELLING_REGEX.find(plainDef)
        if (usMatch != null) {
            spellingLabel = "إملاء أمريكي"
            otherSpelling = usMatch.groupValues[1].trim()
        } else if (ukMatch != null) {
            spellingLabel = "إملاء بريطاني"
            otherSpelling = ukMatch.groupValues[1].trim()
        }

        return ExtractedUsageInfo(
            usageLabels = labels.toList(),
            spellingVariantLabel = spellingLabel,
            otherSpellingWord = otherSpelling
        )
    }

    private fun collectKnownLabels(rawText: String, out: MutableSet<String>) {
        val cleaned = rawText
            .replace("(", "")
            .replace(")", "")
            .trim()
        if (cleaned.isEmpty()) return
        val parts = cleaned.split(Regex("[,;/]|\\band\\b|\\bor\\b", RegexOption.IGNORE_CASE))
        for (part in parts) {
            val token = part.trim()
                .removePrefix("chiefly ")
                .removePrefix("mainly ")
                .removePrefix("usually ")
                .removePrefix("especially ")
                .trim()
            mapTokenToArabicLabel(token)?.let { out.add(it) }
        }
    }

    private fun mapTokenToArabicLabel(token: String): String? {
        return when (token.lowercase(Locale.US).trim()) {
            "formal" -> "رسمي"
            "informal", "colloquial" -> "غير رسمي"
            "slang" -> "عامية"
            "literary", "poetic" -> "أدبي"
            "archaic", "dated", "obsolete" -> "قديم"
            "rare" -> "نادر"
            "offensive", "derogatory", "pejorative" -> "مسيء"
            "vulgar" -> "بذيء"
            "technical" -> "فني"
            "figurative", "figuratively", "metaphorical" -> "مجازي"
            "idiomatic" -> "اصطلاحي"
            "transitive" -> "متعدٍّ"
            "intransitive" -> "لازم"
            "computing", "software", "programming", "computer science" -> "حوسبة"
            "finance", "banking", "economics", "business", "commerce" -> "أعمال ومالية"
            "law", "legal" -> "قانون"
            "medicine", "medical", "anatomy" -> "طب"
            "sports", "baseball", "cricket", "football", "athletics" -> "رياضة"
            "grammar", "linguistics" -> "نحو ولغة"
            "mathematics", "math" -> "رياضيات"
            "nautical", "maritime" -> "بحري"
            "us", "u.s.", "american", "north american", "us spelling" -> "أمريكي"
            "uk", "u.k.", "british", "uk spelling", "british spelling" -> "بريطاني"
            "australian", "australia" -> "أسترالي"
            else -> null
        }
    }
}
