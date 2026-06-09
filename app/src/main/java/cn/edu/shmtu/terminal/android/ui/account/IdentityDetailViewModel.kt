package cn.edu.shmtu.terminal.android.ui.account

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.parser.PersonAccountParser
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.terminal.android.data.remote.PersonAccountCaptchaRequiredException
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.PersonAccount
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.usecase.account.DeleteAccountUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.bill.CaptchaRequiredException
import cn.edu.shmtu.terminal.android.domain.usecase.bill.Purpose
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncAccountBillsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentityDetailUiState(
    val isSyncing: Boolean = false,
    val isLoggingInForSave: Boolean = false,
    val showCaptchaDialog: Boolean = false,
    val captchaImage: ByteArray? = null,
    val captchaAccount: Account? = null,
    val captchaExecution: String? = null,
    val pendingCaptcha: CaptchaRequiredException? = null,
    val syncMessage: String? = null,
    val syncProgress: cn.edu.shmtu.terminal.android.domain.model.SyncProgress? = null,
    /** 正在拉取个人账户详情的账号 id (用于 UI 显示 Loading) */
    val refreshingAccountIds: Set<Long> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class IdentityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val identityRepository: IdentityRepository,
    private val accountRepository: AccountRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val syncAccountBillsUseCase: SyncAccountBillsUseCase,
    private val epayAdapter: EpayAdapter,
) : ViewModel() {
    private val stateHandle = savedStateHandle

    private val identityId: Long = savedStateHandle.get<String>("identityId")?.toLongOrNull() ?: 0L

    val accounts: StateFlow<List<Account>> = if (identityId == 0L) {
        flowOf(emptyList<Account>()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        accountRepository.getAccountsByIdentity(identityId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val identity: StateFlow<Identity?> = if (identityId == 0L) {
        flowOf(null).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    } else {
        identityRepository.getIdentityByIdFlow(identityId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    /** 当前 identity 下所有账号的 PersonAccount 缓存 (accountId -> PersonAccount) */
    val personAccountsByAccountId: StateFlow<Map<Long, PersonAccount>> = accounts
        .flatMapLatest { list ->
            if (list.isEmpty()) flowOf(emptyMap<Long, PersonAccount>())
            else {
                // 对每个账号单独观察;合并为 Map
                val flows = list.map { acc ->
                    accountRepository.observeCachedPersonAccount(acc.id)
                        .map { pa -> acc.id to pa }
                }
                kotlinx.coroutines.flow.combine(flows) { array ->
                    array.filter { it.second != null }
                        .associate { (id, pa) -> id to pa!! }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _uiState = MutableStateFlow(IdentityDetailUiState())
    val uiState: StateFlow<IdentityDetailUiState> = _uiState.asStateFlow()

    private val _editingAccount = MutableStateFlow<Account?>(null)
    val editingAccount: StateFlow<Account?> = _editingAccount.asStateFlow()

    suspend fun getIdentity(): Identity? {
        return identityRepository.getIdentityById(identityId)
    }

    /**
     * 刷新一卡通个人账户详情
     *
     * 流程: EpayAuth.getPersonAccountHtml() → PersonAccountParser().parse() → 写 Room 缓存
     * 失败时: UI 仍可读取 Room 缓存;该函数返回 Result 给调用方用于展示 snackbar。
     */
    fun refreshPersonAccount(account: Account) {
        viewModelScope.launch {
            val current = _uiState.value.refreshingAccountIds
            _uiState.value = _uiState.value.copy(refreshingAccountIds = current + account.id)

            val htmlResult = epayAdapter.fetchPersonAccountHtml(account.id)
            val result = htmlResult.mapCatching { html ->
                PersonAccountParser().parse(html)
            }

            result.fold(
                onSuccess = { info ->
                    accountRepository.savePersonAccount(account.id, info)
                    _uiState.value = _uiState.value.copy(
                        refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                        syncMessage = "「${account.label}」个人账户详情已更新"
                    )
                },
                onFailure = { e ->
                    if (e is PersonAccountCaptchaRequiredException) {
                        _uiState.value = _uiState.value.copy(
                            refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                            showCaptchaDialog = true,
                            captchaImage = e.captchaImage,
                            captchaAccount = account,
                            captchaExecution = e.execution,
                            pendingCaptcha = CaptchaRequiredException(
                                captchaImageBase64 = android.util.Base64.encodeToString(
                                    e.captchaImage,
                                    android.util.Base64.NO_WRAP
                                ),
                                execution = e.execution,
                                accountId = account.id,
                                accountLabel = account.label,
                                syncRange = cn.edu.shmtu.cas.sync.SyncRangePreset.Month,
                                isFullSync = false,
                                purpose = Purpose.PERSON_ACCOUNT,
                            ),
                            syncMessage = "请输入验证码以刷新「${account.label}」的一卡通详情"
                        )
                        return@fold
                    }
                    Log.w("IdentityDetailVM", "refreshPersonAccount failed for ${account.id}: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                        syncMessage = "拉取「${account.label}」详情失败: ${e.message ?: "未知错误"}"
                    )
                }
            )
        }
    }

    fun refreshAccountBills(account: Account) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncMessage = null, syncProgress = null)
            val result = try {
                syncAccountBillsUseCase(account) { progress ->
                    _uiState.value = _uiState.value.copy(syncProgress = progress)
                }
            } catch (e: CaptchaRequiredException) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    showCaptchaDialog = true,
                    captchaImage = android.util.Base64.decode(e.captchaImageBase64, android.util.Base64.DEFAULT),
                    captchaAccount = account,
                    captchaExecution = e.execution,
                    pendingCaptcha = e,
                    syncProgress = cn.edu.shmtu.terminal.android.domain.model.SyncProgress(
                        status = cn.edu.shmtu.terminal.android.domain.model.SyncStatus.GettingCaptcha,
                        accountLabel = account.label,
                    ),
                )
                return@launch
            }

            if (result.success) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncProgress = null,
                    syncMessage = "同步成功，新增 ${result.newCount} 条记录"
                )
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
        val pendingCaptcha = _uiState.value.pendingCaptcha ?: return

        viewModelScope.launch {
            when (pendingCaptcha.purpose) {
                Purpose.SYNC -> {
                    _uiState.value = _uiState.value.copy(
                        isSyncing = true,
                        showCaptchaDialog = false,
                        syncMessage = null,
                    )

                    try {
                        val result = syncAccountBillsUseCase.syncWithCaptcha(
                            account = account,
                            captchaCode = captchaCode,
                            execution = pendingCaptcha.execution,
                            syncRange = pendingCaptcha.syncRange,
                            fullSync = pendingCaptcha.isFullSync,
                        ) { progress ->
                            _uiState.value = _uiState.value.copy(syncProgress = progress)
                        }

                        _uiState.value = _uiState.value.copy(
                            isSyncing = false,
                            captchaImage = null,
                            captchaAccount = null,
                            captchaExecution = null,
                            pendingCaptcha = null,
                            syncProgress = null,
                            syncMessage = if (result.success)
                                "同步成功，新增 ${result.newCount} 条记录"
                            else
                                "同步失败: ${result.errorMessage}"
                        )
                    } catch (e: CaptchaRequiredException) {
                        _uiState.value = _uiState.value.copy(
                            isSyncing = false,
                            showCaptchaDialog = true,
                            captchaImage = android.util.Base64.decode(e.captchaImageBase64, android.util.Base64.DEFAULT),
                            captchaAccount = account,
                            captchaExecution = e.execution,
                            pendingCaptcha = e,
                            syncMessage = "验证码错误，请重试"
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            isSyncing = false,
                            syncMessage = "登录异常: ${e.message}"
                        )
                    }
                }
                Purpose.LOGIN_SAVE -> submitCaptchaForLoginSave(account, pendingCaptcha, captchaCode)
                Purpose.PERSON_ACCOUNT -> submitCaptchaForPersonAccount(account, pendingCaptcha, captchaCode)
            }
        }
    }

    fun updateAccount(accountId: Long, label: String, userId: String, password: String) {
        viewModelScope.launch {
            accountRepository.updateAccount(accountId, label, userId)
            if (password.isNotBlank()) {
                accountRepository.savePassword(accountId, password)
            }
            _editingAccount.value = null
        }
    }

    fun getStoredPassword(accountId: Long): String {
        return accountRepository.getPassword(accountId).orEmpty()
    }

    fun loginAndSave(accountId: Long, label: String, userId: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoggingInForSave = true,
                syncMessage = null,
                showCaptchaDialog = false,
                captchaImage = null,
                captchaAccount = null,
                captchaExecution = null,
                pendingCaptcha = null,
            )

            accountRepository.updateAccount(accountId, label, userId)
            accountRepository.savePassword(accountId, password)

            val updatedAccount = accountRepository.getAccountById(accountId)
            if (updatedAccount == null) {
                _uiState.value = _uiState.value.copy(
                    isLoggingInForSave = false,
                    syncMessage = "账号不存在"
                )
                return@launch
            }

            val challenge = epayAdapter.prepareChallenge(accountId).getOrNull()
            if (challenge != null) {
                _uiState.value = _uiState.value.copy(
                    isLoggingInForSave = false,
                    showCaptchaDialog = true,
                    captchaImage = challenge.captchaImage,
                    captchaAccount = updatedAccount,
                    captchaExecution = challenge.execution,
                    pendingCaptcha = CaptchaRequiredException(
                        captchaImageBase64 = android.util.Base64.encodeToString(challenge.captchaImage, android.util.Base64.NO_WRAP),
                        execution = challenge.execution,
                        accountId = updatedAccount.id,
                        accountLabel = updatedAccount.label,
                        syncRange = cn.edu.shmtu.cas.sync.SyncRangePreset.Month,
                        isFullSync = false,
                        purpose = Purpose.LOGIN_SAVE,
                    ),
                    syncMessage = "请输入验证码以完成登录并保存"
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoggingInForSave = false,
                syncMessage = "获取验证码失败"
            )
        }
    }

    fun dismissCaptchaDialog() {
        _uiState.value = _uiState.value.copy(
            showCaptchaDialog = false,
            captchaImage = null,
            captchaAccount = null,
            captchaExecution = null,
            pendingCaptcha = null,
            isSyncing = false,
            isLoggingInForSave = false,
        )
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }

    fun consumePendingSnackbarMessage() {
        val message = stateHandle.get<String>("account_add_message") ?: return
        stateHandle.remove<String>("account_add_message")
        _uiState.value = _uiState.value.copy(syncMessage = message)
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            deleteAccountUseCase(accountId, identityId)
        }
    }

    fun startEditAccount(account: Account) {
        _editingAccount.value = account
    }

    fun cancelEditAccount() {
        _editingAccount.value = null
    }

    private suspend fun submitCaptchaForLoginSave(
        account: Account,
        pendingCaptcha: CaptchaRequiredException,
        captchaCode: String,
    ) {
        _uiState.value = _uiState.value.copy(
            isLoggingInForSave = true,
            showCaptchaDialog = false,
            syncMessage = null,
        )

        try {
            val password = accountRepository.getPassword(account.id)
            if (password.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoggingInForSave = false,
                    syncMessage = "未找到密码，请重新填写后再试"
                )
                return
            }

            val result = epayAdapter.submitLogin(
                accountId = account.id,
                username = account.userId,
                password = password,
                captchaCode = captchaCode,
                execution = pendingCaptcha.execution,
            )
            when (result.getOrNull()) {
                is LoginSubmitResult.Success -> {
                    accountRepository.updateLoginStatus(account.id, "LOGGED_IN")
                    _uiState.value = _uiState.value.copy(
                        isLoggingInForSave = false,
                        captchaImage = null,
                        captchaAccount = null,
                        captchaExecution = null,
                        pendingCaptcha = null,
                        syncMessage = "登录成功，凭据和 Cookies 已保存"
                    )
                }
                is LoginSubmitResult.ValidateCodeError -> {
                    val challenge = epayAdapter.prepareChallenge(account.id).getOrNull()
                    _uiState.value = _uiState.value.copy(
                        isLoggingInForSave = false,
                        showCaptchaDialog = challenge != null,
                        captchaImage = challenge?.captchaImage,
                        captchaAccount = account,
                        captchaExecution = challenge?.execution,
                        pendingCaptcha = challenge?.let {
                            CaptchaRequiredException(
                                captchaImageBase64 = android.util.Base64.encodeToString(it.captchaImage, android.util.Base64.NO_WRAP),
                                execution = it.execution,
                                accountId = account.id,
                                accountLabel = account.label,
                                syncRange = cn.edu.shmtu.cas.sync.SyncRangePreset.Month,
                                isFullSync = false,
                                purpose = Purpose.LOGIN_SAVE,
                            )
                        },
                        syncMessage = "验证码错误，请重试"
                    )
                }
                is LoginSubmitResult.PasswordError -> {
                    _uiState.value = _uiState.value.copy(
                        isLoggingInForSave = false,
                        syncMessage = "密码错误"
                    )
                }
                is LoginSubmitResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoggingInForSave = false,
                        syncMessage = "登录失败: ${(result.getOrNull() as LoginSubmitResult.Failure).message}"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoggingInForSave = false,
                        syncMessage = "登录失败"
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoggingInForSave = false,
                syncMessage = "登录异常: ${e.message}"
            )
        }
    }

    private suspend fun submitCaptchaForPersonAccount(
        account: Account,
        pendingCaptcha: CaptchaRequiredException,
        captchaCode: String,
    ) {
        _uiState.value = _uiState.value.copy(
            refreshingAccountIds = _uiState.value.refreshingAccountIds + account.id,
            showCaptchaDialog = false,
            syncMessage = null,
        )

        try {
            val password = accountRepository.getPassword(account.id)
            if (password.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(
                    refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                    syncMessage = "未找到密码，请重新填写后再试"
                )
                return
            }

            val loginResult = epayAdapter.submitLogin(
                accountId = account.id,
                username = account.userId,
                password = password,
                captchaCode = captchaCode,
                execution = pendingCaptcha.execution,
            )

            when (val result = loginResult.getOrNull()) {
                is LoginSubmitResult.Success -> {
                    val htmlResult = epayAdapter.fetchPersonAccountHtml(account.id)
                    val parseResult = htmlResult.mapCatching { html ->
                        PersonAccountParser().parse(html)
                    }
                    parseResult.fold(
                        onSuccess = { info ->
                            accountRepository.savePersonAccount(account.id, info)
                            _uiState.value = _uiState.value.copy(
                                refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                                captchaImage = null,
                                captchaAccount = null,
                                captchaExecution = null,
                                pendingCaptcha = null,
                                syncMessage = "「${account.label}」个人账户详情已更新"
                            )
                        },
                        onFailure = { error ->
                            _uiState.value = _uiState.value.copy(
                                refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                                captchaImage = null,
                                captchaAccount = null,
                                captchaExecution = null,
                                pendingCaptcha = null,
                                syncMessage = "刷新失败: ${error.message ?: "未知错误"}"
                            )
                        }
                    )
                }

                is LoginSubmitResult.ValidateCodeError -> {
                    val challenge = epayAdapter.prepareChallenge(account.id).getOrNull()
                    _uiState.value = _uiState.value.copy(
                        refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                        showCaptchaDialog = challenge != null,
                        captchaImage = challenge?.captchaImage,
                        captchaAccount = account,
                        captchaExecution = challenge?.execution,
                        pendingCaptcha = challenge?.let {
                            CaptchaRequiredException(
                                captchaImageBase64 = android.util.Base64.encodeToString(
                                    it.captchaImage,
                                    android.util.Base64.NO_WRAP
                                ),
                                execution = it.execution,
                                accountId = account.id,
                                accountLabel = account.label,
                                syncRange = cn.edu.shmtu.cas.sync.SyncRangePreset.Month,
                                isFullSync = false,
                                purpose = Purpose.PERSON_ACCOUNT,
                            )
                        },
                        syncMessage = "验证码错误，请重试"
                    )
                }

                is LoginSubmitResult.PasswordError -> {
                    _uiState.value = _uiState.value.copy(
                        refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                        syncMessage = "密码错误"
                    )
                }

                is LoginSubmitResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                        syncMessage = "登录失败: ${result.message}"
                    )
                }

                else -> {
                    _uiState.value = _uiState.value.copy(
                        refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                        syncMessage = "登录失败"
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                refreshingAccountIds = _uiState.value.refreshingAccountIds - account.id,
                syncMessage = "登录异常: ${e.message}"
            )
        }
    }
}
