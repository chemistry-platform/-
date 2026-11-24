package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface WiktionaryApi {
    @GET("api/rest_v1/page/definition/{word}")
    suspend fun getDefinitionRaw(
        @Path("word") wordUnderscored: String,
        @Header("User-Agent") userAgent: String = "AlMakhzanjiDictionary/1.0 (Android; contact: dev@almakhzanji.org)",
        @Header("Accept") accept: String = "application/json"
    ): ResponseBody
}
