package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.Accent
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.AudioSourceKind
import com.example.core.ContextualTranslationAnalyzer
import com.example.core.FirstLaunchDataStore
import com.example.core.NetworkMonitor
import com.example.core.OfflinePackManager
import com.example.core.OfflinePackState
import com.example.core.OfflineVoicePackManager
import com.example.core.OfflineVoicePackState
import com.example.core.StaticLabels
import com.example.core.TranslationManager
import com.example.core.UserSettingsPreferences
import com.example.data.local.AppDatabase
import com.example.data.remote.DatamuseApi
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.TatoebaApi
import com.example.data.remote.WiktionaryApi
import com.example.data.repository.LexiconRepository
import com.example.data.repository.WordLoadState
import com.example.domain.model.LanguageDirection
import com.example.ui.screens.SentenceLookupScreen
import com.example.ui.screens.WordDetailScreen
import com.example.ui.theme.AlMakhzanjiTheme
import com.example.ui.theme.DarkAmberSubline
import com.example.ui.theme.DarkBlueLink
import com.example.ui.theme.DarkCanvas
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkRedAccent
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SentenceLookupViewModel
import com.example.ui.viewmodel.WordDetailViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SymmetryAndItems0To13Test {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: UserSettingsPreferences
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var translationManager: TranslationManager
    private lateinit var offlineVoicePackManager: OfflineVoicePackManager
    private lateinit var audioManager: AudioPlayerManager
    private lateinit var packManager: OfflinePackManager
    private lateinit var repository: LexiconRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "audio_cache").deleteRecursively()
        File(context.filesDir, "voice_pack").deleteRecursively()
        File(context.cacheDir, "offline-voice-pack-5k.tar.gz.part").delete()
        File(context.cacheDir, "dictionary-pack.json.gz.part").delete()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(50, TimeUnit.MILLISECONDS)
            .readTimeout(50, TimeUnit.MILLISECONDS)
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val offlineRetrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        val offlineScalarRetrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:1/")
            .client(okHttp)
            .build()

        settings = UserSettingsPreferences(context)
        networkMonitor = NetworkMonitor(context)
        translationManager = TranslationManager(context, networkMonitor, settings)
        offlineVoicePackManager = OfflineVoicePackManager(context, okHttp, networkMonitor)
        audioManager = AudioPlayerManager(
            context = context,
            okHttpClient = okHttp,
            settingsPreferences = settings,
            offlineVoicePackManager = offlineVoicePackManager
        )
        val dao = db.lexiconDao()
        packManager = OfflinePackManager(context, dao, okHttp, networkMonitor)
        repository = LexiconRepository(
            dao = dao,
            dictionaryApi = offlineRetrofit.create(FreeDictionaryApi::class.java),
            datamuseApi = offlineRetrofit.create(DatamuseApi::class.java),
            wiktionaryApi = offlineScalarRetrofit.create(WiktionaryApi::class.java),
            tatoebaApi = offlineScalarRetrofit.create(TatoebaApi::class.java),
            translationManager = translationManager,
            audioPlayerManager = audioManager,
            networkMonitor = networkMonitor
        )
    }

    @After
    fun tearDown() {
        audioManager.release()
        db.close()
    }

    private fun richSymmetryPackJson(): String {
        val distinctDefs = listOf(
            "Move swiftly on foot across open ground.",
            "Manage and direct a commercial enterprise.",
            "Execute software instructions on a processor.",
            "Flow steadily down a rocky mountain valley.",
            "Extend continuously along the eastern border.",
            "Operate a regular passenger bus timetable.",
            "Campaign actively for public political office.",
            "Publish a serialized editorial column weekly.",
            "Smuggle illegal cargo across maritime waters.",
            "Bleed wet ink pigment through cotton fabric.",
            "Unravel delicate silk threads down a stocking.",
            "Melt candles smoothly under summer heat.",
            "Continue unbroken throughout winter season.",
            "Score points rapidly during tournament play.",
            "Unique fifteenth distinct action sense marker."
        )
        val en15Senses = distinctDefs.mapIndexed { idx, d ->
            val n = idx + 1
            """{"pos": "verb", "def": "$d", "ar": "معنى يجري رقم $n"}"""
        }.joinToString(",")
        val en15Syns = (1..15).joinToString(",") { idx -> "\"sprint$idx\"" }
        val ar15Matches = (1..15).joinToString(",") { idx ->
            """{"en": "englishMatch$idx", "pos": "adjective", "def": "Meaning number $idx for taste or trial.", "rank": ${100 + idx}, "src": "wiktionary"}"""
        }
        return """
        {
          "version": 2,
          "words": [
            {
              "word": "run",
              "lang": "en",
              "rank": 40,
              "preview": "v. يَجْرِي؛ يَرْكُضُ؛ يُدِيرُ؛ n. جَرْي",
              "forms": [
                {"form": "runs", "label": "3rd sg."},
                {"form": "ran", "label": "pa. t."},
                {"form": "run", "label": "pa. pple."},
                {"form": "running", "label": "pres. pple."}
              ],
              "syns": [$en15Syns],
              "ants": ["walk", "halt", "stop"],
              "senses": [$en15Senses]
            },
            {
              "word": "concern",
              "lang": "en",
              "rank": 90,
              "preview": "v. يَهُمّ؛ أَقْلَقَ؛ n. قَلَقٌ؛ شَأْنٌ",
              "senses": [
                {"pos": "verb", "def": "To relate to or cause worry.", "ar": "يَهُمّ؛ أَقْلَقَ"},
                {"pos": "noun", "def": "Anxiety or matter of interest.", "ar": "قَلَقٌ؛ شَأْنٌ"}
              ]
            },
            {
              "word": "bitter",
              "lang": "en",
              "rank": 110,
              "preview": "adj. مُرّ؛ حَادّ الطَّعْم؛ مَرِير",
              "senses": [
                {"pos": "adjective", "def": "Having an unpleasant, sharp taste like coffee or butter.", "ar": "مُرّ الطَّعْم"},
                {"pos": "adjective", "def": "Causing deep sorrow or resentment.", "ar": "أَلِيم؛ قَاسٍ"}
              ]
            },
            {
              "word": "the",
              "lang": "en",
              "rank": 1,
              "preview": "det. الـ التعريف",
              "senses": [
                {"pos": "determiner", "def": "Definite article used before specific nouns.", "ar": "أَدَاةُ التَّعْرِيفِ (الـ)"}
              ]
            },
            {
              "word": "house",
              "lang": "en",
              "rank": 70,
              "preview": "n. بَيْت؛ مَنْزِل؛ دَار",
              "senses": [
                {"pos": "noun", "def": "A building for human habitation.", "ar": "بَيْت؛ مَنْزِل"}
              ]
            }
          ],
          "idioms": [
            {
              "phrase": "in the long run",
              "def": "Over a long period of time.",
              "ar": "عَلَى المَدَى الطَّوِيل",
              "ex": "It pays off in the long run.",
              "cat": "IDIOM",
              "rank": 300
            }
          ],
          "reverse_ar": [
            {
              "ar": "مر",
              "voc": "مُرّ",
              "matches": [
                {"en": "bitter", "pos": "adjective", "def": "Sharp, unpleasant taste (not sweet butter).", "rank": 50, "src": "omwn"},
                {"en": "acrid", "pos": "adjective", "def": "Unpleasantly sharp or bitter.", "rank": 90, "src": "wiktionary"},
                $ar15Matches
              ]
            },
            {
              "ar": "مر",
              "voc": "مَرَّ",
              "matches": [
                {"en": "once", "pos": "adverb", "def": "On a single occasion or one time.", "rank": 40, "src": "wiktionary"},
                {"en": "pass", "pos": "verb", "def": "To move past something.", "rank": 60, "src": "wiktionary"}
              ]
            },
            {
              "ar": "يجري",
              "voc": "يَجْرِي",
              "matches": [
                {"en": "run", "pos": "verb", "def": "To move swiftly on foot.", "rank": 40, "src": "wiktionary"},
                {"en": "flow", "pos": "verb", "def": "To move smoothly as liquid.", "rank": 80, "src": "wiktionary"}
              ]
            }
          ]
        }
        """.trimIndent()
    }

    @Test
    fun item0And1_readyBeforeOpenPrebuildsBothEnAndArSymmetrically() = runBlocking {
        packManager.importPackFromJsonStream(ByteArrayInputStream(richSymmetryPackJson().toByteArray()))

        // Prebuild top EN and AR candidates strictly locally (no network)
        repository.prebuildLocalCandidates(listOf("run", "concern", "مر", "يجري"))

        var readyEn = repository.peekReadyEntry("run")
        var readyAr = repository.peekReadyEntry("مر")
        val deadlineMs = System.currentTimeMillis() + 3_000L
        while ((readyEn == null || readyAr == null) && System.currentTimeMillis() < deadlineMs) {
            kotlinx.coroutines.delay(25L)
            readyEn = repository.peekReadyEntry("run")
            readyAr = repository.peekReadyEntry("مر")
        }
        assertNotNull("EN entry should be ready in LRU", readyEn)
        assertNotNull("AR entry should be ready in LRU", readyAr)
        assertEquals("run", readyEn!!.word)
        assertTrue(readyAr!!.isArabicWord)
        assertTrue(readyAr.reverseArabicGroups.isNotEmpty())

        // ensureWordReadyForOpen works symmetrically for EN and AR within <= 1.5s budget
        val ensuredEn = repository.ensureWordReadyForOpen("concern", timeoutMs = 1450L)
        val ensuredAr = repository.ensureWordReadyForOpen("يجري", timeoutMs = 1450L)
        assertNotNull(ensuredEn)
        assertNotNull(ensuredAr)
        assertFalse(ensuredEn!!.posGroups.isEmpty())
        assertTrue(ensuredAr!!.reverseArabicGroups.isNotEmpty())
    }

    @Test
    fun item0_1_6_7_wordDetailScreen_symmetricEnAndAr_clearHidesEntry_seeMoreAndDoubleTap() {
        runBlocking {
            packManager.importPackFromJsonStream(ByteArrayInputStream(richSymmetryPackJson().toByteArray()))
        }

        // Part A: EN -> AR ("run" has 15 senses -> capped at 10 initially -> See more expands to 15)
        val enVm = WordDetailViewModel(
            repository = repository,
            translationManager = translationManager,
            settingsPreferences = settings,
            initialQuery = "run"
        )
        composeRule.setContent {
            AlMakhzanjiTheme(themeMode = ThemeMode.DARK) {
                WordDetailScreen(
                    viewModel = enVm,
                    audioManager = audioManager,
                    onBack = {},
                    onOpenWord = { _, _, _ -> },
                    onOpenSentence = {},
                    onOpenLanguages = {}
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.shadowMainLooper().idleFor(50, TimeUnit.MILLISECONDS)
            enVm.uiState.value is WordLoadState.Success
        }
        composeRule.waitForIdle()

        // 10-item initial cap on EN POS group
        assertEquals(0, composeRule.onAllNodesWithText("Unique fifteenth distinct action sense marker.", substring = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("word_detail_result_list")
            .performScrollToNode(hasTestTag("pos_see_more_verb"))
        composeRule.onNodeWithTag("pos_see_more_verb").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_detail_result_list")
            .performScrollToNode(androidx.compose.ui.test.hasText("Unique fifteenth distinct action sense marker.", substring = true))
        composeRule.onAllNodesWithText("Unique fifteenth distinct action sense marker.", substring = true)[0]
            .performScrollTo()
            .assertIsDisplayed()

        // Forbidden strings absent on EN entry
        assertEquals(0, composeRule.onAllNodesWithText("Machine translated", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("ترجمة آلية", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Offline", ignoreCase = false).fetchSemanticsNodes().size)

        // Clear button (X) hides old entry ("headword_run") and enables clean inline search without stale entry beneath
        composeRule.onNodeWithTag("word_detail_clear").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(enVm.isSearchingInline.value)
        assertEquals("", enVm.inlineQuery.value)
        assertEquals(0, composeRule.onAllNodesWithTag("pos_see_more_verb").fetchSemanticsNodes().size)

        // Typing "hou" in inline search shows live suggestion "house" with Arabic meanings while old "run" entry remains hidden
        composeRule.onNodeWithTag("word_detail_search_field").performTextInput("hou")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.shadowMainLooper().idleFor(100, TimeUnit.MILLISECONDS)
            enVm.inlineSuggestions.value.any { it.word == "house" }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("suggestion_house").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Move swiftly on foot across open ground.", substring = true).fetchSemanticsNodes().size)

        // Part B: AR -> EN symmetry ("مر" has >10 matches across groups -> capped at 10 initially -> See more expands)
        enVm.loadWord("مر")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.shadowMainLooper().idleFor(50, TimeUnit.MILLISECONDS)
            (enVm.uiState.value as? WordLoadState.Success)?.entry?.word == "مر"
        }
        composeRule.waitForIdle()

        // Arabic headword speaker button exists symmetrically
        assertEquals(1, composeRule.onAllNodesWithTag("pronounce_ar_button").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("word_detail_result_list")
            .performScrollToNode(hasTestTag("reverse_arabic_groups_card"))
        assertTrue(composeRule.onAllNodesWithTag("reverse_arabic_groups_card").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, composeRule.onAllNodesWithTag("reverse_match_englishMatch15").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("word_detail_result_list")
            .performScrollToNode(hasTestTag("reverse_arabic_see_more"))
        composeRule.onNodeWithTag("reverse_arabic_see_more").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("word_detail_result_list")
            .performScrollToNode(hasTestTag("reverse_match_englishMatch15"))
        composeRule.onNodeWithTag("reverse_match_englishMatch15").performScrollTo().assertIsDisplayed()

        // Clear X button works identically in AR -> EN
        composeRule.onNodeWithTag("word_detail_clear").performClick()
        composeRule.waitForIdle()
        assertTrue(enVm.isSearchingInline.value)
        assertEquals(0, composeRule.onAllNodesWithTag("reverse_arabic_groups_card").fetchSemanticsNodes().size)
    }

    @Test
    fun item2_4_5_firstLaunchAndOfflineVoicePackPauseResumeState() = runBlocking {
        // FirstLaunchDataStore defaults to false and persists true once marked
        val fl = FirstLaunchDataStore(context)
        assertFalse(fl.firstLaunchHandled.first())
        fl.markFirstLaunchHandled()
        assertTrue(fl.firstLaunchHandled.first())

        // OfflineVoicePackManager finds .opus clips in US & UK folders and detects .part Paused state
        val usDir = File(File(context.filesDir, "voice_pack"), "us").apply { mkdirs() }
        val ukDir = File(File(context.filesDir, "voice_pack"), "uk").apply { mkdirs() }
        File(usDir, "concern.opus").writeBytes(byteArrayOf(1, 2, 3, 4))
        File(ukDir, "concern.opus").writeBytes(byteArrayOf(5, 6, 7, 8))

        assertNotNull(offlineVoicePackManager.findClip("concern", Accent.US))
        assertNotNull(offlineVoicePackManager.findClip("concern", Accent.UK))
        assertNull(offlineVoicePackManager.findClip("concern", Accent.AR))
        assertNull(offlineVoicePackManager.findClip("missingword", Accent.US))

        // Partial .part file restores Paused state cleanly on refreshStatus()
        val voicePart = File(context.cacheDir, "offline-voice-pack-5k.tar.gz.part")
        voicePart.writeBytes(ByteArray(12_345))
        offlineVoicePackManager.refreshStatus()
        assertTrue(offlineVoicePackManager.state.value is OfflineVoicePackState.Paused)

        val dictPart = File(context.cacheDir, "dictionary-pack.json.gz.part")
        dictPart.writeBytes(ByteArray(23_456))
        packManager.refreshStatus()
        assertTrue(packManager.state.value is OfflinePackState.Paused)
    }

    @Test
    fun item3_audioFourTierChain_zeroSpeculative404s_andDebugTracking() {
        // Seed Kokoro manifest with only "bitter" and "the" (omitting "unlistedword")
        audioManager.seedVoiceManifestForTest(
            usWords = setOf("bitter", "the"),
            ukWords = setOf("bitter", "the")
        )

        // Put offline voice pack clip for "concern" (Tier a -> 0 KB)
        val usDir = File(File(context.filesDir, "voice_pack"), "us").apply { mkdirs() }
        File(usDir, "concern.opus").writeBytes(ByteArray(2400) { 1 })

        // Tier (a): Voice Pack / Cache -> 0 KB
        assertEquals(
            AudioSourceKind.CACHE_OR_VOICE_PACK,
            audioManager.classifyAudioSource("concern", Accent.US)
        )
        // Tier (b): Known FreeDict / Wikimedia human URL
        assertEquals(
            AudioSourceKind.HUMAN_RECORDING,
            audioManager.classifyAudioSource(
                "run",
                Accent.US,
                knownHumanUrl = "https://api.dictionaryapi.dev/media/pronunciations/en/run-us.mp3"
            )
        )
        // Tier (c): Listed in Kokoro manifest -> Kokoro only when BuildConfig/manifest resolves; verify TTS fallback cleanly otherwise
        val tierBitter = audioManager.classifyAudioSource("bitter", Accent.US, knownHumanUrl = null)
        assertTrue(tierBitter == AudioSourceKind.KOKORO_OPUS || tierBitter == AudioSourceKind.DEVICE_TTS)
        // Tier (d): Unlisted word -> Device TTS directly (0 speculative 404 requests)
        assertEquals(
            AudioSourceKind.DEVICE_TTS,
            audioManager.classifyAudioSource("unlistedword", Accent.US, knownHumanUrl = null)
        )
        // Arabic word -> Device TTS AR
        assertEquals(
            AudioSourceKind.DEVICE_TTS,
            audioManager.classifyAudioSource("مُرّ", Accent.AR, knownHumanUrl = null)
        )

        // Verify zero 404 requests are fired during playHumanPronunciation for unlisted words
        val httpCallCount = AtomicInteger(0)
        val countingClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                httpCallCount.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        val trackedAudio = AudioPlayerManager(
            context = context,
            okHttpClient = countingClient,
            settingsPreferences = settings,
            offlineVoicePackManager = offlineVoicePackManager
        )
        trackedAudio.seedVoiceManifestForTest(emptySet(), emptySet())
        trackedAudio.playHumanPronunciation("unlistedword", Accent.US, audioUrl = null, key = "test_unlisted")
        ShadowLooper.shadowMainLooper().idleFor(300, TimeUnit.MILLISECONDS)
        assertEquals("Unlisted word must NOT trigger speculative Kokoro HTTP 404", 0, httpCallCount.get())
        trackedAudio.release()
    }

    @Test
    fun item8And9_staticLabelsAndDarkPaletteTokensAreSymmetricAndComplete() {
        // Exact dark theme contrast tokens (Items 8 & 9)
        assertEquals(0xFF0B0B0CUL, DarkCanvas.value shr 32)
        assertEquals(0xFF151517UL, DarkSurface.value shr 32)
        assertEquals(0xFF262629UL, DarkOutline.value shr 32)
        assertEquals(0xFFE5484DUL, DarkRedAccent.value shr 32)
        assertEquals(0xFFF2B94BUL, DarkAmberSubline.value shr 32)
        assertEquals(0xFF6CB4FFUL, DarkBlueLink.value shr 32)

        val requiredHeadings = listOf(
            "Synonyms", "Antonyms", "Collocations", "Idioms & Expressions",
            "Phrasal Verbs", "Word Forms", "Word Family", "More Examples",
            "See more", "See less", "Meanings", "English Matches",
            "Word Breakdown", "Translation", "Literal Translation"
        )
        requiredHeadings.forEach { heading ->
            val ar = StaticLabels.arabicFor(heading)
            assertTrue("Missing StaticLabels Arabic sub-line for '$heading'", ar.isNotBlank() && ArabicTextUtils.containsArabicLetters(ar))
        }

        val requiredPos = listOf("noun", "verb", "adjective", "adverb", "pronoun", "preposition", "conjunction", "interjection", "determiner")
        requiredPos.forEach { pos ->
            assertTrue("Missing POS Arabic for '$pos'", StaticLabels.posArabic(pos).isNotBlank())
        }

        val requiredForms = listOf("plural", "past tense", "past participle", "present participle", "masdar", "root")
        requiredForms.forEach { form ->
            assertTrue("Missing form label Arabic for '$form'", StaticLabels.arabicFor(form).isNotBlank())
        }
    }

    @Test
    fun item13_contextualTranslationAndWordChipsSymmetricEnToArAndArToEn() {
        // AR -> EN: Betty Peter + "مرة" = "bitter" (never "My house" / "Petit" / "once")
        val arSentence = "بيتي بيتر اشترى بعض الزبدة لكن الزبدة كانت مرة"
        val arPrepared = ContextualTranslationAnalyzer.prepareArabicForTranslation(arSentence)
        assertTrue(arPrepared.contains("Betty"))
        assertTrue(arPrepared.contains("Peter"))
        assertTrue(arPrepared.contains("bitter"))
        assertFalse(arPrepared.contains("بيتي"))

        val arDirect = ContextualTranslationAnalyzer.findDirectContextualOverride(arSentence, "ar", "en")
        assertEquals("Betty Peter bought some butter, but the butter was bitter.", arDirect)

        val arRefined = ContextualTranslationAnalyzer.refineEnglishTranslation(
            arSentence,
            "My house Petit bought some butter, but the butter was once."
        )
        assertFalse(arRefined.contains("my house", ignoreCase = true))
        assertFalse(arRefined.contains("petit", ignoreCase = true))
        assertFalse(Regex("\\bonce\\b", RegexOption.IGNORE_CASE).containsMatchIn(arRefined))
        assertTrue(arRefined.contains("Betty"))
        assertTrue(arRefined.contains("bitter"))

        // AR -> EN word breakdown chips map "بيتي" -> Betty and "مرة" -> bitter (with preferredSenseHint = "bitter")
        val arChips = ContextualTranslationAnalyzer.analyzeSentenceWordsInContext(
            sentence = arSentence,
            isArabic = true,
            chipMeanings = emptyMap()
        )
        assertEquals("Betty", arChips.first { it.word == "بيتي" }.meaning)
        val murrahChip = arChips.first { it.word == "مرة" }
        assertEquals("bitter", murrahChip.meaning)
        assertEquals("bitter", murrahChip.preferredSenseHint)

        // Contrast with frequency context: "ذهبت مرة واحدة فقط" keeps "once" (NOT "bitter")
        val freqChips = ContextualTranslationAnalyzer.analyzeSentenceWordsInContext(
            sentence = "ذهبت إلى المكتبة مرة واحدة اليوم",
            isArabic = true,
            chipMeanings = emptyMap()
        )
        assertTrue(freqChips.first { it.word == "مرة" }.meaning.contains("once"))

        // EN -> AR: Betty Botter + "bitter" -> "مُرّة" (never "بيتي الخزاف" / "مرة واحدة")
        val enSentence = "Betty Botter bought some butter, but she said the butter's bitter."
        val enDirect = ContextualTranslationAnalyzer.findDirectContextualOverride(enSentence, "en", "ar")
        assertNotNull(enDirect)
        assertTrue(enDirect!!.contains("بيتي بوتر"))
        assertTrue(enDirect.contains("مُرّة"))

        val enChips = ContextualTranslationAnalyzer.analyzeSentenceWordsInContext(
            sentence = enSentence,
            isArabic = false,
            chipMeanings = emptyMap()
        )
        assertEquals("مُرّ", enChips.first { it.word.equals("bitter", ignoreCase = true) }.meaning)
        assertEquals("زُبْدة", enChips.first { it.word.equals("butter", ignoreCase = true) }.meaning)

        // SentenceLookupScreen UI works symmetrically for AR sentence & (X) clear hides old sentence
        val sentVm = SentenceLookupViewModel(
            repository = repository,
            translationManager = translationManager,
            settingsPreferences = settings,
            initialQuery = arSentence
        )
        composeRule.setContent {
            AlMakhzanjiTheme(themeMode = ThemeMode.DARK) {
                SentenceLookupScreen(
                    viewModel = sentVm,
                    audioManager = audioManager,
                    onBack = {},
                    onOpenWord = { _, _ -> },
                    onOpenDirectPhrase = {},
                    onOpenLanguages = {}
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            ShadowLooper.shadowMainLooper().idleFor(50, TimeUnit.MILLISECONDS)
            sentVm.state.value.contextualWordChips.isNotEmpty()
        }
        composeRule.waitForIdle()
        assertEquals(LanguageDirection.AR_TO_EN, sentVm.direction.value)
        composeRule.onNodeWithTag("sentence_lookup_clear").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertTrue(sentVm.isSearchingInline.value)
    }
}
