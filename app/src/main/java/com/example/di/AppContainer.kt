package com.example.di

import android.content.Context
import com.example.core.AppUpdateManager
import com.example.core.AudioPlayerManager
import com.example.core.FileTranslationManager
import com.example.core.NetworkMonitor
import com.example.core.OfflinePackManager
import com.example.core.TranslationManager
import com.example.data.local.AppDatabase
import com.example.data.remote.DatamuseApi
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.GitHubReleaseApi
import com.example.data.remote.TatoebaApi
import com.example.data.remote.WiktionaryApi
import com.example.data.repository.LexiconRepository
import com.example.ui.theme.ThemePreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    // Section 10: Single shared OkHttpClient with 10 MB cache, connection pool, 8s connect & 10s read timeouts
    val okHttpClient: OkHttpClient by lazy {
        val cacheDir = File(appContext.cacheDir, "http_cache")
        OkHttpClient.Builder()
            .cache(Cache(cacheDir, 10L * 1024L * 1024L))
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val dictionaryApi: FreeDictionaryApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.dictionaryapi.dev/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FreeDictionaryApi::class.java)
    }

    private val datamuseApi: DatamuseApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.datamuse.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DatamuseApi::class.java)
    }

    private val wiktionaryApi: WiktionaryApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://en.wiktionary.org/")
            .client(okHttpClient)
            .build()
            .create(WiktionaryApi::class.java)
    }

    private val tatoebaApi: TatoebaApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://tatoeba.org/")
            .client(okHttpClient)
            .build()
            .create(TatoebaApi::class.java)
    }

    private val gitHubReleaseApi: GitHubReleaseApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubReleaseApi::class.java)
    }

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val themePreferences: ThemePreferences by lazy {
        ThemePreferences(appContext)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(appContext)
    }

    val translationManager: TranslationManager by lazy {
        TranslationManager(
            context = appContext,
            networkMonitor = networkMonitor
        )
    }

    val audioPlayerManager: AudioPlayerManager by lazy {
        AudioPlayerManager(
            context = appContext,
            okHttpClient = okHttpClient
        )
    }

    val fileTranslationManager: FileTranslationManager by lazy {
        FileTranslationManager(
            context = appContext,
            translationManager = translationManager
        )
    }

    val offlinePackManager: OfflinePackManager by lazy {
        OfflinePackManager(
            context = appContext,
            dao = database.lexiconDao(),
            okHttpClient = okHttpClient,
            networkMonitor = networkMonitor
        )
    }

    val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManager(
            context = appContext,
            gitHubApi = gitHubReleaseApi,
            okHttpClient = okHttpClient,
            networkMonitor = networkMonitor
        )
    }

    val repository: LexiconRepository by lazy {
        LexiconRepository(
            dao = database.lexiconDao(),
            dictionaryApi = dictionaryApi,
            datamuseApi = datamuseApi,
            wiktionaryApi = wiktionaryApi,
            tatoebaApi = tatoebaApi,
            translationManager = translationManager,
            audioPlayerManager = audioPlayerManager,
            networkMonitor = networkMonitor
        )
    }

    fun release() {
        audioPlayerManager.release()
        fileTranslationManager.close()
        translationManager.close()
    }
}
