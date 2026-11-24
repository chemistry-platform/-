package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.Accent
import com.example.core.AudioSpeedOptions
import com.example.core.UserSettingsPreferences
import com.example.data.local.AppDatabase
import com.example.data.local.OfflinePackWordEntity
import com.example.data.local.TranslationCacheEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Section12And19Test {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("app_user_settings", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun audioSpeedOptions_sanitizesAndFormatsSupportedSpeeds() {
        assertEquals(0.75f, AudioSpeedOptions.sanitize(0.7f), 0.001f)
        assertEquals(1.0f, AudioSpeedOptions.sanitize(1.0f), 0.001f)
        assertEquals(1.25f, AudioSpeedOptions.sanitize(1.22f), 0.001f)
        assertEquals(1.5f, AudioSpeedOptions.sanitize(2.0f), 0.001f)

        assertEquals("0.75x", AudioSpeedOptions.formatLabel(0.75f))
        assertEquals("1x", AudioSpeedOptions.formatLabel(1.0f))
        assertEquals("1.25x", AudioSpeedOptions.formatLabel(1.25f))
        assertEquals("1.5x", AudioSpeedOptions.formatLabel(1.5f))
    }

    @Test
    fun userSettingsPreferences_hasRequiredDefaultsAndPersistsChanges() {
        val prefs = UserSettingsPreferences(context)
        assertEquals(1.0f, prefs.audioSpeed.value, 0.001f)
        assertEquals(Accent.US, prefs.sentenceAccent.value)
        assertTrue(prefs.showArabicTranslations.value)
        assertTrue(prefs.showMachineTranslationLabel.value)

        prefs.setAudioSpeed(1.25f)
        prefs.setSentenceAccent(Accent.UK)
        prefs.setShowArabicTranslations(false)
        prefs.setShowMachineTranslationLabel(false)

        val reloaded = UserSettingsPreferences(context)
        assertEquals(1.25f, reloaded.audioSpeed.value, 0.001f)
        assertEquals(Accent.UK, reloaded.sentenceAccent.value)
        assertFalse(reloaded.showArabicTranslations.value)
        assertFalse(reloaded.showMachineTranslationLabel.value)
    }

    @Test
    fun lexiconDao_filtersAwlWordsAndCachesTranslationsInRoom() = runBlocking {
        val dao = db.lexiconDao()
        dao.upsertOfflinePackBatch(
            listOf(
                OfflinePackWordEntity(
                    word = "analyze",
                    normalizedWord = "analyze",
                    lang = "en",
                    summaryDefinition = "Examine in detail",
                    tagsCsv = "AWL",
                    hasAwlTag = true,
                    payloadJson = """{"word":"analyze","tags":["AWL"]}"""
                ),
                OfflinePackWordEntity(
                    word = "apple",
                    normalizedWord = "apple",
                    lang = "en",
                    summaryDefinition = "A fruit",
                    tagsCsv = "",
                    hasAwlTag = false,
                    payloadJson = """{"word":"apple"}"""
                )
            )
        )

        val awlList = dao.observeAwlOfflinePackWords().first()
        assertEquals(1, awlList.size)
        assertEquals("analyze", awlList.first().word)
        assertEquals("AWL", awlList.first().tagsCsv)

        dao.upsertTranslationCacheBatch(
            listOf(
                TranslationCacheEntity("k1", "تحليل مفصل", 1000L),
                TranslationCacheEntity("k2", "مثال عملي", 1000L)
            )
        )
        val cached = dao.getTranslationCacheBatch(listOf("k1", "k2", "missing"))
        assertEquals(2, cached.size)
        assertEquals("تحليل مفصل", cached.first { it.cacheKey == "k1" }.translatedText)

        dao.clearTranslationCache()
        assertTrue(dao.getTranslationCacheBatch(listOf("k1", "k2")).isEmpty())
    }
}
