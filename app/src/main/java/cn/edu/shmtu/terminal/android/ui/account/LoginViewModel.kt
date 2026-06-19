package cn.edu.shmtu.terminal.android.ui.account

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.CaptchaOcrHelper
import cn.edu.shmtu.cas.ocr.NcnnModelLoader
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val captchaImage: ByteArray? = null,
    val loginUrl: String = "",
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val isRecognizing: Boolean = false,
    val recognizedText: String? = null,
    /** 上一次登录因验证码错误失败，本次是重试 */
    val isCaptchaRetry: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val secureStorage: SecureStorage,
    private val epayAdapter: EpayAdapter,
    private val settingsDataStore: SettingsDataStore,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val shmtuNcnn = SHMTU_NCNN()

    private val TAG = "LoginViewModel"
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var currentAccountId: Long = 0

    /**
     * 初始化登录流程：
     * 1. 探测登录状态
     * 2. 如果需要登录，获取验证码
     */
    fun initialize(accountId: Long) {
        currentAccountId = accountId
        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
            Log.d(TAG, "Initializing login for account $accountId")

            try {
                // 1. 探测登录状态
                val probeResult = epayAdapter.probeLogin(accountId)
                
                when {
                    probeResult.isFailure -> {
                        Log.e(TAG, "Probe login failed: ${probeResult.exceptionOrNull()?.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "探测登录状态失败: ${probeResult.exceptionOrNull()?.message}"
                        )
                        return@launch
                    }
                    
                    probeResult.getOrNull() is SessionProbe.AlreadyLoggedIn -> {
                        Log.d(TAG, "Already logged in")
                        accountRepository.updateLoginStatus(accountId, "LOGGED_IN")
                        _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                        return@launch
                    }
                    
                    probeResult.getOrNull() is SessionProbe.NeedLogin -> {
                        val needLogin = probeResult.getOrNull() as SessionProbe.NeedLogin
                        Log.d(TAG, "Need login, loginUrl=${needLogin.loginUrl}")
                        _uiState.value = _uiState.value.copy(loginUrl = needLogin.loginUrl)
                    }
                }

                // 2. 获取验证码（execution + captcha image）
                val challengeResult = epayAdapter.prepareChallenge(accountId)
                
                if (challengeResult.isFailure) {
                    Log.e(TAG, "Prepare challenge failed: ${challengeResult.exceptionOrNull()?.message}")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取验证码失败: ${challengeResult.exceptionOrNull()?.message}"
                    )
                    return@launch
                }

                val challenge = challengeResult.getOrNull()
                if (challenge == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取验证码失败"
                    )
                    return@launch
                }

                Log.d(TAG, "Got captcha image, size=${challenge.captchaImage.size}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    captchaImage = challenge.captchaImage
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

    /**
     * 提交验证码完成登录
     */
    fun submitCaptcha(captchaCode: String) {
        if (captchaCode.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入验证码")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, isCaptchaRetry = false)
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

                // 重新获取 challenge（execution 是一次性的）
                val challengeResult = epayAdapter.prepareChallenge(currentAccountId)
                if (challengeResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取验证码失败，请重试"
                    )
                    return@launch
                }

                val challenge = challengeResult.getOrNull()
                if (challenge == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取验证码失败"
                    )
                    return@launch
                }

                // 提交登录
                val submitResult = epayAdapter.submitLogin(
                    currentAccountId,
                    account.userId,
                    password,
                    captchaCode,
                    challenge.execution,
                )

                when {
                    submitResult.isFailure -> {
                        Log.e(TAG, "Submit login failed: ${submitResult.exceptionOrNull()?.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "登录异常: ${submitResult.exceptionOrNull()?.message}"
                        )
                    }
                    
                    submitResult.getOrNull() is LoginSubmitResult.Success -> {
                        Log.d(TAG, "Login successful!")
                        accountRepository.updateLoginStatus(currentAccountId, "LOGGED_IN")
                        _uiState.value = _uiState.value.copy(isLoading = false, loginSuccess = true)
                    }
                    
                    submitResult.getOrNull() is LoginSubmitResult.ValidateCodeError -> {
                        Log.d(TAG, "Login failed - wrong captcha, retrying...")
                        // 自动重新获取验证码，不结束登录流程
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            error = "验证码错误，已刷新验证码，请重新输入"
                        )
                        retryFetchCaptcha()
                    }
                    
                    submitResult.getOrNull() is LoginSubmitResult.PasswordError -> {
                        Log.d(TAG, "Login failed - wrong password")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "密码错误"
                        )
                    }
                    
                    else -> {
                        val msg = (submitResult.getOrNull() as? LoginSubmitResult.Failure)?.message ?: "未知错误"
                        Log.d(TAG, "Login failed: $msg")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "登录失败: $msg"
                        )
                    }
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

    /**
     * 验证码错误后自动重新获取验证码，标记为重试状态
     */
    private fun retryFetchCaptcha() {
        viewModelScope.launch {
            try {
                val challengeResult = epayAdapter.prepareChallenge(currentAccountId)
                if (challengeResult.isFailure || challengeResult.getOrNull() == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "刷新验证码失败，请手动重试",
                        isCaptchaRetry = true
                    )
                    return@launch
                }
                val challenge = challengeResult.getOrNull()!!
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    captchaImage = challenge.captchaImage,
                    error = null,
                    isCaptchaRetry = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying captcha fetch", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "刷新验证码失败: ${e.message}",
                    isCaptchaRetry = true
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearRecognizedText() {
        _uiState.value = _uiState.value.copy(recognizedText = null)
    }

    fun recognizeCaptcha() {
        val imageData = _uiState.value.captchaImage ?: return
        if (_uiState.value.isRecognizing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecognizing = true, error = null, recognizedText = null)

            try {
                val useLocalOcr = settingsDataStore.useLocalOcr.first()
                val result = if (useLocalOcr) {
                    recognizeLocal(imageData)
                } else {
                    recognizeRemote(imageData)
                }

                if (result != null) {
                    _uiState.value = _uiState.value.copy(
                        isRecognizing = false,
                        recognizedText = result
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isRecognizing = false,
                        error = if (useLocalOcr) "识别失败，请确认模型已加载" else "远程识别失败"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR recognition failed", e)
                _uiState.value = _uiState.value.copy(
                    isRecognizing = false,
                    error = "识别异常: ${e.message}"
                )
            }
        }
    }

    private suspend fun recognizeLocal(imageData: ByteArray): String? = withContext(Dispatchers.Default) {
        if (!NcnnModelLoader.ensureLoaded(shmtuNcnn, context)) {
            Log.w(TAG, "Local OCR model not loaded (no downloaded/built-in model found)")
            return@withContext null
        }
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return@withContext null
        val resultObj = shmtuNcnn.predict_validate_code(bitmap)
        if (resultObj == null || resultObj.size < 4) {
            Log.w(TAG, "Local OCR returned null or incomplete result")
            return@withContext null
        }
        val expr = CaptchaOcrHelper.buildExprString(resultObj)
        Log.d(TAG, "Local OCR result: $expr")
        expr
    }

    private suspend fun recognizeRemote(imageData: ByteArray): String? = withContext(Dispatchers.IO) {
        val serverUrl = settingsDataStore.ocrServerUrl.first()
        val parts = serverUrl.split(":")
        if (parts.size != 2) {
            Log.w(TAG, "Invalid OCR server URL: $serverUrl")
            return@withContext null
        }
        val host = parts[0]
        val port = parts[1].toIntOrNull()
        if (port == null || !Captcha.validatePort(port.toString())) {
            Log.w(TAG, "Invalid OCR server port: ${parts[1]}")
            return@withContext null
        }
        Log.d(TAG, "Remote OCR: host=$host, port=$port, imageSize=${imageData.size}")
        val result = Captcha.ocrByRemoteTcpServerAutoRetry(host, port, imageData)
        Log.d(TAG, "Remote OCR result: '$result'")
        if (result.isBlank()) null else result
    }
}
