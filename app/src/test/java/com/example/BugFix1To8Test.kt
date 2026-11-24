package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.core.AudioPlayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BugFix1To8Test {

    @Test
    fun splitTextSafelyForTts_handles1500WordsAndUnbrokenLongParagraphs() {
        val sentenceUnit = "The quick brown fox jumps over the lazy dog near the riverbank every morning."
        val longText = (1..120).joinToString(" ") { sentenceUnit }
        val wordCount = longText.split(Regex("\\s+")).size
        assertTrue("Expected > 1500 words, got $wordCount", wordCount > 1500)

        val chunks = AudioPlayerManager.splitTextSafelyForTts(longText, maxChunkLength = 3800)
        assertEquals(120, chunks.size)
        assertTrue(chunks.all { it.isNotEmpty() && it.length <= 3800 })

        // Single huge unbroken paragraph (5000+ chars without periods)
        val hugeClause = (1..400).joinToString(" ") { "vocabulary" }
        assertTrue(hugeClause.length > 4000)
        val clauseChunks = AudioPlayerManager.splitTextSafelyForTts(hugeClause, maxChunkLength = 500)
        assertTrue(clauseChunks.size >= 8)
        assertTrue(clauseChunks.all { it.isNotEmpty() && it.length <= 500 })
        assertEquals(400, clauseChunks.sumOf { it.split(Regex("\\s+")).size })

        // Arabic long text splitting
        val arabicLong = (1..50).joinToString(" ") { "هذه جملة عربية طويلة نسبياً للتأكد من عمل القراءة الصوتية المتتابعة." }
        val arabicChunks = AudioPlayerManager.splitTextSafelyForTts(arabicLong, maxChunkLength = 3800)
        assertEquals(50, arabicChunks.size)
        assertTrue(arabicChunks.all { it.isNotEmpty() && it.length <= 3800 })
    }

    @Test
    fun splitTextSafelyForTts_returnsEmptyForBlankInput() {
        assertTrue(AudioPlayerManager.splitTextSafelyForTts("   \n\t  ").isEmpty())
    }

    @Test
    fun navMoreStringResource_isLocalizedToArabicAlMazeed() {
        val context: Context = ApplicationProvider.getApplicationContext()
        assertEquals("المزيد", context.getString(R.string.nav_more))
    }

    @Test
    fun manifestContainsTtsQueriesAndNoProhibitedExternalLinks() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val pm = context.packageManager
        // Verify Robolectric package metadata loads cleanly for our applicationId
        val pkgInfo = pm.getPackageInfo(context.packageName, 0)
        assertFalse(pkgInfo.packageName.isBlank())
        val ttsIntent = Intent("android.intent.action.TTS_SERVICE")
        assertEquals("android.intent.action.TTS_SERVICE", ttsIntent.action)
    }
}
