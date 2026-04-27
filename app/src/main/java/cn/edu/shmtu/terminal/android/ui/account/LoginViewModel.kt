package cn.edu.shmtu.terminal.android.ui.account

import android.util.Log
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

    private val TAG = "LoginViewModel"
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var currentAccountId: Long = 0

    fun initialize(accountId: Long) {
        currentAccountId = accountId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d(TAG, "Initializing login for account $accountId")

            try {
                val isLoggedIn = epayAdapter.testLoginStatus(accountId)
                Log.d(TAG, "testLoginStatus result: $isLoggedIn")
                if (isLoggedIn) {
                    _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                    return@launch
                }

                val epayAuth = epayAdapter.getEpayAuth(accountId)
                val loginUrl = epayAuth.getLoginUrl()
                val loginCookie = epayAuth.getLoginCookie()
                val epayCookie = epayAuth.getEpayCookie()
                Log.d(TAG, "Got loginUrl: $loginUrl, loginCookie: ${loginCookie.take(20)}, epayCookie: ${epayCookie.take(20)}")

                if (loginUrl.isBlank()) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "无法获取登录页面，请检查网络")
                    return@launch
                }

                val captchaResult = casAuthAdapter.getCaptcha(loginCookie)
                if (captchaResult == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "无法获取验证码图片")
                    return@launch
                }
                Log.d(TAG, "Got captcha image, cookie: ${captchaResult.cookie.take(20)}")

                val execution = casAuthAdapter.getExecution(loginUrl, loginCookie)
                Log.d(TAG, "Got execution: $execution")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    captchaImage = captchaResult.imageData,
                    loginUrl = loginUrl,
                    execution = execution
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "初始化失败: ${e.message}"
                )
            }
        }
    }

    fun submitCaptcha(captchaCode: String) {
        if (captchaCode.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入验证码")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d(TAG, "Submitting captcha: $captchaCode")

            try {
                val account = accountRepository.getAccountById(currentAccountId)
                val password = secureStorage.getPassword(currentAccountId)
                if (account == null || password == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "账号信息不完整，请重新添加账号"
                    )
                    return@launch
                }

                val success = epayAdapter.loginWithCaptcha(
                    currentAccountId,
                    account.userId,
                    password,
                    captchaCode
                )

                if (success) {
                    Log.d(TAG, "Login successful!")
                    _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                } else {
                    Log.d(TAG, "Login failed - wrong captcha or session expired")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "登录失败，验证码可能错误或会话已过期，请重试"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during login", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "登录异常: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
