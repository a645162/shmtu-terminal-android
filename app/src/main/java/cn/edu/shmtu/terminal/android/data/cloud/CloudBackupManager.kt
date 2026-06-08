package cn.edu.shmtu.terminal.android.data.cloud

import android.util.Log
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.domain.usecase.export.TransferArchiveService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 云备份管理器
 * 统一管理多 Provider、加密、增量、多版本
 */
@Singleton
class CloudBackupManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val transferArchiveService: TransferArchiveService,
    private val webDavProvider: WebDavBackupProvider,
    private val googleDriveProvider: GoogleDriveBackupProvider,
    private val oneDriveProvider: OneDriveBackupProvider
) {
    private val tag = "CloudBackupManager"

    private val providers: Map<String, CloudBackupProvider> = mapOf(
        webDavProvider.providerId to webDavProvider,
        googleDriveProvider.providerId to googleDriveProvider,
        oneDriveProvider.providerId to oneDriveProvider
    )

    private val _backupHistory = MutableStateFlow<List<CloudBackupRecord>>(emptyList())
    val backupHistory: StateFlow<List<CloudBackupRecord>> = _backupHistory.asStateFlow()

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    fun availableProviders(): List<CloudBackupProvider> = providers.values.toList()

    fun getProvider(providerId: String): CloudBackupProvider? = providers[providerId]

    suspend fun backupNow(
        providerId: String,
        password: String?,
        identityIds: Set<Long>? = null
    ): Result<CloudUploadResult> {
        val provider = getProvider(providerId) ?: return Result.failure(
            IllegalArgumentException("Unknown provider: $providerId")
        )
        return try {
            _backupStatus.value = BackupStatus.Preparing("正在打包账单...")
            val archive = transferArchiveService.buildArchiveBytes(identityIds)
            val rawBytes = archive.bytes

            val finalBytes: ByteArray
            val encryptionInfo: String?
            if (!password.isNullOrBlank()) {
                _backupStatus.value = BackupStatus.Preparing("正在加密...")
                val encrypted = encryptBytes(rawBytes, password)
                finalBytes = encrypted.bytes
                encryptionInfo = "AES-256-GCM"
            } else {
                finalBytes = rawBytes
                encryptionInfo = null
            }

            val providerPrefix = getProviderPrefix(providerId)
            val remoteList = provider.list(providerPrefix)
            val localChecksum = sha256(finalBytes)
            val isIncremental = remoteList.firstOrNull()?.checksum == localChecksum
            if (isIncremental) {
                Log.i(tag, "No changes detected, skip upload")
                _backupStatus.value = BackupStatus.Idle
                return Result.success(CloudUploadResult(
                    remotePath = remoteList.first().remotePath,
                    remoteUrl = null,
                    bytes = 0,
                    uploadedAt = System.currentTimeMillis()
                ))
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(Date())
            val ext = if (encryptionInfo != null) "zip.enc" else "zip"
            val fileName = "shmtu-backup-$timestamp.$ext"
            val remotePath = "$providerPrefix/$fileName"

            _backupStatus.value = BackupStatus.Uploading(0L, finalBytes.size.toLong())
            val result = provider.upload(remotePath, finalBytes)
            _backupStatus.value = BackupStatus.Success(result)

            val record = CloudBackupRecord(
                providerId = providerId,
                remotePath = remotePath,
                fileName = fileName,
                size = result.bytes,
                uploadedAt = result.uploadedAt,
                checksum = localChecksum,
                encrypted = encryptionInfo != null,
                identityCount = archive.identityCount,
                billCount = archive.billCount
            )
            _backupHistory.value = _backupHistory.value + record
            saveHistory()

            pruneOldBackups(provider, providerPrefix, keepCount = 10)
            Result.success(result)
        } catch (e: Exception) {
            Log.e(tag, "backupNow failed", e)
            _backupStatus.value = BackupStatus.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(
        providerId: String,
        record: CloudBackupRecord,
        password: String?
    ): Result<RestoreReport> {
        val provider = getProvider(providerId) ?: return Result.failure(
            IllegalArgumentException("Unknown provider")
        )
        return try {
            _backupStatus.value = BackupStatus.Preparing("正在下载...")
            val encrypted = provider.download(record.remotePath)
            val plainBytes = if (record.encrypted) {
                if (password.isNullOrBlank()) {
                    return Result.failure(IllegalStateException("备份已加密，请提供密码"))
                }
                decryptBytes(encrypted, password).bytes
            } else {
                encrypted
            }

            _backupStatus.value = BackupStatus.Preparing("正在导入...")
            val importResult = transferArchiveService.importArchiveBytes(plainBytes, password ?: "")
            _backupStatus.value = BackupStatus.Idle
            Result.success(RestoreReport(
                identities = importResult.identityCount,
                accounts = importResult.accountCount,
                bills = importResult.billCount
            ))
        } catch (e: Exception) {
            Log.e(tag, "restoreBackup failed", e)
            _backupStatus.value = BackupStatus.Failed(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    suspend fun testConnection(providerId: String): Boolean {
        val provider = getProvider(providerId) ?: return false
        return provider.testConnection()
    }

    fun configureWebDav(config: WebDavConfig) {
        webDavProvider.configure(config)
        settingsDataStore.setCloudBackupConfig(
            providerId = "webdav",
            serverUrl = config.serverUrl,
            username = config.username,
            backupRoot = config.backupRoot
        )
    }

    fun restoreConfig() {
        val providerId = settingsDataStore.getCloudBackupProviderId() ?: return
        if (providerId == "webdav") {
            val url = settingsDataStore.getCloudBackupServerUrl() ?: return
            val user = settingsDataStore.getCloudBackupUsername() ?: return
            val pass = settingsDataStore.getCloudBackupPassword() ?: return
            val root = settingsDataStore.getCloudBackupRoot().ifBlank { "shmtu-backup" }
            webDavProvider.configure(WebDavConfig(url, user, pass, root))
        }
    }

    fun restoreWebDavServerUrl(): String = settingsDataStore.getCloudBackupServerUrl().orEmpty()
    fun restoreWebDavUsername(): String = settingsDataStore.getCloudBackupUsername().orEmpty()
    fun restoreWebDavRoot(): String = settingsDataStore.getCloudBackupRoot()

    private fun getProviderPrefix(providerId: String): String =
        settingsDataStore.getCloudBackupRoot().ifBlank { "shmtu-backup" }

    private suspend fun pruneOldBackups(provider: CloudBackupProvider, prefix: String, keepCount: Int) {
        try {
            val list = provider.list(prefix)
            if (list.size > keepCount) {
                list.drop(keepCount).forEach { provider.delete(it.remotePath) }
            }
        } catch (e: Exception) {
            Log.w(tag, "pruneOldBackups failed", e)
        }
    }

    private fun saveHistory() {
        settingsDataStore.setCloudBackupHistory(_backupHistory.value)
    }

    fun loadHistory() {
        _backupHistory.value = settingsDataStore.getCloudBackupHistory()
    }

    private fun encryptBytes(plain: ByteArray, password: String): EncryptedPayload {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plain)
        key.fill(0)
        val out = ByteArrayOutputStream()
        out.write(salt); out.write(iv); out.write(ct)
        return EncryptedPayload(out.toByteArray(), "AES-256-GCM")
    }

    private fun decryptBytes(encrypted: ByteArray, password: String): EncryptedPayload {
        val salt = encrypted.copyOfRange(0, 16)
        val iv = encrypted.copyOfRange(16, 28)
        val ct = encrypted.copyOfRange(28, encrypted.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(ct)
        key.fill(0)
        return EncryptedPayload(plain, "AES-256-GCM")
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 100_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun sha256(data: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(data)
        return d.joinToString("") { "%02x".format(it) }.take(16)
    }
}

data class EncryptedPayload(val bytes: ByteArray, val algorithm: String)

@kotlinx.serialization.Serializable
data class CloudBackupRecord(
    val providerId: String,
    val remotePath: String,
    val fileName: String,
    val size: Long,
    val uploadedAt: Long,
    val checksum: String,
    val encrypted: Boolean,
    val identityCount: Int,
    val billCount: Int
)

data class RestoreReport(
    val identities: Int,
    val accounts: Int,
    val bills: Int
)

sealed class BackupStatus {
    object Idle : BackupStatus()
    data class Preparing(val message: String) : BackupStatus()
    data class Uploading(val transferred: Long, val total: Long) : BackupStatus()
    data class Success(val result: CloudUploadResult) : BackupStatus()
    data class Failed(val reason: String) : BackupStatus()
}
