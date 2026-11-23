package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_words")
data class CachedWordEntity(
    @PrimaryKey val word: String,
    val arabicSummary: String,
    val partOfSpeechSummary: String,
    val payloadJson: String,
    val isAiComplete: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val queryKey: String,
    val displayQuery: String,
    val resolvedEnglishWord: String,
    val arabicPreview: String,
    val direction: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_words")
data class FavoriteWordEntity(
    @PrimaryKey val word: String,
    val arabicSummary: String,
    val usIpa: String,
    val partOfSpeechArabic: String,
    val addedAt: Long = System.currentTimeMillis()
)
