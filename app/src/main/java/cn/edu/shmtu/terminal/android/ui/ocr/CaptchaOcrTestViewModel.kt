package cn.edu.shmtu.terminal.android.ui.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.auth.EpayAuth
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.CaptchaOcrHelper
import cn.edu.shmtu.cas.captcha.RemoteOcrHttpCaptchaResolver
import cn.edu.shmtu.cas.ocr.NcnnModelLoader
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import cn.edu.shmtu.cas.session.LoginSubmitResult
import cn.edu.shmtu.cas.session.SessionProbe
import cn.edu.shmtu.terminal.android.data.local.datastore.OcrServerType
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis
import javax.inject.Inject

@HiltViewModel
class CaptchaOcrTestViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    companion object {
        private const val PROBE_USERNAME = "__captcha_test_invalid_user__"
        private const val PROBE_PASSWORD = "__captcha_test_invalid_password__"
    }

    private val shmtuNcnn = SHMTU_NCNN()
    private var cloudAuth: EpayAuth? = null

    private val _uiState = androidx.compose.runtime.mutableStateOf(CaptchaOcrTestUiState())
    val uiState: androidx.compose.runtime.State<CaptchaOcrTestUiState> = _uiState

    init {
        loadDefaultsFromSettings()
    }

    fun loadDefaultsFromSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val modelVersion = settingsDataStore.ocrModelVersion.first()
            val modelBackbone = settingsDataStore.ocrV2Backbone.first()
            val modelPrecision = settingsDataStore.ocrV2Precision.first()
            val modelTag = settingsDataStore.ocrV2ModelTag.first()
            val remoteType = settingsDataStore.ocrServerType.first()
            val tcp = settingsDataStore.ocrServerUrl.first()
            val http = settingsDataStore.ocrHttpServerUrl.first()
            val useLocal = settingsDataStore.useLocalOcr.first()

            updateState {
                copy(
                    useLocalOcrPreferred = useLocal,
                    localModelVersion = modelVersion,
                    localBackbone = modelBackbone,
                    localPrecision = modelPrecision,
                    localTag = modelTag,
                    remoteServerType = remoteType,
                    remoteServerAddress = if (remoteType == OcrServerType.HTTP) http else tcp,
                    result = null,
                    statusMessage = "已按当前设置载入默认测试参数",
                )
            }
        }
    }

    fun setManualAnswer(value: String) {
        updateState { copy(manualAnswer = value) }
    }

    fun setLocalModelVersion(value: SHMTU_NCNN_Model.ModelVersion) {
        updateState { copy(localModelVersion = value) }
    }

    fun setLocalBackbone(value: String) {
        updateState { copy(localBackbone = value) }
    }

    fun setLocalPrecision(value: String) {
        updateState { copy(localPrecision = value) }
    }

    fun setRemoteServerType(value: OcrServerType) {
        updateState { copy(remoteServerType = value) }
    }

    fun setRemoteServerAddress(value: String) {
        updateState { copy(remoteServerAddress = value) }
    }

    fun clearTransientMessage() {
        updateState { copy(statusMessage = null) }
    }

    fun clearResult() {
        updateState { copy(result = null, statusMessage = null) }
    }

    fun loadImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取图片内容")
            }.onSuccess { bytes ->
                val meta = describeImage(bytes)
                cloudAuth = null
                updateState {
                    copy(
                        imageBytes = bytes,
                        imageLabel = resolveDisplayName(uri),
                        imageMeta = meta,
                        imageSource = CaptchaImageSource.File,
                        challengeExecution = null,
                        result = null,
                        statusMessage = "已载入本地图片，可做手动 / 本地 / 远程识别；云端校验仅支持真实 challenge",
                    )
                }
            }.onFailure { error ->
                updateState { copy(statusMessage = error.message ?: "读取图片失败") }
            }
        }
    }

    fun refreshCloudChallenge() {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isBusy = true, busyAction = OcrBusyAction.FetchingChallenge, statusMessage = null) }
            val result = runCatching {
                val auth = EpayAuth()
                when (auth.probeLogin().getOrElse { throw it }) {
                    SessionProbe.AlreadyLoggedIn -> error("当前会话已处于登录状态，无法拉取验证码 challenge")
                    is SessionProbe.NeedLogin -> Unit
                }
                val challenge = auth.prepareChallenge().getOrElse { throw it }
                cloudAuth = auth
                challenge
            }
            updateState {
                result.fold(
                    onSuccess = { challenge ->
                        copy(
                            isBusy = false,
                            busyAction = null,
                            imageBytes = challenge.captchaImage,
                            imageLabel = "云端验证码",
                            imageMeta = describeImage(challenge.captchaImage),
                            imageSource = CaptchaImageSource.Cloud,
                            challengeExecution = challenge.execution,
                            result = null,
                            manualAnswer = "",
                            statusMessage = "已拉取新的云端验证码，可进行手动、本地、远程识别并校验",
                        )
                    },
                    onFailure = { error ->
                        copy(
                            isBusy = false,
                            busyAction = null,
                            statusMessage = error.message ?: "获取云端验证码失败",
                        )
                    },
                )
            }
        }
    }

    fun runLocalOcr() {
        val imageBytes = _uiState.value.imageBytes ?: run {
            updateState { copy(statusMessage = "请先选择图片或拉取云端验证码") }
            return
        }
        viewModelScope.launch(Dispatchers.Default) {
            updateState { copy(isBusy = true, busyAction = OcrBusyAction.LocalOcr, statusMessage = null) }
            val state = _uiState.value
            val result = runCatching {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: error("无法解码验证码图片")
                val loaded = NcnnModelLoader.ensureLoaded(
                    ncnn = shmtuNcnn,
                    context = context,
                    version = state.localModelVersion,
                    useGpu = false,
                    v2Backbone = state.localBackbone,
                    v2Precision = state.localPrecision,
                )
                if (!loaded) {
                    error("本地模型未就绪，请确认对应版本/骨干网络模型已下载")
                }

                var tuple: Array<Any?>? = null
                val durationMs = measureTimeMillis {
                    tuple = when (state.localModelVersion) {
                        SHMTU_NCNN_Model.ModelVersion.V1 -> shmtuNcnn.predict_validate_code(bitmap)
                        SHMTU_NCNN_Model.ModelVersion.V2 -> shmtuNcnn.predict_validate_code_v2(bitmap)
                    }
                }
                val expression = CaptchaOcrHelper.buildExprString(tuple)
                    ?.takeIf { it.isNotBlank() }
                    ?: error("本地 OCR 返回空结果")
                val answer = CaptchaOcrHelper.extractAnswer(expression).ifBlank {
                    Captcha.getExprResultByExprString(expression)
                }
                OcrTestResult(
                    source = "本地 ${state.localModelVersion.name}",
                    expression = expression,
                    answer = answer.ifBlank { "未解析" },
                    durationMs = durationMs,
                    detail = "tuple=${tuple?.contentDeepToString() ?: "null"}",
                )
            }
            applyOcrResult(result, OcrBusyAction.LocalOcr)
        }
    }

    fun runRemoteOcr() {
        val imageBytes = _uiState.value.imageBytes ?: run {
            updateState { copy(statusMessage = "请先选择图片或拉取云端验证码") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isBusy = true, busyAction = OcrBusyAction.RemoteOcr, statusMessage = null) }
            val state = _uiState.value
            val result = runCatching {
                when (state.remoteServerType) {
                    OcrServerType.HTTP -> runRemoteHttpOcr(imageBytes, state.remoteServerAddress)
                    OcrServerType.TCP -> runRemoteTcpOcr(imageBytes, state.remoteServerAddress)
                }
            }
            applyOcrResult(result, OcrBusyAction.RemoteOcr)
        }
    }

    fun verifyCurrentAnswer() {
        val answer = _uiState.value.manualAnswer.trim()
        if (answer.isEmpty()) {
            updateState { copy(statusMessage = "请先输入或生成一个待校验答案") }
            return
        }
        val auth = cloudAuth
        val execution = _uiState.value.challengeExecution
        if (auth == null || execution.isNullOrBlank() || _uiState.value.imageSource != CaptchaImageSource.Cloud) {
            updateState { copy(statusMessage = "当前不是可校验的云端 challenge，请先点击“刷新云端验证码”") }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isBusy = true, busyAction = OcrBusyAction.CloudVerify, statusMessage = null) }
            val result = runCatching {
                auth.submitLogin(PROBE_USERNAME, PROBE_PASSWORD, answer, execution).getOrElse { throw it }
            }

            result.onSuccess { submitResult ->
                when (submitResult) {
                    LoginSubmitResult.PasswordError -> {
                        cloudAuth = null
                        updateState {
                            copy(
                                isBusy = false,
                                busyAction = null,
                                challengeExecution = null,
                                verification = CloudVerificationResult(
                                    success = true,
                                    title = "云端验证通过",
                                    detail = "返回密码错误，说明验证码答案正确。",
                                ),
                                statusMessage = "当前 challenge 已消费，请刷新云端验证码开始下一轮测试",
                            )
                        }
                    }
                    LoginSubmitResult.Success -> {
                        cloudAuth = null
                        updateState {
                            copy(
                                isBusy = false,
                                busyAction = null,
                                challengeExecution = null,
                                verification = CloudVerificationResult(
                                    success = true,
                                    title = "云端验证通过",
                                    detail = "登录成功，验证码答案正确。",
                                ),
                                statusMessage = "当前 challenge 已消费，请刷新云端验证码开始下一轮测试",
                            )
                        }
                    }
                    LoginSubmitResult.ValidateCodeError -> {
                        val refreshed = runCatching { auth.prepareChallenge().getOrElse { throw it } }.getOrNull()
                        updateState {
                            copy(
                                isBusy = false,
                                busyAction = null,
                                imageBytes = refreshed?.captchaImage ?: imageBytes,
                                imageLabel = if (refreshed != null) "云端验证码（已刷新）" else imageLabel,
                                imageMeta = refreshed?.captchaImage?.let(::describeImage) ?: imageMeta,
                                challengeExecution = refreshed?.execution,
                                manualAnswer = "",
                                verification = CloudVerificationResult(
                                    success = false,
                                    title = "云端验证失败",
                                    detail = "验证码答案错误，已为你刷新新的 challenge。",
                                ),
                                statusMessage = if (refreshed != null) "验证码错误，已自动刷新云端验证码" else "验证码错误，且刷新新 challenge 失败",
                            )
                        }
                    }
                    is LoginSubmitResult.Failure -> {
                        cloudAuth = null
                        updateState {
                            copy(
                                isBusy = false,
                                busyAction = null,
                                challengeExecution = null,
                                verification = CloudVerificationResult(
                                    success = false,
                                    title = "云端验证异常",
                                    detail = submitResult.message,
                                ),
                                statusMessage = "验证返回异常，请重新刷新云端验证码再试",
                            )
                        }
                    }
                }
            }.onFailure { error ->
                updateState {
                    copy(
                        isBusy = false,
                        busyAction = null,
                        verification = CloudVerificationResult(
                            success = false,
                            title = "云端验证异常",
                            detail = error.message ?: "未知错误",
                        ),
                        statusMessage = error.message ?: "云端验证失败",
                    )
                }
            }
        }
    }

    private suspend fun runRemoteHttpOcr(imageBytes: ByteArray, baseUrl: String): OcrTestResult {
        val normalized = baseUrl.trim()
        require(normalized.isNotBlank()) { "请输入有效的 HTTP OCR 地址" }
        var expression = ""
        var answer = ""
        val durationMs = measureTimeMillis {
            val payload = RemoteOcrHttpCaptchaResolver(normalized).resolve(imageBytes).getOrElse { throw it }
            expression = payload.value
            answer = payload.intoFinalAnswer().value
        }
        expression = expression.takeIf { it.isNotBlank() } ?: error("HTTP OCR 返回空结果")
        answer = answer.ifBlank {
            CaptchaOcrHelper.extractAnswer(expression)
        }.ifBlank {
            Captcha.getExprResultByExprString(expression)
        }
        return OcrTestResult(
            source = "远程 HTTP",
            expression = expression,
            answer = answer.ifBlank { "未解析" },
            durationMs = durationMs,
            detail = normalized,
        )
    }

    private fun runRemoteTcpOcr(imageBytes: ByteArray, address: String): OcrTestResult {
        val host = address.substringBefore(":", "").trim()
        val port = address.substringAfter(":", "").trim().toIntOrNull()
        require(host.isNotBlank() && port != null) { "TCP 地址无效，应为 host:port" }
        var expression = ""
        val durationMs = measureTimeMillis {
            expression = Captcha.ocrByRemoteTcpServerAutoRetry(host, port, imageBytes)
        }
        require(expression.isNotBlank()) { "TCP OCR 返回空结果" }
        val answer = CaptchaOcrHelper.extractAnswer(expression).ifBlank {
            Captcha.getExprResultByExprString(expression)
        }
        return OcrTestResult(
            source = "远程 TCP",
            expression = expression,
            answer = answer.ifBlank { "未解析" },
            durationMs = durationMs,
            detail = "$host:$port",
        )
    }

    private fun applyOcrResult(result: Result<OcrTestResult>, action: OcrBusyAction) {
        updateState {
            result.fold(
                onSuccess = { ocrResult ->
                    copy(
                        isBusy = false,
                        busyAction = null,
                        manualAnswer = ocrResult.answer.takeIf { it != "未解析" } ?: manualAnswer,
                        result = ocrResult,
                        verification = null,
                        statusMessage = when (action) {
                            OcrBusyAction.LocalOcr -> "本地识别完成，已把答案写入手动输入框"
                            OcrBusyAction.RemoteOcr -> "远程识别完成，已把答案写入手动输入框"
                            else -> statusMessage
                        },
                    )
                },
                onFailure = { error ->
                    copy(
                        isBusy = false,
                        busyAction = null,
                        statusMessage = error.message ?: "识别失败",
                    )
                },
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index) ?: "已选图片"
            }
        }
        return uri.lastPathSegment ?: "已选图片"
    }

    private fun describeImage(bytes: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth.takeIf { it > 0 } ?: 0
        val height = bounds.outHeight.takeIf { it > 0 } ?: 0
        return "${width} x ${height} · ${formatBytes(bytes.size.toLong())}"
    }

    private fun updateState(transform: CaptchaOcrTestUiState.() -> CaptchaOcrTestUiState) {
        _uiState.value = _uiState.value.transform()
    }

    override fun onCleared() {
        super.onCleared()
        NcnnModelLoader.release(shmtuNcnn)
        cloudAuth = null
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes} B"
        val kb = bytes / 1024f
        if (kb < 1024f) return String.format("%.1f KB", kb)
        return String.format("%.2f MB", kb / 1024f)
    }
}

