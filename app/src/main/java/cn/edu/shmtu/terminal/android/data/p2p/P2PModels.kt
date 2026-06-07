package cn.edu.shmtu.terminal.android.data.p2p

import java.util.UUID

/**
 * P2P data models used across the networking and UI layers.
 */

data class P2PInfo(
    val deviceName: String,
    val ips: List<String>,
    val port: Int = P2PProtocol.DEFAULT_PORT,
    val pairCode: String = generatePairCode()
) {
    fun toQRPayload(): QRPayload = QRPayload(
        ips = ips,
        port = port,
        pairCode = pairCode,
        version = P2PProtocol.PROTOCOL_VERSION
    )
}

data class P2PSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val remoteDevice: String,
    val remoteAddr: String,
    val remotePort: Int = P2PProtocol.DEFAULT_PORT,
    val isPaired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class P2PStatus(
    val isRunning: Boolean = false,
    val sessions: List<P2PSession> = emptyList(),
    val info: P2PInfo? = null
)

data class P2PTransferProgress(
    val sessionId: String,
    val fileName: String,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val direction: TransferDirection = TransferDirection.SEND
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f

    val isComplete: Boolean
        get() = totalBytes > 0 && bytesTransferred >= totalBytes
}

enum class TransferDirection {
    SEND, RECEIVE
}

data class P2PPairRequest(
    val remoteAddr: String,
    val remoteDevice: String,
    val pairCode: String,
    val timestamp: Long = System.currentTimeMillis()
)

private fun generatePairCode(): String {
    // 使用大写字母 + 数字（去掉易混淆的 I/O/0/1），与 Rust 端字符集兼容
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..6).map { chars.random() }.joinToString("")
}
