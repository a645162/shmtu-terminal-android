package cn.edu.shmtu.terminal.android.data.cloud

import android.util.Log
import cn.edu.shmtu.terminal.android.data.cloud.oauth.DeviceFlowClient
import cn.edu.shmtu.terminal.android.data.cloud.oauth.OAuthCredentials
import cn.edu.shmtu.terminal.android.data.cloud.oauth.TokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OneDrive 备份 Provider（OAuth 2.0 Device Authorization Grant 实现）。
 *
 * 启用条件：在 Azure Portal 注册公共客户端（应用类型：Mobile and desktop applications），
 * 把 Client ID 填入 App 设置（Microsoft Device Flow 不需要 Client Secret）。
 *
 * 备份文件存放在 OneDrive AppFolder 下的 shmtu-backup/ 子目录。
 *
 * 如果 Client ID 未配置，isConfigured() 返回 false，UI 上层会禁用。
 */
@Singleton
class OneDriveBackupProvider @Inject constructor(
    private val baseOkHttpClient: OkHttpClient
) : CloudBackupProvider {

    override val providerId: String = "onedrive"
    override val displayName: String = "OneDrive"

    private val tag = "OneDriveBackup"
    private var config: OneDriveConfig? = null
    private var credentials: OAuthCredentials? = null

    private val client: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val deviceFlow = DeviceFlowClient(
        client,
        DeviceFlowClient.MICROSOFT_DEVICE_CODE_URL,
        DeviceFlowClient.MICROSOFT_TOKEN_URL
    )

    fun configure(config: OneDriveConfig, credentials: OAuthCredentials? = null) {
        this.config = config
        this.credentials = credentials
    }

    fun isConfigured(): Boolean = config?.clientId?.isNotBlank() == true

    fun isLoggedIn(): Boolean = credentials?.isValid() == true

    suspend fun startDeviceFlow(): Result<DeviceFlowDisplayInfo> = runCatching {
        val cfg = config ?: throw IllegalStateException("OneDrive 未配置")
        if (!isConfigured()) {
            throw IllegalStateException("Client ID 未填写，请在设置中先配置")
        }
        val resp = deviceFlow.requestDeviceCode(
            clientId = cfg.clientId,
            clientSecret = null,
            scope = "offline_access Files.ReadWrite.AppFolder User.Read"
        ).getOrThrow()
        DeviceFlowDisplayInfo(
            userCode = resp.userCode,
            verificationUrl = resp.resolveVerificationUrl(),
            expiresInSec = resp.expiresIn
        )
    }

    suspend fun completeDeviceFlowAsync(): Result<OAuthCredentials> = runCatching {
        val cfg = config ?: throw IllegalStateException("OneDrive 未配置")
        val resp = deviceFlow.runDeviceFlow(
            clientId = cfg.clientId,
            clientSecret = null,
            scope = "offline_access Files.ReadWrite.AppFolder User.Read"
        )
        val token = resp.getOrThrow()
        val creds = token.toCredentials()
        credentials = creds
        creds
    }

    private fun TokenResponse.toCredentials(): OAuthCredentials = OAuthCredentials(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        expiresAt = if (expiresIn != null) System.currentTimeMillis() + expiresIn * 1000L else 0L,
        scope = scope
    )

    private suspend fun ensureValidToken(): Result<String> {
        val creds = credentials ?: return Result.failure(IllegalStateException("未登录 OneDrive"))
        if (creds.isValid()) return Result.success(creds.accessToken)
        val cfg = config ?: return Result.failure(IllegalStateException("未配置"))
        val refresh = creds.refreshToken ?: return Result.failure(IllegalStateException("缺少 refresh_token，请重新登录"))
        val newToken = deviceFlow.refreshAccessToken(cfg.clientId, null, refresh)
            .getOrElse { return Result.failure(it) }
        val newCreds = newToken.toCredentials().copy(refreshToken = creds.refreshToken)
        credentials = newCreds
        return Result.success(newCreds.accessToken)
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || !isLoggedIn()) return@withContext false
        ensureValidToken().map { token ->
            val req = Request.Builder()
                .url("https://graph.microsoft.com/v1.0/me/drive/special/approot")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    override suspend fun upload(remotePath: String, data: ByteArray): CloudUploadResult =
        withContext(Dispatchers.IO) {
            val token = ensureValidToken().getOrThrow()
            val folderName = config?.folderName?.takeIf { it.isNotBlank() } ?: "shmtu-backup"
            val fileName = remotePath.substringAfterLast('/')
            val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/" +
                "$folderName/$fileName:/content"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .put(data.toRequestBody("application/octet-stream".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw RuntimeException("OneDrive upload HTTP ${resp.code}: ${resp.body?.string()}")
                }
                val body = resp.body?.string().orEmpty()
                val id = extractJsonString(body, "id") ?: "NEW_ID"
                CloudUploadResult(
                    remotePath = id,
                    remoteUrl = "https://onedrive.live.com/?id=$id",
                    bytes = data.size.toLong(),
                    uploadedAt = System.currentTimeMillis()
                )
            }
        }

    override suspend fun download(remotePath: String): ByteArray = withContext(Dispatchers.IO) {
        val token = ensureValidToken().getOrThrow()
        val url = "https://graph.microsoft.com/v1.0/me/drive/items/$remotePath/content"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("OneDrive download HTTP ${resp.code}")
            }
            resp.body?.bytes() ?: throw RuntimeException("Empty response body")
        }
    }

    override suspend fun list(prefix: String): List<CloudBackupMeta> = withContext(Dispatchers.IO) {
        val token = ensureValidToken().getOrElse { return@withContext emptyList() }
        val folderName = config?.folderName?.takeIf { it.isNotBlank() } ?: "shmtu-backup"
        val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/" +
            "$folderName:/children"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(tag, "OneDrive list HTTP ${resp.code}")
                return@withContext emptyList()
            }
            val raw = resp.body?.string().orEmpty()
            parseOneDriveList(raw)
        }
    }

    override suspend fun delete(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val token = ensureValidToken().getOrElse { return@withContext false }
        val url = "https://graph.microsoft.com/v1.0/me/drive/items/$remotePath"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    private fun parseOneDriveList(json: String): List<CloudBackupMeta> {
        val results = mutableListOf<CloudBackupMeta>()
        val valueStart = json.indexOf("\"value\":[")
        if (valueStart < 0) return emptyList()
        val valueBlock = json.substring(valueStart + 8)
        val itemRegex = Regex("""\{[^{}]*?"id"\s*:\s*"[^"]+"[^{}]*?\}""")
        for (match in itemRegex.findAll("[$valueBlock]")) {
            val body = match.value
            val id = extractJsonString(body, "id") ?: continue
            val name = extractJsonString(body, "name") ?: continue
            val size = extractJsonNumber(body, "size") ?: 0L
            val modTime = extractJsonString(body, "lastModifiedDateTime") ?: ""
            results.add(CloudBackupMeta(
                remotePath = id,
                name = name,
                size = size,
                lastModified = parseRfc3339Millis(modTime)
            ))
        }
        return results.sortedByDescending { it.lastModified }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonNumber(json: String, key: String): Long? {
        val regex = Regex(""""$key"\s*:\s*(\d+)""")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun parseRfc3339Millis(text: String): Long = try {
        java.time.Instant.parse(text).toEpochMilli()
    } catch (_: Exception) { 0L }
}

data class OneDriveConfig(
    val clientId: String,
    val folderName: String = "shmtu-backup"
)
