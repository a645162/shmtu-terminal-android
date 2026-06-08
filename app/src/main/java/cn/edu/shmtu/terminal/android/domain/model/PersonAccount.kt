package cn.edu.shmtu.terminal.android.domain.model

/**
 * 一卡通个人账户详情 - 领域模型
 *
 * UI 展示需要的全部字段,字段命名与 [cn.edu.shmtu.cas.parser.PersonAccountInfo] 保持一致。
 */
data class PersonAccount(
    val realName: String = "",
    val realNameAuthStatus: String = "",

    val cashBalance: Double = 0.0,
    val cashBalanceRaw: String = "",
    val securityQuestionStatus: String = "",
    val registerDate: String = "",

    val studentId: String = "",
    val email: String = "",
    val nickname: String = "",
    val gender: String = "",
    val className: String = "",
    val mobile: String = "",
    val fixedLine: String = "",
    val idType: String = "",
    val idNumber: String = "",
    val remark: String = "",
    val userType: String = "",

    val updatedAt: Long = 0L
)
