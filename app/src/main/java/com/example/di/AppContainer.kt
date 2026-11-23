package com.example.di

import android.content.Context
import com.example.BuildConfig
import com.example.core.AudioPlayerManager
import com.example.core.NetworkMonitor
import com.example.data.local.AppDatabase
import com.example.data.remote.DatamuseApi
import com.example.data.remote.FreeDictionaryApi
import com.example.data.remote.GeminiLexiconService
import com.example.data.remote.MlKitTranslationService
import com.example.data.repository.LexiconRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
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

    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    val networkMonitor: NetworkMonitor by lazy {
        NetworkMonitor(appContext)
    }

    val mlKitService: MlKitTranslationService by lazy {
        MlKitTranslationService()
    }

    private val geminiService: GeminiLexiconService by lazy {
        GeminiLexiconService()
    }

    val audioPlayerManager: AudioPlayerManager by lazy {
        AudioPlayerManager(appContext)
    }

    val repository: LexiconRepository by lazy {
        LexiconRepository(
            dao = database.lexiconDao(),
            dictionaryApi = dictionaryApi,
            datamuseApi = datamuseApi,
            mlKitService = mlKitService,
            geminiService = geminiService,
            networkMonitor = networkMonitor
        )
    }

    fun release() {
        audioPlayerManager.release()
        mlKitService.close()
    }
}
