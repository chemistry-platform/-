package com.example.core

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {
    private const val CRASH_FILE_NAME = "last_crash.txt"
    private const val TAG = "CrashReporter"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext ?: context
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                saveCrash(appContext, throwable, thread.name, thread.id)
                previousHandler?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    fun saveCrash(
        context: Context,
        throwable: Throwable,
        threadName: String = Thread.currentThread().name,
        threadId: Long = Thread.currentThread().id
    ) {
        try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
            val abis = runCatching { Build.SUPPORTED_ABIS.joinToString(", ") }.getOrDefault("unknown")
            val stackTrace = Log.getStackTraceString(throwable)
            val report = buildString {
                appendLine("=== معجم المخزنجي — تقرير خطأ ===")
                appendLine("Time: $timestamp")
                appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE ?: "?"})")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("ABIs: $abis")
                appendLine("Thread: $threadName (id=$threadId)")
                appendLine()
                appendLine(stackTrace)
            }
            FileOutputStream(file, false).use { fos ->
                fos.write(report.toByteArray(Charsets.UTF_8))
                fos.flush()
                runCatching { fos.fd.sync() }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed writing crash report", e)
        }
    }

    fun readLastCrash(context: Context): String? {
        return try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (!file.exists() || file.length() <= 0L) null
            else file.readText(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed reading last crash", e)
            null
        }
    }

    fun clearLastCrash(context: Context) {
        runCatching {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (file.exists()) file.delete()
        }
    }
}
