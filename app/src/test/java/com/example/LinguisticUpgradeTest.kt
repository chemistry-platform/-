package com.example

import com.example.core.ArabicTextUtils
import com.example.core.ContextualTranslationAnalyzer
import com.example.core.LinguisticRanker
import com.example.data.repository.WiktionaryHtmlParser
import com.example.domain.model.SenseItem
import com.example.domain.model.SenseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LinguisticUpgradeTest {

    @Test
    fun contextualAnalyzer_disambiguatesRunningACompanyAndFixesLiteralArabic() {
        val prepared = ContextualTranslationAnalyzer.prepareEnglishForTranslation(
            "This man is running a company."
        )
        assertEquals("This man is managing a company.", prepared)

        val fixed1 = ContextualTranslationAnalyzer.refineArabicTranslation(
            sourceEnglish = "This man is running a company.",
            rawArabic = "هذا الرجل يجري شركة."
        )
        assertTrue(fixed1.contains("يدير شركة"))

        val fixed2 = ContextualTranslationAnalyzer.refineArabicTranslation(
            sourceEnglish = "This man is running a company.",
            rawArabic = "يجري هذا الرجل شركة"
        )
        assertTrue(fixed2.startsWith("يدير "))
    }

    @Test
    fun contextualAnalyzer_handlesMultipleContextualDomainsAndIdioms() {
        assertEquals(
            "He is executing a program.",
            ContextualTranslationAnalyzer.prepareEnglishForTranslation("He is running a program.")
        )
        assertEquals(
            "She is campaigning for president.",
            ContextualTranslationAnalyzer.prepareEnglishForTranslation("She is running for president.")
        )
        assertEquals(
            "The river is flowing fast.",
            ContextualTranslationAnalyzer.prepareEnglishForTranslation("The river is running fast.")
        )
        assertEquals(
            "We lacked enough time.",
            ContextualTranslationAnalyzer.prepareEnglishForTranslation("We ran out of time.")
        )
        assertEquals(
            "الأفعال أبلغ من الأقوال.",
            ContextualTranslationAnalyzer.findDirectContextualOverride("Actions speak louder than words.", "en", "ar")
        )
        assertEquals(
            "In full swing.",
            ContextualTranslationAnalyzer.findDirectContextualOverride("على قدم وساق", "ar", "en")
        )
    }

    @Test
    fun contextualAnalyzer_detectsInflectedEnglishAndArabicIdiomsInSentences() {
        val enDetected = ContextualTranslationAnalyzer.detectKnownIdiomsInSentence(
            sentence = "Firefighters sprang into action as soon as we ran out of time.",
            isArabic = false
        )
        val phrases = enDetected.map { it.phrase }
        assertTrue("spring into action" in phrases)
        assertTrue("run out of" in phrases)
        assertTrue(enDetected.first { it.phrase == "run out of" }.isPhrasalVerb)
        assertFalse(enDetected.first { it.phrase == "spring into action" }.isPhrasalVerb)

        val arDetected = ContextualTranslationAnalyzer.detectKnownIdiomsInSentence(
            sentence = "التحضيرات تجري على قدم وساق منذ الصباح.",
            isArabic = true
        )
        assertEquals(listOf("على قدم وساق"), arDetected.map { it.phrase })
    }

    @Test
    fun linguisticRanker_deduplicatesMergesAndRanksEverydayBeforeTechnicalAndArchaic() {
        val raw = listOf(
            SenseItem(
                index = 1,
                partOfSpeech = "verb",
                partOfSpeechArabic = "فعل",
                definition = "(archaic) To flow slowly as a liquid.",
                usageLabels = listOf("قديم"),
                source = SenseSource.WIKTIONARY
            ),
            SenseItem(
                index = 2,
                partOfSpeech = "verb",
                partOfSpeechArabic = "فعل",
                definition = "(computing) To execute a computer program.",
                usageLabels = listOf("حوسبة"),
                source = SenseSource.WIKTIONARY
            ),
            SenseItem(
                index = 3,
                partOfSpeech = "verb",
                partOfSpeechArabic = "فعل",
                definition = "To move swiftly on foot.",
                definitionArabic = null,
                examples = listOf("Children run across the field."),
                source = SenseSource.WORDNET
            ),
            SenseItem(
                index = 4,
                partOfSpeech = "verb",
                partOfSpeechArabic = "فعل",
                definition = "To move swiftly on foot",
                definitionArabic = "يَرْكُضُ؛ يَجْرِي",
                definitionArabicIsMachine = false,
                synonyms = listOf("sprint"),
                source = SenseSource.FREE_DICTIONARY
            ),
            SenseItem(
                index = 5,
                partOfSpeech = "noun",
                partOfSpeechArabic = "اسم",
                definition = "An act of running quickly.",
                source = SenseSource.FREE_DICTIONARY
            )
        )

        val ranked = LinguisticRanker.dedupAndRankSenses(raw)
        val verbs = ranked.filter { it.partOfSpeech == "verb" }
        assertEquals(3, verbs.size)
        // Everyday core sense ranked first & merged curated Arabic + example + synonym
        assertEquals("To move swiftly on foot", verbs[0].definition)
        assertEquals("يَرْكُضُ؛ يَجْرِي", verbs[0].definitionArabic)
        assertFalse(verbs[0].definitionArabicIsMachine)
        assertTrue("Children run across the field." in verbs[0].examples)
        assertTrue("sprint" in verbs[0].synonyms)
        // Technical sense before archaic sense
        assertTrue(verbs[1].isTechnical)
        assertTrue("قديم" in verbs[2].usageLabels)

        val groups = LinguisticRanker.groupAndNumberPosGroups(ranked) { pos ->
            when (pos) {
                "verb" -> "فعل"
                "noun" -> "اسم"
                else -> pos
            }
        }
        assertEquals(listOf("verb", "noun"), groups.map { it.partOfSpeech })
        assertEquals(listOf(1, 2, 3), groups[0].senses.map { it.index })
    }

    @Test
    fun linguisticRanker_preservesDistinctArabicVocalizedVariantsAndClassifiesPhrases() {
        val senses = listOf(
            SenseItem(
                index = 1,
                partOfSpeech = "adjective",
                partOfSpeechArabic = "صفة",
                definition = "مُرّ",
                definitionArabic = "bitter",
                headwordVariant = "مُرّ",
                source = SenseSource.WIKTIONARY
            ),
            SenseItem(
                index = 2,
                partOfSpeech = "adjective",
                partOfSpeechArabic = "صفة",
                definition = "مَرّ",
                definitionArabic = "bitter",
                headwordVariant = "مَرّ",
                source = SenseSource.WIKTIONARY
            )
        )
        val deduped = LinguisticRanker.dedupAndRankSenses(senses)
        assertEquals(2, deduped.size)

        assertEquals("PHRASAL_VERB", LinguisticRanker.classifyEnglishPhrase("run out of", "run"))
        assertEquals("PHRASAL_VERB", LinguisticRanker.classifyEnglishPhrase("run into", "run"))
        assertEquals("IDIOM", LinguisticRanker.classifyEnglishPhrase("in the long run", "run"))
        assertEquals("COMPOUND", LinguisticRanker.classifyEnglishPhrase("dry run", "run"))
        assertNotNull(LinguisticRanker.inferGrammarStructure("run out of", "PHRASAL_VERB"))
    }

    @Test
    fun wiktionaryHtmlParser_extractsVocalizedTranslationsAndMultiVocalizedArabicEntries() {
        val translationsHtml = """
            <div class="NavFrame">
              <div class="NavHead">to move swiftly on foot</div>
              <div class="NavContent">
                <ul>
                  <li>Arabic: <span class="Arab" lang="ar">رَكَضَ</span>, <span class="Arab" lang="ar">جَرَى</span></li>
                  <li>Persian: <span class="fa-Arab" lang="fa">دویدن</span></li>
                </ul>
              </div>
            </div>
            <div class="NavFrame">
              <div class="NavHead">to control or manage a business</div>
              <div class="NavContent">
                <ul>
                  <li>Arabic: <span class="Arab" lang="ar">أَدَارَ</span>, <span class="Arab" lang="ar">يُدِيرُ</span></li>
                </ul>
              </div>
            </div>
        """.trimIndent()

        val glosses = WiktionaryHtmlParser.parseEnglishTranslationsHtml(translationsHtml)
        assertEquals(2, glosses.size)
        assertEquals("رَكَضَ؛ جَرَى", WiktionaryHtmlParser.matchBestArabicTranslation("To move swiftly on foot", glosses))
        assertEquals("أَدَارَ؛ يُدِيرُ", WiktionaryHtmlParser.matchBestArabicTranslation("To manage a business", glosses))

        val arabicPageHtml = """
            <h2><span id="Arabic">Arabic</span></h2>
            <h3>Etymology 1</h3>
            <h4>Adjective</h4>
            <p><strong class="Arab headword" lang="ar">مُرّ</strong> (<span class="tr">murr</span>)</p>
            <ol>
              <li>bitter (taste)
                <div class="h-usage-example"><span class="Arab e-example" lang="ar">شَرِبْتُ قَهْوَةً مُرَّةً</span> <span class="e-translation">I drank bitter coffee.</span></div>
              </li>
            </ol>
            <h5>Antonyms</h5>
            <ul><li><span class="Arab" lang="ar">حُلْو</span></li></ul>
            <h3>Etymology 2</h3>
            <h4>Verb</h4>
            <p><strong class="Arab headword" lang="ar">مَرَّ</strong> (<span class="tr">marra</span>)</p>
            <ol>
              <li>to pass by, go past</li>
            </ol>
            <h5>Synonyms</h5>
            <ul><li><span class="Arab" lang="ar">عَبَرَ</span></li></ul>
        """.trimIndent()

        val parsedAr = WiktionaryHtmlParser.parseArabicSectionFromPageHtml(arabicPageHtml, "مر")
        val canonicalMarra = ArabicTextUtils.canonicalizeArabicWithDiacritics("مَرَّ")
        assertEquals(listOf("مُرّ", canonicalMarra), parsedAr.vocalizedHeadwords)
        assertEquals("murr", parsedAr.transliteration)
        assertEquals(2, parsedAr.senses.size)
        assertEquals("مُرّ", parsedAr.senses[0].definition)
        assertEquals("bitter (taste)", parsedAr.senses[0].definitionArabic)
        assertEquals(listOf("شَرِبْتُ قَهْوَةً مُرَّةً"), parsedAr.senses[0].examples)
        assertEquals(canonicalMarra, parsedAr.senses[1].definition)
        assertTrue("حُلْو" in parsedAr.antonyms)
        assertTrue("عَبَرَ" in parsedAr.synonyms)
    }

    @Test
    fun arabicTextUtils_preservesAndCanonicalizesTashkeelOrder() {
        assertTrue(ArabicTextUtils.hasArabicDiacritics("مُرّ"))
        assertFalse(ArabicTextUtils.hasArabicDiacritics("مر"))
        // Shadda + fatha vs fatha + shadda normalize identically
        val orderA = "مَر\u0651\u064E"
        val orderB = "مَر\u064E\u0651"
        assertEquals(
            ArabicTextUtils.canonicalizeArabicWithDiacritics(orderA),
            ArabicTextUtils.canonicalizeArabicWithDiacritics(orderB)
        )
        assertNotEquals(
            ArabicTextUtils.canonicalizeArabicWithDiacritics("مُرّ"),
            ArabicTextUtils.canonicalizeArabicWithDiacritics("مَرَّ")
        )
    }
}
