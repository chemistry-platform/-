package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LanguageDirection {
    EN_TO_AR,
    AR_TO_EN
}

@Serializable
enum class SenseSource {
    FREE_DICTIONARY,
    WIKTIONARY,
    OFFLINE_PACK
}

@Serializable
data class SenseItem(
    val index: Int,
    val partOfSpeech: String,
    val partOfSpeechArabic: String,
    val definition: String,
    val examples: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val source: SenseSource = SenseSource.FREE_DICTIONARY
)

@Serializable
data class PosSenseGroup(
    val partOfSpeech: String,
    val partOfSpeechArabic: String,
    val senses: List<SenseItem>
)

@Serializable
data class WordFamilyItem(
    val word: String,
    val partOfSpeech: String,
    val partOfSpeechArabic: String
)

@Serializable
data class ConfusableCandidate(
    val word: String,
    val labels: List<String>
)

@Serializable
data class ConfusableWordDetail(
    val word: String,
    val labels: List<String> = emptyList(),
    val partOfSpeech: String = "",
    val shortDefinition: String = "",
    val example: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null
)

@Serializable
data class TatoebaSentence(
    val english: String,
    val arabic: String
)

@Serializable
data class WordEntry(
    val word: String,
    val usIpa: String = "",
    val ukIpa: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val posGroups: List<PosSenseGroup> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    val prepositionsAndPhrases: List<String> = emptyList(),
    val wordFamily: List<WordFamilyItem> = emptyList(),
    val confusableCandidates: List<ConfusableCandidate> = emptyList(),
    val confusableDetails: Map<String, ConfusableWordDetail> = emptyMap(),
    val generalTerms: List<String> = emptyList(),
    val specificTerms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList(),
    val tatoebaSentences: List<TatoebaSentence> = emptyList(),
    val hasWiktionaryAttribution: Boolean = false,
    val hasTatoebaAttribution: Boolean = false,
    val isFromOfflinePack: Boolean = false,
    val isFromCache: Boolean = false
)

@Serializable
data class SearchSuggestion(
    val word: String,
    val partOfSpeech: String = "",
    val summaryDefinition: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val isLocal: Boolean = false
)
