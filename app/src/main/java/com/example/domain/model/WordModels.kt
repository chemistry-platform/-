package com.example.domain.model

import androidx.compose.runtime.Immutable
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
    WORDNET,
    OFFLINE_PACK
}

@Immutable
@Serializable
data class SenseItem(
    val index: Int,
    val partOfSpeech: String,
    val partOfSpeechArabic: String,
    val definition: String,
    val definitionArabic: String? = null,
    val definitionArabicIsMachine: Boolean = false,
    val examples: List<String> = emptyList(),
    val exampleTranslations: Map<String, String> = emptyMap(),
    val exampleIsMachine: Map<String, Boolean> = emptyMap(),
    val exampleAudioUrls: Map<String, String> = emptyMap(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val usageLabels: List<String> = emptyList(),
    val spellingVariantLabel: String? = null,
    val otherSpellingWord: String? = null,
    val usageNote: String? = null,
    val headwordVariant: String? = null,
    val isFigurative: Boolean = false,
    val isTechnical: Boolean = false,
    val source: SenseSource = SenseSource.FREE_DICTIONARY
)

@Immutable
@Serializable
data class PosSenseGroup(
    val partOfSpeech: String,
    val partOfSpeechArabic: String,
    val senses: List<SenseItem>
)

@Immutable
@Serializable
data class WordFamilyItem(
    val word: String,
    val partOfSpeech: String,
    val partOfSpeechArabic: String
)

@Immutable
@Serializable
data class ConfusableCandidate(
    val word: String,
    val labels: List<String>
)

@Immutable
@Serializable
data class ConfusableWordDetail(
    val word: String,
    val labels: List<String> = emptyList(),
    val partOfSpeech: String = "",
    val shortDefinition: String = "",
    val shortDefinitionArabic: String? = null,
    val shortDefinitionArabicIsMachine: Boolean = false,
    val example: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null
)

@Immutable
@Serializable
data class TatoebaSentence(
    val english: String,
    val arabic: String,
    val audioUrl: String? = null
)

@Immutable
@Serializable
data class PhraseDetailItem(
    val phrase: String,
    val englishMeaning: String = "",
    val arabicMeaning: String? = null,
    val isArabicMachine: Boolean = false,
    val example: String = "",
    val exampleArabic: String? = null,
    val exampleIsMachine: Boolean = false,
    val exampleAudioUrl: String? = null,
    val isIdiom: Boolean = false,
    val category: String = "",
    val usageExplanation: String? = null,
    val grammarStructure: String? = null
)

@Immutable
@Serializable
data class DetectedIdiom(
    val phrase: String,
    val englishMeaning: String = "",
    val arabicMeaning: String? = null,
    val isArabicMachine: Boolean = false,
    val example: String = "",
    val isPhrasalVerb: Boolean = false,
    val usageNote: String? = null
)

@Immutable
data class PhraseSheetData(
    val phrase: String,
    val arabicMeaning: String? = null,
    val isArabicMachine: Boolean = false,
    val missingArabicPack: Boolean = false,
    val example: String = "",
    val exampleArabic: String? = null,
    val exampleIsMachine: Boolean = false,
    val exampleAudioUrl: String? = null,
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val isLoading: Boolean = false
)

@Immutable
data class InstantTranslationResult(
    val query: String,
    val translation: String? = null,
    val isArabicSource: Boolean = false,
    val isMachine: Boolean = true,
    val missingPack: Boolean = false,
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val isLoading: Boolean = false
)

@Immutable
@Serializable
data class WordEntry(
    val word: String,
    val displayWord: String = word,
    val isArabicWord: Boolean = false,
    val lang: String = "en",
    val transliteration: String = "",
    val vocalizedForms: List<String> = emptyList(),
    val usIpa: String = "",
    val ukIpa: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val tags: List<String> = emptyList(),
    val machineTranslations: List<String> = emptyList(),
    val missingTranslationPack: Boolean = false,
    val posGroups: List<PosSenseGroup> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val collocations: List<String> = emptyList(),
    val compoundExpressions: List<String> = emptyList(),
    val prepositionsAndPhrases: List<String> = emptyList(),
    val phrasalVerbsAndIdioms: List<PhraseDetailItem> = emptyList(),
    val wordFamily: List<WordFamilyItem> = emptyList(),
    val confusableCandidates: List<ConfusableCandidate> = emptyList(),
    val confusableDetails: Map<String, ConfusableWordDetail> = emptyMap(),
    val generalTerms: List<String> = emptyList(),
    val specificTerms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList(),
    val relatedExplanations: Map<String, String> = emptyMap(),
    val tatoebaSentences: List<TatoebaSentence> = emptyList(),
    val hasWiktionaryAttribution: Boolean = false,
    val hasTatoebaAttribution: Boolean = false,
    val isFromOfflinePack: Boolean = false,
    val isFromCache: Boolean = false
)

@Immutable
@Serializable
data class SearchSuggestion(
    val word: String,
    val partOfSpeech: String = "",
    val summaryDefinition: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null,
    val isArabic: Boolean = false,
    val isLocal: Boolean = false
)

@Immutable
data class AwlWordItem(
    val word: String,
    val phonetic: String = "",
    val summaryDefinition: String = "",
    val usAudioUrl: String? = null,
    val ukAudioUrl: String? = null
)

@Immutable
data class SentenceLookupResult(
    val originalText: String,
    val isArabicInput: Boolean,
    val machineTranslation: String? = null,
    val missingTranslationPack: Boolean = false,
    val wordChips: List<String> = emptyList(),
    val detectedIdioms: List<DetectedIdiom> = emptyList(),
    val tatoebaSentences: List<TatoebaSentence> = emptyList(),
    val isUpdating: Boolean = false,
    val directPhraseEntryAvailable: Boolean = false
)
