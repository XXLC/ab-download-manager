package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.downloaderinui.BasicDownloadItem
import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.DownloaderInUi
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadChecker
import com.abdownloadmanager.shared.util.DownloadSystem
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.IYtDlpCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadItem
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadJob
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloader
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.downloader.monitor.ProcessingDownloadItemFactoryInputs
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.monitor.RangeBasedProcessingDownloadItemState
import ir.amirab.downloader.monitor.UiRangedPart
import ir.amirab.downloader.part.PartDownloadStatus
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

class YtDlpDownloaderInUi(
    downloader: YtDlpDownloader,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : DownloaderInUi<
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker,
        YtDlpDownloadItem,
        YtDlpNewDownloadInputs,
        YtDlpEditDownloadInputs,
        YtDlpItemToCredentialMapper,
        YtDlpDownloadJob,
        YtDlpDownloader,
        >(downloader) {

    override val name: StringSource = "yt-dlp".asStringSource()

    override fun createLinkChecker(initialCredentials: YtDlpDownloadCredentials): YtDlpLinkChecker {
        return YtDlpLinkChecker(initialCredentials, downloader.client)
    }

    override fun newDownloadUiChecker(
        initialCredentials: YtDlpDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope,
    ): YtDlpNewDownloadUiChecker {
        return YtDlpNewDownloadUiChecker(
            initialCredentials = initialCredentials,
            linkCheckerFactory = this,
            initialFolder = initialFolder,
            initialName = initialName,
            downloadSystem = downloadSystem,
            scope = scope,
        )
    }

    override fun createNewDownloadInputs(
        initialCredentials: YtDlpDownloadCredentials,
        initialFolder: String,
        initialName: String,
        downloadSystem: DownloadSystem,
        scope: CoroutineScope,
    ): YtDlpNewDownloadInputs {
        return YtDlpNewDownloadInputs(
            downloadUiChecker = newDownloadUiChecker(
                initialCredentials = initialCredentials,
                initialFolder = initialFolder,
                initialName = initialName,
                downloadSystem = downloadSystem,
                scope = scope,
            ),
            sizeAndSpeedUnitProvider = sizeAndSpeedUnitProvider,
        )
    }

    override fun createEditDownloadChecker(
        currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        linkChecker: YtDlpLinkChecker,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope,
    ): EditDownloadChecker<YtDlpDownloadItem, YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker> {
        return YtDlpEditDownloadChecker(
            currentDownloadItem = currentDownloadItem,
            editedDownloadItem = editedDownloadItem,
            linkChecker = linkChecker,
            conflictDetector = conflictDetector,
            scope = scope,
        )
    }

    override fun createEditDownloadInputs(
        currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
        conflictDetector: DownloadConflictDetector,
        scope: CoroutineScope,
    ): YtDlpEditDownloadInputs {
        return YtDlpEditDownloadInputs(
            currentDownloadItem = currentDownloadItem,
            editedDownloadItem = editedDownloadItem,
            mapper = YtDlpItemToCredentialMapper(),
            conflictDetector = conflictDetector,
            linkCheckerFactory = this,
            editDownloadCheckerFactory = this,
            scope = scope,
            sizeAndSpeedUnitProvider = sizeAndSpeedUnitProvider,
        )
    }

    override fun acceptDownloadCredentials(item: IDownloadCredentials): Boolean {
        return item is IYtDlpCredentials
    }

    /**
     * Chỉ tự nhận các trang video quen thuộc. Các link khác vẫn chọn được thủ công trong mục "Downloader".
     */
    override fun supportsThisLink(link: String): Boolean {
        return VIDEO_SITES.containsMatchIn(link)
    }

    override fun createMinimumCredentials(link: String): YtDlpDownloadCredentials {
        return YtDlpDownloadCredentials(link = link)
    }

    override fun createBareDownloadItem(
        credentials: YtDlpDownloadCredentials,
        basicDownloadItem: BasicDownloadItem,
    ): YtDlpDownloadItem {
        return YtDlpDownloadItem.createWithCredentials(
            credentials = credentials,
            id = -1,
            folder = basicDownloadItem.folder,
            name = basicDownloadItem.name,
            contentLength = basicDownloadItem.contentLength,
            preferredConnectionCount = basicDownloadItem.preferredConnectionCount,
            speedLimit = basicDownloadItem.speedLimit,
            fileChecksum = basicDownloadItem.fileChecksum,
        )
    }

    override fun createProcessingDownloadItemState(
        props: ProcessingDownloadItemFactoryInputs<YtDlpDownloadJob>,
    ): ProcessingDownloadItemState {
        val job = props.downloadJob
        val item = job.downloadItem
        val status = job.status.value
        val length = item.contentLength
        val downloaded = job.getDownloadedSize()
        // yt-dlp tự chia luồng nên với giao diện đây chỉ là một đoạn duy nhất
        val part = UiRangedPart(
            id = 0,
            status = if (status is DownloadJobStatus.IsActive) PartDownloadStatus.ReceivingData else PartDownloadStatus.IDLE,
            howMuchProceed = downloaded,
            percent = if (length > 0) (downloaded * 100 / length).toInt().coerceIn(0, 100) else null,
            length = length.takeIf { it > 0 },
            partSpace = 1f,
        )
        return RangeBasedProcessingDownloadItemState(
            id = item.id,
            folder = item.folder,
            name = item.name,
            downloadLink = item.link,
            contentLength = length,
            saveLocation = item.name,
            dateAdded = item.dateAdded,
            startTime = item.startTime ?: -1,
            completeTime = item.completeTime ?: -1,
            status = status,
            speed = props.speed,
            parts = listOf(part),
            supportResume = true,
            isWaiting = props.isWaiting,
        )
    }

    private companion object {
        val VIDEO_SITES = Regex(
            """^https?://([\w-]+\.)*(youtube\.com|youtu\.be|vimeo\.com|dailymotion\.com|twitch\.tv|tiktok\.com|bilibili\.com|soundcloud\.com)(/|\?|$)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
