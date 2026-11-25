package com.example.core

import com.example.domain.model.PosSenseGroup
import com.example.domain.model.SenseItem
import com.example.domain.model.SenseSource
import com.example.domain.model.WordEntry
import java.util.Locale

data class PosHeadwordMeanings(
    val partOfSpeech: String,
    val partOfSpeechArabic: String,
    val posBadgeLabel: String,
    val meanings: List<String>,
    val isMachineOnly: Boolean
)

/**
 * Prioritizes, deduplicates, classifies, and enriches dictionary senses and related expressions
 * according to linguistic usefulness:
 *  1. Core/fundamental everyday meanings
 *  2. Common contextual meanings
 *  3. Figurative / idiomatic meanings
 *  4. Specialized / technical meanings
 *  5. Archaic / obsolete / rare meanings
 */
object LinguisticRanker {

    private val POS_PRIORITY = mapOf(
        "verb" to 1,
        "noun" to 2,
        "adjective" to 3,
        "adverb" to 4,
        "preposition" to 5,
        "pronoun" to 6,
        "conjunction" to 7,
        "interjection" to 8,
        "determiner" to 9,
        "particle" to 10,
        "numeral" to 11,
        "idiom" to 12,
        "phrase" to 13,
        "proverb" to 14
    )

    private val STOP_WORDS_FOR_SIMILARITY = setOf(
        "a", "an", "the", "to", "of", "in", "on", "at", "for", "with", "by", "from",
        "as", "or", "and", "something", "someone", "somebody", "anything", "one"
    )

    private val PHRASAL_PARTICLES = setOf(
        "at", "after", "for", "up", "down", "out", "in", "on", "off", "over",
        "into", "with", "about", "through", "back", "away", "around", "by", "to",
        "from", "of", "against", "along", "across", "behind", "between", "beyond",
        "under", "onto", "towards", "within", "without", "ahead", "apart", "aside"
    )

    private val TECHNICAL_LABELS = setOf(
        "فني", "حوسبة", "أعمال ومالية", "قانون", "طب", "رياضة", "نحو ولغة", "رياضيات", "بحري"
    )

    private val FIGURATIVE_LABELS = setOf("مجازي", "اصطلاحي")
    private val ARCHAIC_OR_RARE_LABELS = setOf("قديم", "نادر", "مسيء", "بذيء")

    fun posPriority(pos: String): Int =
        POS_PRIORITY[pos.trim().lowercase(Locale.US)] ?: 50

    /**
     * Deduplicates similar definitions within the same POS while merging examples, synonyms,
     * antonyms, labels, and curated Arabic meanings, then ranks senses by practical utility.
     */
    fun dedupAndRankSenses(raw: List<SenseItem>): List<SenseItem> {
        if (raw.isEmpty()) return emptyList()

        val groupedByPos = LinkedHashMap<String, MutableList<SenseItem>>()
        for (sense in raw) {
            val cleanDef = sense.definition.replace(Regex("\\s+"), " ").trim()
            if (cleanDef.length < 3) continue
            val enriched = enrichSenseMetadata(sense.copy(definition = cleanDef))
            val posKey = enriched.partOfSpeech.lowercase(Locale.US).ifBlank { "word" }
            val bucket = groupedByPos.getOrPut(posKey) { ArrayList() }

            val matchIdx = findDuplicateIndex(bucket, enriched)
            if (matchIdx >= 0) {
                bucket[matchIdx] = mergeTwoSenses(bucket[matchIdx], enriched)
            } else {
                bucket.add(enriched)
            }
        }

        val sortedGroups = groupedByPos.entries.sortedBy { posPriority(it.key) }
        val result = ArrayList<SenseItem>(raw.size)
        for ((_, list) in sortedGroups) {
            val ranked = list.sortedByDescending { scoreSense(it) }
            result.addAll(ranked)
        }
        return result
    }

    fun groupAndNumberPosGroups(
        senses: List<SenseItem>,
        posArabicMapper: (String) -> String
    ): List<PosSenseGroup> {
        if (senses.isEmpty()) return emptyList()
        return senses
            .groupBy { it.partOfSpeech.lowercase(Locale.US).ifBlank { "word" } }
            .entries
            .sortedBy { posPriority(it.key) }
            .map { (posKey, list) ->
                val renumbered = list.mapIndexed { idx, s -> s.copy(index = idx + 1) }
                PosSenseGroup(
                    partOfSpeech = posKey,
                    partOfSpeechArabic = posArabicMapper(posKey),
                    senses = renumbered
                )
            }
    }

