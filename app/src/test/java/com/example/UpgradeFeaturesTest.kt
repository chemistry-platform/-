package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.Accent
import com.example.core.AudioPlayerManager
import com.example.core.CrashReporter
import com.example.core.TranslationManager
import com.example.data.remote.DatamuseWordDto
import com.example.data.remote.WikimediaCommonsApi
import com.example.data.repository.LexiconRepository
import com.example.domain.model.SenseSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpgradeFeaturesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CrashReporter.clearLastCrash(context)
    }

    @Test
    fun crashReporter_savesReadsAndClearsCrashReport() {
        assertNull(CrashReporter.readLastCrash(context))

        CrashReporter.saveCrash(context, RuntimeException("Boom on launch"), "main")
        val saved = CrashReporter.readLastCrash(context)
        assertTrue(saved != null && saved.contains("Boom on launch") && saved.contains("main"))

        CrashReporter.clearLastCrash(context)
        assertNull(CrashReporter.readLastCrash(context))
    }

    @Test
    fun wikimediaCommonsApi_parsesAudioAndNormalizesUrls() {
        assertTrue(WikimediaCommonsApi.buildCandidateTitles("test", Accent.US).contains("File:En-us-test.ogg"))
        assertTrue(WikimediaCommonsApi.buildCandidateTitles("test", Accent.UK).contains("File:En-gb-test.mp3"))

        val imageInfoJson = """
            {
              "query": {
                "pages": {
                  "-1": { "missing": "" },
                  "123": {
                    "imageinfo": [
                      { "url": "//upload.wikimedia.org/wikipedia/commons/a/ab/En-us-test.ogg" },
                      { "url": "http://upload.wikimedia.org/wikipedia/commons/a/ab/En-us-test.mp3" }
                    ]
                  }
                }
              }
            }
        """.trimIndent()
        val picked = WikimediaCommonsApi.parseImageInfoAudioUrl(imageInfoJson)
        assertEquals("https://upload.wikimedia.org/wikipedia/commons/a/ab/En-us-test.mp3", picked)

        val allImagesJson = """
            {
              "query": {
                "allimages": [
                  { "url": "http://upload.wikimedia.org/wikipedia/commons/1/11/En-uk-colour.oga" }
                ]
              }
            }
        """.trimIndent()
        assertEquals(
            "https://upload.wikimedia.org/wikipedia/commons/1/11/En-uk-colour.oga",
            WikimediaCommonsApi.parseAllImagesAudioUrl(allImagesJson)
        )
    }

    @Test
    fun lexiconRepository_parsesWordNetDefsFromDatamuse() {
        val dtos = listOf(
            DatamuseWordDto(
                word = "run",
                defs = listOf(
                    "v\tmove fast by using one's feet",
                    "n\tan act of running or a period of running",
                    "adj\t(US) continuous or unbroken"
                )
            )
        )
        val senses = LexiconRepository.parseDatamuseWordNetSenses(dtos)
        assertEquals(3, senses.size)
        assertEquals("verb", senses[0].partOfSpeech)
        assertEquals("فعل", senses[0].partOfSpeechArabic)
        assertEquals(SenseSource.WORDNET, senses[0].source)
        assertEquals("noun", senses[1].partOfSpeech)
        assertEquals("adjective", senses[2].partOfSpeech)
    }

    @Test
    fun translationManager_buildsContextWindowAndTokenizesLiteralText() {
        val sentences = listOf("I saw a bat.", "It flew over the bank.", "The river was wide.")
        val win = TranslationManager.buildContextWindow(sentences, 1)
        assertEquals(3, win.expectedLines)
        assertEquals(1, win.targetLineIndex)
        assertEquals("It flew over the bank.", TranslationManager.extractSentenceFromWindowTranslation("رأيت خفاشاً.\nطار فوق الضفة.\nكان النهر واسعاً.", 1, 3)?.let {
            TranslationManager.extractSentenceFromWindowTranslation(
                rawTranslated = "I saw a bat.\nIt flew over the bank.\nThe river was wide.",
                targetLineOffset = win.targetLineIndex,
                expectedLines = win.expectedLines
            )
        })

        val original = "Hello,\nworld!\n\nLine 3."
        val tokens = TranslationManager.tokenizeForLiteralTranslation(original)
        assertTrue(tokens.any { it.first == "Hello" && it.second })
        assertTrue(tokens.any { it.first == ",\n" && !it.second })
        assertTrue(tokens.any { it.first == "!\n\n" && !it.second })
        assertEquals(original, tokens.joinToString("") { it.first })
    }

    @Test
    fun audioPlayerManager_splitsLongTextForReaderHighlighting() {
        val longText = (1..250).joinToString(" ") { idx ->
            "Sentence number $idx has clear meaning."
        }
        val sentences = AudioPlayerManager.splitTextSafelyForTts(longText, maxChunkLength = 300)
        assertEquals(250, sentences.size)
        assertTrue(sentences.all { it.length <= 300 })
        assertFalse(sentences.any { it.isBlank() })
    }
}
