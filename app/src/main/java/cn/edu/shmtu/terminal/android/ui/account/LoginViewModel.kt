package cn.edu.shmtu.terminal.android.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.remote.CasAuthAdapter
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val captchaImage: ByteArray? = null,
    val captchaCookie: String = "",
    val loginUrl: String = "",
    val execution: String = "",
    val error: String? = null,
    val loginSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val secureStorage: SecureStorage,
    private val casAuthAdapter: CasAuthAdapter,
    private val epayAdapter: EpayAdapter
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun initialize(accountId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val isLoggedIn = epayAdapter.testLoginStatus(accountId)
            if (isLoggedIn) {
                _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                return@launch
            }

            val loginUrl = casAuthAdapter.getLoginUrl(accountId, epayAdapter)
            if (loginUrl.isBlank()) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "无法获取登录页面")
                return@launch
            }

            val captchaResult = casAuthAdapter.getCaptcha()
            if (captchaResult == null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "无法获取验证码")
                return@launch
            }

            val execution = casAuthAdapter.getExecution(loginUrl, "")

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                captchaImage = captchaResult.imageData,
                captchaCookie = captchaResult.cookie,
                loginUrl = loginUrl,
                execution = execution
            )
        }
    }

    fun submitCaptcha(accountId: Long, captchaCode: String) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.loginUrl.isBlank() || state.execution.isBlank()) {
                _uiState.value = _uiState.value.copy(error = "登录状态已过期，请重试")
                return@launch
            }

            if (captchaCode.isBlank()) {
                _uiState.value = _uiState.value.copy(error = "请输入验证码")
                return@launch
            }

            val account = accountRepository.getAccountById(accountId)
            val password = secureStorage.getPassword(accountId)
            if (account == null || password == null) {
                _uiState.value = _uiState.value.copy(error = "账号信息不完整，请重新添加账号")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val casResult = casAuthAdapter.casLogin(
                url = state.loginUrl,
                username = account.userId,
                password = password,
                validateCode = captchaCode,
                execution = state.execution,
                cookie = state.captchaCookie
            )

            if (casResult.first != 302) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "登录失败，请检查验证码"
                )
                return@launch
            }

            val redirectResult = casAuthAdapter.casRedirect(casResult.second, casResult.third)
            if (redirectResult.first != 302) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "登录成功但无法跳转"
                )
                return@launch
            }

            val billResult = epayAdapter.fetchBillPage(accountId, 1)
            if (billResult.first == 200) {
                _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "登录状态验证失败"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
