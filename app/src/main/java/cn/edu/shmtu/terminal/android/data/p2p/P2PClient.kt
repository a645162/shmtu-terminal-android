package cn.edu.shmtu.terminal.android.data.p2p

import android.util.Base64
import android.util.Log
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

interface P2PClientCallback {
    fun onTransferOffer(sessionId: String, transferId: String, totalSize: Long, billCount: Int): Boolean
}

/**
 * P2P TCP client that connects to a remote device, handles pairing, and sends data.
 * Wire protocol matches Rust `shmtu-p2p` crate.
 */
class P2PClient {

    private val tag = "P2PClient"
    private val pairResponseTimeoutMs = 65_000

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    /** Heartbeat coroutine job, cancelled when the read loop exits. */
    private var heartbeatJob: Job? = null
    private var receiveJob: Job? = null

    /** AES-256-GCM encryption key; null until encryption negotiation succeeds. */
    var encryptionKey: ByteArray? = null
        private set

    var sessionId: String? = null
    var clientCallback: P2PClientCallback? = null

    @Volatile
    private var receiveLoopNotifyDisconnect = true

    /**
     * Connect to a remote P2P server.
     * @param host Remote IP address
     * @param port Remote port
     * @param timeoutMs Connection timeout in milliseconds
     * @return Result with the connected socket's input/output streams
     */
    suspend fun connect(
        host: String,
        port: Int,
        timeoutMs: Int = 10000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            sock.soTimeout = 30000 // read timeout
            socket = sock
            input = sock.getInputStream()
            output = sock.getOutputStream()
            Log.i(tag, "Connected to $host:$port")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Failed to connect to $host:$port", e)
            close()
            Result.failure(e)
        }
    }

    /**
     * Send a pair request to the remote server and wait for the response.
     * @param deviceName Our device name
     * @param pairCode The pair code to authenticate
     * @return Result with PairAcceptPayload on success, or failure
     */
    suspend fun sendPairRequest(
        deviceName: String,
        pairCode: String,
        listenPort: Int?,
        listenIps: List<String>
    ): Result<PairAcceptPayload> = withContext(Dispatchers.IO) {
        try {
            val outStream = output ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )
            val inStream = input ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )

            // Send pair request using kotlinx.serialization
            val req = PairRequestPayload(
                pairCode = pairCode,
                deviceName = deviceName,
                listenPort = listenPort,
                listenIps = listenIps
            )
            val payload = p2pJson.encodeToString(PairRequestPayload.serializer(), req)
                .toByteArray(Charsets.UTF_8)
            FrameCodec.writeFrame(outStream, P2PFrame(P2PProtocol.TYPE_PAIR_REQUEST.toByte(), payload))
            Log.d(tag, "Sent PairRequest to remote (pair_code=****)")

            // Read pair response. Remote desktop peers wait for explicit user approval,
            // so allow a longer timeout than the general socket read timeout.
            socket?.soTimeout = pairResponseTimeoutMs
            val startedAt = SystemClock.elapsedRealtime()
            var responseFrame: P2PFrame? = null
            while (responseFrame == null) {
                val frame = FrameCodec.readFrame(inStream)
                    ?: return@withContext Result.failure(
                        Exception("Connection closed while waiting for pair response")
                    )

                when (frame.type.toInt() and 0xFF) {
                    P2PProtocol.TYPE_PING -> {
                        Log.d(tag, "Ignoring PING while waiting for pair response")
                        FrameCodec.writeFrame(outStream, P2PFrame(P2PProtocol.TYPE_PONG.toByte(), ByteArray(0)))
                    }
                    P2PProtocol.TYPE_PONG -> {
                        Log.d(tag, "Ignoring PONG while waiting for pair response")
                    }
                    else -> {
                        responseFrame = frame
                    }
                }

                if (SystemClock.elapsedRealtime() - startedAt > pairResponseTimeoutMs) {
                    throw SocketTimeoutException("Timed out waiting for pair response frame")
                }
            }
            socket?.soTimeout = 30000

            when (responseFrame.type.toInt() and 0xFF) {
                P2PProtocol.TYPE_PAIR_ACCEPT -> {
                    val accept = p2pJson.decodeFromString<PairAcceptPayload>(
                        String(responseFrame.payload, Charsets.UTF_8)
                    )
                    Log.i(tag, "Pair accepted by ${accept.deviceName}, session=${accept.sessionId}")
                    Result.success(accept)
                }
                P2PProtocol.TYPE_PAIR_REJECT -> {
                    val reject = p2pJson.decodeFromString<PairRejectPayload>(
                        String(responseFrame.payload, Charsets.UTF_8)
                    )
                    Log.w(tag, "Pair rejected by remote: ${reject.reason}")
                    Result.failure(Exception("配对被拒绝: ${reject.reason}"))
                }
                else -> {
                    Log.e(tag, "Unexpected response type: ${P2PProtocol.typeName(responseFrame.type)}")
                    Result.failure(Exception("Unexpected response type"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            Log.e(tag, "Pair request timed out", e)
            Result.failure(Exception("配对请求超时"))
        } catch (e: Exception) {
            Log.e(tag, "Pair request failed", e)
            Result.failure(e)
        } finally {
            try {
                socket?.soTimeout = 30000
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Perform encryption negotiation after pairing succeeds.
     * Sends EncryptionNegotiate and waits for EncryptionConfirm.
     * On success, sets [encryptionKey] for subsequent frame encryption/decryption.
     * On failure, disconnects and throws.
     */
    suspend fun negotiateEncryption(pairCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val outStream = output ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )
            val inStream = input ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )

            // 1. Generate negotiation parameters
            val salt = P2PCrypto.generateSalt()
            val clientNonce = P2PCrypto.generateClientNonce()

            // 2. Send EncryptionNegotiate
            val negotiatePayload = EncryptionNegotiatePayload(
                method = "aes-256-gcm",
                salt = Base64.encodeToString(salt, Base64.NO_WRAP),
                iterations = 600_000,
                clientNonce = Base64.encodeToString(clientNonce, Base64.NO_WRAP)
            )
            val negotiateBytes = p2pJson.encodeToString(EncryptionNegotiatePayload.serializer(), negotiatePayload)
                .toByteArray(Charsets.UTF_8)
            FrameCodec.writeFrame(outStream, P2PFrame(P2PProtocol.TYPE_ENCRYPTION_NEGOTIATE.toByte(), negotiateBytes))
            Log.d(tag, "Sent EncryptionNegotiate")

            // 3. Read EncryptionConfirm response
            val confirmFrame = FrameCodec.readFrame(inStream)
                ?: return@withContext Result.failure(
                    Exception("Connection closed while waiting for encryption confirmation")
                )

            when (confirmFrame.type.toInt() and 0xFF) {
                P2PProtocol.TYPE_ENCRYPTION_CONFIRM -> {
                    val confirm = p2pJson.decodeFromString<EncryptionConfirmPayload>(
                        String(confirmFrame.payload, Charsets.UTF_8)
                    )
                    val derivedKey = P2PCrypto.deriveKey(pairCode, salt)
                    val expectedVerification = P2PCrypto.generateVerification(salt, clientNonce, derivedKey)
                    val actualVerification = Base64.decode(confirm.verification, Base64.NO_WRAP)

                    if (!expectedVerification.contentEquals(actualVerification)) {
                        // Zero the derived key before discarding
                        derivedKey.fill(0)
                        disconnect()
                        return@withContext Result.failure(
                            Exception("Encryption verification failed — pair code mismatch or tampering")
                        )
                    }

                    encryptionKey = derivedKey
                    Log.i(tag, "Encryption negotiated successfully")
                    Result.success(Unit)
                }
                else -> {
                    // Peer does not support encryption — mandatory, so disconnect
                    disconnect()
                    Result.failure(Exception("Peer does not support encryption"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Encryption negotiation failed", e)
            Result.failure(e)
        }
    }

    /**
     * Send a transfer offer to the paired remote device.
     */
    suspend fun sendTransferOffer(
        transferId: String,
        totalSize: Long,
        billCount: Int,
        description: String = "Bill data transfer"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val outStream = output ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )
            val inStream = input ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )

            val offer = TransferOfferPayload(
                transferId = transferId,
                description = description,
                totalSize = totalSize,
                billCount = billCount
            )
            val payload = p2pJson.encodeToString(TransferOfferPayload.serializer(), offer)
                .toByteArray(Charsets.UTF_8)
            sendEncryptedFrame(outStream, P2PFrame(P2PProtocol.TYPE_TRANSFER_OFFER.toByte(), payload))

            // Read accept/reject
            val responseFrame = readEncryptedFrame(inStream)
                ?: return@withContext Result.failure(
                    Exception("Connection closed while waiting for transfer response")
                )

            val accepted = when (responseFrame.type.toInt() and 0xFF) {
                P2PProtocol.TYPE_TRANSFER_ACCEPT -> true
                P2PProtocol.TYPE_TRANSFER_REJECT -> false
                else -> false
            }
            Result.success(accepted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Transfer offer failed", e)
            Result.failure(e)
        }
    }

    /**
     * Send bill data to the remote device in chunks using the Rust-aligned protocol.
     * Each chunk is serialized as a TransferDataPayload with Base64-encoded data.
     * @param transferId The transfer ID from the offer
     * @param data The complete data to send
     * @param onProgress Callback for progress updates (bytesTransferred, totalBytes)
     */
    suspend fun sendTransferData(
        transferId: String,
        data: ByteArray,
        host: String,
        port: Int,
        pairCode: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val salt = P2PCrypto.generateSalt()
            val transferKey = P2PCrypto.deriveKey(pairCode, salt)
            val transferSocket = Socket()
            transferSocket.connect(InetSocketAddress(host, port), 10000)
            transferSocket.soTimeout = 30000
            val transferInput = transferSocket.getInputStream()
            val transferOutput = transferSocket.getOutputStream()

            val currentSessionId = sessionId
                ?: return@withContext Result.failure(IllegalStateException("Session not set"))
            val openPayload = TransferChannelOpenPayload(
                sessionId = currentSessionId,
                transferId = transferId,
                pairCode = pairCode,
                salt = Base64.encodeToString(salt, Base64.NO_WRAP)
            )
            val openBytes = p2pJson.encodeToString(TransferChannelOpenPayload.serializer(), openPayload)
                .toByteArray(Charsets.UTF_8)
            FrameCodec.writeFrame(transferOutput, P2PFrame(P2PProtocol.TYPE_TRANSFER_CHANNEL_OPEN.toByte(), openBytes))

            val readyFrame = readEncryptedFrame(transferInput, transferKey)
                ?: return@withContext Result.failure(Exception("传输通道未就绪"))
            val ready = p2pJson.decodeFromString<TransferChannelReadyPayload>(
                String(readyFrame.payload, Charsets.UTF_8)
            )
            if (ready.transferId != transferId) {
                return@withContext Result.failure(Exception("传输通道返回的 transferId 不匹配"))
            }

            val chunkSize = 32 * 1024 // 32KB chunks
            var offset = 0
            var sequence = 0

            try {
                while (offset < data.size && isActive) {
                    val end = minOf(offset + chunkSize, data.size)
                    val chunk = data.copyOfRange(offset, end)

                    val transferData = TransferDataPayload(
                        transferId = transferId,
                        sequence = sequence,
                        data = Base64.encodeToString(chunk, Base64.NO_WRAP)
                    )
                    val payload = p2pJson.encodeToString(TransferDataPayload.serializer(), transferData)
                        .toByteArray(Charsets.UTF_8)
                    sendEncryptedFrame(
                        transferOutput,
                        P2PFrame(P2PProtocol.TYPE_TRANSFER_DATA.toByte(), payload),
                        transferKey
                    )

                    offset = end
                    sequence++
                    onProgress(offset.toLong(), data.size.toLong())
                }

                val checksum = computeChecksum(data)
                val endMsg = TransferEndPayload(
                    transferId = transferId,
                    checksum = checksum
                )
                val endPayload = p2pJson.encodeToString(TransferEndPayload.serializer(), endMsg)
                    .toByteArray(Charsets.UTF_8)
                sendEncryptedFrame(
                    transferOutput,
                    P2PFrame(P2PProtocol.TYPE_TRANSFER_END.toByte(), endPayload),
                    transferKey
                )

                val resultFrame = readEncryptedFrame(transferInput, transferKey)
                    ?: return@withContext Result.failure(Exception("未收到传输结果"))
                val result = p2pJson.decodeFromString<TransferChannelResultPayload>(
                    String(resultFrame.payload, Charsets.UTF_8)
                )
                if (!result.success) {
                    return@withContext Result.failure(Exception(result.reason.ifBlank { "接收端校验失败" }))
                }

                Log.i(tag, "Transfer complete: ${data.size} bytes, $sequence chunks, checksum=$checksum")
                Result.success(Unit)
            } finally {
                transferKey.fill(0)
                try { transferInput.close() } catch (_: Exception) {}
                try { transferOutput.close() } catch (_: Exception) {}
                try { transferSocket.close() } catch (_: Exception) {}
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Transfer data failed", e)
            Result.failure(e)
        }
    }

    /**
     * Send a disconnect frame and close the connection.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            val outStream = output
            if (outStream != null) {
                val msg = DisconnectPayload(reason = "Client disconnect")
                val payload = p2pJson.encodeToString(DisconnectPayload.serializer(), msg)
                    .toByteArray(Charsets.UTF_8)
                FrameCodec.writeFrame(outStream, P2PFrame(P2PProtocol.TYPE_DISCONNECT.toByte(), payload))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort
        } finally {
            stopHeartbeat()
            clearEncryptionKey()
            close()
        }
    }

    /**
     * Start the heartbeat loop: send PING every 30 seconds.
     * Must be called after pairing succeeds; cancelled by [stopHeartbeat].
     */
    fun startHeartbeat(scope: CoroutineScope) {
        stopHeartbeat()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L)
                try {
                    val outStream = output ?: break
                    // PING frame has empty payload — never encrypted
                    FrameCodec.writeFrame(outStream, P2PFrame(P2PProtocol.TYPE_PING.toByte(), ByteArray(0)))
                    Log.d(tag, "Sent PING")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(tag, "Heartbeat PING failed", e)
                    break
                }
            }
        }
    }

    /**
     * Start a background receive loop for long-lived paired connections so
     * control frames do not accumulate and break the next request/response.
     */
    fun startReceiving(scope: CoroutineScope, onDisconnected: (() -> Unit)? = null) {
        stopReceiving()
        receiveLoopNotifyDisconnect = true
        receiveJob = scope.launch(Dispatchers.IO) {
            try {
                val inStream = input ?: return@launch
                while (isActive) {
                    val frame = readEncryptedFrame(inStream) ?: break
                    val keepOpen = handleFrame(frame)
                    if (!keepOpen) {
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                Log.d(tag, "Receive loop timed out waiting for frame")
            } catch (e: EOFException) {
                Log.d(tag, "Receive loop reached EOF")
            } catch (e: Exception) {
                Log.w(tag, "Receive loop stopped", e)
            } finally {
                if (receiveLoopNotifyDisconnect) {
                    onDisconnected?.invoke()
                }
            }
        }
    }

    /**
     * Stop the heartbeat loop.
     */
    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    fun stopReceiving(notifyDisconnected: Boolean = false) {
        receiveLoopNotifyDisconnect = notifyDisconnected
        receiveJob?.cancel()
        receiveJob = null
    }

    /**
     * Handle an inbound frame in the read loop.
     * Returns true if the connection should stay open, false to close it.
     */
    fun handleFrame(frame: P2PFrame): Boolean {
        return when (frame.type.toInt() and 0xFF) {
            P2PProtocol.TYPE_PING -> {
                // Reply PONG — never encrypted
                try {
                    val outStream = output
                    if (outStream != null) {
                        FrameCodec.writeFrame(outStream, P2PFrame(P2PProtocol.TYPE_PONG.toByte(), ByteArray(0)))
                        Log.d(tag, "Replied PONG to PING")
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to send PONG", e)
                }
                true
            }
            P2PProtocol.TYPE_PONG -> {
                Log.d(tag, "Received PONG")
                true
            }
            P2PProtocol.TYPE_DISCONNECT -> {
                Log.d(tag, "Remote sent Disconnect")
                false
            }
            P2PProtocol.TYPE_TRANSFER_OFFER -> {
                val transferOffer = p2pJson.decodeFromString<TransferOfferPayload>(
                    String(frame.payload, Charsets.UTF_8)
                )
                val currentSessionId = sessionId
                val accepted = if (currentSessionId != null) {
                    clientCallback?.onTransferOffer(
                        currentSessionId,
                        transferOffer.transferId,
                        transferOffer.totalSize,
                        transferOffer.billCount
                    ) == true
                } else {
                    false
                }

                try {
                    val outStream = output
                    if (outStream != null) {
                        if (accepted) {
                            val accept = TransferAcceptPayload(transferId = transferOffer.transferId)
                            val payload = p2pJson.encodeToString(TransferAcceptPayload.serializer(), accept)
                                .toByteArray(Charsets.UTF_8)
                            sendEncryptedFrame(outStream, P2PFrame(P2PProtocol.TYPE_TRANSFER_ACCEPT.toByte(), payload))
                        } else {
                            val reject = TransferRejectPayload(
                                transferId = transferOffer.transferId,
                                reason = "接收端未准备好"
                            )
                            val payload = p2pJson.encodeToString(TransferRejectPayload.serializer(), reject)
                                .toByteArray(Charsets.UTF_8)
                            sendEncryptedFrame(outStream, P2PFrame(P2PProtocol.TYPE_TRANSFER_REJECT.toByte(), payload))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Failed to reply transfer offer", e)
                    return false
                }
                true
            }
            else -> {
                Log.w(tag, "Unhandled frame type: ${P2PProtocol.typeName(frame.type)}")
                true
            }
        }
    }

    /**
     * Send a frame, encrypting the payload if an encryption key is set and the
     * message type requires encryption.
     */
    private fun sendEncryptedFrame(output: OutputStream, frame: P2PFrame, keyOverride: ByteArray? = null) {
        val key = keyOverride ?: encryptionKey
        if (key != null && P2PCrypto.shouldEncrypt(frame.type)) {
            val encrypted = P2PCrypto.encrypt(key, frame.payload)
            FrameCodec.writeFrame(output, P2PFrame(frame.type, encrypted))
        } else {
            FrameCodec.writeFrame(output, frame)
        }
    }

    /**
     * Read a frame, decrypting the payload if an encryption key is set and the
     * message type requires encryption.
     */
    private fun readEncryptedFrame(input: InputStream): P2PFrame? {
        val frame = FrameCodec.readFrame(input) ?: return null
        val key = encryptionKey
        return if (key != null && P2PCrypto.shouldEncrypt(frame.type)) {
            try {
                val decrypted = P2PCrypto.decrypt(key, frame.payload)
                P2PFrame(frame.type, decrypted)
            } catch (e: Exception) {
                Log.e(tag, "Failed to decrypt frame of type ${P2PProtocol.typeName(frame.type)}", e)
                null
            }
        } else {
            frame
        }
    }

    private fun readEncryptedFrame(input: InputStream, keyOverride: ByteArray?): P2PFrame? {
        val frame = FrameCodec.readFrame(input) ?: return null
        return if (keyOverride != null && P2PCrypto.shouldEncrypt(frame.type)) {
            try {
                val decrypted = P2PCrypto.decrypt(keyOverride, frame.payload)
                P2PFrame(frame.type, decrypted)
            } catch (e: Exception) {
                Log.e(tag, "Failed to decrypt transfer frame of type ${P2PProtocol.typeName(frame.type)}", e)
                null
            }
        } else {
            frame
        }
    }

    /**
     * Zero and clear the encryption key.
     */
    private fun clearEncryptionKey() {
        encryptionKey?.fill(0)
        encryptionKey = null
    }

    fun close() {
        stopReceiving()
        stopHeartbeat()
        clearEncryptionKey()
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
    }

    companion object {
        /**
         * Compute FNV-1a checksum matching Rust `compute_checksum`.
         * Returns a 16-character lowercase hex string.
         *
         * Uses Kotlin Long multiplication which wraps on overflow (two's complement),
         * matching the wrapping semantics required by FNV-1a.
         */
        fun computeChecksum(data: ByteArray): String {
            var hash: Long = 0xcbf29ce484222325UL.toLong()
            val prime: Long = 0x100000001b3UL.toLong()
            for (byte in data) {
                hash = hash xor (byte.toLong() and 0xFF)
                hash = hash * prime
            }
            return String.format("%016x", hash)
        }
    }
}
