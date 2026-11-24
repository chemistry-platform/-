package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LexiconDao {
    // --- API Cache keyed by (word, source) ---
    @Query("SELECT * FROM api_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getApiCache(cacheKey: String): ApiCacheEntity?

    @Query("SELECT * FROM api_cache WHERE word = :normalizedWord")
    suspend fun getAllSourcesForWord(normalizedWord: String): List<ApiCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApiCache(entity: ApiCacheEntity)

    @Query("DELETE FROM api_cache")
    suspend fun clearApiCache()

    // --- Word Index for fast local prefix suggestions ---
    @Query("SELECT * FROM word_index WHERE word LIKE :prefix || '%' ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun searchIndexByPrefix(prefix: String, limit: Int = 10): List<WordIndexEntity>

    @Query("SELECT * FROM word_index WHERE word = :normalizedWord LIMIT 1")
    suspend fun getWordIndex(normalizedWord: String): WordIndexEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordIndex(entity: WordIndexEntity)

    @Query("SELECT COUNT(*) FROM word_index")
    fun observeCachedCount(): Flow<Int>

    @Query("DELETE FROM word_index")
    suspend fun clearWordIndex()

    // --- Offline Dictionary Pack ---
    @Query("SELECT * FROM offline_pack_words WHERE word = :normalizedWord LIMIT 1")
    suspend fun getOfflinePackWord(normalizedWord: String): OfflinePackWordEntity?

    @Query("SELECT * FROM offline_pack_words WHERE word LIKE :prefix || '%' ORDER BY word ASC LIMIT :limit")
    suspend fun searchOfflinePackByPrefix(prefix: String, limit: Int = 10): List<OfflinePackWordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflinePackBatch(words: List<OfflinePackWordEntity>)

    @Query("SELECT COUNT(*) FROM offline_pack_words")
    fun observeOfflinePackCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM offline_pack_words")
    suspend fun getOfflinePackCount(): Int

    @Query("DELETE FROM offline_pack_words")
    suspend fun clearOfflinePack()

    // --- Search History (max 50 unique, newest first) ---
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: SearchHistoryEntity)

    @Query(
        "DELETE FROM search_history WHERE queryKey NOT IN " +
            "(SELECT queryKey FROM search_history ORDER BY timestamp DESC LIMIT 50)"
    )
    suspend fun trimHistoryTo50()

    @Query("DELETE FROM search_history WHERE queryKey = :queryKey")
    suspend fun deleteHistoryItem(queryKey: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    // --- Favorites ---
    @Query("SELECT * FROM favorite_words ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteWordEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_words WHERE word = :normalizedWord)")
    fun observeIsFavorite(normalizedWord: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteWordEntity)

    @Query("DELETE FROM favorite_words WHERE word = :normalizedWord")
    suspend fun deleteFavorite(normalizedWord: String)
}
