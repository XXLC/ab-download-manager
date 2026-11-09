package ir.amirab.downloader.downloaditem.ytdlp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface YtDlpUpdateResult {
    data object NotInstalled : YtDlpUpdateResult
    data class UpToDate(val version: String) : YtDlpUpdateResult
    data class UpdateAvailable(val installed: String, val latest: String) : YtDlpUpdateResult
}

/**
 * Chỉ so phiên bản đang cài với bản phát hành mới nhất trên GitHub, không tải hay cài gì.
 */
class YtDlpUpdateChecker(
    private val client: YtDlpClient,
    private val http: OkHttpClient,
) {
    suspend fun check(): YtDlpUpdateResult {
        val installed = client.installedVersion() ?: return YtDlpUpdateResult.NotInstalled
        val latest = fetchLatestVersion()
        return if (isNewer(latest, installed)) {
            YtDlpUpdateResult.UpdateAvailable(installed = installed, latest = latest)
        } else {
            YtDlpUpdateResult.UpToDate(installed)
        }
    }

    private suspend fun fetchLatestVersion(): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw YtDlpException("Can't check yt-dlp updates: HTTP ${response.code}")
            }
            val body = response.body.string()
            Json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.content
                ?: throw YtDlpException("Can't check yt-dlp updates: no version in response")
        }
    }

    companion object {
        const val RELEASES_PAGE_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest"
        private const val LATEST_RELEASE_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"

        /**
         * Phiên bản của yt-dlp có dạng 2026.08.19 hoặc 2026.08.19.232812 (nightly), so từng số theo thứ tự.
         */
        fun isNewer(candidate: String, installed: String): Boolean {
            val a = parts(candidate)
            val b = parts(installed)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(version: String): List<Int> {
            return version.trim().removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        }
    }
}
