package cn.edu.shmtu.terminal.android.data.p2p

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * P2P protocol message types and frame codec, matching the Rust `shmtu-p2p` crate.
 *
 * Wire format:
 *   [4 bytes: body length (big-endian u32)]  -- does NOT include the 4-byte length field itself
 *   [4 bytes: magic "SHTP"]
 *   [4 bytes: protocol version (big-endian u32)]
 *   [1 byte : message type]
 *   [N bytes: payload (UTF-8 JSON for structured messages, empty for PING/PONG)]
 */
object P2PProtocol {
    const val DEFAULT_PORT = 19827
    const val PROTOCOL_VERSION = 1

    /** Protocol magic bytes: "SHTP" */
    val PROTOCOL_MAGIC: ByteArray = byteArrayOf('S'.code.toByte(), 'H'.code.toByte(), 'T'.code.toByte(), 'P'.code.toByte())

    // Message types (must match Rust `protocol.rs`)
    const val TYPE_PAIR_REQUEST = 0x01
    const val TYPE_PAIR_ACCEPT = 0x02
    const val TYPE_PAIR_REJECT = 0x03
    const val TYPE_PING = 0x04
    const val TYPE_PONG = 0x05
    const val TYPE_ENCRYPTION_NEGOTIATE = 0x06
    const val TYPE_ENCRYPTION_CONFIRM = 0x07
    const val TYPE_TRANSFER_OFFER = 0x10
    const val TYPE_TRANSFER_ACCEPT = 0x11
    const val TYPE_TRANSFER_REJECT = 0x12
    const val TYPE_TRANSFER_DATA = 0x13
    const val TYPE_TRANSFER_END = 0x14
    const val TYPE_TRANSFER_CHANNEL_OPEN = 0x15
    const val TYPE_TRANSFER_CHANNEL_READY = 0x16
    const val TYPE_TRANSFER_CHANNEL_RESULT = 0x17
    const val TYPE_DISCONNECT = 0xFF

    fun typeName(type: Byte): String = when (type.toInt() and 0xFF) {
        TYPE_PAIR_REQUEST -> "PairRequest"
        TYPE_PAIR_ACCEPT -> "PairAccept"
        TYPE_PAIR_REJECT -> "PairReject"
        TYPE_PING -> "Ping"
        TYPE_PONG -> "Pong"
        TYPE_ENCRYPTION_NEGOTIATE -> "EncryptionNegotiate"
        TYPE_ENCRYPTION_CONFIRM -> "EncryptionConfirm"
        TYPE_TRANSFER_OFFER -> "TransferOffer"
        TYPE_TRANSFER_ACCEPT -> "TransferAccept"
        TYPE_TRANSFER_REJECT -> "TransferReject"
        TYPE_TRANSFER_DATA -> "TransferData"
        TYPE_TRANSFER_END -> "TransferEnd"
        TYPE_TRANSFER_CHANNEL_OPEN -> "TransferChannelOpen"
        TYPE_TRANSFER_CHANNEL_READY -> "TransferChannelReady"
        TYPE_TRANSFER_CHANNEL_RESULT -> "TransferChannelResult"
        TYPE_DISCONNECT -> "Disconnect"
        else -> "Unknown(0x${type.toInt().toString(16)})"
    }
}

/**
 * A decoded protocol frame: type byte + raw payload.
 */
data class P2PFrame(
    val type: Byte,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is P2PFrame) return false
        return type == other.type && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * type + payload.contentHashCode()
}

/**
 * Frame encoder/decoder aligned with Rust `ProtocolFrame`.
 *
 * Wire format (matching `shmtu-p2p/src/protocol.rs`):
 *   [4 bytes: body length (big-endian u32)]  // magic(4) + version(4) + type(1) + payload(N) = 9 + N
 *   [4 bytes: magic "SHTP"]
 *   [4 bytes: protocol version (big-endian u32)]
 *   [1 byte : message type]
 *   [N bytes: payload]
 */
object FrameCodec {

    private const val HEADER_SIZE = 4          // length prefix
    private const val MAGIC_SIZE = 4
    private const val VERSION_SIZE = 4
    private const val TYPE_SIZE = 1
    private const val FRAME_BODY_OVERHEAD = MAGIC_SIZE + VERSION_SIZE + TYPE_SIZE // 9

    /**
     * Encode a frame into bytes.
     */
    fun encode(frame: P2PFrame): ByteArray {
        val bodyLen = FRAME_BODY_OVERHEAD + frame.payload.size
        val buffer = ByteBuffer.allocate(HEADER_SIZE + bodyLen)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(bodyLen)
        buffer.put(P2PProtocol.PROTOCOL_MAGIC)
        buffer.putInt(P2PProtocol.PROTOCOL_VERSION)
        buffer.put(frame.type)
        buffer.put(frame.payload)
        return buffer.array()
    }

    /**
     * Write a frame to an output stream and flush.
     */
    fun writeFrame(output: OutputStream, frame: P2PFrame) {
        output.write(encode(frame))
        output.flush()
    }

    /**
     * Read a frame from an input stream. Blocks until a complete frame is available.
     * Returns null if the stream reaches end-of-file or if the frame is malformed.
     */
    fun readFrame(input: InputStream): P2PFrame? {
        // Read 4-byte length header
        val lengthBytes = readExact(input, HEADER_SIZE) ?: return null
        val bodyLen = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).int

