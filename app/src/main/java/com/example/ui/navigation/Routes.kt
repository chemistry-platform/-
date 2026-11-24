package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object DiscoverRoute

@Serializable
object FavoritesRoute

@Serializable
object MoreRoute

@Serializable
data class QuickTranslateRoute(
    val initialQuery: String = "",
    val isArabicSource: Boolean = false
)

@Serializable
object TranslationRoute

@Serializable
object LanguagesRoute

@Serializable
object SourcesCreditsRoute

@Serializable
data class WordDetailRoute(
    val query: String,
    val fallbackBaseWord: String? = null,
    val highlightChip: String? = null
)
