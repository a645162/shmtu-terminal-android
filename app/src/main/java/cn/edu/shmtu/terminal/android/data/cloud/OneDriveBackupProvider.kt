package cn.edu.shmtu.terminal.android.data.cloud

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OneDrive 备份 Provider（占位实现）
 *
 * 依赖 Microsoft Graph API + OAuth2 (Azure AD)。
 * 当前未集成 MSAL/OAuth 流程，需要：
 * 1. 注册 Azure AD 应用
 * 2. 集成 MSAL 或 AppAuth 库
 * 3. 申请 Files.ReadWrite scope
 */
@Singleton
class OneDriveBackupProvider @Inject constructor(
    private val baseOkHttpClient: OkHttpClient
) : CloudBackupProvider {

    override val providerId: String = "onedrive"
    override val displayName: String = "OneDrive"

    private val tag = "OneDriveBackup"
    private var config: OneDriveConfig? = null

    private val client: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun configure(config: OneDriveConfig) { this.config = config }

    override suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        Log.w(tag, "OneDrive testConnection: not implemented (OAuth required)")
        false
    }

    override suspend fun upload(remotePath: String, data: ByteArray): CloudUploadResult =
        withContext(Dispatchers.IO) {
            throw NotImplementedError("OneDrive upload requires Microsoft Graph OAuth2 access token")
        }

    override suspend fun download(remotePath: String): ByteArray = withContext(Dispatchers.IO) {
        throw NotImplementedError("OneDrive download requires Microsoft Graph OAuth2")
    }

    override suspend fun list(prefix: String): List<CloudBackupMeta> = withContext(Dispatchers.IO) { emptyList() }

    override suspend fun delete(remotePath: String): Boolean = withContext(Dispatchers.IO) { false }
}

data class OneDriveConfig(
    val accessToken: String,
    val refreshToken: String? = null,
    val clientId: String? = null,
    val folderPath: String = ""
)
