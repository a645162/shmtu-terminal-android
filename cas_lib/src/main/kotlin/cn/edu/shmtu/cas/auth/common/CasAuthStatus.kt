package com.khm.shmtu.cas.auth.common

enum class CasAuthStatus(val code: Int) {
    SUCCESS(200),
    VALIDATE_CODE_ERROR(-1),
    PASSWORD_ERROR(-2),
    FAILURE(404)
}
