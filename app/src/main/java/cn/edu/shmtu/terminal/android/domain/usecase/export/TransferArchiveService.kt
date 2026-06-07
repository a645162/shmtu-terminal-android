package cn.edu.shmtu.terminal.android.domain.usecase.export

import cn.edu.shmtu.terminal.android.data.local.db.BillDatabaseManager
import cn.edu.shmtu.terminal.android.data.local.db.dao.AccountDao
import cn.edu.shmtu.terminal.android.data.local.db.dao.IdentityDao
import cn.edu.shmtu.terminal.android.data.local.db.entity.AccountEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.BillEntity
import cn.edu.shmtu.terminal.android.data.local.db.entity.IdentityEntity
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.KeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferArchiveService @Inject constructor(
    private val identityDao: IdentityDao,
    private val accountDao: AccountDao,
    private val accountRepository: AccountRepository,
    private val billDbManager: BillDatabaseManager
) {
    data class ArchivePayload(
        val bytes: ByteArray,
        val identityCount: Int,
        val accountCount: Int,
        val billCount: Int,
        val encrypted: Boolean
    )

    data class ArchiveImportResult(
        val identityCount: Int,
        val accountCount: Int,
        val billCount: Int
    )

    suspend fun buildArchiveBytes(identityIds: Set<Long>? = null): ArchivePayload {
        val exportIdentities = exportIdentities(identityIds)
        val payload = JSONObject().apply {
            put("schema_version", 1)
            put("export_time", System.currentTimeMillis())
            put("format", "shmtu-transfer-archive")
            put("identities", exportIdentities.payload)
        }.toString(2)

        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(payload.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return ArchivePayload(
            bytes = zipOut.toByteArray(),
            identityCount = exportIdentities.identityCount,
            accountCount = exportIdentities.accountCount,
            billCount = exportIdentities.billCount,
            encrypted = false
        )
    }

    suspend fun buildEncryptedArchiveBytes(password: String?, identityIds: Set<Long>? = null): ArchivePayload {
        val zipPayload = buildArchiveBytes(identityIds)
        return if (password.isNullOrBlank()) {
            zipPayload
        } else {
            zipPayload.copy(
                bytes = encryptArchive(zipPayload.bytes, password),
                encrypted = true
            )
        }
    }

    suspend fun importArchiveBytes(data: ByteArray, password: String?): ArchiveImportResult {
        val plainBytes = if (isEncryptedArchive(data)) {
            decryptArchive(data, password ?: throw IllegalArgumentException("缺少解密密码"))
        } else {
            data
        }

        val manifestJson = unzipManifest(plainBytes)
        val root = JSONObject(manifestJson)
        val identities = root.optJSONArray("identities")
            ?: throw IllegalArgumentException("数据包缺少 identities")

        var importedIdentityCount = 0
        var importedAccountCount = 0
        var importedBillCount = 0

        for (i in 0 until identities.length()) {
            val identityObj = identities.optJSONObject(i) ?: continue
            val (identityId, identityImported) = upsertIdentity(identityObj)
            if (identityImported) {
                importedIdentityCount++
            }
            val accountResult = upsertAccounts(identityId, identityObj.optJSONArray("accounts"))
            importedAccountCount += accountResult.importedCount
            importedBillCount += importBills(identityId, accountResult.mapping, identityObj.optJSONArray("bills"))
        }

        return ArchiveImportResult(
            identityCount = importedIdentityCount,
            accountCount = importedAccountCount,
            billCount = importedBillCount
        )
    }

    private suspend fun exportIdentities(identityIds: Set<Long>?): ExportArchiveStats {
        val identities = identityDao.getAllIdentities().first()
            .filter { identityIds == null || it.id in identityIds }
        val result = JSONArray()
        var accountCount = 0
        var billCount = 0

        for (identity in identities) {
            val accounts = accountDao.getAccountsByIdentity(identity.id).first()
            val accountById = accounts.associateBy { it.id }
            val bills = billDbManager.getIdentityDatabase(identity.id).billDao().getAllBills().first()
            accountCount += accounts.size
            billCount += bills.size

            result.put(
                JSONObject().apply {
                    put("identity", identity.toJson())
                    put("accounts", JSONArray().apply {
                        accounts.forEach { account ->
                            put(
                                JSONObject().apply {
                                    put("entity", account.toJson())
                                    put("password", accountRepository.getPassword(account.id))
                                }
                            )
                        }
                    })
                    put("bills", JSONArray().apply {
                        bills.forEach { bill ->
                            put(
                                bill.toJson(
                                    sourceUserId = accountById[bill.accountId]?.userId.orEmpty()
                                )
                            )
                        }
                    })
                }
            )
        }

        return ExportArchiveStats(
            payload = result,
            identityCount = identities.size,
            accountCount = accountCount,
            billCount = billCount
        )
    }

    private suspend fun upsertIdentity(identityBundle: JSONObject): Pair<Long, Boolean> {
        val identityJson = identityBundle.optJSONObject("identity")
            ?: throw IllegalArgumentException("identity 节点缺失")
        val username = identityJson.optString("username")
        require(username.isNotBlank()) { "identity.username 不能为空" }

        val existing = identityDao.getByUsername(username)
        val entity = IdentityEntity(
            id = existing?.id ?: 0L,
            username = username,
            remark = identityJson.optString("remark", ""),
            birthday = identityJson.optString("birthday", ""),
            enrollmentDate = identityJson.optString("enrollment_date", ""),
            graduationDate = identityJson.optString("graduation_date", ""),
            displayOrder = identityJson.optInt("display_order", 0),
            createdAt = identityJson.optLong("created_at", System.currentTimeMillis())
        )

        val current = existing ?: run {
            val insertedId = identityDao.insert(entity)
            if (insertedId != -1L) {
                return insertedId to true
            }
            identityDao.getByUsername(username)
                ?: throw IllegalArgumentException("identity 插入后回查失败: $username")
        }

        identityDao.update(entity.copy(id = current.id))
        return current.id to false
    }

    private suspend fun upsertAccounts(
        identityId: Long,
        accountsJson: JSONArray?
    ): AccountImportResult {
        val mapping = linkedMapOf<String, Long>()
        if (accountsJson == null) return AccountImportResult(mapping, 0)

        val existingAccounts = accountDao.getAccountsByIdentity(identityId).first()
        val byUserId = existingAccounts.associateBy { it.userId }
        var importedCount = 0

        for (i in 0 until accountsJson.length()) {
            val accountBundle = accountsJson.optJSONObject(i) ?: continue
            val entityJson = accountBundle.optJSONObject("entity") ?: continue
            val userId = entityJson.optString("user_id")
            if (userId.isBlank()) continue

            val existing = byUserId[userId]
            val entity = AccountEntity(
                id = existing?.id ?: 0L,
                identityId = identityId,
                label = entityJson.optString("label", userId),
                userId = userId,
                accountType = entityJson.optString("account_type", "EPAY"),
                loginStatus = entityJson.optString("login_status", "LOGGED_OUT"),
                lastSyncTime = entityJson.optLong("last_sync_time").takeIf { it > 0 },
                displayOrder = entityJson.optInt("display_order", 0),
                createdAt = entityJson.optLong("created_at", System.currentTimeMillis())
            )

            val current = existing ?: run {
                val insertedId = accountDao.insert(entity)
                if (insertedId != -1L) {
                    importedCount++
                    mapping[userId] = insertedId
                    accountBundle.optString("password").takeIf { it.isNotBlank() }?.let { password ->
                        accountRepository.savePassword(insertedId, password)
                    }
                    continue
                }
                accountDao.getAccountsByIdentity(identityId).first().firstOrNull { it.userId == userId }
                    ?: throw IllegalArgumentException("account 插入后回查失败: $userId")
            }

            accountDao.updateAccount(current.id, entity.label, entity.userId)
            accountDao.updateLoginStatus(current.id, entity.loginStatus)
            accountDao.updateAccountMetadata(
                id = current.id,
                accountType = entity.accountType,
                displayOrder = entity.displayOrder,
                createdAt = entity.createdAt
            )
            if (entity.lastSyncTime != null) {
                accountDao.updateLastSyncTime(current.id, entity.lastSyncTime)
            }
            val accountId = current.id

            accountBundle.optString("password").takeIf { it.isNotBlank() }?.let { password ->
                accountRepository.savePassword(accountId, password)
            }

            mapping[userId] = accountId
        }

        return AccountImportResult(mapping, importedCount)
    }

    private suspend fun importBills(
        identityId: Long,
        accountMapping: Map<String, Long>,
        billsJson: JSONArray?
    ): Int {
        if (billsJson == null || billsJson.length() == 0) return 0

        val dao = billDbManager.getIdentityDatabase(identityId).billDao()
        val entities = mutableListOf<BillEntity>()

        for (i in 0 until billsJson.length()) {
            val billJson = billsJson.optJSONObject(i) ?: continue
            val sourceUserId = billJson.optString("source_user_id", "")
            val mappedAccountId = accountMapping[sourceUserId] ?: REMOTE_ACCOUNT_ID
            entities += BillEntity(
                accountId = mappedAccountId,
                accountLabel = billJson.optString("account_label", sourceUserId.ifBlank { "导入" }),
                dateStr = billJson.optString("date_str", ""),
                timeStr = billJson.optString("time_str", ""),
                dateTimeStrFormat = billJson.optString("date_time_formatted", ""),
                type = billJson.optString("item_type", ""),
                transactionNo = billJson.optString("number", "").ifBlank {
                    "import_${sha256Short("$identityId-$i-${System.nanoTime()}")}"
                },
                targetUser = billJson.optString("target_user", ""),
                money = billJson.optString("money_str", "0"),
                method = billJson.optString("method", ""),
                status = billJson.optString("status_str", "SUCCESS"),
                position = billJson.optString("position", "").ifBlank { null },
                room = billJson.optString("room", "").ifBlank { null },
                notes = billJson.optString("notes", "").ifBlank { null },
                category = billJson.optString("category", "").ifBlank { null },
                building = billJson.optString("building", "").ifBlank { null }
            )
        }

        return dao.insertAll(entities).count { it != -1L }
    }

    private fun unzipManifest(data: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(data)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    return zis.readBytes().toString(Charsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw IllegalArgumentException("压缩包中未找到 manifest.json")
    }

    private fun encryptArchive(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_SIZE).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, deriveSecretKey(password, salt), GCMParameterSpec(TAG_SIZE_BITS, iv))
        val encrypted = cipher.doFinal(data)

        val out = ByteArrayOutputStream()
        out.write(ENCRYPTED_MAGIC)
        out.write(salt)
        out.write(iv)
        out.write(encrypted)
        return out.toByteArray()
    }

    private fun decryptArchive(data: ByteArray, password: String): ByteArray {
        require(isEncryptedArchive(data)) { "不是加密压缩包" }
        require(data.size > ENCRYPTED_MAGIC.size + SALT_SIZE + IV_SIZE) { "加密压缩包损坏" }

        val saltStart = ENCRYPTED_MAGIC.size
        val ivStart = saltStart + SALT_SIZE
        val cipherStart = ivStart + IV_SIZE
        val salt = data.copyOfRange(saltStart, ivStart)
        val iv = data.copyOfRange(ivStart, cipherStart)
        val encrypted = data.copyOfRange(cipherStart, data.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, deriveSecretKey(password, salt), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    fun isEncryptedArchive(data: ByteArray): Boolean {
        return data.size >= ENCRYPTED_MAGIC.size &&
            data.copyOfRange(0, ENCRYPTED_MAGIC.size).contentEquals(ENCRYPTED_MAGIC)
    }

    private fun deriveSecretKey(password: String, salt: ByteArray) =
        SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(
            PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        ).let { javax.crypto.spec.SecretKeySpec(it.encoded, "AES") }

    private fun IdentityEntity.toJson(): JSONObject = JSONObject().apply {
        put("username", username)
        put("remark", remark)
        put("birthday", birthday)
        put("enrollment_date", enrollmentDate)
        put("graduation_date", graduationDate)
        put("display_order", displayOrder)
        put("created_at", createdAt)
    }

    private fun AccountEntity.toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        put("user_id", userId)
        put("account_type", accountType)
        put("login_status", loginStatus)
        put("last_sync_time", lastSyncTime ?: 0L)
        put("display_order", displayOrder)
        put("created_at", createdAt)
    }

    private fun BillEntity.toJson(sourceUserId: String): JSONObject = JSONObject().apply {
        put("account_label", accountLabel)
        put("source_user_id", sourceUserId)
        put("date_str", dateStr)
        put("time_str", timeStr)
        put("date_time_formatted", dateTimeStrFormat)
        put("item_type", type)
        put("number", transactionNo)
        put("target_user", targetUser)
        put("money_str", money)
        put("method", method)
        put("status_str", status)
        put("position", position)
        put("room", room)
        put("notes", notes)
        put("category", category)
        put("building", building)
    }

    private fun sha256Short(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private companion object {
        const val REMOTE_ACCOUNT_ID = -1L
        val ENCRYPTED_MAGIC = byteArrayOf('S'.code.toByte(), 'H'.code.toByte(), 'A'.code.toByte(), 'R'.code.toByte())
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val PBKDF2_ITERATIONS = 100_000
        const val KEY_SIZE_BITS = 256
        const val TAG_SIZE_BITS = 128
        const val SALT_SIZE = 16
        const val IV_SIZE = 12
        val secureRandom = SecureRandom()
    }

    private data class ExportArchiveStats(
        val payload: JSONArray,
        val identityCount: Int,
        val accountCount: Int,
        val billCount: Int
    )

    private data class AccountImportResult(
        val mapping: Map<String, Long>,
        val importedCount: Int
    )
}