        if (bodyLen < FRAME_BODY_OVERHEAD || bodyLen > 10 * 1024 * 1024) {
            return null // Invalid or too large
        }

        // Read body: magic(4) + version(4) + type(1) + payload
        val body = readExact(input, bodyLen) ?: return null

        // Validate magic
        for (i in 0 until MAGIC_SIZE) {
            if (body[i] != P2PProtocol.PROTOCOL_MAGIC[i]) return null
        }

        // Read version (not strictly validated; we accept and ignore for forward compat)
        ByteBuffer.wrap(body, MAGIC_SIZE, VERSION_SIZE)
            .order(ByteOrder.BIG_ENDIAN).int

        // Read type
        val type = body[MAGIC_SIZE + VERSION_SIZE]

        // Extract payload
        val payload = body.copyOfRange(FRAME_BODY_OVERHEAD, body.size)
        return P2PFrame(type, payload)
    }

    /**
     * Read exactly [n] bytes from the input stream.
     * Returns null if EOF is reached before [n] bytes are available.
     */
    private fun readExact(input: InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = input.read(buf, offset, n - offset)
            if (read < 0) return null // EOF
            offset += read
        }
        return buf
    }
}

// ============================================================================
// kotlinx.serialization JSON configuration
// ============================================================================

/**
 * Shared Json instance configured for forward compatibility.
 * `ignoreUnknownKeys = true` allows old/new field mismatches across versions.
 */
internal val p2pJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// ============================================================================
// Wire protocol payloads — all field names match Rust serde structs
// ============================================================================

/** PairRequest: { pair_code, device_name, listen_port?, listen_ips? } */
@Serializable
data class PairRequestPayload(
    @SerialName("pair_code") val pairCode: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("listen_port") val listenPort: Int? = null,
    @SerialName("listen_ips") val listenIps: List<String> = emptyList()
)

/** PairAccept: { device_name, session_id } */
@Serializable
data class PairAcceptPayload(
    @SerialName("device_name") val deviceName: String,
    @SerialName("session_id") val sessionId: String
)

/** PairReject: { reason } */
@Serializable
data class PairRejectPayload(
    @SerialName("reason") val reason: String = ""
)

/** TransferOffer: { transfer_id, description, total_size, bill_count } */
@Serializable
data class TransferOfferPayload(
    @SerialName("transfer_id") val transferId: String,
    @SerialName("description") val description: String,
    @SerialName("total_size") val totalSize: Long,
    @SerialName("bill_count") val billCount: Int
)

/** TransferAccept: { transfer_id } */
@Serializable
data class TransferAcceptPayload(
    @SerialName("transfer_id") val transferId: String
)

/** TransferReject: { transfer_id, reason } */
@Serializable
data class TransferRejectPayload(
    @SerialName("transfer_id") val transferId: String,
    @SerialName("reason") val reason: String = ""
)

/**
 * TransferData: { transfer_id, sequence, data (Base64) }.
 * The `data` field carries a Base64-encoded binary chunk.
 */
@Serializable
data class TransferDataPayload(
    @SerialName("transfer_id") val transferId: String,
    @SerialName("sequence") val sequence: Int,
    @SerialName("data") val data: String
)

/** TransferEnd: { transfer_id, checksum } */
@Serializable
data class TransferEndPayload(
    @SerialName("transfer_id") val transferId: String,
    @SerialName("checksum") val checksum: String
)

@Serializable
data class TransferChannelOpenPayload(
    @SerialName("session_id") val sessionId: String,
    @SerialName("transfer_id") val transferId: String,
    @SerialName("pair_code") val pairCode: String,
    @SerialName("salt") val salt: String
)

@Serializable
data class TransferChannelReadyPayload(
    @SerialName("transfer_id") val transferId: String
)

@Serializable
data class TransferChannelResultPayload(
    @SerialName("transfer_id") val transferId: String,
    @SerialName("success") val success: Boolean,
    @SerialName("reason") val reason: String = ""
)

/** Disconnect: { reason } */
@Serializable
data class DisconnectPayload(
    @SerialName("reason") val reason: String = ""
)

// ============================================================================
// Encryption negotiation payloads
// ============================================================================

/**
 * EncryptionNegotiate: client -> server after pairing succeeds.
 * Carries the KDF parameters (method, salt, iterations) and a client nonce
 * so the server can derive the same key and return a verification token.
 */
@Serializable
data class EncryptionNegotiatePayload(
    @SerialName("method") val method: String,
    @SerialName("salt") val salt: String,           // Base64
    @SerialName("iterations") val iterations: Int,
    @SerialName("client_nonce") val clientNonce: String  // Base64
)

/**
 * EncryptionConfirm: server -> client in response to EncryptionNegotiate.
 * Carries an HMAC-SHA256 verification token truncated to 16 bytes, proving
 * the server derived the same key from the same pair code.
 */
@Serializable
data class EncryptionConfirmPayload(
    @SerialName("verification") val verification: String  // Base64
)

/**
 * QR payload structure matching Rust `discovery.rs::QRPayload`:
 *   { ips, port, pair_code, version }
 */
@Serializable
data class QRPayload(
    @SerialName("ips") val ips: List<String> = emptyList(),
    @SerialName("port") val port: Int = P2PProtocol.DEFAULT_PORT,
    @SerialName("pair_code") val pairCode: String = "",
    @SerialName("version") val version: Int = P2PProtocol.PROTOCOL_VERSION
)
