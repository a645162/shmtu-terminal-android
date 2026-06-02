package cn.edu.shmtu.cas.captcha

/**
 * 手动验证码解析器
 *
 * 由调用方提供 handler 回调，用于 UI 弹窗等交互场景。
 * 对齐 C# 版本的 ManualCaptchaResolver。
 */
class ManualCaptchaResolver(
    private val handler: suspend (ByteArray) -> CaptchaAnswer
) : CaptchaResolver {
    override suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer> {
        return try {
            Result.success(handler(imageData))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
