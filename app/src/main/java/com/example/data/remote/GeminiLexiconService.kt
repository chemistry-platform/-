package com.example.data.remote

import android.util.Log
import com.example.domain.model.CollocationItem
import com.example.domain.model.PhraseItem
import com.example.domain.model.WordFamilyItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiLexiconService"

data class RawSenseInput(
    val index: Int,
    val partOfSpeech: String,
    val englishDefinition: String,
    val existingExample: String?
)

data class EnrichedSenseResult(
    val index: Int,
    val arabicMeaning: String,
    val grammarLabel: String,
    val registerTag: String,
    val exampleEnglish: String,
    val exampleArabic: String
)

data class GeminiEnrichmentResult(
    val arabicSummary: List<String>,
    val registerTags: List<String>,
    val enrichedSenses: List<EnrichedSenseResult>,
    val phrases: List<PhraseItem>,
    val wordFamily: List<WordFamilyItem>,
    val collocations: List<CollocationItem>,
    val originArabic: String?
)

class GeminiLexiconService {

    private val lexiconSchema = Schema.obj(
        properties = mapOf(
            "arabicSummary" to Schema.array(
                items = Schema.string(),
                description = "All concise Arabic meanings for the word across all contexts, ordered by frequency."
            ),
            "registerTags" to Schema.array(
                items = Schema.string(),
                description = "Register tags in Arabic such as رسمي, معاصر, دارج, عامي, أدبي, تقني when applicable."
            ),
            "senses" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "index" to Schema.integer(description = "Matching 1-based index of the input definition."),
                        "arabicMeaning" to Schema.string(description = "Accurate Arabic meaning for this specific sense."),
                        "grammarLabel" to Schema.string(description = "Concise English grammar label like [countable], [uncountable], [transitive], [intransitive], or empty string."),
                        "registerTag" to Schema.string(description = "Arabic register label like رسمي or دارج or عامي, or empty string."),
                        "exampleEnglish" to Schema.string(description = "One original, natural English example sentence illustrating this exact sense."),
                        "exampleArabic" to Schema.string(description = "Natural Arabic translation of exampleEnglish.")
                    ),
                    optionalProperties = listOf("grammarLabel", "registerTag")
                ),
                description = "One entry for EVERY input sense index."
            ),
            "phrases" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "phrase" to Schema.string(description = "Common English phrase, idiom, or phrasal verb using the word."),
                        "arabicMeaning" to Schema.string(description = "Arabic meaning of the phrase."),
                        "exampleEnglish" to Schema.string(description = "Original English example sentence."),
                        "exampleArabic" to Schema.string(description = "Arabic translation of the example sentence.")
                    )
                ),
                description = "Up to 8 common phrases or phrasal verbs."
            ),
            "wordFamily" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "word" to Schema.string(description = "Related derived English word (e.g., runner, running)."),
                        "partOfSpeech" to Schema.string(description = "English part of speech (noun, verb, adjective, adverb)."),
                        "partOfSpeechArabic" to Schema.string(description = "Arabic part of speech (اسم, فعل, صفة, ظرف)."),
                        "arabicMeaning" to Schema.string(description = "Short Arabic meaning.")
                    )
                ),
                description = "Up to 6 word family members."
            ),
            "collocations" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "collocation" to Schema.string(description = "Common English collocation."),
                        "arabicMeaning" to Schema.string(description = "Arabic translation.")
                    )
                ),
                description = "Up to 6 common collocations."
            ),
            "originArabic" to Schema.string(
                description = "Concise Arabic note explaining the word's origin/etymology, or empty string.",
                nullable = true
            )
        ),
        optionalProperties = listOf("registerTags", "originArabic")
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = lexiconSchema
                temperature = 0.3f
            }
        )
    }

    suspend fun enrichWord(
        word: String,
        rawSenses: List<RawSenseInput>,
        originHint: String?
    ): Result<GeminiEnrichmentResult> {
        val sensesBlock = rawSenses.joinToString(separator = "\n") { sense ->
            "${sense.index}. [${sense.partOfSpeech}] ${sense.englishDefinition}"
        }
        val prompt = """
            You are an expert bilingual English-Arabic lexicographer for the dictionary "معجم المخزنجي".
            Analyze the English word "$word" and its listed senses below.
            Rules:
            - Generate ORIGINAL example sentences (do NOT copy copyrighted dictionaries).
            - Provide an entry in "senses" for EVERY numbered sense below (indices 1..${rawSenses.size}).
            - Keep Arabic translations crisp, accurate, and natural in Modern Standard Arabic.
            - For each sense, provide ONE original English example sentence and its Arabic translation.
            - Include ALL common Arabic meanings of "$word" in "arabicSummary" (for example for "run": ["يجري", "يدير", "يشتغل", "يسيل", "يترشح", "يمتد"]).
            - Origin hint from open API: ${originHint ?: "none"}
            
            Senses:
            $sensesBlock
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val jsonText = response.text ?: throw IllegalStateException("Empty Gemini response")
            Result.success(parseEnrichmentJson(jsonText))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini enrichment timed out for $word", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini enrichment failed for $word", e)
            Result.failure(e)
        }
    }

    private fun parseEnrichmentJson(jsonText: String): GeminiEnrichmentResult {
        val root = JSONObject(jsonText)

        val summaryArr = root.optJSONArray("arabicSummary")
        val arabicSummary = buildList {
            if (summaryArr != null) {
                for (i in 0 until summaryArr.length()) {
                    val value = summaryArr.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }

        val registerArr = root.optJSONArray("registerTags")
        val registerTags = buildList {
            if (registerArr != null) {
                for (i in 0 until registerArr.length()) {
                    val value = registerArr.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }

        val sensesArr = root.optJSONArray("senses")
        val enrichedSenses = buildList {
            if (sensesArr != null) {
                for (i in 0 until sensesArr.length()) {
                    val obj = sensesArr.optJSONObject(i) ?: continue
                    add(
                        EnrichedSenseResult(
                            index = obj.optInt("index", i + 1),
                            arabicMeaning = obj.optString("arabicMeaning").trim(),
                            grammarLabel = obj.optString("grammarLabel").trim(),
                            registerTag = obj.optString("registerTag").trim(),
                            exampleEnglish = obj.optString("exampleEnglish").trim(),
                            exampleArabic = obj.optString("exampleArabic").trim()
                        )
                    )
                }
            }
        }

        val phrasesArr = root.optJSONArray("phrases")
        val phrases = buildList {
            if (phrasesArr != null) {
                for (i in 0 until phrasesArr.length()) {
                    val obj = phrasesArr.optJSONObject(i) ?: continue
                    val phrase = obj.optString("phrase").trim()
                    val meaning = obj.optString("arabicMeaning").trim()
                    if (phrase.isNotEmpty() && meaning.isNotEmpty()) {
                        add(
                            PhraseItem(
                                phrase = phrase,
                                arabicMeaning = meaning,
                                exampleEnglish = obj.optString("exampleEnglish").trim(),
                                exampleArabic = obj.optString("exampleArabic").trim()
                            )
                        )
                    }
                }
            }
        }

        val familyArr = root.optJSONArray("wordFamily")
        val wordFamily = buildList {
            if (familyArr != null) {
                for (i in 0 until familyArr.length()) {
                    val obj = familyArr.optJSONObject(i) ?: continue
                    val w = obj.optString("word").trim()
                    if (w.isNotEmpty()) {
                        add(
                            WordFamilyItem(
                                word = w,
                                partOfSpeech = obj.optString("partOfSpeech").trim(),
                                partOfSpeechArabic = obj.optString("partOfSpeechArabic").trim(),
                                arabicMeaning = obj.optString("arabicMeaning").trim()
                            )
                        )
                    }
                }
            }
        }

        val collocationsArr = root.optJSONArray("collocations")
        val collocations = buildList {
            if (collocationsArr != null) {
                for (i in 0 until collocationsArr.length()) {
                    val obj = collocationsArr.optJSONObject(i) ?: continue
                    val col = obj.optString("collocation").trim()
                    val meaning = obj.optString("arabicMeaning").trim()
                    if (col.isNotEmpty() && meaning.isNotEmpty()) {
                        add(CollocationItem(collocation = col, arabicMeaning = meaning))
                    }
                }
            }
        }

        val originArabic = root.optString("originArabic").trim().takeIf {
            it.isNotEmpty() && it != "null"
        }

        return GeminiEnrichmentResult(
            arabicSummary = arabicSummary,
            registerTags = registerTags,
            enrichedSenses = enrichedSenses,
            phrases = phrases,
            wordFamily = wordFamily,
            collocations = collocations,
            originArabic = originArabic
        )
    }
}
