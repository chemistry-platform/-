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
data class WordDetailRoute(
    val query: String
)

@Serializable
data class PhrasesListRoute(
    val word: String
)
