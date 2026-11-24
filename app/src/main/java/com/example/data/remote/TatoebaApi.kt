package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TatoebaApi {
    @GET("en/api_v0/search")
    suspend fun searchSentencesRaw(
        @Query("query") query: String,
        @Query("from") from: String = "eng",
        @Query("to") to: String = "ara",
        @Query("orphans") orphans: String = "no",
        @Query("unapproved") unapproved: String = "no",
        @Query("sort") sort: String = "relevance",
        @Header("User-Agent") userAgent: String = "AlMakhzanjiDictionary/1.0 (Android)"
    ): ResponseBody
}
