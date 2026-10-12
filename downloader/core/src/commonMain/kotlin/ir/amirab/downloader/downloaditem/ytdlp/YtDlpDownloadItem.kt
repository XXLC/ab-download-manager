package ir.amirab.downloader.downloaditem.ytdlp

import arrow.core.Option
import arrow.core.getOrElse
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.util.HttpUrlUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ytdlp")
data class YtDlpDownloadItem(
    override var id: Long,
    override var folder: String,
    override var name: String,
    override var link: String,
    override var contentLength: Long = IDownloadItem.LENGTH_UNKNOWN,
    override var downloadPage: String? = null,
    override var dateAdded: Long = 0,
    override var startTime: Long? = null,
    override var completeTime: Long? = null,
    override var status: DownloadStatus = DownloadStatus.Added,
    override var preferredConnectionCount: Int? = null,
    override var speedLimit: Long = 0,
    override var fileChecksum: String? = null,
    override var formatPreset: String = YtDlpPreset.Default.id,
) : IDownloadItem, IYtDlpCredentials {
    override fun copy(
        id: Option<Long>,
        folder: Option<String>,
        name: Option<String>,
        link: Option<String>,
        contentLength: Option<Long>,
        downloadPage: Option<String?>,
        dateAdded: Option<Long>,
        startTime: Option<Long?>,
        completeTime: Option<Long?>,
        status: Option<DownloadStatus>,
        preferredConnectionCount: Option<Int?>,
        speedLimit: Option<Long>,
        fileChecksum: Option<String?>,
    ): IDownloadItem {
        return copy(
            id = id.getOrElse { this.id },
            folder = folder.getOrElse { this.folder },
            name = name.getOrElse { this.name },
            link = link.getOrElse { this.link },
            contentLength = contentLength.getOrElse { this.contentLength },
            downloadPage = downloadPage.getOrElse { this.downloadPage },
            dateAdded = dateAdded.getOrElse { this.dateAdded },
            startTime = startTime.getOrElse { this.startTime },
            completeTime = completeTime.getOrElse { this.completeTime },
            status = status.getOrElse { this.status },
            preferredConnectionCount = preferredConnectionCount.getOrElse { this.preferredConnectionCount },
            speedLimit = speedLimit.getOrElse { this.speedLimit },
            fileChecksum = fileChecksum.getOrElse { this.fileChecksum },
        )
    }

    override fun copy(
        link: Option<String>,
        downloadPage: Option<String?>,
    ): YtDlpDownloadItem {
        return copy(
            link = link.getOrElse { this.link },
            downloadPage = downloadPage.getOrElse { this.downloadPage },
        )
    }

    override fun validateItem() {
        validateCredentials()
    }

    override fun validateCredentials() {
        require(HttpUrlUtils.isValidUrl(link)) { "invalid url: $link" }
    }

    override fun withCredentials(credentials: IDownloadCredentials): YtDlpDownloadItem {
        return if (credentials is IYtDlpCredentials) {
            apply {
                link = credentials.link
                downloadPage = credentials.downloadPage
                formatPreset = credentials.formatPreset
            }
        } else {
            this
        }
    }

    val preset: YtDlpPreset
        get() = YtDlpPreset.fromId(formatPreset)

    companion object {
        fun createWithCredentials(
            credentials: IYtDlpCredentials,
            id: Long,
            folder: String,
            name: String,
            contentLength: Long = IDownloadItem.LENGTH_UNKNOWN,
            dateAdded: Long = 0,
            status: DownloadStatus = DownloadStatus.Added,
            preferredConnectionCount: Int? = null,
            speedLimit: Long = 0,
            fileChecksum: String? = null,
        ): YtDlpDownloadItem {
            return YtDlpDownloadItem(
                id = id,
                folder = folder,
                name = name,
                link = credentials.link,
                contentLength = contentLength,
                downloadPage = credentials.downloadPage,
                dateAdded = dateAdded,
                status = status,
                preferredConnectionCount = preferredConnectionCount,
                speedLimit = speedLimit,
                fileChecksum = fileChecksum,
                formatPreset = credentials.formatPreset,
            )
        }
    }
}

fun YtDlpDownloadItem.applyFrom(other: YtDlpDownloadItem) {
    id = other.id
    folder = other.folder
    name = other.name
    link = other.link
    contentLength = other.contentLength
    downloadPage = other.downloadPage
    dateAdded = other.dateAdded
    startTime = other.startTime
    completeTime = other.completeTime
    status = other.status
    preferredConnectionCount = other.preferredConnectionCount
    speedLimit = other.speedLimit
    fileChecksum = other.fileChecksum
    formatPreset = other.formatPreset
}
