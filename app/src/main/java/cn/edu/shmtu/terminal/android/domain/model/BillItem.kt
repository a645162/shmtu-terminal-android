package cn.edu.shmtu.terminal.android.domain.model

data class BillItem(
    val id: Long,
    val accountId: Long,
    val accountLabel: String,
    val dateTimeStrFormat: String,
    val type: String,
    val transactionNo: String,
    val targetUser: String,
    val money: String,
    val method: String,
    val status: String
)
