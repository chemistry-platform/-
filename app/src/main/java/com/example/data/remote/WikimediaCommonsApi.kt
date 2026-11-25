package com.example.data.remote

import com.example.core.Accent
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.Locale

interface WikimediaCommonsApi {
    @GET("w/api.php")
    suspend fun getImageInfoByTitles(
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("prop") prop: String = "imageinfo",
        @Query("iiprop") iiProp: String = "url",
        @Query("titles") titles: String
    ): ResponseBody

    @GET("w/api.php")
    suspend fun searchAudioFiles(
        @Query("action") action: String = "query",
        @Query("format") format: String = "json",
        @Query("list") list: String = "allimages",
        @Query("aiprefix") prefix: String,
        @Query("ailimit") limit: Int = 6
    ): ResponseBody

    companion object {
        private val AUDIO_EXTS = listOf(".mp3", ".ogg", ".oga", ".wav", ".flac", ".m4a")

        fun buildCandidateTitles(word: String, accent: Accent): String {
            val clean = word.trim().lowercase(Locale.US)
                .replace(Regex("\\s+"), "_")
                .replace(Regex("[^a-z0-9_'-]"), "")
            if (clean.isBlank()) return ""
            return when (accent) {
                Accent.US -> listOf(
                    "File:En-us-$clean.ogg",
                    "File:En-us-$clean.oga",
                    "File:En-us-$clean.mp3",
                    "File:En-us-$clean.wav",
                    "File:En-us-$clean-noun.ogg",
                    "File:En-us-$clean-verb.ogg"
                ).joinToString("|")
                Accent.UK -> listOf(
                    "File:En-uk-$clean.ogg",
                    "File:En-uk-$clean.oga",
                    "File:En-gb-$clean.ogg",
                    "File:En-gb-$clean.oga",
                    "File:En-uk-$clean.mp3",
                    "File:En-gb-$clean.mp3"
                ).joinToString("|")
            }
        }

        fun buildAllImagesPrefixes(word: String, accent: Accent): List<String> {
            val clean = word.trim().lowercase(Locale.US)
                .replace(Regex("\\s+"), "_")
                .replace(Regex("[^a-z0-9_'-]"), "")
            if (clean.isBlank()) return emptyList()
            return when (accent) {
                Accent.US -> listOf("En-us-$clean.")
                Accent.UK -> listOf("En-uk-$clean.", "En-gb-$clean.")
            }
        }

        fun parseImageInfoAudioUrl(rawJson: String): String? {
            return runCatching {
                val root = JSONObject(rawJson)
                val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return@runCatching null
                val candidates = ArrayList<String>()
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val pageKey = keys.next()
                    val pageObj = pages.optJSONObject(pageKey) ?: continue
                    if (pageObj.has("missing")) continue
                    val imageInfo = pageObj.optJSONArray("imageinfo") ?: continue
                    for (i in 0 until imageInfo.length()) {
                        val infoObj = imageInfo.optJSONObject(i) ?: continue
                        val url = normalizeHttpsUrl(infoObj.optString("url"))
                        if (url != null && isAudioFileUrl(url)) {
                            candidates.add(url)
                        }
                    }
                }
                pickBestAudioCandidate(candidates)
            }.getOrNull()
        }

        fun parseAllImagesAudioUrl(rawJson: String): String? {
            return runCatching {
                val root = JSONObject(rawJson)
                val arr = root.optJSONObject("query")?.optJSONArray("allimages") ?: return@runCatching null
                val candidates = ArrayList<String>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val url = normalizeHttpsUrl(item.optString("url"))
                    if (url != null && isAudioFileUrl(url)) {
                        candidates.add(url)
                    }
                }
                pickBestAudioCandidate(candidates)
            }.getOrNull()
        }

        fun isAudioFileUrl(url: String): Boolean {
            val lower = url.substringBefore('?').lowercase(Locale.US)
            return AUDIO_EXTS.any { lower.endsWith(it) }
        }

        fun pickBestAudioCandidate(urls: List<String>): String? {
            if (urls.isEmpty()) return null
            // Prefer MP3/WAV for broadest Android MediaPlayer compatibility, then OGG/OGA
            return urls.firstOrNull { it.lowercase(Locale.US).endsWith(".mp3") }
                ?: urls.firstOrNull { it.lowercase(Locale.US).endsWith(".wav") }
                ?: urls.firstOrNull { it.lowercase(Locale.US).endsWith(".ogg") }
                ?: urls.firstOrNull { it.lowercase(Locale.US).endsWith(".oga") }
                ?: urls.firstOrNull()
        }

        fun normalizeHttpsUrl(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return when {
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> null
            }
        }
    }
}
