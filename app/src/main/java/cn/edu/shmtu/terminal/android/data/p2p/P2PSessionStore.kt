package cn.edu.shmtu.terminal.android.data.p2p

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class P2PSessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("p2p_sessions", Context.MODE_PRIVATE)

    fun loadSessions(): List<P2PSession> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            p2pJson.decodeFromString(PersistedSessions.serializer(), raw)
                .sessions
                .map { it.toModel() }
        }.getOrDefault(emptyList())
    }

    fun saveSessions(sessions: Collection<P2PSession>) {
        val payload = PersistedSessions(
            sessions = sessions
                .filter { it.isPaired && !it.pairCode.isNullOrBlank() }
                .map { PersistedSession.fromModel(it.copy(isConnected = false)) }
        )
        prefs.edit()
            .putString(KEY, p2pJson.encodeToString(PersistedSessions.serializer(), payload))
            .apply()
    }

    @Serializable
    private data class PersistedSessions(
        val sessions: List<PersistedSession> = emptyList()
    )

    @Serializable
    private data class PersistedSession(
        val sessionId: String,
        val remoteDevice: String,
        val remoteAddr: String,
        val remotePort: Int,
        val pairCode: String,
        val reconnectIps: List<String> = emptyList(),
        val reconnectPort: Int? = null,
        val isLocallyInitiated: Boolean = false,
        val createdAt: Long
    ) {
        fun toModel(): P2PSession = P2PSession(
            sessionId = sessionId,
            remoteDevice = remoteDevice,
            remoteAddr = remoteAddr,
            remotePort = remotePort,
            pairCode = pairCode,
            reconnectIps = reconnectIps,
            reconnectPort = reconnectPort,
            isLocallyInitiated = isLocallyInitiated,
            isPaired = true,
            isConnected = false,
            createdAt = createdAt
        )

        companion object {
            fun fromModel(session: P2PSession): PersistedSession = PersistedSession(
                sessionId = session.sessionId,
                remoteDevice = session.remoteDevice,
                remoteAddr = session.remoteAddr,
                remotePort = session.remotePort,
                pairCode = session.pairCode ?: "",
                reconnectIps = session.reconnectIps,
                reconnectPort = session.reconnectPort,
                isLocallyInitiated = session.isLocallyInitiated,
                createdAt = session.createdAt
            )
        }
    }

    companion object {
        private const val KEY = "paired_sessions"
    }
}
