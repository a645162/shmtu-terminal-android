package cn.edu.shmtu.terminal.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.data.remote.WechatAuthAdapter
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DebugSettingsUiState(
    val isClearingCookies: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val secureStorage: SecureStorage,
    private val epayAdapter: EpayAdapter,
    private val wechatAuthAdapter: WechatAuthAdapter
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSettingsUiState())
    val uiState: StateFlow<DebugSettingsUiState> = _uiState.asStateFlow()

    fun clearAllCookies() {
        if (_uiState.value.isClearingCookies) return
        _uiState.value = _uiState.value.copy(isClearingCookies = true, message = null)

        viewModelScope.launch {
            runCatching {
                val accounts = accountRepository.getAllAccounts()
                accounts.forEach { account ->
                    secureStorage.removeEpayCookie(account.id)
                    secureStorage.removeLoginUrl(account.id)
                    epayAdapter.invalidateSession(account.id)
                    wechatAuthAdapter.invalidateSession(account.id)
                    accountRepository.updateLoginStatus(account.id, "LOGGED_OUT")
                }
                accounts.size
            }.onSuccess { count ->
                _uiState.value = DebugSettingsUiState(
                    isClearingCookies = false,
                    message = if (count == 0) {
                        "没有可清理的账号"
                    } else {
                        "已清理 $count 个账号的 Cookies"
                    }
                )
            }.onFailure { error ->
                _uiState.value = DebugSettingsUiState(
                    isClearingCookies = false,
                    message = "清理失败: ${error.message}"
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
