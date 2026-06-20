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
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
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

/** 登录流程错误类型，用于 UI 差异化展示 */
enum class LoginErrorType {
    NETWORK,   // 网络连接问题（无网络、超时、DNS 失败等）
    SERVER,    // 服务器异常（返回意外响应、会话过期等）
    CAPTCHA,   // 验证码相关（获取失败、输入错误等）
    PASSWORD,  // 密码错误
    ACCOUNT,   // 账号信息问题
    OCR,       // OCR 识别失败
    UNKNOWN;   // 未知/其他错误

    /** 可恢复的错误（网络波动、服务器临时异常等），重试可能成功 */
    val isRecoverable: Boolean
        get() = this in listOf(NETWORK, SERVER, CAPTCHA, OCR)
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val captchaImage: ByteArray? = null,
    val loginUrl: String = "",
    val error: String? = null,
    val errorType: LoginErrorType? = null,
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

    /** 根据异常信息推断错误类型 */
    private fun classifyException(e: Throwable): LoginErrorType {
        val msg = e.message.orEmpty().lowercase()
        return when {
            // 网络类
            msg.contains("timeout") || msg.contains("timed out") -> LoginErrorType.NETWORK
            msg.contains("connection") && (msg.contains("refused") || msg.contains("reset")) -> LoginErrorType.NETWORK
            msg.contains("unable to resolve host") || msg.contains("dns") -> LoginErrorType.NETWORK
            msg.contains("no route to host") -> LoginErrorType.NETWORK
            msg.contains("networkunreachable") || msg.contains("network is unreachable") -> LoginErrorType.NETWORK
            msg.contains("connectexception") -> LoginErrorType.NETWORK
            msg.contains("sockettimeout") -> LoginErrorType.NETWORK
            msg.contains("eofexception") -> LoginErrorType.NETWORK
            msg.contains("sslhandshake") || msg.contains("ssl") -> LoginErrorType.NETWORK
            // 服务器类
            msg.contains("500") || msg.contains("502") || msg.contains("503") -> LoginErrorType.SERVER
            msg.contains("internal server error") -> LoginErrorType.SERVER
            msg.contains("302") || msg.contains("redirect") -> LoginErrorType.SERVER
            msg.contains("未登录") -> LoginErrorType.SERVER
            else -> LoginErrorType.UNKNOWN
        }
    }

