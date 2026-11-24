package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
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
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AppUpdateManager"
private const val TARGET_ASSET_NAME = "app-release.apk"
private const val MSG_UP_TO_DATE = "التطبيق محدّث"
private const val MSG_CHECK_FAILED = "تعذر التحقق، حاول لاحقاً"
private const val MSG_OFFLINE = "لا يوجد اتصال بالإنترنت"

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
    private val okHttpClient: OkHttpClient,
    private val networkMonitor: NetworkMonitor
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var checkJob: Job? = null
    private var activeCheckForced: Boolean = false
    private var downloadJob: Job? = null
    private var pendingInstallFile: File? = null

    fun checkForUpdate(force: Boolean = false) {
        if (_uiState.value is UpdateUiState.Downloading ||
            _uiState.value is UpdateUiState.UpdateAvailable ||
            _uiState.value is UpdateUiState.NeedsInstallPermission
        ) {
            return
        }

        if (checkJob?.isActive == true) {
            if (force && !activeCheckForced) {
                checkJob?.cancel()
            } else {
                return
            }
        }

        val repoPair = parseRepoOwnerAndName(BuildConfig.GITHUB_REPO)
        if (repoPair == null) {
            Log.e(TAG, "Update check aborted: invalid BuildConfig.GITHUB_REPO='${BuildConfig.GITHUB_REPO}'")
            if (force) {
                _uiState.value = UpdateUiState.Message(MSG_CHECK_FAILED)
            }
            return
        }

        if (!networkMonitor.isOnline()) {
            Log.e(TAG, "Update check aborted: device is offline")
            if (force) {
                _uiState.value = UpdateUiState.Message(MSG_OFFLINE)
            }
            return
        }

        val (owner, repo) = repoPair
        val url = buildReleasesApiUrl(owner, repo)
        val installedCode = BuildConfig.VERSION_CODE
        val installedName = BuildConfig.VERSION_NAME

        activeCheckForced = force
        checkJob = scope.launch {
            if (force) {
                _uiState.value = UpdateUiState.Checking
            }
            try {
                Log.d(
                    TAG,
                    "Checking updates for repo=$owner/$repo installed=$installedName ($installedCode) url=$url"
                )
                val outcome = withContext(Dispatchers.IO) {
                    fetchAndEvaluateReleases(
                        okHttpClient = okHttpClient,
                        url = url,
                        installedVersionCode = installedCode,
                        userAgent = "Al-Makhzanji-Android-Updater/$installedName"
                    )
                }

                when (outcome) {
                    is UpdateCheckOutcome.UpdateAvailable -> {
                        Log.d(
                            TAG,
                            "Update available: remote=${outcome.tagName} (${outcome.versionCode}) > installed=$installedCode"
                        )
                        _uiState.value = UpdateUiState.UpdateAvailable(outcome.downloadUrl)
                    }
                    is UpdateCheckOutcome.UpToDate -> {
                        Log.d(TAG, "App is up to date (installed=$installedCode)")
                        _uiState.value = if (force) {
                            UpdateUiState.Message(MSG_UP_TO_DATE)
                        } else {
                            UpdateUiState.Idle
                        }
                    }
                    is UpdateCheckOutcome.Error -> {
                        Log.e(TAG, "GitHub release check failed: ${outcome.reason}", outcome.throwable)
                        _uiState.value = if (force) {
                            UpdateUiState.Message(MSG_CHECK_FAILED)
                        } else {
                            UpdateUiState.Idle
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected exception during update check", e)
                _uiState.value = if (force) {
                    UpdateUiState.Message(MSG_CHECK_FAILED)
                } else {
                    UpdateUiState.Idle
                }
            } finally {
                activeCheckForced = false
            }
        }
    }

    fun startUpdateDownload() {
        val current = _uiState.value as? UpdateUiState.UpdateAvailable ?: return
        val downloadUrl = current.downloadUrl

        if (!networkMonitor.isOnline()) {
            _uiState.value = UpdateUiState.Message(MSG_OFFLINE)
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
                        .cacheControl(CacheControl.FORCE_NETWORK)
                        .header("Cache-Control", "no-cache")
                        .header("User-Agent", "Al-Makhzanji-Android-Updater/${BuildConfig.VERSION_NAME}")
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

    internal sealed interface UpdateCheckOutcome {
        data class UpdateAvailable(
            val downloadUrl: String,
            val tagName: String,
            val versionCode: Int
        ) : UpdateCheckOutcome

        data object UpToDate : UpdateCheckOutcome

        data class Error(
            val reason: String,
            val throwable: Throwable? = null
        ) : UpdateCheckOutcome
    }

    companion object {
        internal fun parseRepoOwnerAndName(rawRepo: String?): Pair<String, String>? {
            val trimmed = rawRepo?.trim() ?: return null
            if (trimmed.isEmpty() || trimmed.equals("unknown/unknown", ignoreCase = true)) {
                return null
            }
            val parts = trimmed.split("/")
            if (parts.size != 2) return null
            val owner = parts[0].trim()
            val repo = parts[1].trim()
            if (owner.isEmpty() || repo.isEmpty()) return null
            return owner to repo
        }

        internal fun buildReleasesApiUrl(owner: String, repo: String): String {
            return "https://api.github.com/repos/$owner/$repo/releases?per_page=30"
        }

        internal fun fetchAndEvaluateReleases(
            okHttpClient: OkHttpClient,
            url: String,
            installedVersionCode: Int,
            userAgent: String
        ): UpdateCheckOutcome {
            return try {
                val request = Request.Builder()
                    .url(url)
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .header("Cache-Control", "no-cache, no-store, max-age=0")
                    .header("Pragma", "no-cache")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", userAgent)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val rawBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return UpdateCheckOutcome.Error(
                            reason = "HTTP ${response.code} from $url: ${rawBody.take(300)}"
                        )
                    }
                    evaluateReleasesJson(
                        jsonBody = rawBody,
                        installedVersionCode = installedVersionCode
                    )
                }
            } catch (e: Exception) {
                UpdateCheckOutcome.Error(
                    reason = "Network or I/O error calling $url: ${e.message}",
                    throwable = e
                )
            }
        }

        /**
         * Parses GitHub releases JSON purely using org.json (no reflection) so R8 never breaks it.
         * Extracts the integer after the last dot in tag_name (e.g. v1.0.5 -> 5) and compares against
         * BuildConfig.VERSION_CODE, ignoring draft, prerelease, and dictionary-pack releases.
         */
        internal fun evaluateReleasesJson(
            jsonBody: String,
            installedVersionCode: Int
        ): UpdateCheckOutcome {
            val trimmed = jsonBody.trim()
            if (trimmed.isEmpty()) {
                return UpdateCheckOutcome.Error("Empty response body from GitHub releases API")
            }

            if (trimmed.startsWith("{")) {
                val message = runCatching {
                    JSONObject(trimmed).optString("message", "").trim()
                }.getOrNull().orEmpty()
                return UpdateCheckOutcome.Error(
                    if (message.isNotEmpty()) {
                        "GitHub API returned error object: $message"
                    } else {
                        "Expected JSON array of releases but received JSON object"
                    }
                )
            }

            val releasesArray = try {
                JSONArray(trimmed)
            } catch (e: JSONException) {
                return UpdateCheckOutcome.Error(
                    reason = "Failed parsing GitHub releases JSONArray: ${e.message}",
                    throwable = e
                )
            }

            var bestTag = ""
            var bestVersionCode = -1
            var bestApkUrl: String? = null

            for (i in 0 until releasesArray.length()) {
                val releaseObj = releasesArray.optJSONObject(i) ?: continue
                if (releaseObj.optBoolean("draft", false)) continue
                if (releaseObj.optBoolean("prerelease", false)) continue

                val tagName = releaseObj.optString("tag_name", "").trim()
                if (tagName.isEmpty()) continue
                if (tagName.equals("dictionary-pack", ignoreCase = true) ||
                    tagName.contains("dictionary-pack", ignoreCase = true)
                ) {
                    continue
                }

                val versionCode = parseVersionAfterLastDot(tagName) ?: continue
                val assetsArray = releaseObj.optJSONArray("assets") ?: continue
                var apkUrl: String? = null
                for (j in 0 until assetsArray.length()) {
                    val assetObj = assetsArray.optJSONObject(j) ?: continue
                    val assetName = assetObj.optString("name", "").trim()
                    val downloadUrl = assetObj.optString("browser_download_url", "").trim()
                    if (assetName.equals(TARGET_ASSET_NAME, ignoreCase = true) && downloadUrl.isNotEmpty()) {
                        apkUrl = downloadUrl
                        break
                    }
                }
                if (apkUrl == null) continue

                if (versionCode > bestVersionCode) {
                    bestVersionCode = versionCode
                    bestTag = tagName
                    bestApkUrl = apkUrl
                }
            }

            return if (bestApkUrl != null && bestVersionCode > installedVersionCode) {
                UpdateCheckOutcome.UpdateAvailable(
                    downloadUrl = bestApkUrl,
                    tagName = bestTag,
                    versionCode = bestVersionCode
                )
            } else {
                UpdateCheckOutcome.UpToDate
            }
        }

        internal fun parseVersionAfterLastDot(tagName: String?): Int? {
            val raw = tagName?.trim() ?: return null
            if (raw.isEmpty() || raw.contains("dictionary-pack", ignoreCase = true)) return null
            val lastDot = raw.lastIndexOf('.')
            if (lastDot < 0 || lastDot >= raw.length - 1) return null
            val suffix = raw.substring(lastDot + 1).trim()
            return suffix.toIntOrNull()
        }
    }
}
