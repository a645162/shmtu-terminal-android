package cn.edu.shmtu.terminal.android.data.cloud

import android.util.Log
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
 * Google Drive 备份 Provider（占位实现）
 *
 * 依赖 Google Drive REST API v3 + OAuth2。
 * OAuth 流程需要 Client ID 和 access token。
 * 当前实现未启用，调用 API 会抛出 NotImplementedError。
 */
@Singleton
class GoogleDriveBackupProvider @Inject constructor(
    private val baseOkHttpClient: OkHttpClient
) : CloudBackupProvider {

    override val providerId: String = "google_drive"
    override val displayName: String = "Google Drive"

    private val tag = "GoogleDriveBackup"
    private var config: GoogleDriveConfig? = null

    private val client: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun configure(config: GoogleDriveConfig) { this.config = config }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        Log.w(tag, "Google Drive testConnection: not implemented (OAuth required)")
        false
    }

    override suspend fun upload(remotePath: String, data: ByteArray): CloudUploadResult =
        withContext(Dispatchers.IO) {
            throw NotImplementedError("Google Drive upload requires OAuth2 client ID and access token")
        }

    override suspend fun download(remotePath: String): ByteArray = withContext(Dispatchers.IO) {
        throw NotImplementedError("Google Drive download requires OAuth2")
    }

    override suspend fun list(prefix: String): List<CloudBackupMeta> = withContext(Dispatchers.IO) { emptyList() }

    override suspend fun delete(remotePath: String): Boolean = withContext(Dispatchers.IO) { false }
}

data class GoogleDriveConfig(
    val accessToken: String,
    val refreshToken: String? = null,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val folderId: String? = null
)
