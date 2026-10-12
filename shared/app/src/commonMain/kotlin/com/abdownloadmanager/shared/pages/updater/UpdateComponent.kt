package com.abdownloadmanager.shared.pages.updater

import com.abdownloadmanager.UpdateCheckStatus
import com.abdownloadmanager.shared.util.AppVersion
import com.abdownloadmanager.shared.util.BaseComponent
import com.abdownloadmanager.UpdateManager
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pagemanager.NotificationSender
import com.abdownloadmanager.shared.ui.widget.MessageDialogType
import com.abdownloadmanager.shared.util.mvi.ContainsEffects
import com.abdownloadmanager.shared.util.mvi.supportEffects
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.component.KoinComponent
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UpdateComponent(
    ctx: ComponentContext,
    private val notificationSender: NotificationSender,
    private val updateManager: UpdateManager,
) : BaseComponent(ctx),
    ContainsEffects<UpdateComponent.Effects> by supportEffects(),
    KoinComponent {

    fun isUpdateSupported(): Boolean {
        return updateManager.isUpdateSupported()
    }

    val currentVersion = AppVersion.get()
    val showNewUpdate = MutableStateFlow(false)
    val newVersionData = updateManager.newVersionData
    private var updateApplierJob: Job? = null

    val updateCheckStatus = updateManager.updateCheckStatus

    fun performUpdate() {
        updateApplierJob?.cancel()
        updateApplierJob = scope.launch {
            try {
                updateManager.update()
            } catch (e: Exception) {
                showMessage(e)
            }
        }
    }

    private fun showMessage(e: Exception) {
        e.printStackTrace()
        notificationSender.sendDialogNotification(
            Res.string.update_error.asStringSource(),
            e.localizedMessage.orEmpty().asStringSource(),
            type = MessageDialogType.Error,
        )
    }

    fun showNewUpdate() {
        showNewUpdate.update { true }
    }

    // các lần kiểm tra chạy lần lượt để kết quả luôn thuộc về đúng yêu cầu đã gọi nó
    private val checkMutex = Mutex()

    /**
     * Kiểm tra bản mới. Ở chế độ [silent] (kiểm tra tự động lúc khởi động) chỉ hiện thông báo khi có bản mới,
     * không hiện "đang kiểm tra", "không có bản mới" hay lỗi.
     */
    fun requestCheckForUpdate(silent: Boolean = false) {
        scope.launch {
            checkMutex.withLock {
                if (!silent) sendEffect(Effects.CheckingForUpdate)
                updateManager.checkForUpdate()
                when (val status = updateManager.updateCheckStatus.value) {
                    is UpdateCheckStatus.Error -> if (!silent) sendEffect(Effects.Error(status.e))
                    UpdateCheckStatus.NewUpdate -> {
                        sendEffect(Effects.NewUpdate)
                        showNewUpdate()
                    }

                    UpdateCheckStatus.NoUpdate -> if (!silent) sendEffect(Effects.NoUpdate)
                    UpdateCheckStatus.IDLE, UpdateCheckStatus.Checking -> {}
                }
            }
        }
    }

    fun requestClose() {
        showNewUpdate.update { false }
    }

    sealed interface Effects {
        data object CheckingForUpdate : Effects
        data object NoUpdate : Effects
        data object NewUpdate : Effects
        data class Error(val throwable: Throwable) : Effects
    }
}