enum class CaptchaImageSource {
    None,
    File,
    Cloud,
}

enum class OcrBusyAction {
    FetchingChallenge,
    LocalOcr,
    RemoteOcr,
    CloudVerify,
}

data class OcrTestResult(
    val source: String,
    val expression: String,
    val answer: String,
    val durationMs: Long,
    val detail: String,
)

data class CloudVerificationResult(
    val success: Boolean,
    val title: String,
    val detail: String,
)

data class CaptchaOcrTestUiState(
    val useLocalOcrPreferred: Boolean = true,
    val localModelVersion: SHMTU_NCNN_Model.ModelVersion = SHMTU_NCNN_Model.ModelVersion.V2,
    val localTag: String = "",
    val localBackbone: String = SHMTU_NCNN_Model.V2_DEFAULT_BACKBONE,
    val localPrecision: String = SHMTU_NCNN_Model.V2_DEFAULT_PRECISION,
    val remoteServerType: OcrServerType = OcrServerType.HTTP,
    val remoteServerAddress: String = OcrServerType.DEFAULT_HTTP_URL,
    val imageBytes: ByteArray? = null,
    val imageLabel: String = "",
    val imageMeta: String = "",
    val imageSource: CaptchaImageSource = CaptchaImageSource.None,
    val challengeExecution: String? = null,
    val manualAnswer: String = "",
    val isBusy: Boolean = false,
    val busyAction: OcrBusyAction? = null,
    val result: OcrTestResult? = null,
    val verification: CloudVerificationResult? = null,
    val statusMessage: String? = null,
)
