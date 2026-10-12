package ir.amirab.downloader.downloaditem.ytdlp

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.destination.DownloadDestination
import ir.amirab.downloader.downloaditem.DownloadJob
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.utils.ExceptionUtils
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Tải bằng yt-dlp. Tạm dừng là dừng tiến trình, tiếp tục là chạy lại (yt-dlp tự nối tiếp từ tệp `.part`).
 * Không tự thử lại khi lỗi: trạng thái lỗi hiện ra và người dùng bấm tiếp tục.
 */
class YtDlpDownloadJob(
    override val downloadItem: YtDlpDownloadItem,
    downloadManager: DownloadManager,
    private val client: YtDlpClient,
) : DownloadJob(
    downloadManager = downloadManager,
) {
    private val listDb by downloadManager::dlListDb

    private lateinit var destination: YtDlpDestination
    private val itemSaveLock = Mutex()
    private val downloadedBytes = AtomicLong(0)
    private var lastSavedDownloadItem: YtDlpDownloadItem? = null

    override fun getDestination(): DownloadDestination = destination

    override suspend fun actualBoot() {
        initializeDestination()
    }

    override fun initializeDestination() {
        destination = YtDlpDestination(downloadManager.calculateOutputFile(downloadItem))
    }

    override suspend fun resume() {
        if (isDownloadActive.value) {
            return
        }
        _isDownloadActive.update { true }
        val activeScope = newScopeBasedOn(scope).also { activeDownloadScope = it }
        activeScope.launch {
            boot()
            onDownloadResuming()
            try {
                destination.prepareDestinationFolder()
                var started = false
                client.download(createRequest()) { progress ->
                    downloadedBytes.set(progress.downloadedBytes)
                    // contentLength là ước lượng từ lúc kiểm tra link, nới ra để phần trăm không vượt 100%
                    if (downloadItem.contentLength > 0 && progress.downloadedBytes > downloadItem.contentLength) {
                        downloadItem.contentLength = progress.downloadedBytes
                    }
                    if (!started) {
                        started = true
                        markAsDownloading()
                    }
                }
                adoptActualOutputName()
                onDownloadFinished()
            } catch (e: Exception) {
                if (ExceptionUtils.isNormalCancellation(e)) {
                    // pause() is already in charge of this
                    return@launch
                }
                // pause() cancels this scope, so it must run from the main one
                scope.launch { pause(e) }
            }
        }
    }

    private suspend fun markAsDownloading() {
        downloadItem.status = DownloadStatus.Downloading
        if (downloadItem.startTime == null) {
            downloadItem.startTime = System.currentTimeMillis()
        }
        saveState()
        onDownloadResumed()
    }

    private fun createRequest(): YtDlpDownloadRequest {
        val output = destination.outputFile
        return YtDlpDownloadRequest(
            link = downloadItem.link,
            preset = downloadItem.preset,
            outputDirectory = output.parentFile,
            baseName = destination.baseName,
            connections = downloadItem.preferredConnectionCount
                ?: downloadManager.settings.defaultThreadCount,
            speedLimit = downloadItem.speedLimit,
        )
    }

    /** yt-dlp có thể chọn đuôi khác dự kiến, cập nhật tên để item trỏ đúng tệp thật. */
    private fun adoptActualOutputName() {
        val actual = destination.findActualOutput()
        if (actual != null && actual != destination.outputFile) {
            downloadItem.name = actual.name
            initializeDestination()
        }
    }

    override suspend fun pause(throwable: Throwable) {
        boot()
        activeDownloadScope?.coroutineContext?.job?.cancelAndJoin()
        activeDownloadScope = null
        onDownloadCanceled(throwable)
    }

    override suspend fun reset() {
        pause()
        destination.cleanUpJunkFiles()
        destination.deleteOutputFile()
        downloadedBytes.set(0)
        downloadItem.contentLength = IDownloadItem.LENGTH_UNKNOWN
        downloadItem.status = DownloadStatus.Added
        downloadItem.startTime = null
        downloadItem.completeTime = null
        saveState()
        downloadManager.onDownloadItemChange(downloadItem)
    }

    override fun getDownloadedSize(): Long {
        return if (isDownloadActive.value) {
            downloadedBytes.get()
        } else {
            destination.sizeOnDisk()
        }
    }

    override fun onDownloadFinishedBeforeSave() {
        downloadItem.contentLength = destination.outputFile.length()
    }

    override suspend fun saveState() {
        itemSaveLock.withLock {
            if (lastSavedDownloadItem != downloadItem) {
                listDb.update(downloadItem)
                lastSavedDownloadItem = downloadItem.copy()
            }
        }
    }

    override suspend fun changeConfig(
        updater: (IDownloadItem) -> Unit,
        extraConfig: DownloadJobExtraConfig?,
    ): IDownloadItem {
        boot()
        val previousItem = downloadItem.copy()
        val newItem = previousItem.copy().apply(updater)
        val previousDestination = downloadManager.calculateOutputFile(previousItem)
        val newDestination = downloadManager.calculateOutputFile(newItem)
        val shouldUpdateDestination = previousDestination != newDestination
        val needsRestart = isDownloadActive.value && (
                shouldUpdateDestination ||
                        previousItem.link != newItem.link ||
                        previousItem.formatPreset != newItem.formatPreset ||
                        previousItem.preferredConnectionCount != newItem.preferredConnectionCount ||
                        previousItem.speedLimit != newItem.speedLimit
                )
        if (needsRestart) {
            pause()
        }
        if (shouldUpdateDestination) {
            destination.moveOutput(newDestination)
        }
        downloadItem.applyFrom(newItem)
        if (shouldUpdateDestination) {
            initializeDestination()
        }
        saveState()
        if (needsRestart) {
            resume()
        }
        return downloadItem
    }

    override fun reloadSettings() {
        // thread count is read when yt-dlp starts, nothing to apply live
    }

    override suspend fun extraConfigsReceived(config: DownloadJobExtraConfig) {
        // yt-dlp has no extra config
    }
}
