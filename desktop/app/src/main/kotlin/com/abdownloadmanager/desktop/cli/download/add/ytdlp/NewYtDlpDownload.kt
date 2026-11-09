package com.abdownloadmanager.desktop.cli.download.add.ytdlp

import com.abdownloadmanager.desktop.cli.download.add.shared.BaseNewDownload
import com.abdownloadmanager.integration.model.IDownloadCredentialsFromIntegration
import com.abdownloadmanager.integration.model.YtDlpDownloadCredentialsFromIntegration
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpPreset

class NewYtDlpDownload : BaseNewDownload("ytdlp") {

    val link by linkOption()
    val preset by option(
        "--preset",
        help = "quality: ${YtDlpPreset.entries.joinToString { it.id }}",
    ).choice(*YtDlpPreset.entries.map { it.id }.toTypedArray())
        .default(YtDlpPreset.Default.id)

    override fun createDownload(): IDownloadCredentialsFromIntegration {
        return YtDlpDownloadCredentialsFromIntegration(
            link = link,
            downloadPage = downloadPage,
            formatPreset = preset,
        )
    }
}