    private fun enrichSenseMetadata(sense: SenseItem): SenseItem {
        val labels = LinkedHashSet(sense.usageLabels)
        val defLower = sense.definition.lowercase(Locale.US)

        // Check explicit parenthetical qualifiers inside the definition itself (not guessing prose)
        val leadingParen = Regex("^\\(([^)]{1,64})\\)\\s+").find(sense.definition)
        if (leadingParen != null) {
            val inner = leadingParen.groupValues[1].lowercase(Locale.US)
            if (inner.contains("figurative")) labels.add("مجازي")
            if (inner.contains("idiomatic")) labels.add("اصطلاحي")
            if (inner.contains("comput") || inner.contains("software") || inner.contains("programming")) labels.add("حوسبة")
            if (inner.contains("finance") || inner.contains("business") || inner.contains("economics")) labels.add("أعمال ومالية")
            if (inner.contains("law") || inner.contains("legal")) labels.add("قانون")
            if (inner.contains("med") || inner.contains("anatomy")) labels.add("طب")
            if (inner.contains("sport") || inner.contains("baseball") || inner.contains("cricket")) labels.add("رياضة")
            if (inner.contains("grammar")) labels.add("نحو ولغة")
            if (inner.contains("archaic") || inner.contains("obsolete") || inner.contains("dated")) labels.add("قديم")
        }

        val isFig = sense.isFigurative || labels.any { it in FIGURATIVE_LABELS } ||
            defLower.startsWith("(figuratively)") || defLower.startsWith("(figurative)")
        val isTech = sense.isTechnical || labels.any { it in TECHNICAL_LABELS }

        val note = sense.usageNote?.takeIf { it.isNotBlank() } ?: buildUsageNoteFromLabels(
            labels = labels,
            isFigurative = isFig,
            isTechnical = isTech
        )

        return sense.copy(
            usageLabels = labels.toList(),
            isFigurative = isFig,
            isTechnical = isTech,
            usageNote = note
        )
    }

    private fun buildUsageNoteFromLabels(
        labels: Set<String>,
        isFigurative: Boolean,
        isTechnical: Boolean
    ): String? {
        val techDomains = labels.filter { it in TECHNICAL_LABELS }
        return when {
            isFigurative && techDomains.isNotEmpty() -> "استخدام مجازي ضمن مجال: ${techDomains.joinToString("، ")}"
            techDomains.isNotEmpty() -> "مصطلح متخصص (${techDomains.joinToString("، ")})"
            isFigurative -> "معنى مجازي أو اصطلاحي يختلف عن المعنى الحرفي"
            "قديم" in labels -> "استخدام قديم أو قليل الشيوع حالياً"
            "عامية" in labels -> "تعبير عامي شائع في المحادثات غير الرسمية"
            else -> null
        }
    }

    private fun findDuplicateIndex(existing: List<SenseItem>, candidate: SenseItem): Int {
        val candTokens = contentTokens(candidate.definition)
        val candNorm = normalizeDefForDedup(candidate.definition)
        for (i in existing.indices) {
            val prev = existing[i]
            // Never merge two different vocalized Arabic headwords!
            if (!prev.headwordVariant.isNullOrBlank() &&
                !candidate.headwordVariant.isNullOrBlank() &&
                prev.headwordVariant != candidate.headwordVariant
            ) {
                continue
            }
            val prevNorm = normalizeDefForDedup(prev.definition)
            if (prevNorm == candNorm) return i
            if (candTokens.size >= 3 && prevNorm.isNotBlank()) {
                val prevTokens = contentTokens(prev.definition)
                if (prevTokens.size >= 3) {
                    val intersection = candTokens.count { it in prevTokens }
                    val union = (candTokens + prevTokens).size
                    if (union > 0 && intersection.toFloat() / union.toFloat() >= 0.80f) {
                        return i
                    }
                }
            }
        }
        return -1
    }

