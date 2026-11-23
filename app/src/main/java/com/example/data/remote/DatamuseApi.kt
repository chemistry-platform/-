package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class DatamuseWordDto(
    val word: String? = null,
    val score: Int? = null,
    val tags: List<String>? = null
)

interface DatamuseApi {
    @GET("words")
    suspend fun getSynonyms(
        @Query("rel_syn") word: String,
        @Query("max") max: Int = 20
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getAntonyms(
        @Query("rel_ant") word: String,
        @Query("max") max: Int = 15
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getPrefixSuggestions(
        @Query("sp") spellingPattern: String,
        @Query("md") metadata: String = "p",
        @Query("max") max: Int = 12
    ): List<DatamuseWordDto>

    @GET("words")
    suspend fun getRelatedWords(
        @Query("ml") meansLike: String,
        @Query("md") metadata: String = "p",
        @Query("max") max: Int = 16
    ): List<DatamuseWordDto>
}
