package cn.edu.shmtu.terminal.android.domain.usecase.bill

import cn.edu.shmtu.cas.sync.SyncRangePreset

/**
 * 同步过程中需要手动输入验证码时抛出
 * 对齐 Rust 版 MANUAL_CAPTCHA_REQUIRED 错误标记
 */
class CaptchaRequiredException(
    val captchaImageBase64: String,
    val execution: String,
    val accountId: Long,
    val accountLabel: String,
    val syncRange: SyncRangePreset,
    val isFullSync: Boolean,
) : Exception("MANUAL_CAPTCHA_REQUIRED|$captchaImageBase64|$execution")
