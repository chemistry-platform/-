package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.core.CrashReporter
import com.example.di.AppContainer

class AlMakhzanjiApplication : Application() {

    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        runCatching {
            CrashReporter.install(this)
        }.onFailure {
            Log.e(TAG, "Failed installing CrashReporter in attachBaseContext", it)
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            CrashReporter.install(this)
        }.onFailure {
            Log.e(TAG, "Failed installing CrashReporter in onCreate", it)
        }
    }

    private companion object {
        const val TAG = "AlMakhzanjiApp"
    }
}
