package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String? = null,
    val name: String? = null,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null,
    val assets: List<GitHubAssetDto>? = null
)

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    val name: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
    val size: Long? = null
)

interface GitHubReleaseApi {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 15,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Header("User-Agent") userAgent: String = "Al-Makhzanji-Android-Updater"
    ): List<GitHubReleaseDto>
}
