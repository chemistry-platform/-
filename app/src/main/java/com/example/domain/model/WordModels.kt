package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LanguageDirection {
    EN_TO_AR,
    AR_TO_EN
}

@Serializable
data class WordEntry(
    val word: String,
    val usIpa: String = "",
    val ukIpa: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val arabicMeaningsSummary: List<String> = emptyList(),
    val partsOfSpeech: List<String> = emptyList(),
    val registerTags: List<String> = emptyList(),
    val synonymGroups: List<SynonymGroup> = emptyList(),
    val allSynonyms: List<String> = emptyList(),
    val allAntonyms: List<String> = emptyList(),
    val senses: List<SenseItem> = emptyList(),
    val phrases: List<PhraseItem> = emptyList(),
    val wordFamily: List<WordFamilyItem> = emptyList(),
    val collocations: List<CollocationItem> = emptyList(),
    val wordNetSynsets: List<WordNetSynset> = emptyList(),
    val origin: String? = null,
    val aiStatusNote: String? = null,
    val isFromCache: Boolean = false
)

@Serializable
data class SynonymGroup(
    val meaningLabel: String,
    val synonyms: List<String>
)

@Serializable
data class SenseItem(
    val index: Int,
    val partOfSpeech: String,
    val partOfSpeechArabic: String,
    val grammarLabel: String = "",
    val registerTag: String = "",
    val arabicMeaning: String,
    val englishDefinition: String,
    val exampleEnglish: String,
    val exampleArabic: String,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList()
)

@Serializable
data class PhraseItem(
    val phrase: String,
    val arabicMeaning: String,
    val exampleEnglish: String = "",
    val exampleArabic: String = ""
)

@Serializable
data class WordFamilyItem(
    val word: String,
    val partOfSpeech: String,
    val partOfSpeechArabic: String,
    val arabicMeaning: String = ""
)

@Serializable
data class CollocationItem(
    val collocation: String,
    val arabicMeaning: String
)

@Serializable
data class WordNetSynset(
    val partOfSpeech: String,
    val partOfSpeechArabic: String,
    val definition: String,
    val arabicMeaning: String,
    val synonyms: List<String>,
    val antonyms: List<String>,
    val relatedTerms: List<String> = emptyList()
)

@Serializable
data class SearchSuggestion(
    val englishWord: String,
    val arabicMeaning: String,
    val partOfSpeechArabic: String = "",
    val isFromCache: Boolean = false
)

@Serializable
data class ArabicSearchResolution(
    val arabicQuery: String,
    val primaryEnglish: String,
    val candidateWords: List<SearchSuggestion>
)
