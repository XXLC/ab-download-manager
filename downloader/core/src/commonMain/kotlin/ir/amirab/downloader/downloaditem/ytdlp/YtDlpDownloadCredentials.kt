package ir.amirab.downloader.downloaditem.ytdlp

import arrow.core.Option
import arrow.core.getOrElse
import ir.amirab.downloader.downloaditem.IDownloadCredentials
import ir.amirab.util.HttpUrlUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface IYtDlpCredentials : IDownloadCredentials {
    /** id của [YtDlpPreset] */
    val formatPreset: String
}

@Serializable
@SerialName("ytdlp")
data class YtDlpDownloadCredentials(
    override val link: String,
    override val downloadPage: String? = null,
    override val formatPreset: String = YtDlpPreset.Default.id,
) : IYtDlpCredentials {
    override fun copy(
        link: Option<String>,
        downloadPage: Option<String?>,
    ): IDownloadCredentials {
        return copy(
            link = link.getOrElse { this.link },
            downloadPage = downloadPage.getOrElse { this.downloadPage },
        )
    }

    override fun validateCredentials() {
        require(HttpUrlUtils.isValidUrl(link)) { "invalid url: $link" }
    }
}