    /** 将技术性异常信息转为用户友好的描述 */
    private fun friendlyErrorMessage(e: Throwable, context: String): String {
        val msg = e.message.orEmpty().lowercase()
        return when (classifyException(e)) {
            LoginErrorType.NETWORK -> when {
                msg.contains("timeout") || msg.contains("timed out") ->
                    "网络连接超时，请检查网络后重试"
                msg.contains("unable to resolve host") || msg.contains("dns") ->
                    "无法连接到服务器，请检查网络是否正常"
                msg.contains("connection refused") || msg.contains("connection reset") ->
                    "服务器拒绝连接，可能正在维护，请稍后重试"
                msg.contains("sslhandshake") || msg.contains("ssl") ->
                    "安全连接失败，请检查网络环境"
                else -> "网络连接失败，请检查网络设置后重试"
            }
            LoginErrorType.SERVER -> when {
                msg.contains("500") || msg.contains("502") || msg.contains("503") ->
                    "服务器暂时不可用，请稍后重试"
                msg.contains("302") || msg.contains("redirect") || msg.contains("未登录") ->
                    "登录会话已过期，请重新尝试"
                else -> "服务器异常，请稍后重试"
            }
            else -> when (context) {
                "probe" -> "检查登录状态失败，请重试"
                "challenge" -> "获取验证码失败，请重试"
                "submit" -> "登录提交失败，请重试"
                "retry_captcha" -> "刷新验证码失败，请点击刷新按钮重试"
                "ocr" -> "验证码识别失败，请尝试手动输入"
                else -> "操作失败，请重试"
            }
        }
    }
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
                        val ex = probeResult.exceptionOrNull()
                        Log.e(TAG, "Probe login failed: ${ex?.message}")
                        val type = ex?.let { classifyException(it) } ?: LoginErrorType.UNKNOWN
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = ex?.let { friendlyErrorMessage(it, "probe") } ?: "检查登录状态失败，请重试",
                            errorType = type
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
                    val ex = challengeResult.exceptionOrNull()
                    Log.e(TAG, "Prepare challenge failed: ${ex?.message}")
                    val type = ex?.let { classifyException(it) } ?: LoginErrorType.UNKNOWN
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ex?.let { friendlyErrorMessage(it, "challenge") } ?: "获取验证码失败，请重试",
                        errorType = type
                    )
                    return@launch
                }

                val challenge = challengeResult.getOrNull()
                if (challenge == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取验证码失败，请重试",
                        errorType = LoginErrorType.CAPTCHA
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
                    error = friendlyErrorMessage(e, "probe"),
                    errorType = classifyException(e)
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
            _uiState.value = _uiState.value.copy(
                isLoading = true, error = null, errorType = null, isCaptchaRetry = false
            )
            Log.d(TAG, "Submitting captcha: $captchaCode")

            try {
                val account = accountRepository.getAccountById(currentAccountId)
                val password = secureStorage.getPassword(currentAccountId)
                if (account == null || password == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "账号信息不完整，请返回重新添加账号",
                        errorType = LoginErrorType.ACCOUNT
                    )
                    return@launch
                }

                // 重新获取 challenge（execution 是一次性的）
                val challengeResult = epayAdapter.prepareChallenge(currentAccountId)
                if (challengeResult.isFailure) {
                    val ex = challengeResult.exceptionOrNull()
                    val type = ex?.let { classifyException(it) } ?: LoginErrorType.UNKNOWN
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ex?.let { friendlyErrorMessage(it, "challenge") } ?: "获取验证码失败，请重试",
                        errorType = type
                    )
                    return@launch
                }

                val challenge = challengeResult.getOrNull()
                if (challenge == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "获取验证码失败，请重试",
                        errorType = LoginErrorType.CAPTCHA
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
                        val ex = submitResult.exceptionOrNull()
                        Log.e(TAG, "Submit login failed: ${ex?.message}")
                        val type = ex?.let { classifyException(it) } ?: LoginErrorType.UNKNOWN
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = ex?.let { friendlyErrorMessage(it, "submit") } ?: "登录提交失败，请重试",
                            errorType = type
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
                            error = "验证码输入错误，已自动刷新验证码，请重新输入",
                            errorType = LoginErrorType.CAPTCHA
                        )
                        retryFetchCaptcha()
                    }

                    submitResult.getOrNull() is LoginSubmitResult.PasswordError -> {
                        Log.d(TAG, "Login failed - wrong password")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "用户名或密码错误，请返回检查账号信息",
                            errorType = LoginErrorType.PASSWORD
                        )
                    }

                    else -> {
                        val msg = (submitResult.getOrNull() as? LoginSubmitResult.Failure)?.message ?: "未知错误"
                        Log.d(TAG, "Login failed: $msg")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "登录失败，请稍后重试",
                            errorType = LoginErrorType.SERVER
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during login", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = friendlyErrorMessage(e, "submit"),
                    errorType = classifyException(e)
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
                    val ex = challengeResult.exceptionOrNull()
                    val type = ex?.let { classifyException(it) } ?: LoginErrorType.NETWORK
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = ex?.let { friendlyErrorMessage(it, "retry_captcha") } ?: "刷新验证码失败，请点击刷新按钮重试",
                        errorType = type,
                        isCaptchaRetry = true
                    )
                    return@launch
                }
                val challenge = challengeResult.getOrNull()!!
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    captchaImage = challenge.captchaImage,
                    error = null,
                    errorType = null,
                    isCaptchaRetry = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error retrying captcha fetch", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = friendlyErrorMessage(e, "retry_captcha"),
                    errorType = classifyException(e),
                    isCaptchaRetry = true
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, errorType = null)
    }

    fun clearRecognizedText() {
        _uiState.value = _uiState.value.copy(recognizedText = null)
    }

    fun recognizeCaptcha() {
        val imageData = _uiState.value.captchaImage ?: return
        if (_uiState.value.isRecognizing) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecognizing = true, error = null, errorType = null, recognizedText = null)

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
                        error = if (useLocalOcr) "本地识别失败，请确认模型已加载或尝试手动输入" else "远程识别失败，请检查 OCR 服务器地址或尝试手动输入",
                        errorType = LoginErrorType.OCR
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "OCR recognition failed", e)
                _uiState.value = _uiState.value.copy(
                    isRecognizing = false,
                    error = friendlyErrorMessage(e, "ocr").let { if (it == "操作失败，请重试") "识别失败，请尝试手动输入" else it },
                    errorType = classifyException(e).let { if (it == LoginErrorType.UNKNOWN) LoginErrorType.OCR else it }
                )
            }
        }
    }

    private suspend fun recognizeLocal(imageData: ByteArray): String? = withContext(Dispatchers.Default) {
        val modelVersion = settingsDataStore.ocrModelVersion.first()
        val v2Backbone = settingsDataStore.ocrV2Backbone.first()
        val v2Precision = settingsDataStore.ocrV2Precision.first()
        if (!NcnnModelLoader.ensureLoaded(shmtuNcnn, context, modelVersion, false, v2Backbone, v2Precision)) {
            Log.w(TAG, "Local OCR model not loaded (no downloaded/built-in model found)")
            return@withContext null
        }
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return@withContext null
        val resultObj = when (modelVersion) {
            SHMTU_NCNN_Model.ModelVersion.V1 -> shmtuNcnn.predict_validate_code(bitmap)
            SHMTU_NCNN_Model.ModelVersion.V2 -> shmtuNcnn.predict_validate_code_v2(bitmap)
        }
        if (resultObj == null || resultObj.size < 4) {
            Log.w(TAG, "Local OCR returned null or incomplete result")
            return@withContext null
        }
        val expr = CaptchaOcrHelper.buildExprString(resultObj)
        Log.i(TAG, "Local OCR result: expr=$expr, answer=${expr?.let { CaptchaOcrHelper.extractAnswer(it) }}")
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
