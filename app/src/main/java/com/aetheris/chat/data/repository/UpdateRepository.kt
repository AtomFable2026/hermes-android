package com.aetheris.chat.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String
)

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val releaseUrl: String = "",
    val currentVersion: String = ""
)

@Singleton
class UpdateRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** GitHub 仓库信息 */
    companion object {
        private const val GITHUB_REPO = "AtomFable2026/hermes-android"
        private const val API_RELEASES = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val UNIVERSAL_APK_PREFIX = "app-universal-debug"
    }

    /**
     * 检查是否有新版本
     * @param currentVersion 当前版本号 (如 "1.0.0")
     */
    suspend fun checkUpdate(currentVersion: String): UpdateInfo {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(API_RELEASES)
                    .header("Accept", "application/vnd.github+json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext UpdateInfo(false, currentVersion = currentVersion)
                }

                val body = response.body?.string() ?: return@withContext UpdateInfo(false, currentVersion = currentVersion)
                val release = json.decodeFromString<GitHubRelease>(body)

                // 版本号比较（去掉 "build-" 前缀）
                val latestTag = release.tag_name.removePrefix("build-")
                val hasUpdate = latestTag.toIntOrNull()?.let { tagNum ->
                    currentVersion.replace(".", "").toIntOrNull()?.let { currNum ->
                        tagNum > currNum
                    } ?: true
                } ?: false

                // 找 universal APK 下载链接
                val apkAsset = release.assets.find { it.name.startsWith(UNIVERSAL_APK_PREFIX) }
                val downloadUrl = apkAsset?.browser_download_url ?: ""

                UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = release.tag_name,
                    downloadUrl = downloadUrl,
                    releaseUrl = release.html_url,
                    currentVersion = currentVersion
                )
            } catch (e: Exception) {
                UpdateInfo(false, currentVersion = currentVersion)
            }
        }
    }

    /** 在浏览器中打开下载页面 */
    fun openDownloadPage(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
