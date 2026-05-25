package cn.edu.shmtu.cas.auth.common

/**
 * 探测登录状态结果
 */
sealed class LoginProbe {
    /**
     * 已经登录成功
     */
    object AlreadyLoggedIn : LoginProbe()

    /**
     * 需要登录
     * @property loginUrl 登录页面 URL
     */
    data class NeedLogin(val loginUrl: String) : LoginProbe()
}

/**
 * 提交登录结果
 */
sealed class LoginSubmitResult {
    /**
     * 登录成功
     */
    object Success : LoginSubmitResult()

    /**
     * 验证码错误
     */
    object ValidateCodeError : LoginSubmitResult()

    /**
     * 密码错误
     */
    object PasswordError : LoginSubmitResult()

    /**
     * 其他失败
     * @property message 错误信息
     */
    data class Failure(val message: String) : LoginSubmitResult()
}

/**
 * 登录所需材料
 * @property execution CAS execution token
 * @property captchaImage 验证码图片字节数据
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