package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.ArabicTextUtils
import com.example.core.AudioPlayerManager
import com.example.core.NetworkMonitor
import com.example.core.OfflinePackManager
import com.example.core.TranslationManager
import com.example.core.UserSettingsPreferences
import com.example.data.local.AppDatabase
import androidx.compose.ui.test.onAllNodesWithText
import com.example.data.remote.DatamuseApi
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.MyMemoryApi
import com.example.data.remote.TatoebaApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import com.example.data.remote.WiktionaryApi
import com.example.data.repository.LexiconRepository
import com.example.data.repository.WiktionaryHtmlParser
import com.example.data.repository.WordLoadState
import com.example.domain.model.LanguageDirection
import com.example.ui.screens.QuickTranslateScreen
import com.example.ui.theme.AlMakhzanjiTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.QuickTranslateViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DictionaryPackAndSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: LexiconRepository
    private lateinit var packManager: OfflinePackManager
    private lateinit var audioManager: AudioPlayerManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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

        val settings = UserSettingsPreferences(context)
        val networkMonitor = NetworkMonitor(context)
        val translationManager = TranslationManager(context, networkMonitor, settings)
        audioManager = AudioPlayerManager(context, okHttp, settings)
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
        db.close()
    }

    private fun sampleV2PackJson(): String {
        val extraConcWords = (1..42).joinToString(",") { idx ->
            """
            {
              "word": "concextra$idx",
              "lang": "en",
              "rank": ${1000 + idx},
              "preview": "n. معنى إضافي رقم $idx",
              "senses": [{"pos": "noun", "def": "Synthetic entry $idx", "ar": "معنى إضافي رقم $idx"}]
            }
            """.trimIndent()
        }
        return """
        {
          "version": 2,
          "words": [
            {
              "word": "conc",
              "lang": "en",
              "rank": 9000,
              "preview": "n. اختصار",
              "senses": [{"pos": "noun", "def": "Abbreviation", "ar": "اختصار"}]
            },
            {
              "word": "concern",
              "lang": "en",
              "rank": 120,
              "preview": "v. يَهُمّ؛ أَقْلَقَ؛ n. قَلَقٌ؛ شَأْنٌ",
              "senses": [
                {"pos": "verb", "def": "To relate to or worry.", "ar": "يَهُمّ؛ أَقْلَقَ"},
                {"pos": "noun", "def": "Anxiety or matter of interest.", "ar": "قَلَقٌ؛ شَأْنٌ"}
              ]
            },
            {
              "word": "concert",
              "lang": "en",
              "rank": 220,
              "preview": "n. حَفْلَة مُوسِيقِيَّة؛ اِتِّفَاق؛ v. اِتَّفَقَ",
              "senses": [
                {"pos": "noun", "def": "A musical performance.", "ar": "حَفْلَة مُوسِيقِيَّة؛ اِتِّفَاق"},
                {"pos": "verb", "def": "To arrange by mutual agreement.", "ar": "اِتَّفَقَ"}
              ]
            },
            {
              "word": "concentrate",
              "lang": "en",
              "rank": 310,
              "preview": "v. يُرَكِّز؛ رَكَّزَ؛ n. مُرَكَّزٌ",
              "senses": [
                {"pos": "verb", "def": "To focus attention.", "ar": "يُرَكِّز؛ رَكَّزَ"},
                {"pos": "noun", "def": "A concentrated substance.", "ar": "مُرَكَّزٌ"}
              ]
            },
            {
              "word": "concept",
              "lang": "en",
              "rank": 350,
              "preview": "n. مَفْهُوم؛ فِكْرَة",
              "senses": [
                {"pos": "noun", "def": "An abstract idea.", "ar": "مَفْهُوم؛ فِكْرَة"}
              ]
            },
            {
              "word": "conclusion",
              "lang": "en",
              "rank": 400,
              "preview": "n. اِسْتِنْتَاج؛ خَاتِمَة",
              "senses": [
                {"pos": "noun", "def": "The end or deduction.", "ar": "اِسْتِنْتَاج؛ خَاتِمَة"}
              ]
            },
            {
              "word": "concrete",
              "lang": "en",
              "rank": 450,
              "preview": "n. خَرَسَانَة؛ adj. مَادِّيّ؛ مَلْمُوس",
              "senses": [
                {"pos": "noun", "def": "Building material.", "ar": "خَرَسَانَة"},
                {"pos": "adjective", "def": "Existing in material form.", "ar": "مَادِّيّ؛ مَلْمُوس"}
              ]
            },
            {
              "word": "reconcile",
              "lang": "en",
              "rank": 150,
              "preview": "v. يُصَالِح؛ يُوَفِّق",
              "senses": [
                {"pos": "verb", "def": "To restore friendly relations.", "ar": "يُصَالِح؛ يُوَفِّق"}
              ]
            },
            {
              "word": "town",
              "lang": "en",
              "rank": 180,
              "preview": "n. مَدِينَة؛ بَلْدَة",
              "senses": [
                {"pos": "noun", "def": "An urban area smaller than a city.", "ar": "مَدِينَة؛ بَلْدَة"}
              ]
            },
            {
              "word": "conctest500",
              "lang": "en",
              "rank": 125,
              "senses": [
                {"pos": "verb", "def": "Verb with 250 meanings", "ar": "معنى_فعل_1؛ معنى_فعل_2"},
                {"pos": "noun", "def": "Noun with 250 meanings", "ar": "معنى_اسم_1؛ معنى_اسم_2"}
              ]
            },
            {
              "word": "run",
              "lang": "en",
              "rank": 50,
              "forms": [
                {"form": "run", "label": "pa. pple."},
                {"form": "ran", "label": "pa. t."}
              ],
              "preview": "v. يَجْرِي؛ يَرْكُضُ؛ يُدِيرُ؛ يَتَحَكَّمُ؛ يَتَدَفَّقُ؛ n. جَرْي؛ مَسَار",
              "senses": [
                {"pos": "verb", "def": "To move rapidly on foot.", "ar": "يَجْرِي؛ يَرْكُضُ؛ يُدِيرُ؛ يَتَحَكَّمُ؛ يَتَدَفَّقُ"},
                {"pos": "noun", "def": "An act of running.", "ar": "جَرْي؛ مَسَار"}
              ]
            },
            $extraConcWords
          ],
          "idioms": [
            {
              "phrase": "hit the town",
              "def": "To go out and enjoy entertainment at night in a city.",
              "ar": "يخرج للسهر والاستمتاع في المدينة",
              "ex": "We decided to hit the town on Friday night.",
              "cat": "IDIOM",
              "rank": 500
            },
            {
              "phrase": "definition only idiom",
              "def": "To act with unusual patience in difficult times.",
              "ar": "",
              "ex": "She showed a definition only idiom.",
              "cat": "IDIOM",
              "rank": 700
            }
          ],
          "reverse_ar": [
            {
              "ar": "يجري",
              "voc": "يَجْرِي",
              "matches": [
                {"en": "run", "pos": "verb", "def": "To move swiftly or operate.", "rank": 50, "src": "wiktionary"},
                {"en": "manage", "pos": "verb", "def": "To handle or direct.", "rank": 600, "src": "wiktionary"},
                {"en": "flow", "pos": "verb", "def": "To move along smoothly.", "rank": 900, "src": "omwn"}
              ]
            },
            {
              "ar": "مر",
              "voc": "مُرّ",
              "matches": [
                {"en": "bitter", "pos": "adjective", "def": "Having an acrid, sharp taste.", "rank": 800, "src": "omwn"},
                {"en": "harsh", "pos": "adjective", "def": "Unpleasantly rough or severe.", "rank": 900, "src": "wiktionary"},
                {"en": "acrid", "pos": "adjective", "def": "Unpleasantly bitter and pungent.", "rank": 2500, "src": "omwn"},
                {"en": "myrrh", "pos": "noun", "def": "Fragrant gum resin.", "rank": 4000, "src": "wiktionary"}
              ]
            },
            {
              "ar": "مر",
              "voc": "مَرَّ",
              "matches": [
                {"en": "to pass", "pos": "verb", "def": "To move past or go by.", "rank": 300, "src": "wiktionary"},
                {"en": "to go by", "pos": "verb", "def": "To elapse or move past.", "rank": 750, "src": "omwn"}
              ]
            },
            {
              "ar": "مر",
              "voc": "مِرّ",
              "matches": [
                {"en": "gallant", "pos": "adjective", "def": "Brave and spirited.", "rank": 5000, "src": "wiktionary"}
              ]
            }
          ]
        }
        """.trimIndent()
    }

    @Test
    fun packImport_and_livePrefixSearch_concRankingPreviewAndPagination() = runBlocking {
        val bytes = packManager.importPackFromJsonStream(ByteArrayInputStream(sampleV2PackJson().toByteArray()))
        assertTrue(bytes > 0L)

        val dao = db.lexiconDao()
        assertTrue(dao.getOfflinePackCount() >= 50)
        assertEquals(2, dao.getPackIdiomCount())
        assertTrue(dao.getReverseArabicCount() >= 7)

        val page1 = repository.getLiveSuggestionsPaged("conc", offset = 0, limit = 40)
        assertEquals(40, page1.items.size)
        assertTrue(page1.hasMore)

        // Exact match first -> startsWith ordered by frequencyRank -> contains ("reconcile") after startsWith
        assertEquals("conc", page1.items.first().word)
        val expectedTop = listOf("conc", "concern", "conctest500", "concert", "concentrate", "concept", "conclusion", "concrete")
        assertEquals(expectedTop, page1.items.take(8).map { it.word })

        val concernRow = page1.items[1]
        assertTrue(concernRow.isLocal)
        assertTrue(concernRow.posPreview.contains("v."))
        assertTrue(concernRow.posPreview.contains("n."))
        assertTrue(concernRow.posPreview.contains("؛"))

        // Word with multiple meanings preserves all meanings without truncation
        val row500 = page1.items[2]
        assertEquals("conctest500", row500.word)
        assertTrue(row500.posPreview.contains("معنى_فعل_1"))
        assertTrue(row500.posPreview.contains("معنى_فعل_2"))
        assertTrue(row500.posPreview.contains("معنى_اسم_1"))
        assertTrue(row500.posPreview.contains("معنى_اسم_2"))

        val entry500 = (repository.observeWordEntry("conctest500")
            .first { it is WordLoadState.Success && !it.isUpdating } as WordLoadState.Success).entry
        val headword500 = com.example.core.LinguisticRanker.extractPosHeadwordMeanings(entry500)
        assertEquals(4, headword500.sumOf { it.meanings.size })

        val page2 = repository.getLiveSuggestionsPaged("conc", offset = 40, limit = 40)
        assertFalse(page2.hasMore)
        assertTrue(page2.items.any { it.word == "reconcile" })
        // Contains match ("reconcile") must be ranked after startsWith matches
        assertTrue(page1.items.none { it.word == "reconcile" })
    }

    @Test
    fun arabicNormalization_and_reverseArabicGroupedLookup_forMurr() = runBlocking {
        packManager.importPackFromJsonStream(ByteArrayInputStream(sampleV2PackJson().toByteArray()))

        assertEquals("مر", ArabicTextUtils.normalizeArabicForMatching("مُرّ"))
        assertEquals("مر", ArabicTextUtils.normalizeArabicForMatching("مَرَّ"))
        assertEquals("مر", ArabicTextUtils.normalizeArabicForMatching("مِرْ"))
        assertEquals("احمد", ArabicTextUtils.normalizeArabicForMatching("أَحْمَدُ"))
        assertEquals("مدرسه", ArabicTextUtils.normalizeArabicForMatching("مَدْرَسَة"))

        val finalState = repository.observeWordEntry("مر")
            .first { state -> state is WordLoadState.Success && !state.isUpdating }
        val entry = (finalState as WordLoadState.Success).entry
        assertTrue(entry.isArabicWord)
        assertTrue(entry.reverseArabicGroups.size >= 3)

        val canonicalMarra = ArabicTextUtils.canonicalizeArabicWithDiacritics("مَرَّ")
        val vocForms = entry.reverseArabicGroups.map { it.vocalizedForm }
        assertTrue("مُرّ" in vocForms)
        assertTrue(canonicalMarra in vocForms)
        assertTrue("مِرّ" in vocForms)

        val murrGroup = entry.reverseArabicGroups.first { it.vocalizedForm == "مُرّ" }
        val murrWords = murrGroup.matches.map { it.englishWord }
        assertTrue("bitter" in murrWords)
        assertTrue("harsh" in murrWords)
        assertTrue("acrid" in murrWords)
        assertTrue("myrrh" in murrWords)

        val marraGroup = entry.reverseArabicGroups.first { it.vocalizedForm == canonicalMarra }
        val marraWords = marraGroup.matches.map { it.englishWord }
        assertTrue("to pass" in marraWords)
        assertTrue("to go by" in marraWords)

        // Reverse suggestions during live search
        val revSug = repository.getLiveSuggestionsPaged("مر", offset = 0, limit = 40)
        assertTrue(revSug.items.any { it.word == "مُرّ" && it.posPreview.contains("bitter") })
        assertTrue(revSug.items.any { it.word == canonicalMarra && it.posPreview.contains("to pass") })
    }

    @Test
    fun englishAndArabicSuggestions_showAllTranslatedMeaningsUnderEachWord() = runBlocking {
        packManager.importPackFromJsonStream(ByteArrayInputStream(sampleV2PackJson().toByteArray()))

        // 1. English live prefix search e.g. "r" or "run"
        val rSuggestions = repository.getLiveSuggestionsPaged("r", offset = 0, limit = 50)
        assertTrue(rSuggestions.items.isNotEmpty())
        val runSug = rSuggestions.items.firstOrNull { it.word == "run" }
        assertNotNull(runSug)
        assertTrue(runSug!!.posPreview.contains("يَجْرِي"))
        assertTrue(runSug.posPreview.contains("يُدِيرُ"))
        assertTrue(runSug.posPreview.contains("جَرْي"))
        // Every English suggestion must have Arabic meanings
        rSuggestions.items.forEach { item ->
            assertTrue(
                "Expected Arabic meanings under '${item.word}', got '${item.posPreview}'",
                ArabicTextUtils.containsArabicLetters(item.posPreview)
            )
        }

        // 2. Arabic live search e.g. "يجري"
        val yajriSug = repository.getLiveSuggestionsPaged("يجري", offset = 0, limit = 50)
        assertTrue(yajriSug.items.isNotEmpty())
        val yajriItem = yajriSug.items.firstOrNull {
            ArabicTextUtils.normalizeArabicForMatching(it.word) == "يجري"
        }
        assertNotNull(yajriItem)
        assertTrue(yajriItem!!.posPreview.contains("run"))
        assertTrue(yajriItem.posPreview.contains("manage"))
        assertTrue(yajriItem.posPreview.contains("flow"))
        // Every Arabic suggestion must have English meanings
        yajriSug.items.forEach { item ->
            assertTrue(
                "Expected English meanings under '${item.word}', got '${item.posPreview}'",
                item.posPreview.any { it in 'a'..'z' || it in 'A'..'Z' }
            )
        }
    }

    @Test
    fun idioms_strictNonLiteralRule_hitTheTown_and_singleWordIdiomSearch() = runBlocking {
        packManager.importPackFromJsonStream(ByteArrayInputStream(sampleV2PackJson().toByteArray()))

        // 1. "hit the town" returns real idiomatic meaning, NEVER literal "يضرب المدينة"
        val hitTown = repository.instantTranslateForSearch("hit the town")
        assertEquals("يخرج للسهر والاستمتاع في المدينة", hitTown.translation)
        assertNotEquals("يضرب المدينة", hitTown.translation)
        assertFalse(hitTown.noMeaningFound)

        // 2. Unknown idiom without definition returns noMeaningFound = true (NEVER word-by-word MT)
        val unknownIdiom = repository.instantTranslateForSearch("flibbertigibbet on ice")
        assertTrue(unknownIdiom.noMeaningFound)
        assertEquals(null, unknownIdiom.translation)

        // 3. Single-word lookup for "town" finds containing idiom "hit the town"
        val townState = repository.observeWordEntry("town")
            .first { state -> state is WordLoadState.Success && !state.isUpdating }
        val townEntry = (townState as WordLoadState.Success).entry
        val townPhrases = (townEntry.phrasalVerbsAndIdioms.map { it.phrase } + townEntry.idioms).map { it.lowercase() }
        assertTrue("hit the town" in townPhrases)

        // 4. Pack idiom with English definition only preserves def and reports noMeaningFound=true when offline ML Kit pack absent (never literal)
        val (hasDefOnlyEntry, _) = repository.inspectChipTap("definition only idiom")
        assertTrue(hasDefOnlyEntry)
        val defOnlyInstant = repository.instantTranslateForSearch("definition only idiom")
        assertTrue(defOnlyInstant.noMeaningFound)
        assertNull(defOnlyInstant.translation)
        val defOnlyEntry = repository.observeWordEntry("definition only idiom")
            .first { state -> state is WordLoadState.Success && !state.isUpdating } as WordLoadState.Success
        assertEquals(
            "To act with unusual patience in difficult times.",
            defOnlyEntry.entry.phrasalVerbsAndIdioms.first().englishMeaning
        )

        // 5. Unknown idiom chip tap opens bottom sheet with noMeaningFound=true (never literal fallback)
        val (hasUnknownEntry, unknownSheet) = repository.inspectChipTap("flibbertigibbet on ice")
        assertFalse(hasUnknownEntry)
        assertTrue(unknownSheet.noMeaningFound)
        assertNull(unknownSheet.arabicMeaning)
    }

    @Test
    fun quickTranslateScreen_liveSearchConc_and_clearButtonResetsResultsKeepingFocus() {
        runBlocking {
            packManager.importPackFromJsonStream(ByteArrayInputStream(sampleV2PackJson().toByteArray()))
        }

        val viewModel = QuickTranslateViewModel(
            repository = repository,
            initialQuery = "",
            initialDirection = LanguageDirection.EN_TO_AR
        )

        composeRule.setContent {
            AlMakhzanjiTheme(themeMode = ThemeMode.LIGHT) {
                QuickTranslateScreen(
                    viewModel = viewModel,
                    audioManager = audioManager,
                    onBack = {},
                    onOpenWord = {},
                    onOpenSentence = {}
                )
            }
        }

        // Clear button hidden while empty
        assertEquals(0, composeRule.onAllNodesWithTag("quick_translate_clear").fetchSemanticsNodes().size)

        // Type "conc" and advance Robolectric ShadowLooper + Compose clock past the 80ms debounce
        composeRule.onNodeWithTag("quick_translate_input").performTextInput("conc")
        composeRule.waitForIdle()
        assertEquals("conc", viewModel.query.value)
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idleFor(250, TimeUnit.MILLISECONDS)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.shadowMainLooper().idleFor(50, TimeUnit.MILLISECONDS)
            composeRule.mainClock.advanceTimeByFrame()
            viewModel.suggestions.value.any { it.word == "concern" }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("quick_translate_clear").assertIsDisplayed()
        // Instant box & direct_lookup must NOT appear while typing before clicking Search icon
        assertEquals(0, composeRule.onAllNodesWithTag("instant_translation_card").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("direct_lookup_button").fetchSemanticsNodes().size)

        composeRule.onNodeWithTag("quick_translate_list")
            .performScrollToNode(hasTestTag("suggestion_concern"))
        composeRule.onNodeWithTag("suggestion_concern").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_translate_list")
            .performScrollToNode(hasTestTag("suggestion_conctest500"))
        composeRule.onNodeWithTag("suggestion_conctest500").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_translate_list")
            .performScrollToNode(hasTestTag("suggestion_concert"))
        composeRule.onNodeWithTag("suggestion_concert").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_translate_list")
            .performScrollToNode(hasTestTag("suggestion_concentrate"))
        composeRule.onNodeWithTag("suggestion_concentrate").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_translate_list")
            .performScrollToNode(hasTestTag("suggestion_concept"))
        composeRule.onNodeWithTag("suggestion_concept").assertIsDisplayed()

        // Pressing the search icon reveals the instant box below the search bar
        composeRule.onNodeWithTag("quick_translate_search_button").performClick()
        composeRule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.shadowMainLooper().idleFor(200, TimeUnit.MILLISECONDS)
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.shadowMainLooper().idleFor(50, TimeUnit.MILLISECONDS)
            composeRule.mainClock.advanceTimeByFrame()
            viewModel.isSearchSubmitted.value && viewModel.instantTranslation.value != null
        }
        composeRule.onNodeWithTag("quick_translate_list")
            .performScrollToNode(hasTestTag("instant_translation_card"))
        composeRule.onNodeWithTag("instant_translation_card").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_translate_list")
            .performScrollToNode(hasTestTag("direct_lookup_button"))
        composeRule.onNodeWithTag("direct_lookup_button").assertIsDisplayed()

        // Tap X clear button: field becomes empty, results & box reset, focus remains on input
        composeRule.onNodeWithTag("quick_translate_clear").performClick()
        composeRule.waitForIdle()

        assertEquals("", viewModel.query.value)
        assertTrue(viewModel.suggestions.value.isEmpty())
        assertEquals(0, composeRule.onAllNodesWithTag("quick_translate_clear").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("instant_translation_card").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("suggestion_concern").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("quick_translate_input").assertIsFocused()

        // Confirm forbidden labels never appear in UI
        assertEquals(0, composeRule.onAllNodesWithText("ترجمة آلية", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Machine translated", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Tatoeba", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun wordFormsAndFrequencyRank_parsedFromOfflinePack() = runBlocking {
        packManager.importPackFromJsonStream(ByteArrayInputStream(sampleV2PackJson().toByteArray()))
        val state = repository.observeWordEntry("run")
            .first { it is WordLoadState.Success && !it.isUpdating } as WordLoadState.Success
        val entry = state.entry
        assertEquals(50, entry.frequencyRank)
        assertTrue(entry.wordForms.any { it.form == "run" && it.label == "pa. pple." })
        assertTrue(entry.wordForms.any { it.form == "ran" && it.label == "pa. t." })
    }

    @Test
    fun wiktionaryParser_extractsHeadwordFormsAndArabicTranslations() {
        val sampleHtml = """
            <p><strong class="Latn headword" lang="en">run</strong> (<i>third-person singular simple present</i> <b class="Latn form-of" lang="en"><a href="/wiki/runs">runs</a></b>, <i>simple past</i> <b class="Latn form-of" lang="en"><a href="/wiki/ran">ran</a></b>, <i>past participle</i> <b class="Latn form-of" lang="en"><a href="/wiki/run">run</a></b>)</p>
            <div class="NavFrame"><div class="NavHead">move swiftly</div><div class="NavContent"><ul><li>Arabic: <span class="Arab" lang="ar">يَجْرِي</span>, <span class="Arab" lang="ar">يَرْكُضُ</span>, <span class="Arab" lang="ar">يُدِيرُ</span></li></ul></div></div>
        """.trimIndent()
        val forms = WiktionaryHtmlParser.parseEnglishHeadwordForms(sampleHtml, "run")
        assertTrue(forms.any { it.form == "ran" && it.label == "pa. t." })
        val glosses = WiktionaryHtmlParser.parseEnglishTranslationsHtml(sampleHtml)
        assertTrue(glosses.isNotEmpty())
        val allAr = glosses.flatMap { it.arabicTerms }
        assertTrue("يَجْرِي" in allAr)
        assertTrue("يَرْكُضُ" in allAr)
        assertTrue("يُدِيرُ" in allAr)
    }

    @Test
    fun onlineMyMemoryApi_translatesWithoutMlKitAndCachesInRoom() = runBlocking {
        val okHttp = OkHttpClient.Builder().build()
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
        val fakeMyMemory = object : MyMemoryApi {
            override suspend fun translate(query: String, langPair: String): ResponseBody {
                return """{"responseData":{"translatedText":"المَعْنَى العَرَبِي"},"responseStatus":200}"""
                    .toResponseBody("application/json".toMediaType())
            }
        }
        val settings = UserSettingsPreferences(context)
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNet = cm.activeNetwork
        if (activeNet != null) {
            val nc = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
            org.robolectric.Shadows.shadowOf(nc)
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            org.robolectric.Shadows.shadowOf(cm).setNetworkCapabilities(activeNet, nc)
        }
        val networkMonitor = NetworkMonitor(context)
        assertTrue("Expected NetworkMonitor to report online in test", networkMonitor.isOnline())
        val translationManager = TranslationManager(context, networkMonitor, settings)
        val onlineRepo = LexiconRepository(
            dao = db.lexiconDao(),
            dictionaryApi = offlineRetrofit.create(FreeDictionaryApi::class.java),
            datamuseApi = offlineRetrofit.create(DatamuseApi::class.java),
            wiktionaryApi = offlineScalarRetrofit.create(WiktionaryApi::class.java),
            tatoebaApi = offlineScalarRetrofit.create(TatoebaApi::class.java),
            translationManager = translationManager,
            audioPlayerManager = audioManager,
            networkMonitor = networkMonitor,
            myMemoryApi = fakeMyMemory
        )
        val sentence = "I run quickly to work every morning."
        val result = onlineRepo.instantTranslateForSearch(sentence)
        assertEquals("المَعْنَى العَرَبِي", result.translation)
        assertFalse(result.missingPack)

        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(sentence.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        val cached = db.lexiconDao().getTranslationCacheBatch(
            listOf("whole|en|ar|$digest")
        )
        assertEquals(1, cached.size)
        assertEquals("المَعْنَى العَرَبِي", cached.first().translatedText)
    }
}
