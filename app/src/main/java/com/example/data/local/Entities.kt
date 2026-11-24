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
    indices = [Index(value = ["normalizedWord", "lang"])]
)
data class WordIndexEntity(
    @PrimaryKey val word: String,
    val normalizedWord: String = word,
    val lang: String = "en",
    val summaryDefinition: String,
    val partOfSpeech: String,
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Offline Dictionary Pack storage imported via streaming GZIP + JsonReader (Section 5 & 15.7).
 */
@Entity(
    tableName = "offline_pack_words",
    indices = [Index(value = ["normalizedWord", "lang"])]
)
data class OfflinePackWordEntity(
    @PrimaryKey val word: String,
    val normalizedWord: String = word,
    val lang: String = "en",
    val phonetic: String = "",
    val summaryDefinition: String = "",
    val payloadJson: String
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
