package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-(word, source) cache for external dictionary APIs (Section 4.9 & 15).
 * source values: "freedict", "wiktionary", "wiktionary_ar", "datamuse", "tatoeba", "tatoeba_ar", "confusable_entry"
 */
@Entity(
    tableName = "api_cache",
    indices = [Index(value = ["word", "source"])]
)
data class ApiCacheEntity(
    @PrimaryKey val cacheKey: String,
    val word: String,
    val source: String,
    val payloadJson: String,
    val timestampMs: Long = System.currentTimeMillis()
)

/**
 * Lightweight local word index built from cached lookups for instant prefix suggestions & favorites metadata.
 */
@Entity(
    tableName = "word_index",
    indices = [
        Index(value = ["normalizedWord", "lang"]),
        Index(value = ["frequencyRank"])
    ]
)
data class WordIndexEntity(
    @PrimaryKey val word: String,
    val normalizedWord: String = word,
    val lang: String = "en",
    val summaryDefinition: String,
    val partOfSpeech: String,
    val previewSummary: String = "",
    val frequencyRank: Int = Int.MAX_VALUE,
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Offline Dictionary Pack storage imported via streaming GZIP + JsonReader (Section 5, 12.2 & 15.7).
 */
@Entity(
    tableName = "offline_pack_words",
    indices = [
        Index(value = ["normalizedWord", "lang"]),
        Index(value = ["hasAwlTag"]),
        Index(value = ["frequencyRank"])
    ]
)
data class OfflinePackWordEntity(
    @PrimaryKey val word: String,
    val normalizedWord: String = word,
    val lang: String = "en",
    val phonetic: String = "",
    val summaryDefinition: String = "",
    val previewSummary: String = "",
    val posTagsCsv: String = "",
    val frequencyRank: Int = Int.MAX_VALUE,
    val tagsCsv: String = "",
    val hasAwlTag: Boolean = false,
    val payloadJson: String
)

/**
 * Reverse Arabic -> English index built from Wiktionary translations & Open Multilingual WordNet Arabic.
 * Enables instant lookup of all English words matching any vocalized or unvocalized Arabic query.
 */
@Entity(
    tableName = "reverse_arabic_index",
    indices = [
        Index(value = ["normalizedArabic"]),
        Index(value = ["normalizedArabic", "vocalizedArabic"]),
        Index(value = ["englishWord"]),
        Index(value = ["frequencyRank"])
    ]
)
data class ReverseArabicEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedArabic: String,
    val vocalizedArabic: String,
    val englishWord: String,
    val partOfSpeech: String = "",
    val englishDefinition: String = "",
    val frequencyRank: Int = Int.MAX_VALUE,
    val source: String = "pack"
)

/**
 * Common idioms, phrasal verbs, and collocations imported from Wiktionary/WordNet/Tatoeba pack.
 */
@Entity(
    tableName = "pack_idioms",
    indices = [
        Index(value = ["normalizedPhrase"]),
        Index(value = ["frequencyRank"])
    ]
)
data class PackIdiomEntity(
    @PrimaryKey val phrase: String,
    val normalizedPhrase: String,
    val englishDefinition: String,
    val arabicMeaning: String = "",
    val exampleSentence: String = "",
    val exampleArabic: String = "",
    val category: String = "IDIOM",
    val frequencyRank: Int = Int.MAX_VALUE,
    val tokensSearch: String = ""
)

/**
 * Room-backed cache for ML Kit translations of dictionary senses & example sentences (Section 19.10).
 */
@Entity(tableName = "translation_cache")
data class TranslationCacheEntity(
    @PrimaryKey val cacheKey: String,
    val translatedText: String,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["normalizedQuery"])]
)
data class SearchHistoryEntity(
    @PrimaryKey val queryKey: String,
    val normalizedQuery: String = queryKey,
    val displayQuery: String,
    val resolvedEnglishWord: String,
    val previewSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_words")
data class FavoriteWordEntity(
    @PrimaryKey val word: String,
    val summaryText: String,
    val usIpa: String,
    val partOfSpeech: String,
    val addedAt: Long = System.currentTimeMillis()
)
