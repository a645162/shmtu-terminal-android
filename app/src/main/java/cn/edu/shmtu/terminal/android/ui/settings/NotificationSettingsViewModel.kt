package cn.edu.shmtu.terminal.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.data.notification.NotificationConfig
import cn.edu.shmtu.terminal.android.data.notification.bot.BotManager
import cn.edu.shmtu.terminal.android.data.notification.WebhookType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val botManager: BotManager
) : ViewModel() {

    val config: StateFlow<NotificationConfig> = settingsDataStore.notificationConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationConfig())

    fun update(transform: (NotificationConfig) -> NotificationConfig) {
        val newConfig = transform(config.value)
        viewModelScope.launch {
            settingsDataStore.setNotificationConfig(newConfig)
        }
    }

    fun setWebhookType(type: WebhookType) {
        update { it.copy(webhookType = type) }
    }

    fun testWebhook(onResult: (Boolean, String) -> Unit) {
        val current = config.value
        if (!current.webhookEnabled || current.webhookUrl.isBlank()) {
            onResult(false, "Webhook 未启用或 URL 为空")
            return
        }
        viewModelScope.launch {
            val result = botManager.forward(
                config = current,
                title = "Webhook 测试",
                content = "这是一条来自海大终端的测试通知消息。",
                vars = emptyMap()
            )
            result.fold(
                onSuccess = { onResult(true, "发送成功") },
                onFailure = { onResult(false, it.message ?: "发送失败") }
            )
        }
    }
}
