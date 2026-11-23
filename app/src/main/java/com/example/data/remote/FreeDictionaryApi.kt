package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class DictionaryEntryDto(
    val word: String? = null,
    val phonetic: String? = null,
    val phonetics: List<PhoneticDto>? = null,
    val origin: String? = null,
    val meanings: List<MeaningDto>? = null
)

@JsonClass(generateAdapter = true)
data class PhoneticDto(
    val text: String? = null,
    val audio: String? = null
)

@JsonClass(generateAdapter = true)
data class MeaningDto(
    val partOfSpeech: String? = null,
    val definitions: List<DefinitionDto>? = null,
    val synonyms: List<String>? = null,
    val antonyms: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class DefinitionDto(
    val definition: String? = null,
    val example: String? = null,
    val synonyms: List<String>? = null,
    val antonyms: List<String>? = null
)

interface FreeDictionaryApi {
    @GET("api/v2/entries/en/{word}")
    suspend fun getWordDefinitions(@Path("word") word: String): List<DictionaryEntryDto>
}
