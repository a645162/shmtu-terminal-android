package cn.edu.shmtu.terminal.android.ui.cloud

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.cloud.BackupStatus
import cn.edu.shmtu.terminal.android.data.cloud.CloudBackupManager
import cn.edu.shmtu.terminal.android.data.cloud.CloudBackupMeta
import cn.edu.shmtu.terminal.android.data.cloud.CloudBackupWorker
import cn.edu.shmtu.terminal.android.data.cloud.DeviceFlowDisplayInfo
import cn.edu.shmtu.terminal.android.data.cloud.WebDavConfig
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val manager: CloudBackupManager,
    private val settingsDataStore: SettingsDataStore,
    private val application: Application
) : ViewModel() {

    val backupStatus: StateFlow<BackupStatus> = manager.backupStatus

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _selectedProvider = MutableStateFlow(
        settingsDataStore.getCloudBackupProviderId() ?: "webdav"
    )
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _webDavServerUrl = MutableStateFlow(manager.restoreWebDavServerUrl())
    val webDavServerUrl: StateFlow<String> = _webDavServerUrl.asStateFlow()
    private val _webDavUsername = MutableStateFlow(manager.restoreWebDavUsername())
    val webDavUsername: StateFlow<String> = _webDavUsername.asStateFlow()
    private val _webDavPassword = MutableStateFlow(settingsDataStore.getCloudBackupPassword().orEmpty())
    val webDavPassword: StateFlow<String> = _webDavPassword.asStateFlow()
    private val _webDavRoot = MutableStateFlow(manager.restoreWebDavRoot())
    val webDavRoot: StateFlow<String> = _webDavRoot.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val autoEnabled: StateFlow<Boolean> = settingsDataStore.cloudBackupAutoEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoIntervalMinutes: StateFlow<Int> = settingsDataStore.cloudBackupAutoInterval
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 360)
    val maxKeep: StateFlow<Int> = settingsDataStore.cloudBackupMaxKeep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    private val _googleClientId = MutableStateFlow(settingsDataStore.getGoogleDriveClientId())
    val googleClientId: StateFlow<String> = _googleClientId.asStateFlow()
    private val _googleClientSecret = MutableStateFlow(settingsDataStore.getGoogleDriveClientSecret())
    val googleClientSecret: StateFlow<String> = _googleClientSecret.asStateFlow()
    private val _googleLoggedIn = MutableStateFlow(settingsDataStore.getGoogleDriveCredentials()?.isValid() == true)
    val googleLoggedIn: StateFlow<Boolean> = _googleLoggedIn.asStateFlow()
    val googleConfigured: Boolean get() = _googleClientId.value.isNotBlank() && _googleClientSecret.value.isNotBlank()

    private val _oneDriveClientId = MutableStateFlow(settingsDataStore.getOneDriveClientId())
    val oneDriveClientId: StateFlow<String> = _oneDriveClientId.asStateFlow()
    private val _oneDriveLoggedIn = MutableStateFlow(settingsDataStore.getOneDriveCredentials()?.isValid() == true)
    val oneDriveLoggedIn: StateFlow<Boolean> = _oneDriveLoggedIn.asStateFlow()
    val oneDriveConfigured: Boolean get() = _oneDriveClientId.value.isNotBlank()

    private val _deviceFlowState = MutableStateFlow<DeviceFlowState>(DeviceFlowState.Idle)
    val deviceFlowState: StateFlow<DeviceFlowState> = _deviceFlowState.asStateFlow()

    private val _remoteBackups = MutableStateFlow<List<CloudBackupMeta>>(emptyList())
    val remoteBackups: StateFlow<List<CloudBackupMeta>> = _remoteBackups.asStateFlow()
    private val _loadingRemote = MutableStateFlow(false)
    val loadingRemote: StateFlow<Boolean> = _loadingRemote.asStateFlow()

    init {
        manager.restoreConfig()
        manager.loadHistory()
    }

    fun selectProvider(providerId: String) {
        _selectedProvider.value = providerId
        _connectionState.value = ConnectionState.Idle
        _deviceFlowState.value = DeviceFlowState.Idle
        settingsDataStore.setCloudBackupProviderId(providerId)
    }

    fun updateWebDavServerUrl(url: String) { _webDavServerUrl.value = url }
    fun updateWebDavUsername(name: String) { _webDavUsername.value = name }
    fun updateWebDavPassword(pass: String) { _webDavPassword.value = pass }
    fun updateWebDavRoot(root: String) { _webDavRoot.value = root }

    fun saveWebDavConfig() {
        manager.configureWebDav(WebDavConfig(
            _webDavServerUrl.value, _webDavUsername.value,
            _webDavPassword.value, _webDavRoot.value.ifBlank { "shmtu-backup" }
        ))
        _message.value = "✓ WebDAV 配置已保存"
    }

    fun updateGoogleClientId(id: String) {
        _googleClientId.value = id
        settingsDataStore.setGoogleDriveClientId(id)
    }
    fun updateGoogleClientSecret(secret: String) {
        _googleClientSecret.value = secret
        settingsDataStore.setGoogleDriveClientSecret(secret)
    }
    fun logoutGoogle() {
        settingsDataStore.setGoogleDriveCredentials(null)
        _googleLoggedIn.value = false
        _message.value = "✓ Google Drive 已登出"
    }

    fun updateOneDriveClientId(id: String) {
        _oneDriveClientId.value = id
        settingsDataStore.setOneDriveClientId(id)
    }
    fun logoutOneDrive() {
        settingsDataStore.setOneDriveCredentials(null)
        _oneDriveLoggedIn.value = false
        _message.value = "✓ OneDrive 已登出"
    }

    fun saveCurrentProviderConfig() {
        when (_selectedProvider.value) {
            "webdav" -> saveWebDavConfig()
            "google_drive" -> {
                manager.googleDriveProvider().configure(
                    cn.edu.shmtu.terminal.android.data.cloud.GoogleDriveConfig(
                        _googleClientId.value, _googleClientSecret.value
                    ),
                    settingsDataStore.getGoogleDriveCredentials()
                )
                _message.value = "✓ Google Drive 配置已保存"
            }
            "onedrive" -> {
                manager.oneDriveProvider().configure(
                    cn.edu.shmtu.terminal.android.data.cloud.OneDriveConfig(_oneDriveClientId.value),
                    settingsDataStore.getOneDriveCredentials()
                )
                _message.value = "✓ OneDrive 配置已保存"
            }
        }
    }

    fun startDeviceFlowLogin() {
        val providerId = _selectedProvider.value
        if (providerId != "google_drive" && providerId != "onedrive") return
        saveCurrentProviderConfig()
        viewModelScope.launch {
            _deviceFlowState.value = DeviceFlowState.Loading
            try {
                val info: DeviceFlowDisplayInfo
                val creds: cn.edu.shmtu.terminal.android.data.cloud.oauth.OAuthCredentials
                when (providerId) {
                    "google_drive" -> {
                        val p = manager.googleDriveProvider()
                        info = p.startDeviceFlow().getOrThrow()
                        _deviceFlowState.value = DeviceFlowState.WaitingForAuth(info)
                        creds = p.completeDeviceFlowAsync().getOrThrow()
                        settingsDataStore.setGoogleDriveCredentials(creds)
                        _googleLoggedIn.value = true
                    }
                    else -> {
                        val p = manager.oneDriveProvider()
                        info = p.startDeviceFlow().getOrThrow()
                        _deviceFlowState.value = DeviceFlowState.WaitingForAuth(info)
                        creds = p.completeDeviceFlowAsync().getOrThrow()
                        settingsDataStore.setOneDriveCredentials(creds)
                        _oneDriveLoggedIn.value = true
                    }
                }
                _deviceFlowState.value = DeviceFlowState.Success
                _message.value = "✓ 登录成功"
            } catch (e: Exception) {
                _deviceFlowState.value = DeviceFlowState.Failed(e.message ?: "登录失败")
                _message.value = "✗ 登录失败：${e.message}"
            }
        }
    }

    fun cancelDeviceFlow() {
        _deviceFlowState.value = DeviceFlowState.Idle
    }

    fun testConnection() {
        val providerId = _selectedProvider.value
        if (providerId == "webdav") {
            manager.configureWebDav(WebDavConfig(
                _webDavServerUrl.value, _webDavUsername.value,
                _webDavPassword.value, _webDavRoot.value.ifBlank { "shmtu-backup" }
            ))
        } else if (providerId == "google_drive") {
            manager.googleDriveProvider().configure(
                cn.edu.shmtu.terminal.android.data.cloud.GoogleDriveConfig(
                    _googleClientId.value, _googleClientSecret.value
                ),
                settingsDataStore.getGoogleDriveCredentials()
            )
        } else if (providerId == "onedrive") {
            manager.oneDriveProvider().configure(
                cn.edu.shmtu.terminal.android.data.cloud.OneDriveConfig(_oneDriveClientId.value),
                settingsDataStore.getOneDriveCredentials()
            )
        }
        viewModelScope.launch {
            _connectionState.value = ConnectionState.Testing
            val connected = manager.testConnection(providerId)
            if (!connected) {
                _connectionState.value = ConnectionState.Failed("连接失败，请检查配置")
                return@launch
            }
            val writeResult = manager.testWriteRead(providerId)
            _connectionState.value = if (writeResult.isSuccess) {
                ConnectionState.Connected("✓ 连接成功，读写验证通过")
            } else {
                ConnectionState.Failed("连接成功但读写验证失败：${writeResult.exceptionOrNull()?.message}")
            }
        }
    }

    fun backupNow(password: String?) {
        val providerId = _selectedProvider.value
        if (providerId == "webdav") {
            manager.configureWebDav(WebDavConfig(
                _webDavServerUrl.value, _webDavUsername.value,
                _webDavPassword.value, _webDavRoot.value.ifBlank { "shmtu-backup" }
            ))
        }
        viewModelScope.launch {
            val result = manager.backupNow(providerId, password)
            _message.value = if (result.isSuccess) "✓ 备份完成" else "✗ ${result.exceptionOrNull()?.message}"
            refreshRemoteBackups()
        }
    }

    fun restoreFromMeta(meta: CloudBackupMeta, password: String?) {
        viewModelScope.launch {
            val result = manager.restoreBackup(_selectedProvider.value, meta, password)
            _message.value = if (result.isSuccess) {
                val r = result.getOrNull()!!
                "✓ 恢复完成：${r.identities}身份/${r.accounts}账号/${r.bills}账单"
            } else "✗ ${result.exceptionOrNull()?.message}"
        }
    }

    fun refreshRemoteBackups() {
        val providerId = _selectedProvider.value
        if (providerId == "webdav") {
            manager.configureWebDav(WebDavConfig(
                _webDavServerUrl.value, _webDavUsername.value,
                _webDavPassword.value, _webDavRoot.value.ifBlank { "shmtu-backup" }
            ))
        }
        viewModelScope.launch {
            _loadingRemote.value = true
            val result = manager.listRemoteBackups(providerId)
            _remoteBackups.value = result.getOrElse { emptyList() }
            _loadingRemote.value = false
            if (result.isFailure) {
                _message.value = "✗ 获取远程列表失败：${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun deleteRemoteBackup(remotePath: String) {
        viewModelScope.launch {
            val result = manager.deleteRemoteBackup(_selectedProvider.value, remotePath)
            _message.value = if (result.isSuccess && result.getOrNull() == true) "✓ 已删除" else "✗ 删除失败"
            refreshRemoteBackups()
        }
    }

    fun setAutoEnabled(enabled: Boolean) {
        settingsDataStore.setCloudBackupAutoEnabled(enabled)
        if (enabled) {
            val interval = settingsDataStore.getCloudBackupAutoIntervalMinutes().toLong()
            CloudBackupWorker.schedule(application, interval)
            _message.value = "已开启自动备份，间隔 ${formatInterval(interval)}"
        } else {
            CloudBackupWorker.cancel(application)
            _message.value = "已关闭自动备份"
        }
    }

    fun setAutoInterval(minutes: Int) {
        settingsDataStore.setCloudBackupAutoIntervalMinutes(minutes)
        if (settingsDataStore.getCloudBackupAutoEnabledValue()) {
            CloudBackupWorker.schedule(application, minutes.toLong())
        }
    }

    fun setAutoPassword(password: String) {
        settingsDataStore.setCloudBackupAutoPassword(password)
    }

    fun setMaxKeep(count: Int) {
        settingsDataStore.setCloudBackupMaxKeep(count)
    }

    fun clearMessage() { _message.value = null }

    private fun formatInterval(minutes: Long): String = when {
        minutes < 60 -> "${minutes}分钟"
        minutes % 60 == 0L -> "${minutes / 60}小时"
        else -> "${minutes / 60}小时${minutes % 60}分钟"
    }
}

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Testing : ConnectionState()
    data class Connected(val message: String) : ConnectionState()
    data class Failed(val message: String) : ConnectionState()
}

sealed class DeviceFlowState {
    object Idle : DeviceFlowState()
    object Loading : DeviceFlowState()
    data class WaitingForAuth(val info: DeviceFlowDisplayInfo) : DeviceFlowState()
    object Success : DeviceFlowState()
    data class Failed(val message: String) : DeviceFlowState()
}
