package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.LinkChecker
import com.abdownloadmanager.shared.util.FilenameFixer
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpClient
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpPreset
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class YtDlpLinkChecker(
    initialCredentials: YtDlpDownloadCredentials,
    private val client: YtDlpClient,
) : LinkChecker<YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes>(initialCredentials) {
    private val _suggestedName = MutableStateFlow<String?>(null)
    override val suggestedName: StateFlow<String?> = _suggestedName.asStateFlow()

    private val _size = MutableStateFlow<Long?>(null)
    override val downloadSize: StateFlow<DownloadSize.Bytes?> = _size.mapStateFlow {
        it?.let(DownloadSize::Bytes)
    }

    override fun infoUpdated(responseInfo: YtDlpResponseInfo?) {
        val preset = YtDlpPreset.fromId(credentials.value.formatPreset)
        _suggestedName.value = responseInfo?.mediaInfo?.title?.let { fileNameFor(it, preset) }
        _size.value = responseInfo?.mediaInfo?.sizeBytes
    }

    override suspend fun actualCheck(credentials: YtDlpDownloadCredentials): YtDlpResponseInfo {
        val preset = YtDlpPreset.fromId(credentials.formatPreset)
        return YtDlpResponseInfo(client.fetchInfo(credentials.link, preset))
    }

    companion object {
        // Windows giới hạn đường dẫn ~260 ký tự và yt-dlp còn thêm hậu tố tạm như .f137.mp4.part
        private const val MAX_TITLE_LENGTH = 120

        fun fileNameFor(title: String, preset: YtDlpPreset): String {
            return FilenameFixer.fix(title).take(MAX_TITLE_LENGTH).trimEnd() + "." + preset.extension
        }
    }
}
