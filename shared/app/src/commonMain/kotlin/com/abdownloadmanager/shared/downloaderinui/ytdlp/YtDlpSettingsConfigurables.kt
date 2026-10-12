package com.abdownloadmanager.shared.downloaderinui.ytdlp

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.configurable.Configurable
import com.abdownloadmanager.shared.ui.configurable.item.EnumConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.IntConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.ToggleableSpeedLimitConfigurable
import com.abdownloadmanager.shared.util.SizeAndSpeedUnitProvider
import com.abdownloadmanager.shared.util.ThreadCountLimitation
import com.abdownloadmanager.shared.util.convertPositiveSpeedToHumanReadable
import ir.amirab.downloader.downloaditem.ytdlp.YtDlpPreset
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.asStringSourceWithARgs
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Các ô cài đặt dùng chung cho hộp thoại thêm mới và chỉnh sửa, vì cả hai chỉ khác nơi lưu giá trị.
 */
internal fun ytDlpConfigurables(
    preset: MutableStateFlow<YtDlpPreset>,
    speedLimit: MutableStateFlow<Long>,
    threadCount: MutableStateFlow<Int>,
    sizeAndSpeedUnitProvider: SizeAndSpeedUnitProvider,
): List<Configurable<*>> = listOf(
    EnumConfigurable(
        title = "Quality".asStringSource(),
        description = "Video quality, or audio only. Video and audio are merged into one MP4 file.".asStringSource(),
        backedBy = preset,
        itemTitle = { it.title.asStringSource() },
        possibleValues = YtDlpPreset.entries,
    ),
    ToggleableSpeedLimitConfigurable(
        Res.string.download_item_settings_speed_limit.asStringSource(),
        Res.string.download_item_settings_speed_limit_description.asStringSource(),
        backedBy = speedLimit,
        describe = {
            if (it == 0L) Res.string.unlimited.asStringSource()
            else convertPositiveSpeedToHumanReadable(
                it, sizeAndSpeedUnitProvider.speedUnit.value
            ).asStringSource()
        }
    ),
    IntConfigurable(
        Res.string.settings_download_thread_count.asStringSource(),
        Res.string.settings_download_thread_count_description.asStringSource(),
        backedBy = threadCount,
        range = 0..ThreadCountLimitation.MAX_ALLOWED_THREAD_COUNT,
        describe = {
            if (it == 0) Res.string.use_global_settings.asStringSource()
            else Res.string.download_item_settings_thread_count_describe
                .asStringSourceWithARgs(
                    Res.string.download_item_settings_thread_count_describe_createArgs(
                        count = it.toString()
                    )
                )
        }
    ),
)
