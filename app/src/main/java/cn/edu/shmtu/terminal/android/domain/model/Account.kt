package cn.edu.shmtu.terminal.android.domain.model

data class Account(
    val id: Long,
    val identityId: Long,
    val label: String,
    val userId: String,
    val accountType: AccountType,
    val loginStatus: LoginStatus,
    val lastSyncTime: Long? = null
)

enum class AccountType { EPAY }

enum class LoginStatus { LOGGED_OUT, LOGGED_IN, ERROR }
