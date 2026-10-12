package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.pagemanager.NotificationSender
import com.abdownloadmanager.shared.ui.widget.NotificationType
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpUpdateChecker
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpUpdateResult
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.platform.isWindows
import ir.amirab.util.platform.Platform
import kotlinx.coroutines.CancellationException

/**
 * Kiểm tra yt-dlp có bản mới không và chỉ thông báo, không tự cập nhật.
 * Ở chế độ [silent] (kiểm tra tự động lúc khởi động) chỉ hiện thông báo khi có bản mới.
 */
class YtDlpUpdateNotifier(
    private val checker: YtDlpUpdateChecker,
    private val notificationSender: NotificationSender,
) {
    suspend fun check(silent: Boolean) {
        val result = try {
            checker.check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!silent) {
                notify(NotificationType.Error, "Can't check yt-dlp updates: ${e.localizedMessage.orEmpty()}")
            }
            return
        }
        when (result) {
            is YtDlpUpdateResult.UpdateAvailable -> notify(
                NotificationType.Info,
                "yt-dlp ${result.latest} is available (installed: ${result.installed}). ${updateHint()}",
            )

            is YtDlpUpdateResult.UpToDate -> if (!silent) {
                notify(NotificationType.Info, "yt-dlp is up to date (${result.version}).")
            }

            YtDlpUpdateResult.NotInstalled -> if (!silent) {
                notify(NotificationType.Warning, "yt-dlp was not found. ${installHint()}")
            }
        }
    }

    // winget chỉ có trên Windows; ở nền tảng khác chỉ dẫn tới trang phát hành để không đưa lệnh không chạy được
    private fun updateHint(): String {
        return if (Platform.isWindows()) {
            "Update it with: winget upgrade yt-dlp.yt-dlp"
        } else {
            "Update it with your package manager or see ${YtDlpUpdateChecker.RELEASES_PAGE_URL}"
        }
    }

    private fun installHint(): String {
        return if (Platform.isWindows()) {
            "Install it with: winget install yt-dlp.yt-dlp"
        } else {
            "Install it with your package manager or see ${YtDlpUpdateChecker.RELEASES_PAGE_URL}"
        }
    }

    private fun notify(type: NotificationType, message: String) {
        notificationSender.sendNotification(
            tag = NOTIFICATION_TAG,
            title = "yt-dlp".asStringSource(),
            description = message.asStringSource(),
            type = type,
        )
    }

    private companion object {
        const val NOTIFICATION_TAG = "YtDlpUpdate"
    }
}
