package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class GitHubReleaseDto(
    @Json(name = "tag_name") val tagName: String? = null,
    val name: String? = null,
    val assets: List<GitHubAssetDto>? = null
)

@JsonClass(generateAdapter = true)
data class GitHubAssetDto(
    val name: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
    val size: Long? = null
)

interface GitHubReleaseApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Accept") accept: String = "application/vnd.github+json",
        @Header("User-Agent") userAgent: String = "Al-Makhzanji-Android-Updater"
    ): GitHubReleaseDto
}
