package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.configureAppCheck
import com.example.di.AppContainer
import com.example.domain.model.LanguageDirection
import com.example.ui.components.AlMakhzanjiBottomBar
import com.example.ui.components.BottomNavTab
import com.example.ui.navigation.DiscoverRoute
import com.example.ui.navigation.FavoritesRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.MoreRoute
import com.example.ui.navigation.PhrasesListRoute
import com.example.ui.navigation.QuickTranslateRoute
import com.example.ui.navigation.WordDetailRoute
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.PhrasesListScreen
import com.example.ui.screens.QuickTranslateScreen
import com.example.ui.screens.WordDetailScreen
import com.example.ui.theme.AlMakhzanjiTheme
import com.example.ui.theme.CanvasBackground
import com.example.ui.viewmodel.FavoritesViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.MoreViewModel
import com.example.ui.viewmodel.QuickTranslateViewModel
import com.example.ui.viewmodel.WordDetailViewModel

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        appContainer = AppContainer(this)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        setContent {
            AlMakhzanjiTheme {
                AlMakhzanjiApp(appContainer = appContainer)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appContainer.isInitialized) {
            appContainer.release()
        }
    }
}

@Composable
private fun AlMakhzanjiApp(appContainer: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val currentBottomTab = when {
        currentDestination?.hasRoute<HomeRoute>() == true -> BottomNavTab.HOME
        currentDestination?.hasRoute<DiscoverRoute>() == true -> BottomNavTab.DISCOVER
        currentDestination?.hasRoute<FavoritesRoute>() == true -> BottomNavTab.FAVORITES
        currentDestination?.hasRoute<MoreRoute>() == true -> BottomNavTab.MORE
        else -> null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasBackground,
        bottomBar = {
            if (currentBottomTab != null) {
                AlMakhzanjiBottomBar(
                    currentTab = currentBottomTab,
                    onSelectTab = { tab ->
                        val targetRoute: Any = when (tab) {
                            BottomNavTab.HOME -> HomeRoute
                            BottomNavTab.DISCOVER -> DiscoverRoute
                            BottomNavTab.FAVORITES -> FavoritesRoute
                            BottomNavTab.MORE -> MoreRoute
                        }
                        navController.navigate(targetRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<HomeRoute> {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(appContainer.repository)
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenQuickTranslate = { query, isArabic ->
                        navController.navigate(
                            QuickTranslateRoute(
                                initialQuery = query,
                                isArabicSource = isArabic
                            )
                        )
                    },
                    onOpenWord = { word ->
                        navController.navigate(WordDetailRoute(query = word))
                    }
                )
            }

            composable<DiscoverRoute> {
                DiscoverScreen(
                    onOpenWord = { word ->
                        navController.navigate(WordDetailRoute(query = word))
                    }
                )
            }

            composable<FavoritesRoute> {
                val favoritesViewModel: FavoritesViewModel = viewModel(
                    factory = FavoritesViewModel.Factory(appContainer.repository)
                )
                FavoritesScreen(
                    viewModel = favoritesViewModel,
                    onOpenWord = { word ->
                        navController.navigate(WordDetailRoute(query = word))
                    }
                )
            }

            composable<MoreRoute> {
                val moreViewModel: MoreViewModel = viewModel(
                    factory = MoreViewModel.Factory(appContainer.repository)
                )
                MoreScreen(viewModel = moreViewModel)
            }

            composable<QuickTranslateRoute> { entry ->
                val args = entry.toRoute<QuickTranslateRoute>()
                val direction = if (args.isArabicSource) {
                    LanguageDirection.AR_TO_EN
                } else {
                    LanguageDirection.EN_TO_AR
                }
                val qtViewModel: QuickTranslateViewModel = viewModel(
                    factory = QuickTranslateViewModel.Factory(
                        repository = appContainer.repository,
                        initialQuery = args.initialQuery,
                        initialDirection = direction
                    )
                )
                QuickTranslateScreen(
                    viewModel = qtViewModel,
                    audioManager = appContainer.audioPlayerManager,
                    onBack = { navController.popBackStack() },
                    onOpenWord = { word ->
                        navController.navigate(WordDetailRoute(query = word))
                    }
                )
            }

            composable<WordDetailRoute> { entry ->
                val args = entry.toRoute<WordDetailRoute>()
                val wordViewModel: WordDetailViewModel = viewModel(
                    key = "word_${args.query}",
                    factory = WordDetailViewModel.Factory(
                        repository = appContainer.repository,
                        initialQuery = args.query
                    )
                )
                WordDetailScreen(
                    viewModel = wordViewModel,
                    audioManager = appContainer.audioPlayerManager,
                    onBack = { navController.popBackStack() },
                    onOpenWord = { word ->
                        navController.navigate(WordDetailRoute(query = word))
                    },
                    onOpenPhrases = { word ->
                        navController.navigate(PhrasesListRoute(word = word))
                    }
                )
            }

            composable<PhrasesListRoute> { entry ->
                val args = entry.toRoute<PhrasesListRoute>()
                val wordViewModel: WordDetailViewModel = viewModel(
                    key = "phrases_${args.word}",
                    factory = WordDetailViewModel.Factory(
                        repository = appContainer.repository,
                        initialQuery = args.word
                    )
                )
                PhrasesListScreen(
                    word = args.word,
                    viewModel = wordViewModel,
                    audioManager = appContainer.audioPlayerManager,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