    private fun mergeTwoSenses(primary: SenseItem, secondary: SenseItem): SenseItem {
        val keepPrimaryDef = sourcePreference(primary.source) >= sourcePreference(secondary.source)
        val bestArabic = when {
            !primary.definitionArabic.isNullOrBlank() && !primary.definitionArabicIsMachine -> primary.definitionArabic
            !secondary.definitionArabic.isNullOrBlank() && !secondary.definitionArabicIsMachine -> secondary.definitionArabic
            !primary.definitionArabic.isNullOrBlank() -> primary.definitionArabic
            else -> secondary.definitionArabic
        }
        val bestArabicIsMachine = when {
            !primary.definitionArabic.isNullOrBlank() && !primary.definitionArabicIsMachine -> false
            !secondary.definitionArabic.isNullOrBlank() && !secondary.definitionArabicIsMachine -> false
            bestArabic.isNullOrBlank() -> false
            else -> primary.definitionArabicIsMachine && secondary.definitionArabicIsMachine
        }

        val mergedExamples = (primary.examples + secondary.examples).distinct().take(3)
        val mergedExTrans = primary.exampleTranslations + secondary.exampleTranslations
        val mergedExIsMachine = primary.exampleIsMachine + secondary.exampleIsMachine
        val mergedExAudio = primary.exampleAudioUrls + secondary.exampleAudioUrls
        val mergedSyns = (primary.synonyms + secondary.synonyms).distinct().take(10)
        val mergedAnts = (primary.antonyms + secondary.antonyms).distinct().take(10)
        val mergedLabels = (primary.usageLabels + secondary.usageLabels).distinct()

        return primary.copy(
            definition = if (keepPrimaryDef) primary.definition else secondary.definition,
            definitionArabic = bestArabic,
            definitionArabicIsMachine = bestArabicIsMachine,
            examples = mergedExamples,
            exampleTranslations = mergedExTrans,
            exampleIsMachine = mergedExIsMachine,
            exampleAudioUrls = mergedExAudio,
            synonyms = mergedSyns,
            antonyms = mergedAnts,
            usageLabels = mergedLabels,
            spellingVariantLabel = primary.spellingVariantLabel ?: secondary.spellingVariantLabel,
            otherSpellingWord = primary.otherSpellingWord ?: secondary.otherSpellingWord,
            usageNote = primary.usageNote ?: secondary.usageNote,
            headwordVariant = primary.headwordVariant ?: secondary.headwordVariant,
            isFigurative = primary.isFigurative || secondary.isFigurative,
            isTechnical = primary.isTechnical || secondary.isTechnical
        )
    }

    private fun scoreSense(sense: SenseItem): Int {
        var score = 100
        // Source weight: curated human-backed dictionary sources score higher
        score += when (sense.source) {
            SenseSource.OFFLINE_PACK -> 35
            SenseSource.FREE_DICTIONARY -> 28
            SenseSource.WIKTIONARY -> 24
            SenseSource.WORDNET -> 16
        }
        // Curated Arabic translation boosts fundamental/core senses
        if (!sense.definitionArabic.isNullOrBlank() && !sense.definitionArabicIsMachine) {
            score += 22
        }
        // Examples and synonyms indicate well-established everyday senses
        if (sense.examples.isNotEmpty()) score += 10
        if (sense.synonyms.isNotEmpty()) score += 6

        // Ranking order: everyday (0 penalty) > contextual > figurative (-18) > technical (-28) > archaic/rare (-60)
        if (sense.isFigurative) score -= 18
        if (sense.isTechnical) score -= 28
        if (sense.usageLabels.any { it in ARCHAIC_OR_RARE_LABELS }) score -= 60
        if (sense.spellingVariantLabel != null) score -= 50

        // Penalize very long esoteric glosses or cross-references ("Alternative form of...")
        val lower = sense.definition.lowercase(Locale.US)
        if (lower.startsWith("alternative form of") ||
            lower.startsWith("alternative spelling of") ||
            lower.startsWith("archaic ") ||
            lower.startsWith("obsolete ") ||
            lower.startsWith("eye dialect")
        ) {
            score -= 65
        }
        if (sense.definition.length in 10..110) score += 6
        return score
    }

    private fun sourcePreference(src: SenseSource): Int = when (src) {
        SenseSource.FREE_DICTIONARY -> 4
        SenseSource.OFFLINE_PACK -> 3
        SenseSource.WIKTIONARY -> 2
        SenseSource.WORDNET -> 1
    }

