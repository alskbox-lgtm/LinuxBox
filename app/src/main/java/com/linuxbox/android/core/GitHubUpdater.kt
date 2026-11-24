package com.linuxbox.android.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersion: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val releaseHtmlUrl: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long
)

object GitHubUpdater {

    private const val REPO_OWNER = "alskbox-lgtm"
    private const val REPO_NAME = "LinuxBox"
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "LinuxBox-Android/$currentVersion")
        }
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) return@withContext null

            val latestClean = tagName.removePrefix("v").removePrefix("V")
            if (!isNewerVersion(latestClean, currentVersion)) {
                return@withContext null
            }

            val htmlUrl = json.optString(
                "html_url",
                "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"
            )
            val title = json.optString("name", tagName).ifBlank { tagName }
            val notes = json.optString("body", "").trim()

            var apkUrl: String? = null
            var apkSize = 0L
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        val isUniversal = name.contains("universal", ignoreCase = true)
                        if (apkUrl == null || isUniversal) {
                            apkUrl = url
                            apkSize = asset.optLong("size", 0L)
                            if (isUniversal) break
                        }
                    }
                }
            }

            AppUpdateInfo(
                latestVersion = latestClean,
                tagName = tagName,
                releaseTitle = title,
                releaseNotes = notes,
                releaseHtmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                apkSizeBytes = apkSize
            )
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }

    suspend fun downloadUpdateApk(
        context: Context,
        downloadUrl: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "LinuxBox-update.apk")
        if (targetFile.exists()) targetFile.delete()

        var currentUrl = downloadUrl
        var redirects = 0
        var conn: HttpURLConnection

        while (true) {
            conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 45_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "LinuxBox-Android-Updater")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 8) {
                val location = conn.getHeaderField("Location")
                    ?: throw IllegalStateException("Redirecionamento inválido (HTTP $code)")
                currentUrl = URL(URL(currentUrl), location).toString()
                conn.disconnect()
                redirects++
            } else {
                break
            }
        }

        try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("Erro ao baixar atualização (HTTP ${conn.responseCode})")
            }
            val total = conn.contentLengthLong
            var downloaded = 0L
            val buf = ByteArray(64 * 1024)
            var lastNotify = 0L

            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        output.write(buf, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 120) {
                            lastNotify = now
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total)
            targetFile
        } finally {
            conn.disconnect()
        }
    }

    fun buildInstallApkIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val candParts = candidate.removePrefix("v").removePrefix("V")
            .split('.').map { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val currParts = current.removePrefix("v").removePrefix("V")
            .split('.').map { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(candParts.size, currParts.size)
        for (i in 0 until maxLen) {
            val a = candParts.getOrElse(i) { 0 }
            val b = currParts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
