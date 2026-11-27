package com.example.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    ARABIC("ar");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
    }
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ENGLISH }

@Composable
fun tr(en: String, ar: String): String {
    return if (LocalAppLanguage.current == AppLanguage.ARABIC) ar else en
}

fun tr(language: AppLanguage, en: String, ar: String): String {
    return if (language == AppLanguage.ARABIC) ar else en
}

object LocalizedLabels {

    fun usageLabel(rawLabel: String, language: AppLanguage): String {
        if (language == AppLanguage.ARABIC) return rawLabel
        return when (rawLabel.trim()) {
            "الكل" -> "All"
            "رسمي" -> "Formal"
            "غير رسمي" -> "Informal"
            "أمريكي" -> "US"
            "بريطاني" -> "UK"
            "إملاء أمريكي" -> "US Spelling"
            "إملاء بريطاني" -> "UK Spelling"
            "مجازي" -> "Figurative"
            "اصطلاحي" -> "Idiomatic"
            "فني" -> "Technical"
            "حوسبة" -> "Computing"
            "أعمال ومالية" -> "Business & Finance"
            "قانون" -> "Law"
            "طب" -> "Medicine"
            "رياضة" -> "Sports"
            "نحو ولغة" -> "Grammar"
            "رياضيات" -> "Mathematics"
            "بحري" -> "Nautical"
            "قديم" -> "Archaic"
            "نادر" -> "Rare"
            "مسيء" -> "Offensive"
            "بذيء" -> "Vulgar"
            "عامية" -> "Slang"
            "أدبي" -> "Literary"
            else -> rawLabel
        }
    }

    fun languageDisplayName(info: LanguagePackInfo, language: AppLanguage): String {
        return if (language == AppLanguage.ARABIC) {
            info.arabicName
        } else {
            val locale = Locale.forLanguageTag(info.code)
            locale.getDisplayName(Locale.ENGLISH)
                .takeIf { it.isNotBlank() && !it.equals(info.code, ignoreCase = true) }
                ?: info.nativeName.takeIf { it.isNotBlank() }
                ?: info.code
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun phraseBadge(category: String, isIdiom: Boolean, language: AppLanguage): String {
        return when {
            category == "COMPOUND" -> "Compound noun"
            isIdiom || category == "IDIOM" -> "Idiom"
            else -> "Phrasal verb"
        }
    }

    fun formatGrammarStructure(raw: String, language: AppLanguage): String {
        if (language == AppLanguage.ARABIC) return raw
        return raw
            .replace("[اسم / ضمير]", "[noun / pronoun]")
            .replace("[شخص / شيء]", "[person / thing]")
            .replace("(فعل مركب)", "(phrasal verb)")
    }

    fun usageNote(raw: String, language: AppLanguage): String {
        if (language == AppLanguage.ARABIC) return raw
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("استخدام مجازي ضمن مجال:") -> {
                val domains = trimmed.removePrefix("استخدام مجازي ضمن مجال:").trim()
                    .split("،", ",")
                    .joinToString(", ") { usageLabel(it.trim(), language) }
                "Figurative usage in: $domains"
            }
            trimmed.startsWith("مصطلح متخصص (") && trimmed.endsWith(")") -> {
                val inner = trimmed.removePrefix("مصطلح متخصص (").removeSuffix(")")
                    .split("،", ",")
                    .joinToString(", ") { usageLabel(it.trim(), language) }
                "Specialized term ($inner)"
            }
            trimmed == "معنى مجازي أو اصطلاحي يختلف عن المعنى الحرفي" ->
                "Figurative or idiomatic meaning distinct from literal usage"
            trimmed == "استخدام قديم أو قليل الشيوع حالياً" ->
                "Archaic or uncommon modern usage"
            trimmed == "تعبير عامي شائع في المحادثات غير الرسمية" ->
                "Colloquial expression common in informal conversation"
            else -> raw
        }
    }
}
