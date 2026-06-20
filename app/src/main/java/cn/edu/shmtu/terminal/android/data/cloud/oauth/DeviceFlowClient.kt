package cn.edu.shmtu.terminal.android.data.cloud.oauth

import android.util.Log
import kotlinx.coroutines.delay

class DeviceFlowClient(
    private val httpClient: okhttp3.OkHttpClient,
    private val deviceCodeUrl: String,
    private val tokenUrl: String
) {
    companion object {
        private const val TAG = "DeviceFlowClient"
        const val GOOGLE_DEVICE_CODE_URL = "https://oauth2.googleapis.com/device/code"
        const val GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val MICROSOFT_DEVICE_CODE_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/devicecode"
        const val MICROSOFT_TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    }

    suspend fun requestDeviceCode(
        clientId: String,
        clientSecret: String?,
        scope: String
    ): Result<DeviceCodeResponse> = runCatching {
        val bodyBuilder = okhttp3.FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", scope)
        if (!clientSecret.isNullOrBlank()) {
            bodyBuilder.add("client_secret", clientSecret)
        }
        val req = okhttp3.Request.Builder()
            .url(deviceCodeUrl)
            .post(bodyBuilder.build())
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("device_code HTTP ${resp.code}: $raw")
            }
            OAuthJson.json.decodeFromString<DeviceCodeResponse>(raw)
        }
    }.onFailure { Log.w(TAG, "requestDeviceCode failed: ${it.message}") }

    suspend fun pollToken(
        clientId: String,
        clientSecret: String?,
        deviceCode: String
    ): TokenPollResult = runCatching {
        val bodyBuilder = okhttp3.FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
        if (!clientSecret.isNullOrBlank()) {
            bodyBuilder.add("client_secret", clientSecret)
        }
        val req = okhttp3.Request.Builder()
            .url(tokenUrl)
            .post(bodyBuilder.build())
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                val token = OAuthJson.json.decodeFromString<TokenResponse>(raw)
                TokenPollResult.Success(token)
            } else {
                val err = try { OAuthJson.json.decodeFromString<TokenErrorResponse>(raw) }
                catch (_: Exception) { TokenErrorResponse(error = "http_${resp.code}", errorDescription = raw) }
                when (err.error) {
                    "authorization_pending" -> TokenPollResult.Pending
                    "slow_down" -> TokenPollResult.SlowDown
                    "expired_token" -> TokenPollResult.Expired
                    "access_denied" -> TokenPollResult.Denied
                    else -> TokenPollResult.Error(err.error, err.errorDescription)
                }
            }
        }
    }.getOrElse { TokenPollResult.Error("exception", it.message) }

    suspend fun runDeviceFlow(
        clientId: String,
        clientSecret: String?,
        scope: String,
        timeoutSec: Long = 300,
        onPending: suspend (DeviceCodeResponse) -> Unit = {}
    ): Result<TokenResponse> {
        val deviceCodeResp = requestDeviceCode(clientId, clientSecret, scope)
            .getOrElse { return Result.failure(it) }
        val deadline = System.currentTimeMillis() + timeoutSec * 1000
        var currentInterval = deviceCodeResp.interval
        while (System.currentTimeMillis() < deadline) {
            delay(currentInterval * 1000)
            onPending(deviceCodeResp)
            when (val r = pollToken(clientId, clientSecret, deviceCodeResp.deviceCode)) {
                is TokenPollResult.Success -> return Result.success(r.token)
                is TokenPollResult.Pending -> continue
                is TokenPollResult.SlowDown -> currentInterval += 5
                is TokenPollResult.Expired -> return Result.failure(RuntimeException("设备码已过期，请重新发起"))
                is TokenPollResult.Denied -> return Result.failure(RuntimeException("用户拒绝授权"))
                is TokenPollResult.Error -> return Result.failure(RuntimeException("${r.error}: ${r.description}"))
            }
        }
        return@runDeviceFlow Result.failure(RuntimeException("授权超时"))
    }

    suspend fun refreshAccessToken(
        clientId: String,
        clientSecret: String?,
        refreshToken: String
    ): Result<TokenResponse> = runCatching {
        val bodyBuilder = okhttp3.FormBody.Builder()
            .add("client_id", clientId)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
        if (!clientSecret.isNullOrBlank()) {
            bodyBuilder.add("client_secret", clientSecret)
        }
        val req = okhttp3.Request.Builder()
            .url(tokenUrl)
            .post(bodyBuilder.build())
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw RuntimeException("refresh HTTP ${resp.code}: $raw")
            }
            OAuthJson.json.decodeFromString<TokenResponse>(raw)
        }
    }
}

sealed class TokenPollResult {
    data class Success(val token: TokenResponse) : TokenPollResult()
    object Pending : TokenPollResult()
    object SlowDown : TokenPollResult()
    object Expired : TokenPollResult()
    object Denied : TokenPollResult()
    data class Error(val error: String, val description: String?) : TokenPollResult()
}
