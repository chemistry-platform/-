package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LexiconDao {
    @Query("SELECT * FROM cached_words WHERE word = :normalizedWord LIMIT 1")
    suspend fun getCachedWord(normalizedWord: String): CachedWordEntity?

    @Query("SELECT * FROM cached_words WHERE word LIKE :prefix || '%' ORDER BY updatedAt DESC LIMIT 10")
    suspend fun searchCachedWordsByPrefix(prefix: String): List<CachedWordEntity>

    @Query("SELECT * FROM cached_words WHERE arabicSummary LIKE '%' || :arabicQuery || '%' ORDER BY updatedAt DESC LIMIT 10")
    suspend fun searchCachedWordsByArabic(arabicQuery: String): List<CachedWordEntity>

    @Query("SELECT COUNT(*) FROM cached_words")
    fun observeCachedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedWord(entity: CachedWordEntity)

    @Query("DELETE FROM cached_words")
    suspend fun clearCachedWords()

    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 25")
    fun observeRecentHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE queryKey = :queryKey")
    suspend fun deleteHistoryItem(queryKey: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM favorite_words ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteWordEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_words WHERE word = :normalizedWord)")
    fun observeIsFavorite(normalizedWord: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteWordEntity)

    @Query("DELETE FROM favorite_words WHERE word = :normalizedWord")
    suspend fun deleteFavorite(normalizedWord: String)
}
