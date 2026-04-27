package cn.edu.shmtu.terminal.android.domain.model

data class Identity(
    val id: Long,
    val name: String,
    val birthday: String = "",
    val enrollmentDate: String = "",
    val graduationDate: String = "",
    val displayOrder: Int = 0,
    val accountCount: Int = 0
)
