package com.example

import com.example.core.ArabicTextUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArabicAndUsageUtilsTest {

    @Test
    fun normalizeArabicForMatching_stripsTashkeelTatweelAndUnifiesHamza() {
        val raw = "أَحْـمَـدُ"
        assertEquals("احمد", ArabicTextUtils.normalizeArabicForMatching(raw))
        assertEquals("أحمد", ArabicTextUtils.stripArabicDiacriticsKeepHamza(raw))
        assertTrue(ArabicTextUtils.containsArabicLetters(raw))
        assertFalse(ArabicTextUtils.containsArabicLetters("resilient"))
    }

    @Test
    fun extractWordTokens_splitsSentenceAndStripsPunctuation() {
        val en = ArabicTextUtils.extractWordTokens("Look after the garden, quickly! Look?")
        assertEquals(listOf("Look", "after", "the", "garden", "quickly"), en)

        val ar = ArabicTextUtils.extractWordTokens("العلم نور، والجهل ظلام!")
        assertEquals(listOf("العلم", "نور", "والجهل", "ظلام"), ar)
    }

    @Test
    fun extractUsageFromWiktionaryHtml_extractsExplicitLabelsOnly() {
        val html = "<span class=\"usage-label-sense\">(<span class=\"ib-content\">formal, US</span>)</span> To inspect carefully."
        val info = ArabicTextUtils.extractUsageFromWiktionaryHtml(
            defHtml = html,
            cleanPlainDef = "(formal, US) To inspect carefully."
        )
        assertEquals(listOf("رسمي", "أمريكي"), info.usageLabels)
        assertNull(info.spellingVariantLabel)
    }

    @Test
    fun extractUsageFromPlainDefinition_extractsSpellingVariant() {
        val info = ArabicTextUtils.extractUsageFromPlainDefinition("US spelling of colour.")
        assertEquals("إملاء أمريكي", info.spellingVariantLabel)
        assertEquals("colour", info.otherSpellingWord)

        val noGuess = ArabicTextUtils.extractUsageFromPlainDefinition("A very common word used by friends.")
        assertTrue(noGuess.usageLabels.isEmpty())
        assertNull(noGuess.spellingVariantLabel)
    }
}
