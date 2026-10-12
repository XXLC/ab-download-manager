package com.abdownloadmanager.updateapplier

import com.abdownloadmanager.updatechecker.UpdateInfo
import ir.amirab.util.URLOpener

/**
 * Không bao giờ tự tải hay cài bản mới: chỉ mở trang phát hành để người dùng tự xem và quyết định.
 * Dùng cho bản tự build, vì cài bản phát hành chính thức sẽ ghi đè các thay đổi riêng.
 */
class NotifyOnlyUpdateApplier(
    private val releasePageUrl: String,
) : UpdateApplier {
    // true để mục "Check for update" vẫn dùng được, việc kiểm tra không thay đổi gì trên máy
    override fun updateSupported(): Boolean = true

    override suspend fun applyUpdate(updateInfo: UpdateInfo) {
        URLOpener.openUrl(releasePageUrl)
    }

    override suspend fun cleanup() {}
}
