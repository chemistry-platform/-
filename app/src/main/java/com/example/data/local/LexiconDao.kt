package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
    @Query(
        "SELECT * FROM word_index " +
            "WHERE (word LIKE :prefix || '%' OR normalizedWord LIKE :prefix || '%') " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun searchIndexByPrefix(prefix: String, limit: Int = 10): List<WordIndexEntity>

    @Query(
        "SELECT * FROM word_index " +
            "WHERE normalizedWord >= :prefix AND normalizedWord < :prefixEnd " +
            "ORDER BY CASE WHEN normalizedWord = :prefix THEN 0 ELSE 1 END, frequencyRank ASC, LENGTH(word) ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun searchIndexPrefixRange(
        prefix: String,
        prefixEnd: String,
        limit: Int = 40,
        offset: Int = 0
    ): List<WordIndexEntity>

    @Query(
        "SELECT * FROM word_index " +
            "WHERE normalizedWord LIKE '%' || :query || '%' " +
            "AND NOT (normalizedWord >= :prefix AND normalizedWord < :prefixEnd) " +
            "ORDER BY frequencyRank ASC, LENGTH(word) ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun searchIndexContains(
        query: String,
        prefix: String,
        prefixEnd: String,
        limit: Int = 40,
        offset: Int = 0
    ): List<WordIndexEntity>

    @Query("SELECT * FROM word_index WHERE word = :normalizedWord OR normalizedWord = :normalizedWord LIMIT 1")
    suspend fun getWordIndex(normalizedWord: String): WordIndexEntity?

    @Query("SELECT * FROM word_index WHERE normalizedWord IN (:normalizedWords)")
    suspend fun getWordIndexesByNormalized(normalizedWords: List<String>): List<WordIndexEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWordIndex(entity: WordIndexEntity)

    @Query("SELECT COUNT(*) FROM word_index")
    fun observeCachedCount(): Flow<Int>

    @Query("DELETE FROM word_index")
    suspend fun clearWordIndex()

    // --- Offline Dictionary Pack ---
    @Query("SELECT * FROM offline_pack_words WHERE word = :key OR normalizedWord = :key LIMIT 1")
    suspend fun getOfflinePackWord(key: String): OfflinePackWordEntity?

    @Query(
        "SELECT * FROM offline_pack_words " +
            "WHERE (word = :key OR normalizedWord = :key) AND lang = :lang LIMIT 1"
    )
    suspend fun getOfflinePackWordByLang(key: String, lang: String): OfflinePackWordEntity?

    @Query(
        "SELECT * FROM offline_pack_words " +
            "WHERE (word LIKE :prefix || '%' OR normalizedWord LIKE :prefix || '%') " +
            "ORDER BY word ASC LIMIT :limit"
    )
    suspend fun searchOfflinePackByPrefix(prefix: String, limit: Int = 10): List<OfflinePackWordEntity>

    @Query(
        "SELECT * FROM offline_pack_words " +
            "WHERE normalizedWord >= :prefix AND normalizedWord < :prefixEnd " +
            "ORDER BY CASE WHEN normalizedWord = :prefix THEN 0 ELSE 1 END, frequencyRank ASC, LENGTH(word) ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun searchOfflinePackPrefixRange(
        prefix: String,
        prefixEnd: String,
        limit: Int = 40,
        offset: Int = 0
    ): List<OfflinePackWordEntity>

    @Query(
        "SELECT * FROM offline_pack_words " +
            "WHERE normalizedWord LIKE '%' || :query || '%' " +
            "AND NOT (normalizedWord >= :prefix AND normalizedWord < :prefixEnd) " +
            "ORDER BY frequencyRank ASC, LENGTH(word) ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun searchOfflinePackContains(
        query: String,
        prefix: String,
        prefixEnd: String,
        limit: Int = 40,
        offset: Int = 0
    ): List<OfflinePackWordEntity>

    @Query("SELECT * FROM offline_pack_words WHERE hasAwlTag = 1 ORDER BY word ASC")
    fun observeAwlOfflinePackWords(): Flow<List<OfflinePackWordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflinePackBatch(words: List<OfflinePackWordEntity>)

    @Query("SELECT COUNT(*) FROM offline_pack_words")
    fun observeOfflinePackCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM offline_pack_words")
    suspend fun getOfflinePackCount(): Int

    @Query("DELETE FROM offline_pack_words")
    suspend fun clearOfflinePack()

    // --- Reverse Arabic -> English Index ---
    @Query(
        "SELECT * FROM reverse_arabic_index " +
            "WHERE normalizedArabic = :normalizedArabic " +
            "ORDER BY frequencyRank ASC, englishWord ASC"
    )
    suspend fun getReverseArabicMatches(normalizedArabic: String): List<ReverseArabicEntryEntity>

    @Query(
        "SELECT * FROM reverse_arabic_index " +
            "WHERE englishWord IN (:englishWords) " +
            "ORDER BY frequencyRank ASC, englishWord ASC"
    )
    suspend fun getReverseArabicForEnglishWords(englishWords: List<String>): List<ReverseArabicEntryEntity>

    @Query(
        "SELECT * FROM reverse_arabic_index " +
            "WHERE englishWord = :englishWord " +
            "ORDER BY frequencyRank ASC"
    )
    suspend fun getReverseArabicForEnglish(englishWord: String): List<ReverseArabicEntryEntity>

    @Query(
        "SELECT * FROM reverse_arabic_index " +
            "WHERE normalizedArabic IN (:normalizedWords) " +
            "ORDER BY frequencyRank ASC, englishWord ASC"
    )
    suspend fun getReverseArabicForNormalizedWords(normalizedWords: List<String>): List<ReverseArabicEntryEntity>

    @Query(
        "SELECT * FROM reverse_arabic_index " +
            "WHERE normalizedArabic >= :prefix AND normalizedArabic < :prefixEnd " +
            "ORDER BY CASE WHEN normalizedArabic = :prefix THEN 0 ELSE 1 END, frequencyRank ASC, LENGTH(normalizedArabic) ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun searchReverseArabicByPrefix(
        prefix: String,
        prefixEnd: String,
        limit: Int = 60,
        offset: Int = 0
    ): List<ReverseArabicEntryEntity>

    @Query(
        "SELECT * FROM reverse_arabic_index " +
            "WHERE normalizedArabic LIKE '%' || :query || '%' " +
            "AND NOT (normalizedArabic >= :prefix AND normalizedArabic < :prefixEnd) " +
            "ORDER BY frequencyRank ASC, LENGTH(normalizedArabic) ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun searchReverseArabicContains(
        query: String,
        prefix: String,
        prefixEnd: String,
        limit: Int = 60,
        offset: Int = 0
    ): List<ReverseArabicEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReverseArabicBatch(items: List<ReverseArabicEntryEntity>)

    @Query("SELECT COUNT(*) FROM reverse_arabic_index")
    suspend fun getReverseArabicCount(): Int

    @Query("DELETE FROM reverse_arabic_index")
    suspend fun clearReverseArabicIndex()

    // --- Pack Idioms, Phrasal Verbs & Collocations ---
    @Query("SELECT * FROM pack_idioms WHERE phrase = :phraseLower OR normalizedPhrase = :phraseLower LIMIT 1")
    suspend fun getPackIdiomExact(phraseLower: String): PackIdiomEntity?

    @Query(
        "SELECT * FROM pack_idioms " +
            "WHERE tokensSearch LIKE '% ' || :wordLower || ' %' " +
            "ORDER BY frequencyRank ASC, phrase ASC LIMIT :limit"
    )
    suspend fun findIdiomsContainingWord(wordLower: String, limit: Int = 30): List<PackIdiomEntity>

    @Query(
        "SELECT * FROM pack_idioms " +
            "WHERE normalizedPhrase >= :prefix AND normalizedPhrase < :prefixEnd " +
            "ORDER BY CASE WHEN normalizedPhrase = :prefix THEN 0 ELSE 1 END, frequencyRank ASC, LENGTH(phrase) ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun searchPackIdiomsByPrefix(
        prefix: String,
        prefixEnd: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<PackIdiomEntity>

    @Query(
        "SELECT * FROM pack_idioms " +
            "WHERE normalizedPhrase LIKE '%' || :query || '%' " +
            "AND NOT (normalizedPhrase >= :prefix AND normalizedPhrase < :prefixEnd) " +
            "ORDER BY frequencyRank ASC, LENGTH(phrase) ASC " +
            "LIMIT :limit OFFSET :offset"
    )
    suspend fun searchPackIdiomsByContains(
        query: String,
        prefix: String,
        prefixEnd: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<PackIdiomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackIdiomsBatch(items: List<PackIdiomEntity>)

    @Query("SELECT COUNT(*) FROM pack_idioms")
    suspend fun getPackIdiomCount(): Int

    @Query("DELETE FROM pack_idioms")
    suspend fun clearPackIdioms()

    @Transaction
    suspend fun clearAllOfflinePackData() {
        clearOfflinePack()
        clearPackIdioms()
        clearReverseArabicIndex()
    }

    // --- Room Sense/Example Translation Cache (Section 19.10) ---
    @Query("SELECT * FROM translation_cache WHERE cacheKey IN (:keys)")
    suspend fun getTranslationCacheBatch(keys: List<String>): List<TranslationCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTranslationCacheBatch(items: List<TranslationCacheEntity>)

    @Query("DELETE FROM translation_cache")
    suspend fun clearTranslationCache()

    // --- Search History (max 50 unique, newest first) ---
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentHistory(): Flow<List<SearchHistoryEntity>>

    @Query(
        "SELECT * FROM search_history " +
            "WHERE queryKey LIKE :prefix || '%' OR normalizedQuery LIKE :prefix || '%' " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun searchHistoryByPrefix(prefix: String, limit: Int = 10): List<SearchHistoryEntity>

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