    private fun normalizeDefForDedup(def: String): String {
        return def.lowercase(Locale.US)
            .replace(Regex("^\\([^)]{1,48}\\)\\s*"), "")
            .replace(Regex("[^a-z0-9\\u0621-\\u064A\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun contentTokens(def: String): Set<String> {
        return normalizeDefForDedup(def)
            .split(' ')
            .filter { it.length >= 3 && it !in STOP_WORDS_FOR_SIMILARITY }
            .toSet()
    }

    /**
     * Classifies a multi-word English phrase as PHRASAL_VERB, IDIOM, or COMPOUND.
     */
    fun classifyEnglishPhrase(
        phrase: String,
        headword: String,
        explicitPhrasals: Set<String> = emptySet(),
        explicitIdioms: Set<String> = emptySet()
    ): String {
        val lower = phrase.trim().lowercase(Locale.US)
        val headLower = headword.trim().lowercase(Locale.US)
        if (lower in explicitIdioms) return "IDIOM"
        if (lower in explicitPhrasals) return "PHRASAL_VERB"

        val words = lower.split(Regex("\\s+"))
        if (words.size in 2..3 && words.first() == headLower && words.drop(1).all { it in PHRASAL_PARTICLES }) {
            return "PHRASAL_VERB"
        }
        if (words.size >= 3 && words.first() == headLower && words.getOrNull(1) in PHRASAL_PARTICLES) {
            return "PHRASAL_VERB"
        }
        // Verb + particle pattern even when headword differs slightly (or adverb + particle)
        if (words.size in 2..3 && words.last() in PHRASAL_PARTICLES && words.size == 2) {
            return "PHRASAL_VERB"
        }
        // Longer figurative phrases/sayings are idioms
        if (words.size >= 4) return "IDIOM"
        return "COMPOUND"
    }

    /**
     * Produces a clear grammatical structure hint for phrasal verbs (e.g., "run out of + [noun]").
     */
    fun inferGrammarStructure(phrase: String, category: String): String? {
        if (category != "PHRASAL_VERB") return null
        val lower = phrase.trim().lowercase(Locale.US)
        val words = lower.split(Regex("\\s+"))
        if (words.size < 2) return null
        return when {
            words.size >= 3 && words.last() in setOf("of", "to", "with", "for", "from", "on", "in", "into", "about") ->
                "$lower + [اسم / ضمير]"
            words.last() in setOf("into", "across", "after", "against", "through") ->
                "$lower + [شخص / شيء]"
            else -> "$lower (فعل مركب)"
        }
    }

    fun formatPosBadgeLabel(partOfSpeech: String, partOfSpeechArabic: String): String {
        val posKey = partOfSpeech.trim().lowercase(Locale.US)
        val englishLabel = when (posKey) {
            "verb" -> "Verb"
            "noun" -> "Noun"
            "adjective" -> "Adjective"
            "adverb" -> "Adverb"
            "preposition" -> "Preposition"
            "pronoun" -> "Pronoun"
            "conjunction" -> "Conjunction"
            "interjection", "exclamation" -> "Interjection"
            "determiner" -> "Determiner"
            "particle" -> "Particle"
            "numeral" -> "Numeral"
            "idiom" -> "Idiom"
            "phrase" -> "Phrase"
            "proverb" -> "Proverb"
            "word" -> "Word"
            else -> posKey.replaceFirstChar { it.titlecase(Locale.US) }
        }
        val arabicLabel = partOfSpeechArabic.trim().ifBlank {
            when (posKey) {
                "verb" -> "فعل"
                "noun" -> "اسم"
                "adjective" -> "صفة"
                "adverb" -> "ظرف"
                "preposition" -> "حرف جر"
                "pronoun" -> "ضمير"
                "conjunction" -> "أداة ربط"
                "interjection", "exclamation" -> "صيغة تعجب"
                "determiner" -> "محدد"
                "particle" -> "أداة"
                "numeral" -> "عدد"
                "idiom" -> "تعبير اصطلاحي"
                "phrase" -> "عبارة"
                "proverb" -> "مَثَل"
                else -> "كلمة"
            }
        }
        return when {
            englishLabel.isBlank() -> arabicLabel
            arabicLabel.equals(englishLabel, ignoreCase = true) -> englishLabel
            else -> "$arabicLabel • $englishLabel"
        }
    }

    /**
     * Builds POS-grouped quick meanings for the top HeadwordCard (both EN->AR and AR->EN).
     * Combines sense meanings per grammatical role and merges unique machine translations
     * into the primary POS group without disturbing lower PosGroupCard sections.
     */
    fun extractPosHeadwordMeanings(
        entry: WordEntry,
        maxPerPos: Int = 8
    ): List<PosHeadwordMeanings> {
        if (entry.posGroups.isEmpty() || maxPerPos <= 0) return emptyList()
        val isTargetArabic = !entry.isArabicWord

        data class MutablePosBucket(
            val partOfSpeech: String,
            val partOfSpeechArabic: String,
            val keyToValue: LinkedHashMap<String, String> = LinkedHashMap(),
            var hasHuman: Boolean = false,
            var hasMachine: Boolean = false
        )

        val buckets = ArrayList<MutablePosBucket>(entry.posGroups.size)

        for (group in entry.posGroups) {
            val bucket = MutablePosBucket(
                partOfSpeech = group.partOfSpeech,
                partOfSpeechArabic = group.partOfSpeechArabic
            )
            // First pass: human/dictionary meanings, second pass: machine-translated sense meanings
            val orderedSenses = group.senses.sortedBy { if (it.definitionArabicIsMachine) 1 else 0 }
            for (sense in orderedSenses) {
                val rawGloss = sense.definitionArabic?.trim().orEmpty()
                if (rawGloss.isEmpty()) continue
                val pieces = splitConciseGlosses(rawGloss, isTargetArabic)
                for (piece in pieces) {
                    val key = meaningDedupKey(piece, isTargetArabic)
                    if (key.isEmpty()) continue
                    val existing = bucket.keyToValue[key]
                    if (existing == null) {
                        if (bucket.keyToValue.size >= maxPerPos) break
                        bucket.keyToValue[key] = piece
                    } else if (isTargetArabic &&
                        !ArabicTextUtils.hasArabicDiacritics(existing) &&
                        ArabicTextUtils.hasArabicDiacritics(piece)
                    ) {
                        bucket.keyToValue[key] = piece
                    }
                    if (sense.definitionArabicIsMachine) {
                        bucket.hasMachine = true
                    } else {
                        bucket.hasHuman = true
                    }
                }
                if (bucket.keyToValue.size >= maxPerPos) break
            }
            buckets.add(bucket)
        }

        // Merge machine translations into primary POS group (preserving exact translation if not already present)
        val primaryBucket = buckets.firstOrNull { it.keyToValue.isNotEmpty() } ?: buckets.firstOrNull()
        if (primaryBucket != null && entry.machineTranslations.isNotEmpty()) {
            val allExistingKeys = buckets.flatMapTo(HashSet()) { it.keyToValue.keys }
            for (mt in entry.machineTranslations) {
                val pieces = splitConciseGlosses(mt, isTargetArabic)
                for (piece in pieces) {
                    val key = meaningDedupKey(piece, isTargetArabic)
                    if (key.isNotEmpty() && key !in allExistingKeys && primaryBucket.keyToValue.size < maxPerPos + 2) {
                        primaryBucket.keyToValue[key] = piece
                        allExistingKeys.add(key)
                        primaryBucket.hasMachine = true
                    }
                }
            }
        }

        return buckets.mapNotNull { b ->
            if (b.keyToValue.isEmpty()) return@mapNotNull null
            PosHeadwordMeanings(
                partOfSpeech = b.partOfSpeech,
                partOfSpeechArabic = b.partOfSpeechArabic,
                posBadgeLabel = formatPosBadgeLabel(b.partOfSpeech, b.partOfSpeechArabic),
                meanings = b.keyToValue.values.toList(),
                isMachineOnly = !b.hasHuman && b.hasMachine
            )
        }
    }

    private fun splitConciseGlosses(raw: String, isTargetArabic: Boolean): List<String> {
        val trimmed = raw.replace(Regex("\\s+"), " ").trim()
        if (trimmed.isEmpty()) return emptyList()
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var parenDepth = 0
        for (ch in trimmed) {
            when (ch) {
                '(', '（', '[' -> {
                    parenDepth++
                    current.append(ch)
                }
                ')', '）', ']' -> {
                    if (parenDepth > 0) parenDepth--
                    current.append(ch)
                }
                ';', '؛', '،' -> {
                    if (parenDepth == 0) {
                        val candidate = current.toString().trim().trim('.', ',', '،', ';', '؛')
                        if (candidate.length >= 2) parts.add(candidate)
                        current.setLength(0)
                    } else {
                        current.append(ch)
                    }
                }
                ',' -> {
                    if (parenDepth == 0 && !isTargetArabic) {
                        val candidate = current.toString().trim().trim('.', ',', '،', ';', '؛')
                        if (candidate.length >= 2) parts.add(candidate)
                        current.setLength(0)
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        val tail = current.toString().trim().trim('.', ',', '،', ';', '؛')
        if (tail.length >= 2) parts.add(tail)
        return parts
    }

    private fun meaningDedupKey(text: String, isTargetArabic: Boolean): String {
        val cleaned = text.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isEmpty()) return ""
        return if (isTargetArabic) {
            ArabicTextUtils.normalizeArabicForMatching(cleaned).lowercase(Locale.US)
        } else {
            cleaned.lowercase(Locale.US)
        }
    }
}
