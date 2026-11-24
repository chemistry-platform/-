package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

private const val WIKTIONARY_UA = "AlMakhzanjiDictionary/1.0 (Android; contact: dev@almakhzanji.org)"

interface WiktionaryApi {
    @GET("api/rest_v1/page/definition/{word}")
    suspend fun getDefinitionRaw(
        @Path("word") wordUnderscored: String,
        @Header("User-Agent") userAgent: String = WIKTIONARY_UA,
        @Header("Accept") accept: String = "application/json"
    ): ResponseBody

    @GET("w/api.php?action=parse&prop=sections&format=json&redirects=1")
    suspend fun getSectionsRaw(
        @Query("page") page: String,
        @Header("User-Agent") userAgent: String = WIKTIONARY_UA,
        @Header("Accept") accept: String = "application/json"
    ): ResponseBody

    @GET("w/api.php?action=parse&prop=links&format=json&redirects=1")
    suspend fun getSectionLinksRaw(
        @Query("page") page: String,
        @Query("section") sectionIndex: String,
        @Header("User-Agent") userAgent: String = WIKTIONARY_UA,
        @Header("Accept") accept: String = "application/json"
    ): ResponseBody
}
