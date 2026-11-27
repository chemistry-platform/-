package com.example.data.remote

import com.example.core.ArabicTextUtils
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Free MyMemory Translation API client (last-resort online translation fallback).
 */
interface MyMemoryApi {

    @GET("get")
    suspend fun translate(
        @Query("q") query: String,
        @Query("langpair") langPair: String
    ): ResponseBody

    companion object {
        private val HTML_TAG_REGEX = Regex("<[^>]+>")

        fun parseTranslatedText(
            rawJson: String,
            sourceText: String,
            wantArabic: Boolean
        ): String? {
            if (rawJson.isBlank()) return null
            return runCatching {
                val root = JSONObject(rawJson)
                val status = root.optInt("responseStatus", 200)
                if (status != 200) return null

                val candidates = mutableListOf<String>()
                root.optJSONObject("responseData")
                    ?.optString("translatedText", "")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { candidates.add(it) }

                val matches = root.optJSONArray("matches")
                if (matches != null) {
                    for (i in 0 until matches.length()) {
                        val mObj = matches.optJSONObject(i) ?: continue
                        val tr = mObj.optString("translation", "").trim()
                        if (tr.isNotBlank()) candidates.add(tr)
                    }
                }

                val cleanSource = sourceText.trim()
                for (raw in candidates) {
                    val cleaned = unescapeBasic(HTML_TAG_REGEX.replace(raw, " "))
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (cleaned.isBlank()) continue
                    if (cleaned.contains("MYMEMORY WARNING", ignoreCase = true)) continue
                    if (cleaned.startsWith("INVALID LANGUAGE PAIR", ignoreCase = true)) continue
                    if (cleaned.equals(cleanSource, ignoreCase = true)) continue
                    if (wantArabic) {
                        if (ArabicTextUtils.containsArabicLetters(cleaned)) return cleaned
                    } else {
                        if (cleaned.any { it in 'a'..'z' || it in 'A'..'Z' }) return cleaned
                    }
                }
                null
            }.getOrNull()
        }

        private fun unescapeBasic(input: String): String = input
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
