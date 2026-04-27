package cn.edu.shmtu.terminal.android.ui.account

import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.terminal.android.data.local.datastore.SecureStorage
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.data.remote.CasAuthAdapter
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val execution: String = "",
    val error: String? = null,
    val loginSuccess: Boolean = false,
    val isRecognizing: Boolean = false,
    val recognizedText: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val secureStorage: SecureStorage,
    private val casAuthAdapter: CasAuthAdapter,
    private val epayAdapter: EpayAdapter,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val shmtuNcnn = SHMTU_NCNN()

    private val TAG = "LoginViewModel"
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var currentAccountId: Long = 0

    fun initialize(accountId: Long) {
        currentAccountId = accountId
        viewModelScope.launch {
            _uiState.value = LoginUiState(isLoading = true)
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

                // Store captcha session cookie back to EpayAuth for later login
                epayAuth.setLoginCookie(captchaResult.cookie)

                val execution = casAuthAdapter.getExecution(loginUrl, captchaResult.cookie)
                Log.d(TAG, "Got execution: $execution")

                // Store execution back to EpayAuth for later login
                epayAuth.setExecution(execution)

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
                    // Clear stale execution and captcha - CAS execution is one-time use
                    epayAdapter.getEpayAuth(currentAccountId).setExecution("")
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
        val status = shmtuNcnn.modelStatus
        if (status == SHMTU_NCNN.ModelStatus.NOT_LOADED) {
            Log.w(TAG, "Local OCR model not loaded")
            return@withContext null
        }
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return@withContext null
        val resultObj = shmtuNcnn.predict_validate_code(bitmap)
        if (resultObj == null || resultObj.size < 2) {
            Log.w(TAG, "Local OCR returned null or incomplete result")
            return@withContext null
        }
        val text = resultObj[1] as? String
        Log.d(TAG, "Local OCR result: $text")
        text
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
