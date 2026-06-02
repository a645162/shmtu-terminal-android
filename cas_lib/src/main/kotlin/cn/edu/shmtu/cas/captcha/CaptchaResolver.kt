package cn.edu.shmtu.cas.captcha

/**
 * 验证码解析器接口
 *
 * 对齐 Rust 版本的 CaptchaResolver trait：
 * - ManualCaptchaResolver: 手动输入
 * - RemoteOcrCaptchaResolver: 远程 TCP OCR 服务
 * - RemoteOcrHttpCaptchaResolver: 远程 HTTP OCR 服务
 */
interface CaptchaResolver {
    suspend fun resolve(imageData: ByteArray): Result<CaptchaAnswer>
}
