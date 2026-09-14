package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.sync.GitHubSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AppVersionInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String,
    val apkSizeMb: Double? = null,
    val publishedAt: String = "",
    val isForceUpdate: Boolean = false
)

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val currentVersionCode: Int,
    val currentVersionName: String,
    val latestVersion: AppVersionInfo? = null,
    val error: String? = null
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    object Checking : UpdateDownloadState()
    data class Downloading(val progress: Float, val downloadedMb: Double, val totalMb: Double) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File, val versionInfo: AppVersionInfo) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateManager"
        private const val PREFS_NAME = "app_update_prefs"
        private const val KEY_CUSTOM_UPDATE_URL = "custom_update_url"
        private const val KEY_CUSTOM_REPO_OWNER = "custom_repo_owner"
        private const val KEY_CUSTOM_REPO_NAME = "custom_repo_name"
        private const val KEY_LAST_CHECK_TIME = "last_check_time"
        private const val KEY_IGNORE_VERSION_CODE = "ignore_version_code"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getCustomRepoOwner(): String = prefs.getString(KEY_CUSTOM_REPO_OWNER, "") ?: ""
    fun getCustomRepoName(): String = prefs.getString(KEY_CUSTOM_REPO_NAME, "") ?: ""
    fun getCustomUpdateUrl(): String = prefs.getString(KEY_CUSTOM_UPDATE_URL, "") ?: ""
    fun getLastCheckTime(): Long = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
    fun getIgnoredVersionCode(): Int = prefs.getInt(KEY_IGNORE_VERSION_CODE, 0)

    fun saveUpdateConfig(owner: String, repo: String, customUrl: String) {
        prefs.edit()
            .putString(KEY_CUSTOM_REPO_OWNER, owner.trim())
            .putString(KEY_CUSTOM_REPO_NAME, repo.trim())
            .putString(KEY_CUSTOM_UPDATE_URL, customUrl.trim())
            .apply()
    }

    fun ignoreVersion(versionCode: Int) {
        prefs.edit().putInt(KEY_IGNORE_VERSION_CODE, versionCode).apply()
    }

    /**
     * Check if a newer version exists on GitHub Releases or Gist/Custom JSON
     */
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME
        prefs.edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

        val customUrl = getCustomUpdateUrl()
        val customOwner = getCustomRepoOwner()
        val customRepo = getCustomRepoName()

        // 1. If custom direct JSON URL is set, query it first
        if (customUrl.isNotBlank()) {
            val result = checkFromDirectUrl(customUrl, currentCode, currentName)
            if (result != null) return@withContext result
        }

        // 2. If custom or configured GitHub Repository is set, query GitHub Releases API
        val syncPrefs = context.getSharedPreferences("github_sync_prefs", Context.MODE_PRIVATE)
        val configuredOwner = customOwner.ifBlank { syncPrefs.getString("repo_owner", "") ?: "" }
        val configuredRepo = customRepo.ifBlank { syncPrefs.getString("repo_name", "") ?: "" }
        val repoOwner = configuredOwner.ifBlank { "longsongline" }
        val repoName = configuredRepo.ifBlank { "FamilySpace" }
        val githubToken = syncPrefs.getString("token", "").takeIf { !it.isNullOrBlank() } ?: GitHubSyncManager.BUILT_IN_GITHUB_TOKEN

        if (repoOwner.isNotBlank() && repoName.isNotBlank()) {
            val repoResult = checkFromGitHubReleases(repoOwner, repoName, githubToken, currentCode, currentName)
            if (repoResult != null) return@withContext repoResult
        }

        // 3. Fallback: check from shared transit Gist for version.json
        val gistId = syncPrefs.getString("gist_id", "").takeIf { !it.isNullOrBlank() } ?: GitHubSyncManager.BUILT_IN_GIST_ID
        val gistResult = checkFromGist(gistId, githubToken, currentCode, currentName)
        if (gistResult != null) return@withContext gistResult

        // No update info available
        UpdateCheckResult(
            hasUpdate = false,
            currentVersionCode = currentCode,
            currentVersionName = currentName,
            error = if (repoOwner.isBlank() && repoName.isBlank() && customUrl.isBlank()) {
                "暂未配置 GitHub 仓库更新源，可前往设置配置或使用 Gist 发布版本"
            } else {
                null
            }
        )
    }

    /**
     * Check GitHub Releases API for the latest release & APK asset
     */
    private fun checkFromGitHubReleases(
        owner: String,
        repo: String,
        token: String?,
        currentCode: Int,
        currentName: String
    ): UpdateCheckResult? {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "FamilySpace-Android-App")

            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub releases request returned ${response.code}")
                    return null
                }
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)

                val tagName = json.optString("tag_name", "").trim()
                val releaseTitle = json.optString("name", tagName)
                val releaseNotes = json.optString("body", "常规性能优化与体验改进")
                val publishedAt = json.optString("published_at", "")

                // Find APK asset in release
                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                var apkSizeMb: Double? = null

                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            val sizeBytes = asset.optLong("size", 0L)
                            if (sizeBytes > 0) {
                                apkSizeMb = sizeBytes / (1024.0 * 1024.0)
                            }
                            break
                        }
                    }
                }

                // If no APK asset found in assets array, check html_url or direct download
                if (apkUrl.isBlank()) {
                    apkUrl = json.optString("html_url", "")
                }

                // Parse version number from tag (e.g. v1.2.0, 1.2, etc.)
                val parsedCode = parseVersionCodeFromTag(tagName)
                val parsedName = tagName.removePrefix("v").removePrefix("V").ifBlank { tagName }

                val isNewer = parsedCode > currentCode || (parsedCode == currentCode && isSemanticNewer(parsedName, currentName))

                val versionInfo = AppVersionInfo(
                    versionCode = parsedCode,
                    versionName = parsedName,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkUrl,
                    apkSizeMb = apkSizeMb,
                    publishedAt = publishedAt
                )

                return UpdateCheckResult(
                    hasUpdate = isNewer,
                    currentVersionCode = currentCode,
                    currentVersionName = currentName,
                    latestVersion = versionInfo
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkFromGitHubReleases failed", e)
            return null
        }
    }

    /**
     * Check version manifest from Gist (e.g. version.json or app_update.json)
     */
    private fun checkFromGist(
        gistId: String,
        token: String?,
        currentCode: Int,
        currentName: String
    ): UpdateCheckResult? {
        if (gistId.isBlank()) return null
        try {
            val url = "https://api.github.com/gists/$gistId"
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github+json")
                .addHeader("User-Agent", "FamilySpace-Android-App")

            if (!token.isNullOrBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $token")
            }

            client.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                val files = json.optJSONObject("files") ?: return null

                // Look for version.json or update.json
                val versionFile = files.optJSONObject("version.json")
                    ?: files.optJSONObject("app_update.json")
                    ?: return null

                val content = versionFile.optString("content", "")
                if (content.isBlank()) return null

                return parseVersionJson(content, currentCode, currentName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkFromGist failed", e)
            return null
        }
    }

    /**
     * Check version manifest from direct custom URL
     */
    private fun checkFromDirectUrl(
        url: String,
        currentCode: Int,
        currentName: String
    ): UpdateCheckResult? {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "FamilySpace-Android-App")
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                return parseVersionJson(bodyStr, currentCode, currentName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkFromDirectUrl failed", e)
            return null
        }
    }

    private fun parseVersionJson(jsonString: String, currentCode: Int, currentName: String): UpdateCheckResult? {
        try {
            val json = JSONObject(jsonString)
            val remoteCode = json.optInt("versionCode", 1)
            val remoteName = json.optString("versionName", "1.0.0")
            val downloadUrl = json.optString("downloadUrl", "")
            val title = json.optString("title", "亲情空间新版本 $remoteName")
            val notes = json.optString("changelog", json.optString("releaseNotes", "常规优化更新"))
            val sizeMb = if (json.has("sizeMb")) json.optDouble("sizeMb") else null
            val isForce = json.optBoolean("forceUpdate", false)

            val isNewer = remoteCode > currentCode || (remoteCode == currentCode && isSemanticNewer(remoteName, currentName))

            val versionInfo = AppVersionInfo(
                versionCode = remoteCode,
                versionName = remoteName,
                releaseTitle = title,
                releaseNotes = notes,
                downloadUrl = downloadUrl,
                apkSizeMb = sizeMb,
                isForceUpdate = isForce
            )

            return UpdateCheckResult(
                hasUpdate = isNewer,
                currentVersionCode = currentCode,
                currentVersionName = currentName,
                latestVersion = versionInfo
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseVersionJson error", e)
            return null
        }
    }

    /**
     * Downloads APK file and reports progress
     */
    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Float, Double, Double) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "FamilySpace-Android-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("下载失败，HTTP状态码: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(IOException("响应数据为空"))
            val totalBytes = body.contentLength()

            // Save in app-specific download folder
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val targetFile = File(downloadDir, "family_space_latest_update.apk")
            if (targetFile.exists()) targetFile.delete()

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                        val downloadedMb = totalRead / (1024.0 * 1024.0)
                        val totalMb = if (totalBytes > 0) totalBytes / (1024.0 * 1024.0) else downloadedMb

                        onProgress(progress, downloadedMb, totalMb)
                    }
                    output.flush()
                }
            }

            if (!targetFile.exists() || targetFile.length() <= 0) {
                return@withContext Result.failure(IOException("安装包写入异常"))
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "downloadApk failed", e)
            Result.failure(e)
        }
    }

    /**
     * Launches Android Package Installer to install APK
     */
    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists()) {
                Log.e(TAG, "installApk: File does not exist: ${apkFile.absolutePath}")
                return
            }

            // Check unknown app sources permission for Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "installApk failed", e)
        }
    }

    private fun parseVersionCodeFromTag(tag: String): Int {
        val clean = tag.removePrefix("v").removePrefix("V").trim()
        val parts = clean.split(".")
        return try {
            if (parts.size >= 3) {
                parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
            } else if (parts.size == 2) {
                parts[0].toInt() * 10000 + parts[1].toInt() * 100
            } else {
                parts[0].toInt()
            }
        } catch (_: Exception) {
            1
        }
    }

    private fun isSemanticNewer(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val rParts = remoteVersion.split(".").mapNotNull { it.toIntOrNull() }
            val cParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = if (i < rParts.size) rParts[i] else 0
                val c = if (i < cParts.size) cParts[i] else 0
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {}
        return false
    }
}
