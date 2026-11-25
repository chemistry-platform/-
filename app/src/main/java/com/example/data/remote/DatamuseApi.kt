package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class DatamuseWordDto(
    val word: String? = null,
    val score: Int? = null,
    val tags: List<String>? = null,
    val defs: List<String>? = null
)

interface DatamuseApi {
    @GET("sug")
    suspend fun getAutocompleteSuggestions(
        @Query("s") query: String,
        @Query("max") max: Int = 10
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getSpellingMatches(
        @Query("sp") pattern: String,
        @Query("md") metadata: String = "p",
        @Query("max") max: Int = 10
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getSynonyms(
        @Query("rel_syn") word: String,
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getAntonyms(
        @Query("rel_ant") word: String,
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getMeansLike(
        @Query("ml") word: String,
        @Query("md") metadata: String = "p",
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getTriggeredWords(
        @Query("rel_trg") word: String,
        @Query("md") metadata: String = "p",
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getAdjectiveCollocations(
        @Query("rel_jjb") nounWord: String,
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getNounCollocations(
        @Query("rel_jja") adjWord: String,
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getFollowers(
        @Query("rel_bga") word: String,
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getMoreGeneralTerms(
        @Query("rel_spc") word: String,
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getMoreSpecificTerms(
        @Query("rel_gen") word: String,
        @Query("max") max: Int = 30
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getHomophones(
        @Query("rel_hom") word: String,
        @Query("max") max: Int = 15
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getSoundsLike(
        @Query("sl") word: String,
        @Query("max") max: Int = 15
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getSimilarSpelling(
        @Query("sp") word: String,
        @Query("max") max: Int = 15
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getWordNetDefinitions(
        @Query("sp") word: String,
        @Query("md") metadata: String = "dp",
        @Query("max") max: Int = 1
    ): List<DatamuseWordDto>
}
