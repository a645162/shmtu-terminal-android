package cn.edu.shmtu.terminal.android.domain.model

/**
 * 同步状态 - 对齐 Rust 版 SyncStatus
 *
 * 细粒度同步进度，支持每页进度回调
 */
sealed class SyncStatus {
    /** 探测登录状态 */
    data object ProbingLogin : SyncStatus()

    /** 获取验证码中 */
    data object GettingCaptcha : SyncStatus()

    /** 登录中 */
    data object LoggingIn : SyncStatus()

    /** 同步账单数据中，page/total 表示每页进度 */
    data class Syncing(val page: Int, val total: Int, val newCount: Int = 0) : SyncStatus()

    /** 持久化数据中 */
    data class Persisting(val totalNew: Int) : SyncStatus()

    /** 同步完成 */
    data class Completed(val totalNew: Int) : SyncStatus()

    /** 同步失败 */
    data class Failed(val error: String) : SyncStatus()
}

/**
 * 同步进度 - 对齐 Rust 版 SyncProgressFrontend
 *
 * 包含当前账号进度和整体身份进度
 */
data class SyncProgress(
    /** 当前同步状态 */
    val status: SyncStatus = SyncStatus.ProbingLogin,

    /** 当前账号索引 (0-based) */
    val accountIndex: Int = 0,

    /** 总账号数 */
    val accountTotal: Int = 1,

    /** 当前账号标签 */
    val accountLabel: String = "",

    /** 是否需要手动验证码 */
    val captchaRequired: Boolean = false,

    /** 验证码图片 base64 (当 captchaRequired=true) */
    val captchaImageBase64: String = "",

    /** 验证码 execution token */
    val captchaExecution: String = ""
)

/**
 * 同步结果 - 扩展版，对齐 Rust 版 SyncProgressFrontend 的 Completed/Failed
 */
data class SyncResult(
    val newCount: Int,
    val success: Boolean = true,
    val errorMessage: String? = null
)
