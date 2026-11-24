package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.UpdateUiState
import com.example.di.AppContainer
import com.example.domain.model.LanguageDirection
import com.example.ui.components.AlMakhzanjiBottomBar
import com.example.ui.components.AppUpdateDialogs
import com.example.ui.components.BottomNavTab
import com.example.ui.navigation.DiscoverRoute
import com.example.ui.navigation.FavoritesRoute
import com.example.ui.navigation.FileTranslationRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.LanguagesRoute
import com.example.ui.navigation.MoreRoute
import com.example.ui.navigation.QuickTranslateRoute
import com.example.ui.navigation.SentenceLookupRoute
import com.example.ui.navigation.SourcesCreditsRoute
import com.example.ui.navigation.TranslationRoute
import com.example.ui.navigation.WordDetailRoute
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.FileTranslationScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LanguagesScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.QuickTranslateScreen
import com.example.ui.screens.SentenceLookupScreen
import com.example.ui.screens.SourcesCreditsScreen
import com.example.ui.screens.TranslationScreen
import com.example.ui.screens.WordDetailScreen
import com.example.ui.theme.AlMakhzanjiTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.FavoritesViewModel
import com.example.ui.viewmodel.FileTranslationViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.LanguagesViewModel
import com.example.ui.viewmodel.MoreViewModel
import com.example.ui.viewmodel.QuickTranslateViewModel
import com.example.ui.viewmodel.SentenceLookupViewModel
import com.example.ui.viewmodel.TranslationViewModel
import com.example.ui.viewmodel.WordDetailViewModel

class MainActivity : ComponentActivity() {

    private lateinit var appContainer: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appContainer = AppContainer(this)
        enableEdgeToEdge()

