package cn.edu.shmtu.terminal.android.domain.model

data class AuthResult(
    val success: Boolean,
    val errorMessage: String? = null
)
