package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.add.NewDownloadInputs
import com.abdownloadmanager.shared.ui.configurable.Configurable
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import com.abdownloadmanager.shared.util.perhostsettings.PerHostSettingsItem
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadItem
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpPreset
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.util.compose.StringSource
import ir.amirab.util.flow.combineStateFlows
import ir.amirab.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class YtDlpNewDownloadInputs(
    downloadUiChecker: YtDlpNewDownloadUiChecker,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : NewDownloadInputs<
        YtDlpDownloadItem,
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker,
        >(
    newDownloadUiChecker = downloadUiChecker
) {
    private val threadCount = MutableStateFlow(null as Int?)
    private val speedLimit = MutableStateFlow(0L)

    override val downloadItem: StateFlow<YtDlpDownloadItem> = combineStateFlows(
        this.credentials,
        this.folder,
        this.name,
        this.downloadSize,
        this.speedLimit,
        this.threadCount,
    ) { credentials, folder, name, size, speedLimit, threadCount ->
        YtDlpDownloadItem(
            id = -1,
            folder = folder,
            name = name,
            link = credentials.link,
            contentLength = size?.bytes ?: IDownloadItem.LENGTH_UNKNOWN,
            dateAdded = openedTime,
            status = DownloadStatus.Added,
            preferredConnectionCount = threadCount,
            speedLimit = speedLimit,
        ).withCredentials(credentials)
    }

    override val downloadJobConfig: StateFlow<DownloadJobExtraConfig?> = MutableStateFlow(null)

    override fun applyHostSettingsToExtraConfig(extraConfig: PerHostSettingsItem) {
        extraConfig.threadCount?.let { threadCount.value = it }
        extraConfig.speedLimit?.let { speedLimit.value = it }
    }

    override val configurableList: List<Configurable<*>> = ytDlpConfigurables(
        preset = credentials.mapTwoWayStateFlow(
            map = { YtDlpPreset.fromId(it.formatPreset) },
            unMap = { copy(formatPreset = it.id) },
        ),
        speedLimit = speedLimit,
        threadCount = threadCount.mapTwoWayStateFlow(
            map = { it ?: 0 },
            unMap = { it.takeIf { it >= 1 } },
        ),
        sizeAndSpeedUnitProvider = sizeAndSpeedUnitProvider,
    )

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Bytes): StringSource {
        return downloadSize.asStringSource(sizeAndSpeedUnitProvider.sizeUnit.value)
    }
}
