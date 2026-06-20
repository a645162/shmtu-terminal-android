package cn.edu.shmtu.terminal.android.data.remote

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import cn.edu.shmtu.cas.captcha.Captcha
import cn.edu.shmtu.cas.captcha.CaptchaAnswer
import cn.edu.shmtu.cas.captcha.CaptchaAnswerKind
import cn.edu.shmtu.cas.captcha.CaptchaOcrHelper
import cn.edu.shmtu.cas.captcha.CaptchaResolver
import cn.edu.shmtu.cas.captcha.RemoteOcrHttpCaptchaResolver
import cn.edu.shmtu.cas.ocr.NcnnModelLoader
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import cn.edu.shmtu.terminal.android.data.local.datastore.CaptchaMode
import cn.edu.shmtu.terminal.android.data.local.datastore.OcrServerType
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一的验证码解析器工厂。
 *
 * 根据 [SettingsDataStore] 中的验证码模式设置构造 [CaptchaResolver]：
 * - [CaptchaMode.MANUAL] → null（由 UI 弹窗处理）
 * - [CaptchaMode.AUTO_OCR] → 本地 NCNN（优先）或远程 OCR（回退）
 *
 * 远程 OCR 支持：
 * - [OcrServerType.HTTP] → RESTful HTTP API（默认，POST /api/ocr）
 * - [OcrServerType.TCP] → 原始 TCP 协议
 *
 * 所有需要验证码自动识别的调用方（[EpayAdapter]、[SyncAccountBillsUseCase]、[LoginViewModel]）
 * 都应通过本工厂获取 resolver，避免各自维护重复的 OCR 逻辑。
 */
@Singleton
class CaptchaResolverFactory @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    @param:ApplicationContext private val context: Context,
) {
    private val shmtuNcnn = SHMTU_NCNN()
    private val tag = "CaptchaResolverFactory"

    /**
     * 根据当前设置构造 [CaptchaResolver]。
     *
     * @return AUTO_OCR 模式下返回 resolver；MANUAL 模式下返回 null，调用方应抛异常交由 UI 处理。
     */
    suspend fun create(): CaptchaResolver? {
        val captchaMode = settingsDataStore.captchaMode.first()
        return when (captchaMode) {
            CaptchaMode.MANUAL -> null
            CaptchaMode.AUTO_OCR -> autoOcrResolver()
        }
    }

    /**
     * 同步版本：直接根据已知的 [CaptchaMode] 构造 resolver。
     * 适用于调用方已经读取过 captchaMode 的场景，避免重复读 Flow。
     */
    fun create(captchaMode: CaptchaMode): CaptchaResolver? {
        return when (captchaMode) {
            CaptchaMode.MANUAL -> null
            CaptchaMode.AUTO_OCR -> autoOcrResolver()
        }
    }

    /**
     * 仅构造自动 OCR resolver（不检查 captchaMode）。
     * 适用于调用方已确认要走自动 OCR 的场景。
     */
    fun autoOcrResolver(): CaptchaResolver {
        return object : CaptchaResolver {
            override suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer> {
                val useLocalOcr = settingsDataStore.useLocalOcr.first()
                return if (useLocalOcr) {
                    resolveLocalOcr(imageData)
                } else {
                    resolveRemoteOcr(imageData)
                }
            }
        }
    }

    /**
     * 远程 OCR 解析，根据 [OcrServerType] 选择协议。
     */
    private suspend fun resolveRemoteOcr(imageData: ByteArray): Result<CaptchaAnswer> {
        val serverType = settingsDataStore.ocrServerType.first()
        return when (serverType) {
            OcrServerType.HTTP -> resolveRemoteHttpOcr(imageData)
            OcrServerType.TCP -> resolveRemoteTcpOcr(imageData)
        }
    }

    /**
     * 远程 RESTful HTTP OCR 解析。
     */
    private suspend fun resolveRemoteHttpOcr(imageData: ByteArray): Result<CaptchaAnswer> {
        val baseUrl = settingsDataStore.ocrHttpServerUrl.first()
        val resolver = RemoteOcrHttpCaptchaResolver(baseUrl)
        return resolver.resolve(imageData)
    }

    /**
     * 远程 TCP OCR 解析。
     */
    private suspend fun resolveRemoteTcpOcr(imageData: ByteArray): Result<CaptchaAnswer> {
        val serverUrl = settingsDataStore.ocrServerUrl.first()
        val parts = serverUrl.split(":")
        return if (parts.size == 2) {
            val port = parts[1].toIntOrNull()
            if (port != null) {
                val answer = Captcha.ocrByRemoteTcpServerAutoRetry(parts[0], port, imageData)
                if (answer.isNotBlank()) {
                    Result.success(CaptchaAnswer(answer, CaptchaAnswerKind.ANSWER))
                } else {
                    Result.failure(Exception("OCR 识别失败"))
                }
            } else Result.failure(Exception("OCR 配置端口无效"))
        } else Result.failure(Exception("OCR 配置无效（需 host:port 格式）"))
    }

    /**
     * 本地 NCNN OCR 解析，失败时自动回退到远程。
     */
    private suspend fun resolveLocalOcr(imageData: ByteArray): Result<CaptchaAnswer> = withContext(Dispatchers.Default) {
        val modelVersion = settingsDataStore.ocrModelVersion.first()
        val v2Backbone = settingsDataStore.ocrV2Backbone.first()
        val v2Precision = settingsDataStore.ocrV2Precision.first()
        if (!NcnnModelLoader.ensureLoaded(shmtuNcnn, context, modelVersion, false, v2Backbone, v2Precision)) {
            Log.w(tag, "local NCNN model not loaded, falling back to remote")
            return@withContext resolveRemoteOcr(imageData)
        }
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            ?: return@withContext Result.failure(Exception("无法解码验证码图片"))
        val resultObj = when (modelVersion) {
            SHMTU_NCNN_Model.ModelVersion.V1 -> shmtuNcnn.predict_validate_code(bitmap)
            SHMTU_NCNN_Model.ModelVersion.V2 -> shmtuNcnn.predict_validate_code_v2(bitmap)
        }
        if (resultObj == null || resultObj.size < 4) {
            Log.w(tag, "local OCR returned null/incomplete, falling back to remote")
            return@withContext resolveRemoteOcr(imageData)
        }
        val expr = CaptchaOcrHelper.buildExprString(resultObj)
        return@withContext if (expr != null) {
            Log.i(
                tag,
                "local OCR success: version=$modelVersion, expr=$expr, answer=${CaptchaOcrHelper.extractAnswer(expr)}"
            )
            Result.success(CaptchaAnswer(expr, CaptchaAnswerKind.EXPRESSION))
        } else {
            Log.w(tag, "local OCR expr parsing failed, falling back to remote")
            resolveRemoteOcr(imageData)
        }
    }
}
