package cn.edu.shmtu.terminal.android.data.cloud

import android.util.Log
import cn.edu.shmtu.terminal.android.data.cloud.oauth.DeviceFlowClient
import cn.edu.shmtu.terminal.android.data.cloud.oauth.OAuthCredentials
import cn.edu.shmtu.terminal.android.data.cloud.oauth.TokenPollResult
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
 * Google Drive 备份 Provider（OAuth 2.0 Device Authorization Grant 实现）。
 *
 * 启用条件：必须在设置中填入 Client ID + Client Secret
 * （在 Google Cloud Console 创建 "TVs and Limited Input devices" 类型的 OAuth Client）。
 *
 * 如果 Client ID/Secret 未配置，isConfigured() 返回 false，
 * 上层（CloudBackupManager / UI）会禁用该 provider 的所有操作。
 */
@Singleton
class GoogleDriveBackupProvider @Inject constructor(
    private val baseOkHttpClient: OkHttpClient
) : CloudBackupProvider {

    override val providerId: String = "google_drive"
    override val displayName: String = "Google Drive"

    private val tag = "GoogleDriveBackup"
    private var config: GoogleDriveConfig? = null
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
        DeviceFlowClient.GOOGLE_DEVICE_CODE_URL,
        DeviceFlowClient.GOOGLE_TOKEN_URL
    )

    fun configure(config: GoogleDriveConfig, credentials: OAuthCredentials? = null) {
        this.config = config
        this.credentials = credentials
    }

    fun isConfigured(): Boolean {
        val cfg = config ?: return false
        return cfg.clientId.isNotBlank() && cfg.clientSecret.isNotBlank()
    }

    fun isLoggedIn(): Boolean = credentials?.isValid() == true

    /** 发起 Device Flow，返回用户需输入的 user_code 和 verification URL */
    suspend fun startDeviceFlow(): Result<DeviceFlowDisplayInfo> = runCatching {
        val cfg = config ?: throw IllegalStateException("Google Drive 未配置")
        if (!isConfigured()) {
            throw IllegalStateException("Client ID 或 Client Secret 未填写，请在设置中先配置")
        }
        val resp = deviceFlow.requestDeviceCode(
            clientId = cfg.clientId,
            clientSecret = cfg.clientSecret,
            scope = "https://www.googleapis.com/auth/drive.file"
        ).getOrThrow()
        DeviceFlowDisplayInfo(
            userCode = resp.userCode,
            verificationUrl = resp.resolveVerificationUrl(),
            expiresInSec = resp.expiresIn
        )
    }

    /**
     * 完整 Device Flow 流程：轮询直到用户授权成功或超时（默认 5 分钟）。
     * 成功后将凭据保存到内存（外层需要持久化到 SettingsDataStore）。
     */
    suspend fun completeDeviceFlowAsync(): Result<OAuthCredentials> = runCatching {
        val cfg = config ?: throw IllegalStateException("Google Drive 未配置")
        val resp = deviceFlow.runDeviceFlow(
            clientId = cfg.clientId,
            clientSecret = cfg.clientSecret,
            scope = "https://www.googleapis.com/auth/drive.file"
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
        val creds = credentials ?: return Result.failure(IllegalStateException("未登录 Google Drive"))
        if (creds.isValid()) return Result.success(creds.accessToken)
        val cfg = config ?: return Result.failure(IllegalStateException("未配置"))
        val refresh = creds.refreshToken ?: return Result.failure(IllegalStateException("缺少 refresh_token，请重新登录"))
        val newToken = deviceFlow.refreshAccessToken(cfg.clientId, cfg.clientSecret, refresh)
            .getOrElse { return Result.failure(it) }
        val newCreds = newToken.toCredentials().copy(refreshToken = creds.refreshToken)
        credentials = newCreds
        return Result.success(newCreds.accessToken)
    }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured() || !isLoggedIn()) return@withContext false
        ensureValidToken().map { token ->
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    override suspend fun upload(remotePath: String, data: ByteArray): CloudUploadResult =
        withContext(Dispatchers.IO) {
            val token = ensureValidToken().getOrThrow()
            val metadata = """{"name":"${remotePath.substringAfterLast('/')}"}"""
            val boundary = "----shmtu${System.currentTimeMillis()}"
            val multipart = buildString {
                append("--").append(boundary).append("\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadata).append("\r\n")
                append("--").append(boundary).append("\r\n")
                append("Content-Type: application/octet-stream\r\n\r\n")
            }.toByteArray() + data + "\r\n--$boundary--\r\n".toByteArray()

            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,size")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "multipart/related; boundary=$boundary")
                .post(multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw RuntimeException("Drive upload HTTP ${resp.code}: ${resp.body?.string()}")
                }
                CloudUploadResult(
                    remotePath = remotePath,
                    remoteUrl = "https://drive.google.com/file/d/NEW_ID/view",
                    bytes = data.size.toLong(),
                    uploadedAt = System.currentTimeMillis()
                )
            }
        }

    override suspend fun download(remotePath: String): ByteArray = withContext(Dispatchers.IO) {
        val token = ensureValidToken().getOrThrow()
        val fileId = remotePath
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("Drive download HTTP ${resp.code}")
            }
            resp.body?.bytes() ?: throw RuntimeException("Empty response body")
        }
    }

    override suspend fun list(prefix: String): List<CloudBackupMeta> = withContext(Dispatchers.IO) {
        val token = ensureValidToken().getOrElse { return@withContext emptyList() }
        val q = "name contains 'shmtu-backup-' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?q=${java.net.URLEncoder.encode(q, "UTF-8")}" +
            "&fields=files(id,name,size,modifiedTime)" +
            "&pageSize=100"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(tag, "Drive list HTTP ${resp.code}")
                return@withContext emptyList()
            }
            val raw = resp.body?.string().orEmpty()
            parseDriveFileList(raw)
        }
    }

    override suspend fun delete(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val token = ensureValidToken().getOrElse { return@withContext false }
        val fileId = remotePath
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        client.newCall(req).execute().use { it.isSuccessful }
    }

    private fun parseDriveFileList(json: String): List<CloudBackupMeta> {
        val results = mutableListOf<CloudBackupMeta>()
        val filesBlock = json.substringAfter("\"files\":[", "").substringBefore("]")
        if (filesBlock.isBlank()) return emptyList()
        val itemRegex = Regex("""\{([^}]+)\}""")
        for (match in itemRegex.findAll("[$filesBlock]")) {
            val body = match.value
            val id = extractJsonString(body, "id") ?: continue
            val name = extractJsonString(body, "name") ?: continue
            val size = extractJsonNumber(body, "size") ?: 0L
            val modTime = extractJsonString(body, "modifiedTime") ?: ""
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
        val regex = Regex(""""$key"\s*:\s*(\d+(?:\.\d+)?)""")
        return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun parseRfc3339Millis(text: String): Long = try {
        java.time.Instant.parse(text).toEpochMilli()
    } catch (_: Exception) { 0L }
}

data class DeviceFlowDisplayInfo(
    val userCode: String,
    val verificationUrl: String,
    val expiresInSec: Long
)

data class GoogleDriveConfig(
    val clientId: String,
    val clientSecret: String,
    val folderName: String = "shmtu-backup"
)
