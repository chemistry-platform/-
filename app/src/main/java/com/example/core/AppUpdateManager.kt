package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.remote.GitHubReleaseApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AppUpdateManager"
private const val PREFS_NAME = "app_updater_prefs"
private const val KEY_LAST_CHECK_MS = "last_check_timestamp_ms"
private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
private const val TARGET_ASSET_NAME = "app-release.apk"

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class UpdateAvailable(val downloadUrl: String) : UpdateUiState
    data class Downloading(val progress: Float, val indeterminate: Boolean = false) : UpdateUiState
    data class NeedsInstallPermission(val apkFile: File) : UpdateUiState
    data class Message(val text: String) : UpdateUiState
}

class AppUpdateManager(
    context: Context,
    private val gitHubApi: GitHubReleaseApi,
    private val okHttpClient: OkHttpClient,
    private val networkMonitor: NetworkMonitor
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var pendingInstallFile: File? = null

    fun checkForUpdate(force: Boolean = false) {
        if (checkJob?.isActive == true) return
        if (_uiState.value is UpdateUiState.Downloading ||
            _uiState.value is UpdateUiState.UpdateAvailable ||
            _uiState.value is UpdateUiState.NeedsInstallPermission
        ) {
            return
        }

        if (!force) {
            val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
            val elapsed = System.currentTimeMillis() - lastCheck
            if (lastCheck > 0L && elapsed < CHECK_INTERVAL_MS) {
                return
            }
        }

        val repoParts = BuildConfig.GITHUB_REPO.split("/")
        if (repoParts.size != 2 ||
            repoParts[0].isBlank() ||
            repoParts[1].isBlank() ||
            BuildConfig.GITHUB_REPO == "unknown/unknown"
        ) {
            if (force) {
                _uiState.value = UpdateUiState.Message("التطبيق محدّث")
            }
            return
        }

        if (!networkMonitor.isOnline()) {
            if (force) {
                _uiState.value = UpdateUiState.Message("لا يوجد اتصال بالإنترنت")
            }
            return
        }

        checkJob = scope.launch {
            if (force) {
                _uiState.value = UpdateUiState.Checking
            }
            try {
                val release = withContext(Dispatchers.IO) {
                    gitHubApi.getLatestRelease(
                        owner = repoParts[0].trim(),
                        repo = repoParts[1].trim()
                    )
                }

                prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()

                val releaseCode = parseReleaseNumber(release.tagName)
                val apkAsset = release.assets.orEmpty().firstOrNull { asset ->
                    asset.name.equals(TARGET_ASSET_NAME, ignoreCase = true) &&
                        !asset.browserDownloadUrl.isNullOrBlank()
                }

                if (releaseCode != null &&
                    releaseCode > BuildConfig.VERSION_CODE &&
                    apkAsset?.browserDownloadUrl != null
                ) {
                    _uiState.value = UpdateUiState.UpdateAvailable(apkAsset.browserDownloadUrl)
                } else {
                    _uiState.value = if (force) {
                        UpdateUiState.Message("التطبيق محدّث")
                    } else {
                        UpdateUiState.Idle
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                Log.w(TAG, "GitHub release check HTTP ${e.code()}", e)
                if (force) {
                    val msg = when (e.code()) {
                        404 -> "التطبيق محدّث"
                        403, 429 -> "تم تجاوز حد الطلبات مؤقتاً، حاول لاحقاً"
                        else -> "تعذر التحقق من التحديث حالياً"
                    }
                    _uiState.value = UpdateUiState.Message(msg)
                } else {
                    _uiState.value = UpdateUiState.Idle
                }
            } catch (e: Exception) {
                Log.w(TAG, "GitHub release check failed", e)
                _uiState.value = if (force) {
                    UpdateUiState.Message("تعذر التحقق من التحديث حالياً")
                } else {
                    UpdateUiState.Idle
                }
            }
        }
    }

    fun startUpdateDownload() {
        val current = _uiState.value as? UpdateUiState.UpdateAvailable ?: return
        val downloadUrl = current.downloadUrl

        if (!networkMonitor.isOnline()) {
            _uiState.value = UpdateUiState.Message("لا يوجد اتصال بالإنترنت")
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            _uiState.value = UpdateUiState.Downloading(progress = 0f, indeterminate = true)
            try {
                val apkFile = withContext(Dispatchers.IO) {
                    val targetFile = File(appContext.cacheDir, TARGET_ASSET_NAME)
                    val tempFile = File(appContext.cacheDir, "$TARGET_ASSET_NAME.tmp")
                    if (tempFile.exists()) tempFile.delete()

                    val request = Request.Builder()
                        .url(downloadUrl)
                        .header("User-Agent", "Al-Makhzanji-Android-Updater")
                        .build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Download failed with HTTP ${response.code}")
                        }
                        val body = response.body ?: throw IllegalStateException("Empty APK response body")
                        val totalBytes = body.contentLength()
                        val input = body.byteStream()
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var downloaded = 0L
                            var lastEmitPercent = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalBytes > 0L) {
                                    val fraction = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    val percent = (fraction * 100).toInt()
                                    if (percent != lastEmitPercent) {
                                        lastEmitPercent = percent
                                        _uiState.value = UpdateUiState.Downloading(
                                            progress = fraction,
                                            indeterminate = false
                                        )
                                    }
                                }
                            }
                            output.flush()
                        }
                    }

                    if (targetFile.exists()) targetFile.delete()
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    targetFile
                }

                proceedToInstall(apkFile)
            } catch (e: CancellationException) {
                File(appContext.cacheDir, "$TARGET_ASSET_NAME.tmp").delete()
                _uiState.value = UpdateUiState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Failed downloading APK update", e)
                File(appContext.cacheDir, "$TARGET_ASSET_NAME.tmp").delete()
                _uiState.value = UpdateUiState.Message("تعذر تحميل التحديث، حاول مجدداً")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.value = UpdateUiState.Idle
    }

    fun dismissDialog() {
        pendingInstallFile = null
        _uiState.value = UpdateUiState.Idle
    }

    fun buildManageUnknownSourcesIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${appContext.packageName}")
        }
    }

    fun onHostResumed() {
        val file = pendingInstallFile ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            appContext.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile = null
            launchApkInstaller(file)
        }
    }

    private fun proceedToInstall(apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile = apkFile
            _uiState.value = UpdateUiState.NeedsInstallPermission(apkFile)
        } else {
            pendingInstallFile = null
            launchApkInstaller(apkFile)
        }
    }

    private fun launchApkInstaller(apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(installIntent)
            _uiState.value = UpdateUiState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching APK installer", e)
            _uiState.value = UpdateUiState.Message("تعذر فتح مثبّت التحديث")
        }
    }

    private fun parseReleaseNumber(tagName: String?): Int? {
        val raw = tagName?.trim().orEmpty()
        if (raw.isEmpty() || !raw.contains('.')) return null
        val tail = raw.substringAfterLast('.')
        return tail.takeWhile { it.isDigit() }.toIntOrNull()
    }
}
