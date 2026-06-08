package cn.edu.shmtu.terminal.android.ui.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.cloud.BackupStatus
import cn.edu.shmtu.terminal.android.data.cloud.CloudBackupManager
import cn.edu.shmtu.terminal.android.data.cloud.CloudBackupRecord
import cn.edu.shmtu.terminal.android.data.cloud.WebDavConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val manager: CloudBackupManager
) : ViewModel() {

    val backupStatus: StateFlow<BackupStatus> = manager.backupStatus
    val backupHistory: StateFlow<List<CloudBackupRecord>> = manager.backupHistory

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _webDavConfig = MutableStateFlow(WebDavUiState())
    val webDavConfig: StateFlow<WebDavUiState> = _webDavConfig.asStateFlow()

    init {
        manager.restoreConfig()
        manager.loadHistory()
        _webDavConfig.value = WebDavUiState(
            serverUrl = manager.restoreWebDavServerUrl(),
            username = manager.restoreWebDavUsername(),
            root = manager.restoreWebDavRoot()
        )
    }

    fun configureWebDav(url: String, username: String, password: String, root: String) {
        manager.configureWebDav(WebDavConfig(
            serverUrl = url, username = username, password = password,
            backupRoot = root.ifBlank { "shmtu-backup" }
        ))
    }

    fun saveWebDavConfig(url: String, username: String, password: String, root: String) {
        manager.configureWebDav(WebDavConfig(url, username, password, root.ifBlank { "shmtu-backup" }))
        _message.value = "已保存"
    }

    fun testConnection(providerId: String) {
        viewModelScope.launch {
            val ok = manager.testConnection(providerId)
            _message.value = if (ok) "✓ 连接成功" else "✗ 连接失败，请检查配置"
        }
    }

    fun backupNow(providerId: String, password: String?) {
        viewModelScope.launch {
            val result = manager.backupNow(providerId, password)
            _message.value = if (result.isSuccess) "✓ 备份完成" else "✗ ${result.exceptionOrNull()?.message}"
        }
    }

    fun restoreBackup(providerId: String, record: CloudBackupRecord, password: String?) {
        viewModelScope.launch {
            val result = manager.restoreBackup(providerId, record, password)
            _message.value = if (result.isSuccess) {
                val r = result.getOrNull()!!
                "✓ 恢复完成：${r.identities}身份/${r.accounts}账号/${r.bills}账单"
            } else "✗ ${result.exceptionOrNull()?.message}"
        }
    }

    fun getWebDavServerUrl(): String = _webDavConfig.value.serverUrl
    fun getWebDavUsername(): String = _webDavConfig.value.username
    fun getWebDavRoot(): String = _webDavConfig.value.root.ifBlank { "shmtu-backup" }

    fun clearMessage() { _message.value = null }
}

data class WebDavUiState(
    val serverUrl: String = "",
    val username: String = "",
    val root: String = "shmtu-backup"
)
