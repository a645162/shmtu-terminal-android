package cn.edu.shmtu.cas.captcha

/**
 * 远程 TCP OCR 验证码解析器（旧 API）
 *
 * 通过 TCP Socket 连接 OCR 服务，发送图片数据 + "<END>" 标记，接收识别结果。
 * 对齐 Rust 版本的 ocr_tcp.rs。
 */
class RemoteOcrCaptchaResolver(
    private val host: String,
    private val port: Int,
    private val retryTimes: Int = 3
) : CaptchaResolver {
    override suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer> {
        return try {
            val result = Captcha.ocrByRemoteTcpServerAutoRetry(host, port, imageData, retryTimes)
            if (result.isNotEmpty()) {
                Result.success(CaptchaAnswer(result, CaptchaAnswerKind.EXPRESSION))
            } else {
                Result.failure(Exception("OCR 识别返回空结果"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
