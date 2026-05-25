package cn.edu.shmtu.terminal.android.data.repository

import cn.edu.shmtu.terminal.android.data.local.db.dao.SessionDao
import cn.edu.shmtu.terminal.android.data.local.db.entity.SessionEntity
import cn.edu.shmtu.terminal.android.data.local.security.EncryptionService
import cn.edu.shmtu.terminal.android.domain.model.Session
import cn.edu.shmtu.terminal.android.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val encryptionService: EncryptionService
) : SessionRepository {

    override fun observeSession(accountId: String): Flow<Session?> {
        return sessionDao.observeByAccountId(accountId).map { it?.toDomain() }
    }

    override suspend fun getSession(accountId: String): Session? {
        return sessionDao.getByAccountId(accountId)?.toDomain()
    }

    override suspend fun saveSession(accountId: String, cookiesJson: String, loginTime: String?, expireTime: String?): Long {
        val encryptedCookies = encryptionService.encryptCookie(cookiesJson)
        val now = Instant.now().toString()
        
        val session = SessionEntity(
            accountId = accountId,
            cookies = encryptedCookies,
            loginTime = loginTime ?: now,
            expireTime = expireTime,
            isValid = true
        )
        
        return sessionDao.insert(session)
    }

    override suspend fun updateCookies(accountId: String, cookiesJson: String) {
        val encryptedCookies = encryptionService.encryptCookie(cookiesJson)
        val now = Instant.now().toString()
        sessionDao.updateCookies(accountId, encryptedCookies, now)
    }

    override suspend fun getDecryptedCookies(accountId: String): String? {
        val session = sessionDao.getByAccountId(accountId) ?: return null
        return try {
            encryptionService.decryptCookie(session.cookies)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun invalidateSession(accountId: String) {
        sessionDao.invalidate(accountId)
    }

    override suspend fun deleteSession(accountId: String) {
        sessionDao.delete(accountId)
    }

    override suspend fun clearInvalidSessions() {
        sessionDao.deleteInvalid()
    }

    override suspend fun getAllValidSessions(): List<Session> {
        return sessionDao.getAllValid().map { it.toDomain() }
    }

    private fun SessionEntity.toDomain(): Session = Session(
        id = id,
        accountId = accountId,
        cookies = cookies,
        loginTime = loginTime,
        expireTime = expireTime,
        isValid = isValid
    )
}
