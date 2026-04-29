package cn.edu.shmtu.terminal.android.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.remote.CasAuthAdapter
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
import cn.edu.shmtu.terminal.android.domain.usecase.account.DeleteAccountUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncAccountBillsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentityDetailUiState(
    val isSyncing: Boolean = false,
    val showCaptchaDialog: Boolean = false,
    val captchaImage: ByteArray? = null,
    val captchaAccount: Account? = null,
    val syncMessage: String? = null
)

@HiltViewModel
class IdentityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val identityRepository: IdentityRepository,
    private val accountRepository: AccountRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val syncAccountBillsUseCase: SyncAccountBillsUseCase,
    private val epayAdapter: EpayAdapter,
    private val casAuthAdapter: CasAuthAdapter,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val identityId: Long = savedStateHandle.get<String>("identityId")?.toLongOrNull() ?: 0L

    val accounts: StateFlow<List<Account>> = if (identityId == 0L) {
        flowOf(emptyList<Account>()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        accountRepository.getAccountsByIdentity(identityId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    private val _uiState = MutableStateFlow(IdentityDetailUiState())
    val uiState: StateFlow<IdentityDetailUiState> = _uiState.asStateFlow()

    private val _editingAccount = MutableStateFlow<Account?>(null)
    val editingAccount: StateFlow<Account?> = _editingAccount.asStateFlow()

    suspend fun getIdentity(): Identity? {
        return identityRepository.getIdentityById(identityId)
    }

    fun refreshAccountBills(account: Account) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null)

            val result = syncAccountBillsUseCase(account)

            if (result.success) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "同步成功，新增 ${result.newCount} 条记录"
                )
                return@launch
            }

            if (result.errorMessage == "Session expired, need re-login") {
                try {
                    val epayAuth = epayAdapter.getEpayAuth(account.id)
                    epayAuth.testLoginStatus()

                    val loginUrl = epayAuth.getLoginUrl()
                    val loginCookie = epayAuth.getLoginCookie()

                    if (loginUrl.isBlank()) {
                        _uiState.value = _uiState.value.copy(
                            isSyncing = false,
                            syncMessage = "无法获取登录页面"
                        )
                        return@launch
                    }

                    val captchaResult = casAuthAdapter.getCaptcha(loginCookie)
                    if (captchaResult == null) {
                        _uiState.value = _uiState.value.copy(
                            isSyncing = false,
                            syncMessage = "无法获取验证码"
                        )
                        return@launch
                    }

                    epayAuth.setLoginCookie(captchaResult.cookie)

                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        showCaptchaDialog = true,
                        captchaImage = captchaResult.imageData,
                        captchaAccount = account
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncMessage = "获取验证码失败: ${e.message}"
                    )
                }
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isSyncing = false,
                syncMessage = "同步失败: ${result.errorMessage}"
            )
        }
    }

    fun submitCaptcha(captchaCode: String) {
        val account = _uiState.value.captchaAccount ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSyncing = true,
                showCaptchaDialog = false
            )

            try {
                val password = secureStorage.getPassword(account.id)
                if (password == null) {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncMessage = "未找到密码，请重新添加账号"
                    )
                    return@launch
                }

                val success = epayAdapter.loginWithCaptcha(account.id, account.userId, password, captchaCode)

                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        syncMessage = "登录失败，验证码错误或已过期"
                    )
                    return@launch
                }

                val result = syncAccountBillsUseCase(account)

                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = if (result.success)
                        "同步成功，新增 ${result.newCount} 条记录"
                    else
                        "同步失败: ${result.errorMessage}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncMessage = "登录异常: ${e.message}"
                )
            }
        }
    }

    fun dismissCaptchaDialog() {
        _uiState.value = _uiState.value.copy(showCaptchaDialog = false)
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            deleteAccountUseCase(accountId, identityId)
        }
    }

    fun startEditAccount(account: Account) {
        _editingAccount.value = account
    }

    fun updateAccount(accountId: Long, label: String, userId: String) {
        viewModelScope.launch {
            accountRepository.updateAccount(accountId, label, userId)
            _editingAccount.value = null
        }
    }

    fun cancelEditAccount() {
        _editingAccount.value = null
    }
}