        setContent {
            val themeMode by appContainer.themePreferences.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                )
            }

            AlMakhzanjiTheme(themeMode = themeMode) {
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
    val snackbarHostState = remember { SnackbarHostState() }

    val updateManager = appContainer.appUpdateManager
    val updateUiState by updateManager.uiState.collectAsStateWithLifecycle()

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        updateManager.onHostResumed()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateManager.onHostResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val currentBottomTab = when {
        currentDestination?.hasRoute<HomeRoute>() == true -> BottomNavTab.HOME
        currentDestination?.hasRoute<DiscoverRoute>() == true -> BottomNavTab.DISCOVER
        currentDestination?.hasRoute<FileTranslationRoute>() == true -> BottomNavTab.FILES
        currentDestination?.hasRoute<FavoritesRoute>() == true -> BottomNavTab.FAVORITES
        currentDestination?.hasRoute<MoreRoute>() == true -> BottomNavTab.MORE
        else -> null
    }

    LaunchedEffect(updateUiState) {
        val msg = (updateUiState as? UpdateUiState.Message)?.text
        if (!msg.isNullOrBlank() && currentBottomTab != BottomNavTab.MORE) {
            snackbarHostState.showSnackbar(msg)
            updateManager.dismissDialog()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (currentBottomTab != null) {
                AlMakhzanjiBottomBar(
                    currentTab = currentBottomTab,
                    onSelectTab = { tab ->
                        val targetRoute: Any = when (tab) {
                            BottomNavTab.HOME -> HomeRoute
                            BottomNavTab.DISCOVER -> DiscoverRoute
                            BottomNavTab.FILES -> FileTranslationRoute
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
                LaunchedEffect(Unit) {
                    updateManager.checkForUpdate(force = false)
                }
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
                    onOpenTranslation = {
                        navController.navigate(TranslationRoute)
                    },
                    onOpenFileTranslation = {
                        navController.navigate(FileTranslationRoute)
                    },
                    onOpenLanguages = {
                        navController.navigate(LanguagesRoute)
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

            composable<FileTranslationRoute> {
                val fileTranslationViewModel: FileTranslationViewModel = viewModel(
                    factory = FileTranslationViewModel.Factory(
                        translationManager = appContainer.translationManager,
                        fileTranslationManager = appContainer.fileTranslationManager
                    )
                )
                FileTranslationScreen(
                    viewModel = fileTranslationViewModel,
                    onOpenLanguages = { navController.navigate(LanguagesRoute) }
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
                    factory = MoreViewModel.Factory(
                        repository = appContainer.repository,
                        updateManager = appContainer.appUpdateManager,
                        offlinePackManager = appContainer.offlinePackManager,
                        themePreferences = appContainer.themePreferences
                    )
                )
                MoreScreen(
                    viewModel = moreViewModel,
                    onOpenLanguages = { navController.navigate(LanguagesRoute) },
                    onOpenTranslation = { navController.navigate(TranslationRoute) },
                    onOpenFileTranslation = { navController.navigate(FileTranslationRoute) },
                    onOpenSourcesCredits = { navController.navigate(SourcesCreditsRoute) },
                    onOpenWord = { word ->
                        navController.navigate(WordDetailRoute(query = word))
                    }
                )
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
                    },
                    onOpenSentence = { sentence ->
                        navController.navigate(SentenceLookupRoute(query = sentence))
                    }
                )
            }

            composable<TranslationRoute> {
                val translationViewModel: TranslationViewModel = viewModel(
                    factory = TranslationViewModel.Factory(appContainer.translationManager)
                )
                TranslationScreen(
                    viewModel = translationViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenLanguages = { navController.navigate(LanguagesRoute) }
                )
            }

            composable<LanguagesRoute> {
                val languagesViewModel: LanguagesViewModel = viewModel(
                    factory = LanguagesViewModel.Factory(appContainer.translationManager)
                )
                LanguagesScreen(
                    viewModel = languagesViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable<SourcesCreditsRoute> {
                SourcesCreditsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable<WordDetailRoute> { entry ->
                val args = entry.toRoute<WordDetailRoute>()
                val wordViewModel: WordDetailViewModel = viewModel(
                    key = "word_${args.query}_${args.fallbackBaseWord.orEmpty()}",
                    factory = WordDetailViewModel.Factory(
                        repository = appContainer.repository,
                        translationManager = appContainer.translationManager,
                        initialQuery = args.query,
                        fallbackBaseWord = args.fallbackBaseWord,
                        highlightChip = args.highlightChip
                    )
                )
                WordDetailScreen(
                    viewModel = wordViewModel,
                    audioManager = appContainer.audioPlayerManager,
                    onBack = { navController.popBackStack() },
                    onOpenWord = { query, fallbackBase, highlightChip ->
                        navController.navigate(
                            WordDetailRoute(
                                query = query,
                                fallbackBaseWord = fallbackBase,
                                highlightChip = highlightChip
                            )
                        )
                    },
                    onOpenSentence = { sentence ->
                        navController.navigate(SentenceLookupRoute(query = sentence))
                    },
                    onOpenLanguages = { navController.navigate(LanguagesRoute) }
                )
            }

            composable<SentenceLookupRoute> { entry ->
                val args = entry.toRoute<SentenceLookupRoute>()
                val sentenceViewModel: SentenceLookupViewModel = viewModel(
                    key = "sentence_${args.query}",
                    factory = SentenceLookupViewModel.Factory(
                        repository = appContainer.repository,
                        initialQuery = args.query
                    )
                )
                SentenceLookupScreen(
                    viewModel = sentenceViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenWord = { word ->
                        navController.navigate(WordDetailRoute(query = word))
                    },
                    onOpenDirectPhrase = { phrase ->
                        navController.navigate(WordDetailRoute(query = phrase)) {
                            popUpTo<SentenceLookupRoute> { inclusive = true }
                        }
                    },
                    onOpenLanguages = { navController.navigate(LanguagesRoute) }
                )
            }
        }
    }

    AppUpdateDialogs(
        state = updateUiState,
        onAcceptUpdate = { updateManager.startUpdateDownload() },
        onDismiss = { updateManager.dismissDialog() },
        onCancelDownload = { updateManager.cancelDownload() },
        onOpenInstallPermissionSettings = {
            runCatching {
                unknownSourcesLauncher.launch(updateManager.buildManageUnknownSourcesIntent())
            }
        }
    )
}
