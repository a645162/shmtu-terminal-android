package cn.edu.shmtu.cas.session

/**
 * 会话信息数据类
 *
 * 存储账号的登录会话（cookies），加密存储
 * 对齐 Rust 版本的 SessionInfo
 *
 * @property id 数据库主键
 * @property accountId 学号（唯一标识）
 * @property cookies 加密后的 Cookies JSON
 * @property loginTime 登录时间（ISO 8601）
 * @property expireTime 预估过期时间（ISO 8601）
 * @property isValid 会话是否仍有效
 */
data class SessionInfo(
    val id: Long = 0,
    val accountId: String,
    val cookies: String,
    val loginTime: String? = null,
    val expireTime: String? = null,
    val isValid: Boolean = true
)

/**
 * 会话探测结果
 */
sealed class SessionProbe {
    /** 已登录 */
    data object AlreadyLoggedIn : SessionProbe()

    /** 需要登录，提供登录 URL */
    data class NeedLogin(val loginUrl: String) : SessionProbe()
}

/**
 * 登录提交结果
 */
sealed class LoginSubmitResult {
    /** 登录成功 */
    data object Success : LoginSubmitResult()

    /** 验证码错误 */
    data object ValidateCodeError : LoginSubmitResult()

    /** 密码错误 */
    data object PasswordError : LoginSubmitResult()

    /** 其他失败 */
    data class Failure(val message: String) : LoginSubmitResult()
}

/**
 * 登录挑战数据
 */
data class LoginChallenge(
    val execution: String,
    val captchaImage: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LoginChallenge

        if (execution != other.execution) return false
        if (!captchaImage.contentEquals(other.captchaImage)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = execution.hashCode()
        result = 31 * result + captchaImage.contentHashCode()
        return result
    }
}