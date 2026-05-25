package cn.edu.shmtu.terminal.android.domain.model

/**
 * Session 领域模型
 */
data class Session(
    val id: Long = 0,
    val accountId: String,
    val cookies: String,
    val loginTime: String? = null,
    val expireTime: String? = null,
    val isValid: Boolean = true
)
