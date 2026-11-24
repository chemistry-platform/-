package com.example

import com.example.core.AppUpdateManager
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BugFix9UpdateCheckTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parseRepoOwnerAndName_validatesRepoFormat() {
        assertNull(AppUpdateManager.parseRepoOwnerAndName(null))
        assertNull(AppUpdateManager.parseRepoOwnerAndName(""))
        assertNull(AppUpdateManager.parseRepoOwnerAndName("unknown/unknown"))
        assertNull(AppUpdateManager.parseRepoOwnerAndName("invalid_repo"))
        assertEquals("myuser" to "myrepo", AppUpdateManager.parseRepoOwnerAndName(" myuser / myrepo "))
        assertEquals(
            "https://api.github.com/repos/myuser/myrepo/releases?per_page=30",
            AppUpdateManager.buildReleasesApiUrl("myuser", "myrepo")
        )
    }

    @Test
    fun parseVersionAfterLastDot_extractsBuildNumberAndIgnoresDictionaryPack() {
        assertEquals(5, AppUpdateManager.parseVersionAfterLastDot("v1.0.5"))
        assertEquals(14, AppUpdateManager.parseVersionAfterLastDot("1.0.14"))
        assertNull(AppUpdateManager.parseVersionAfterLastDot("dictionary-pack"))
        assertNull(AppUpdateManager.parseVersionAfterLastDot("dictionary-pack.99"))
        assertNull(AppUpdateManager.parseVersionAfterLastDot("v10"))
    }

    @Test
    fun evaluateReleasesJson_detectsNewerReleaseAndIgnoresDictionaryPackDraftsAndPrereleases() {
        val json = """
            [
              {
                "tag_name": "dictionary-pack",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "dictionary-pack.json.gz",
                    "browser_download_url": "https://example.com/dictionary-pack.json.gz"
                  },
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "https://example.com/wrong.apk"
                  }
                ]
              },
              {
                "tag_name": "v1.0.9",
                "draft": true,
                "prerelease": false,
                "assets": [
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "https://example.com/draft.apk"
                  }
                ]
              },
              {
                "tag_name": "v1.0.8",
                "draft": false,
                "prerelease": true,
                "assets": [
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "https://example.com/pre.apk"
                  }
                ]
              },
              {
                "tag_name": "v1.0.5",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "https://github.com/owner/repo/releases/download/v1.0.5/app-release.apk"
                  }
                ]
              },
              {
                "tag_name": "v1.0.4",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "app-release.apk",
                    "browser_download_url": "https://github.com/owner/repo/releases/download/v1.0.4/app-release.apk"
                  }
                ]
              }
            ]
        """.trimIndent()

        val outcome = AppUpdateManager.evaluateReleasesJson(json, installedVersionCode = 4)
        assertTrue(outcome is AppUpdateManager.UpdateCheckOutcome.UpdateAvailable)
        val avail = outcome as AppUpdateManager.UpdateCheckOutcome.UpdateAvailable
        assertEquals("v1.0.5", avail.tagName)
        assertEquals(5, avail.versionCode)
        assertEquals(
            "https://github.com/owner/repo/releases/download/v1.0.5/app-release.apk",
            avail.downloadUrl
        )

        val sameVersion = AppUpdateManager.evaluateReleasesJson(json, installedVersionCode = 5)
        assertTrue(sameVersion is AppUpdateManager.UpdateCheckOutcome.UpToDate)
    }

    @Test
    fun evaluateReleasesJson_returnsErrorOnMalformedOrErrorPayload() {
        assertTrue(
            AppUpdateManager.evaluateReleasesJson("", installedVersionCode = 4)
                is AppUpdateManager.UpdateCheckOutcome.Error
        )
        assertTrue(
            AppUpdateManager.evaluateReleasesJson("""{"message":"Not Found"}""", installedVersionCode = 4)
                is AppUpdateManager.UpdateCheckOutcome.Error
        )
        assertTrue(
            AppUpdateManager.evaluateReleasesJson("not json", installedVersionCode = 4)
                is AppUpdateManager.UpdateCheckOutcome.Error
        )
    }

    @Test
    fun fetchAndEvaluateReleases_bypassesHttpCacheAndSendsUserAgentAndFailsOn404() {
        val callCount = AtomicInteger(0)
        var capturedUserAgent: String? = null
        var capturedCacheControl: String? = null

        val cachedClient = OkHttpClient.Builder()
            .cache(Cache(tempFolder.newFolder("okhttp_cache"), 2L * 1024L * 1024L))
            .addInterceptor { chain ->
                val req = chain.request()
                capturedUserAgent = req.header("User-Agent")
                capturedCacheControl = req.header("Cache-Control")
                val n = callCount.incrementAndGet()
                val (code, body) = when (n) {
                    1 -> 200 to """[{"tag_name":"v1.0.4","draft":false,"prerelease":false,"assets":[{"name":"app-release.apk","browser_download_url":"https://example.com/1.0.4.apk"}]}]"""
                    2 -> 200 to """[{"tag_name":"v1.0.5","draft":false,"prerelease":false,"assets":[{"name":"app-release.apk","browser_download_url":"https://example.com/1.0.5.apk"}]}]"""
                    else -> 404 to """{"message":"Not Found"}"""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .header("Cache-Control", "public, max-age=3600")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val first = AppUpdateManager.fetchAndEvaluateReleases(
            okHttpClient = cachedClient,
            url = "https://api.github.com/repos/owner/repo/releases?per_page=30",
            installedVersionCode = 4,
            userAgent = "Al-Makhzanji-Android-Updater/1.0.4"
        )
        assertTrue(first is AppUpdateManager.UpdateCheckOutcome.UpToDate)
        assertEquals("Al-Makhzanji-Android-Updater/1.0.4", capturedUserAgent)
        assertTrue(capturedCacheControl.orEmpty().contains("no-cache"))

        val second = AppUpdateManager.fetchAndEvaluateReleases(
            okHttpClient = cachedClient,
            url = "https://api.github.com/repos/owner/repo/releases?per_page=30",
            installedVersionCode = 4,
            userAgent = "Al-Makhzanji-Android-Updater/1.0.4"
        )
        assertEquals(2, callCount.get())
        assertTrue(second is AppUpdateManager.UpdateCheckOutcome.UpdateAvailable)

        val third = AppUpdateManager.fetchAndEvaluateReleases(
            okHttpClient = cachedClient,
            url = "https://api.github.com/repos/owner/repo/releases?per_page=30",
            installedVersionCode = 4,
            userAgent = "Al-Makhzanji-Android-Updater/1.0.4"
        )
        assertEquals(3, callCount.get())
        assertTrue(third is AppUpdateManager.UpdateCheckOutcome.Error)
    }
}
