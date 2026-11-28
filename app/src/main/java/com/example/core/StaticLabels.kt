package com.example.core

import java.util.Locale

/**
 * Bundled zero-network EN <-> AR lookup for every UI label, section heading,
 * part-of-speech tag, form label, and action caption.
 */
object StaticLabels {
    private val enToArMap: Map<String, String> = mapOf(
        // App & navigation
        "almakhzanji dictionary" to "معجم المخزنجي",
        "home" to "الرئيسية",
        "search" to "بحث",
        "discover" to "اكتشف",
        "favorites" to "المفضلة",
        "files" to "الملفات",
        "settings" to "الإعدادات",
        "more" to "المزيد",
        "history" to "السجل",
        "recent searches" to "عمليات البحث الأخيرة",
        "quick translate" to "الترجمة السريعة",
        "word lookup" to "بحث الكلمات",
        "sentence translation" to "ترجمة الجمل",

        // Languages & direction
        "english" to "الإنجليزية",
        "arabic" to "العربية",
        "english ⇄ arabic" to "إنجليزي ⇄ عربي",
        "arabic ⇄ english" to "عربي ⇄ إنجليزي",
        "us" to "أمريكي",
        "uk" to "بريطاني",
        "ar" to "عربي",

        // Section headings (Item 8 & 9)
        "meanings" to "المعاني",
        "definitions" to "التعريفات",
        "translation" to "الترجمة",
        "synonyms" to "المترادفات",
        "antonyms" to "المتضادات",
        "idioms" to "التعابير الاصطلاحية",
        "idioms & expressions" to "التعابير الاصطلاحية والعبارات",
        "phrasal verbs" to "الأفعال المركبة",
        "collocations" to "المتلازمات اللفظية",
        "phrases" to "العبارات والتراكيب",
        "homophones" to "المتجانسات اللفظية",
        "look-alike words" to "كلمات متشابهة شكلاً",
        "sound-alike words" to "كلمات متشابهة نطقاً",
        "similarly spelled" to "كلمات متشابهة إملائياً",
        "confusable words" to "كلمات يسهل الخلط بينها",
        "word forms" to "تصاريف الكلمة",
        "word family" to "عائلة الكلمة",
        "related terms" to "كلمات ذات صلة",
        "more examples" to "أمثلة إضافية",
        "examples" to "أمثلة",
        "word breakdown" to "تفصيل الكلمات",
        "words in sentence" to "كلمات الجملة",
        "grammar & usage" to "القواعد والاستخدام",
        "usage notes" to "ملاحظات الاستخدام",
        "english matches" to "الكلمات الإنجليزية المقابلة",

        // Collapsible & buttons
        "see more" to "عرض المزيد",
        "see less" to "عرض أقل",
        "clear" to "مسح",
        "download" to "تنزيل",
        "pause" to "إيقاف مؤقت",
        "resume" to "استئناف",
        "delete" to "حذف",
        "update" to "تحديث",
        "check updates" to "التحقق من التحديثات",
        "close" to "إغلاق",
        "cancel" to "إلغاء",
        "listen" to "استماع",
        "copy" to "نسخ",
        "share" to "مشاركة",
        "open dictionary" to "فتح المعجم",
        "pro pack" to "الحزمة الاحترافية",
        "offline voice pack" to "حزمة الصوت دون إنترنت",
        "literal translation" to "الترجمة الحرفية",
        "sources & licenses" to "المصادر والتراخيص",

        // Parts of speech
        "noun" to "اسم",
        "n." to "اسم",
        "verb" to "فعل",
        "v." to "فعل",
        "adjective" to "صفة",
        "adj." to "صفة",
        "adj" to "صفة",
        "adverb" to "ظرف",
        "adv." to "ظرف",
        "adv" to "ظرف",
        "pronoun" to "ضمير",
        "pron." to "ضمير",
        "preposition" to "حرف جر",
        "prep." to "حرف جر",
        "conjunction" to "حرف عطف",
        "conj." to "حرف عطف",
        "interjection" to "أداة تعجب",
        "interj." to "أداة تعجب",
        "determiner" to "أداة تحديد",
        "det." to "أداة تحديد",
        "article" to "أداة تعريف",
        "particle" to "أداة",
        "numeral" to "عدد",
        "phrase" to "عبارة",
        "phr." to "عبارة",
        "idiom" to "تعبير اصطلاحي",
        "compound noun" to "اسم مركب",
        "phrasal verb" to "فعل مركب",
        "word" to "كلمة",
        "proper noun" to "اسم علم",

        // Word forms / inflection labels
        "plural" to "الجمع",
        "pl." to "الجمع",
        "past tense" to "الماضي",
        "pa. t." to "الماضي",
        "past participle" to "التصريف الثالث",
        "pa. pple." to "التصريف الثالث",
        "present participle" to "اسم الفاعل",
        "pres. pple." to "اسم الفاعل",
        "3rd person singular" to "المفرد الغائب",
        "3rd sg." to "المفرد الغائب",
        "comparative" to "صيغة المقارنة",
        "comp." to "صيغة المقارنة",
        "superlative" to "صيغة التفضيل",
        "sup." to "صيغة التفضيل",
        "singular" to "المفرد",
        "feminine" to "المؤنث",
        "masculine" to "المذكر",
        "dual" to "المثنى",
        "masdar" to "المصدر",
        "active participle" to "اسم الفاعل",
        "passive participle" to "اسم المفعول",
        "root" to "الجذر",

        // Categories & Colloquial sections (Item 2 & 4 & 5)
        "compound adjective" to "صفة مركبة",
        "compound noun" to "اسم مركب",
        "compound verb" to "فعل مركب",
        "proverb" to "مثل شعبي",
        "prefix" to "سابقة",
        "suffix" to "لاحقة",
        "abbreviation" to "اختصار",
        "slang" to "عامية",
        "informal" to "غير رسمي",
        "colloquial arabic" to "العامية المصرية والعربية",
        "informal / slang" to "عامية وغير رسمي",
        "other translations" to "ترجمات أخرى",
        "sentence words" to "كلمات الجملة"
    )

    fun arabicFor(englishLabel: String): String {
        val cleaned = englishLabel.trim()
        if (cleaned.isEmpty()) return ""
        val exact = enToArMap[cleaned.lowercase(Locale.US)]
        if (exact != null) return exact
        val strippedCount = cleaned.replace(Regex("\\s*\\(\\d+\\)$"), "").trim()
        return enToArMap[strippedCount.lowercase(Locale.US)] ?: ""
    }

    fun posArabic(posTag: String): String {
        val lower = posTag.trim().lowercase(Locale.US)
        return enToArMap[lower] ?: when {
            lower.contains("noun") -> "اسم"
            lower.contains("verb") -> "فعل"
            lower.contains("adj") -> "صفة"
            lower.contains("adv") -> "ظرف"
            lower.contains("prep") -> "حرف جر"
            lower.contains("pron") -> "ضمير"
            lower.contains("conj") -> "حرف عطف"
            lower.contains("interj") -> "أداة تعجب"
            lower.contains("det") -> "أداة تحديد"
            lower.contains("idiom") -> "تعبير اصطلاحي"
            lower.contains("phrase") -> "عبارة"
            else -> "كلمة"
        }
    }
}
