package cn.edu.shmtu.terminal.android.domain.repository

import cn.edu.shmtu.terminal.android.domain.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * Session Repository 接口
 */
interface SessionRepository {
    fun observeSession(accountId: String): Flow<Session?>
    suspend fun getSession(accountId: String): Session?
    suspend fun saveSession(accountId: String, cookiesJson: String, loginTime: String? = null, expireTime: String? = null): Long
    suspend fun updateCookies(accountId: String, cookiesJson: String)
    suspend fun getDecryptedCookies(accountId: String): String?
    suspend fun invalidateSession(accountId: String)
    suspend fun deleteSession(accountId: String)
    suspend fun clearInvalidSessions()
    suspend fun getAllValidSessions(): List<Session>
}
