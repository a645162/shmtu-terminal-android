package cn.edu.shmtu.terminal.android.data.cloud

import java.io.InputStream

/**
 * 云备份 Provider 接口
 *
 * 所有云存储后端（WebDAV / Google Drive / OneDrive）实现此接口。
 * 新增 Provider 只需实现此接口并在 DI 中注册。
 */
interface CloudBackupProvider {

    /** Provider 唯一 ID，如 "webdav", "google_drive", "onedrive" */
    val providerId: String

    /** Provider 显示名称 */
    val displayName: String

    /**
     * 测试连接 + 认证
     */
    suspend fun testConnection(): Boolean

    /**
     * 上传备份文件
     */
    suspend fun upload(remotePath: String, data: ByteArray): CloudUploadResult

    /**
     * 下载备份文件
     */
    suspend fun download(remotePath: String): ByteArray

    /**
     * 列出指定前缀下的所有备份文件
     */
    suspend fun list(prefix: String): List<CloudBackupMeta>

    /**
     * 删除指定备份
     */
    suspend fun delete(remotePath: String): Boolean

    /**
     * 获取当前账号的存储配额信息（可选实现）
     */
    suspend fun getQuota(): CloudQuota? = null
}

data class CloudUploadResult(
    val remotePath: String,
    val remoteUrl: String?,
    val bytes: Long,
    val versionId: String? = null,
    val uploadedAt: Long = System.currentTimeMillis()
)

data class CloudBackupMeta(
    val remotePath: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val versionId: String? = null,
    val checksum: String? = null
)

data class CloudQuota(
    val used: Long,
    val total: Long
) {
    val percentUsed: Float get() = if (total > 0) used.toFloat() / total else 0f
    val available: Long get() = (total - used).coerceAtLeast(0L)
}
