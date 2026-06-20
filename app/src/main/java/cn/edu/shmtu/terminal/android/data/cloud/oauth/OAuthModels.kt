package cn.edu.shmtu.terminal.android.data.cloud.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OAuth 2.0 Device Authorization Grant 流程的通用结果/数据类。
 * 兼容 Google Drive 和 Microsoft OneDrive 两个 provider。
 *
 * 流程（RFC 8628）：
 * 1. App POST /device/code 拿到 user_code + device_code + verification_url
 * 2. App 显示 user_code + verification_url 给用户
 * 3. 用户在浏览器打开 verification_url，输入 user_code 并授权
 * 4. App 轮询 /token 端点直到返回 access_token / refresh_token
 */

@Serializable
data class DeviceCodeRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("scope") val scope: String,
    @SerialName("client_secret") val clientSecret: String? = null
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_url") val verificationUrl: String? = null,
    @SerialName("verification_uri") val verificationUri: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("interval") val interval: Long = 5,
    @SerialName("message") val message: String? = null
) {
    fun resolveVerificationUrl(): String = verificationUrl ?: verificationUri ?: ""
}

@Serializable
data class TokenPollRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("device_code") val deviceCode: String,
    @SerialName("grant_type") val grantType: String = "urn:ietf:params:oauth:grant-type:device_code",
    @SerialName("client_secret") val clientSecret: String? = null
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("scope") val scope: String? = null
)

@Serializable
data class TokenErrorResponse(
    @SerialName("error") val error: String,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class TokenRefreshRequest(
    @SerialName("client_id") val clientId: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("client_secret") val clientSecret: String? = null
)

@Serializable
data class OAuthCredentials(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("scope") val scope: String? = null
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expiresAt > 0 && now >= expiresAt
    fun isValid(now: Long = System.currentTimeMillis()): Boolean = accessToken.isNotBlank() && !isExpired(now)
}

object OAuthJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}
