package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.shared.downloaderinui.DownloadSize
import com.abdownloadmanager.shared.downloaderinui.edit.CanEditDownloadResult
import com.abdownloadmanager.shared.downloaderinui.edit.DownloadConflictDetector
import com.abdownloadmanager.shared.downloaderinui.edit.EditDownloadChecker
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadCredentials
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpDownloadItem
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpResponseInfo
import ir.amirab.util.FileNameValidator
import ir.amirab.util.HttpUrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class YtDlpEditDownloadChecker(
    currentDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    editedDownloadItem: MutableStateFlow<YtDlpDownloadItem>,
    linkChecker: YtDlpLinkChecker,
    conflictDetector: DownloadConflictDetector,
    scope: CoroutineScope,
) : EditDownloadChecker<YtDlpDownloadItem, YtDlpDownloadCredentials, YtDlpResponseInfo, DownloadSize.Bytes, YtDlpLinkChecker>(
    currentDownloadItem = currentDownloadItem,
    editedDownloadItem = editedDownloadItem,
    linkChecker = linkChecker,
    conflictDetector = conflictDetector,
    scope = scope,
) {
    init {
        editedDownloadItem
            .onEach { _canEditResult.value = CanEditDownloadResult.Waiting }
            .launchIn(scope)
    }

    override fun check() {
        _canEditResult.value = CanEditDownloadResult.Waiting
        _canEditResult.value = check(
            current = currentDownloadItem.value,
            edited = editedDownloadItem.value,
        )
    }

    private fun check(
        current: YtDlpDownloadItem,
        edited: YtDlpDownloadItem,
    ): CanEditDownloadResult {
        if (current == edited) {
            return CanEditDownloadResult.NothingChanged
        }
        if (!HttpUrlUtils.isValidUrl(edited.link)) {
            return CanEditDownloadResult.InvalidURL
        }
        if (edited.name != current.name) {
            if (!FileNameValidator.isValidFileName(edited.name)) {
                return CanEditDownloadResult.InvalidFileName
            }
            if (conflictDetector.checkAlreadyExists(current, edited)) {
                return CanEditDownloadResult.FileNameAlreadyExists
            }
        }
        return CanEditDownloadResult.CanEdit(emptyList())
    }
}
