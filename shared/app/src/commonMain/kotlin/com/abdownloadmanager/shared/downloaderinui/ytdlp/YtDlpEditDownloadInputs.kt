package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.LinkCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadCheckerFactory
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadInputs
import com.abdownloadmanager.shared.ui.configurable.Configurable
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import ir.amirab.downloader.downloaditem.DownloadJobExtraConfig
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadItem
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.util.compose.StringSource
import ir.amirab.util.flow.mapTwoWayStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class YtDlpEditDownloadInputs(
    currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    mapper: YtDlpItemToCredentialMapper,
    conflictDetector: DownloadConflictDetector,
    linkCheckerFactory: LinkCheckerFactory<YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>,
    editDownloadCheckerFactory: EditDownloadCheckerFactory<YtDlpDownloadItem, YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>,
    scope: CoroutineScope,
    private val sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
) : EditDownloadInputs<
        YtDlpDownloadItem,
        YtDlpDownloadCredentials,
        YtDlpResponseInfo,
        DownloadSize.Bytes,
        YtDlpLinkChecker,
        YtDlpItemToCredentialMapper,
        >(
    currentDownloadItem,
    editedDownloadItem,
    mapper = mapper,
    scope = scope,
    conflictDetector = conflictDetector,
    linkCheckerFactory = linkCheckerFactory,
    editDownloadCheckerFactory = editDownloadCheckerFactory,
) {
    override val configurableList: List<Configurable<*>> = ytDlpConfigurables(
        preset = editedDownloadItem.mapTwoWayStateFlow(
            map = { it.preset },
            unMap = { copy(formatPreset = it.id) },
        ),
        speedLimit = editedDownloadItem.mapTwoWayStateFlow(
            map = { it.speedLimit },
            unMap = { copy(speedLimit = it) },
        ),
        threadCount = editedDownloadItem.mapTwoWayStateFlow(
            map = { it.preferredConnectionCount ?: 0 },
            unMap = { copy(preferredConnectionCount = it.takeIf { it >= 1 }) },
        ),
        sizeAndSpeedUnitProvider = sizeAndSpeedUnitProvider,
    )

    override val downloadJobConfig: StateFlow<DownloadJobExtraConfig?> = MutableStateFlow(null)

    override fun applyEditedItemTo(item: YtDlpDownloadItem) {
        val edited = editedDownloadItem.value
        // id, dateAdded, startTime, completeTime và status do job quản lý nên không đổi ở đây
        item.link = edited.link
        item.downloadPage = edited.downloadPage
        item.folder = edited.folder
        item.name = edited.name
        item.contentLength = edited.contentLength
        item.preferredConnectionCount = edited.preferredConnectionCount
        item.speedLimit = edited.speedLimit
        item.fileChecksum = edited.fileChecksum
        item.formatPreset = edited.formatPreset
    }

    override fun downloadSizeToStringSource(downloadSize: DownloadSize.Bytes): StringSource {
        return downloadSize.asStringSource(sizeAndSpeedUnitProvider.sizeUnit.value)
    }
}
