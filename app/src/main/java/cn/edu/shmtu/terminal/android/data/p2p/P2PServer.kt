package cn.edu.shmtu.terminal.android.data.p2p

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Callback interface for P2P server events.
 */
interface P2PServerCallback {
    fun onPairRequest(remoteAddr: String, deviceName: String, pairCode: String)
    fun onTransferReceived(sessionId: String, fileName: String, data: ByteArray, billCount: Int)
    fun onClientConnected(remoteAddr: String)
    fun onClientDisconnected(remoteAddr: String)
    fun onError(message: String)
}

/**
 * P2P TCP server that listens for incoming connections, handles pairing, and receives data.
 * Wire protocol matches Rust `shmtu-p2p` crate.
 *
 * @param parentScope CoroutineScope to use for client handling. When cancelled, all child
 *   coroutines are cancelled as well, providing proper lifecycle management.
 */
class P2PServer(private val parentScope: CoroutineScope) {

    private val tag = "P2PServer"
    private val pendingPairTimeoutMs = 60_000L

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var callback: P2PServerCallback? = null

    // Thread-safe pending pair requests
    private val pendingPairRequests = ConcurrentHashMap<String, PendingPairRequest>()

    // Track paired sessions (remoteAddr -> true) to enforce pairing before transfer
    private val pairedRemotes = ConcurrentHashMap<String, Boolean>()

    fun setCallback(cb: P2PServerCallback) {
        callback = cb
    }

    val running: Boolean get() = isRunning

    /**
     * Start listening on the given port.
     * This is a suspending function that blocks until [stop] is called.
     */
    suspend fun start(
        port: Int = P2PProtocol.DEFAULT_PORT,
        deviceName: String,
        expectedPairCode: String
    ) = withContext(Dispatchers.IO) {
        if (isRunning) {
            Log.w(tag, "Server already running")
            return@withContext
        }

        try {
            val ss = ServerSocket(port)
            serverSocket = ss
            isRunning = true
            Log.i(tag, "Server started on port $port, pair_code=****")

            while (isRunning && isActive) {
                try {
                    val clientSocket = ss.accept()
                    Log.d(tag, "Client connected: ${clientSocket.remoteSocketAddress}")
                    callback?.onClientConnected(clientSocket.inetAddress.hostAddress ?: "unknown")

                    // Handle client in a separate coroutine so accept loop is not blocked
                    parentScope.launch {
                        handleClient(clientSocket, deviceName, expectedPairCode)
                    }
                } catch (e: java.net.SocketException) {
                    if (isRunning) {
                        Log.e(tag, "Socket exception while accepting", e)
                    }
                    // Server socket closed, exit loop
                    break
                } catch (e: CancellationException) {
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Server failed to start", e)
            callback?.onError("服务启动失败: ${e.message}")
        } finally {
            isRunning = false
            serverSocket = null
        }
    }

    /**
     * Accept a pending pair request from the given remote address.
     */
    fun acceptPair(remoteAddr: String, sessionId: String): Boolean {
        val pending = pendingPairRequests[remoteAddr]
        if (pending == null) {
            Log.w(tag, "acceptPair ignored, no pending request for $remoteAddr")
            return false
        }
        try {
            Log.i(tag, "Sending PairAccept to $remoteAddr, session=$sessionId")
            val accept = PairAcceptPayload(
                deviceName = pending.deviceName,
                sessionId = sessionId
            )
            val payload = p2pJson.encodeToString(PairAcceptPayload.serializer(), accept)
                .toByteArray(Charsets.UTF_8)
            writeFrameLocked(pending.outputStream, pending.writeLock, P2PFrame(P2PProtocol.TYPE_PAIR_ACCEPT.toByte(), payload))
            pendingPairRequests.remove(remoteAddr)
            pairedRemotes[remoteAddr] = true
            pending.isPaired.set(true)
            Log.i(tag, "Pair accepted for $remoteAddr, session=$sessionId")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to send pair accept", e)
            return false
        }
    }

    /**
     * Reject a pending pair request from the given remote address.
     */
    fun rejectPair(remoteAddr: String): Boolean {
        val pending = pendingPairRequests[remoteAddr]
        if (pending == null) {
            Log.w(tag, "rejectPair ignored, no pending request for $remoteAddr")
            return false
        }
        try {
            Log.i(tag, "Sending PairReject to $remoteAddr")
            val reject = PairRejectPayload(reason = "Rejected by user")
            val payload = p2pJson.encodeToString(PairRejectPayload.serializer(), reject)
                .toByteArray(Charsets.UTF_8)
            writeFrameLocked(pending.outputStream, pending.writeLock, P2PFrame(P2PProtocol.TYPE_PAIR_REJECT.toByte(), payload))
            pendingPairRequests.remove(remoteAddr)
            Log.i(tag, "Pair rejected for $remoteAddr")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to send pair reject", e)
            return false
        }
    }

    /**
     * Send a TransferAccept frame.
     */
    private fun sendTransferAccept(
        outputStream: OutputStream,
        writeLock: ReentrantLock,
        transferId: String,
        encryptionKey: ByteArray?
    ) {
        val accept = TransferAcceptPayload(transferId = transferId)
        val payload = p2pJson.encodeToString(TransferAcceptPayload.serializer(), accept)
            .toByteArray(Charsets.UTF_8)
        sendEncryptedFrame(outputStream, writeLock, P2PFrame(P2PProtocol.TYPE_TRANSFER_ACCEPT.toByte(), payload), encryptionKey)
    }

    /**
     * Send a TransferReject frame.
     */
    private fun sendTransferReject(
        outputStream: OutputStream,
        writeLock: ReentrantLock,
        transferId: String,
        reason: String = "",
        encryptionKey: ByteArray?
    ) {
        val reject = TransferRejectPayload(transferId = transferId, reason = reason)
        val payload = p2pJson.encodeToString(TransferRejectPayload.serializer(), reject)
            .toByteArray(Charsets.UTF_8)
        sendEncryptedFrame(outputStream, writeLock, P2PFrame(P2PProtocol.TYPE_TRANSFER_REJECT.toByte(), payload), encryptionKey)
    }

    /**
     * Handle a connected client: read frames, process pairing, receive transfers, handle PING/PONG.
     */
    private suspend fun handleClient(
        socket: Socket,
        ourDeviceName: String,
        expectedPairCode: String
    ) = withContext(Dispatchers.IO) {
        val remoteAddr = socket.inetAddress.hostAddress ?: "unknown"
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        var heartbeatJob: Job? = null
        var encryptionKey: ByteArray? = null

        try {
            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()
            inputStream = inStream
            outputStream = outStream
            socket.soTimeout = 60000

            var isPaired = false
            val isPairConfirmed = AtomicBoolean(false)
            val writeLock = ReentrantLock()

            // Start heartbeat only after pairing is confirmed to avoid interleaving
            // PING frames with PairAccept/Reject responses on the same socket.
            heartbeatJob = launchHeartbeat(parentScope, outStream, remoteAddr, writeLock, isPairConfirmed)

            while (isActive && !socket.isClosed) {
                val frame = FrameCodec.readFrame(inStream) ?: break

                when (frame.type.toInt() and 0xFF) {
                    P2PProtocol.TYPE_PING -> {
                        // Reply PONG — never encrypted
                        try {
                            writeFrameLocked(outStream, writeLock, P2PFrame(P2PProtocol.TYPE_PONG.toByte(), ByteArray(0)))
                            Log.d(tag, "Replied PONG to PING from $remoteAddr")
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to send PONG to $remoteAddr", e)
                        }
                    }

                    P2PProtocol.TYPE_PONG -> {
                        Log.d(tag, "Received PONG from $remoteAddr")
                    }

                    P2PProtocol.TYPE_PAIR_REQUEST -> {
                        val req = p2pJson.decodeFromString<PairRequestPayload>(
                            String(frame.payload, Charsets.UTF_8)
                        )
                        Log.d(tag, "Pair request from $remoteAddr: device=${req.deviceName} code=****")

                        if (req.pairCode.equals(expectedPairCode, ignoreCase = true)) {
                            // Valid pair code - notify callback for user confirmation
                            val pendingRequest = PendingPairRequest(
                                remoteAddr = remoteAddr,
                                deviceName = ourDeviceName,
                                outputStream = outStream,
                                clientSocket = socket,
                                writeLock = writeLock,
                                isPaired = isPairConfirmed
                            )
                            pendingPairRequests[remoteAddr] = pendingRequest
                            Log.i(tag, "Pending pair request stored for $remoteAddr, awaiting confirmation")
                            callback?.onPairRequest(remoteAddr, req.deviceName, req.pairCode)
                            launchPendingPairTimeout(remoteAddr, pendingRequest)
                        } else {
                            // Pair code mismatch - reject immediately
                            val reject = PairRejectPayload(reason = "Pair code mismatch")
                            val payload = p2pJson.encodeToString(PairRejectPayload.serializer(), reject)
                                .toByteArray(Charsets.UTF_8)
                            writeFrameLocked(outStream, writeLock, P2PFrame(P2PProtocol.TYPE_PAIR_REJECT.toByte(), payload))
                            Log.w(tag, "Pair code mismatch from $remoteAddr")
                        }
                    }

                    P2PProtocol.TYPE_ENCRYPTION_NEGOTIATE -> {
                        if (!isPaired && !pairedRemotes.containsKey(remoteAddr)) {
                            Log.w(tag, "Encryption negotiate rejected - not paired: $remoteAddr")
                            break
                        }
                        isPaired = true

                        val negotiate = p2pJson.decodeFromString<EncryptionNegotiatePayload>(
                            String(frame.payload, Charsets.UTF_8)
                        )
                        Log.d(tag, "Encryption negotiate from $remoteAddr: method=${negotiate.method}")

                        // Validate parameters
                        if (negotiate.method != "aes-256-gcm") {
                            Log.w(tag, "Unsupported encryption method: ${negotiate.method}")
                            break
                        }
                        if (negotiate.iterations < 100_000) {
                            Log.w(tag, "PBKDF2 iterations too low: ${negotiate.iterations}")
                            break
                        }

                        val salt = Base64.decode(negotiate.salt, Base64.NO_WRAP)
                        if (salt.size != 16) {
                            Log.w(tag, "Invalid salt length: ${salt.size}")
                            break
                        }
                        val clientNonce = Base64.decode(negotiate.clientNonce, Base64.NO_WRAP)

                        val derivedKey = P2PCrypto.deriveKey(expectedPairCode, salt)
                        val verification = P2PCrypto.generateVerification(salt, clientNonce, derivedKey)

                        val confirmPayload = EncryptionConfirmPayload(
                            verification = Base64.encodeToString(verification, Base64.NO_WRAP)
                        )
                        val confirmBytes = p2pJson.encodeToString(EncryptionConfirmPayload.serializer(), confirmPayload)
                            .toByteArray(Charsets.UTF_8)
                        writeFrameLocked(outStream, writeLock, P2PFrame(P2PProtocol.TYPE_ENCRYPTION_CONFIRM.toByte(), confirmBytes))

                        encryptionKey = derivedKey
                        Log.i(tag, "Encryption negotiated with $remoteAddr")
                    }

                    P2PProtocol.TYPE_TRANSFER_OFFER -> {
                        if (!isPaired && !pairedRemotes.containsKey(remoteAddr)) {
                            sendTransferReject(outStream, writeLock, "", reason = "Not paired", encryptionKey = encryptionKey)
                            Log.w(tag, "Transfer offer rejected - not paired: $remoteAddr")
                            continue
                        }
                        isPaired = true

                        // Decrypt if needed
                        val offerPayload = decryptIfNeeded(frame, encryptionKey)
                        val offer = p2pJson.decodeFromString<TransferOfferPayload>(
                            String(offerPayload, Charsets.UTF_8)
                        )
                        Log.d(tag, "Transfer offer: transfer=${offer.transferId} desc=${offer.description} size=${offer.totalSize} count=${offer.billCount}")

                        // Accept the transfer
                        sendTransferAccept(outStream, writeLock, offer.transferId, encryptionKey)

                        // Receive data frames
                        val dataBuffer = java.io.ByteArrayOutputStream()
                        var receivedBytes = 0L

                        while (isActive) {
                            val dataFrame = FrameCodec.readFrame(inStream) ?: break
                            val decryptedPayload: ByteArray? = when (dataFrame.type.toInt() and 0xFF) {
                                P2PProtocol.TYPE_PING -> {
                                    try {
                                        writeFrameLocked(outStream, writeLock, P2PFrame(P2PProtocol.TYPE_PONG.toByte(), ByteArray(0)))
                                    } catch (_: Exception) {}
                                    null
                                }

                                P2PProtocol.TYPE_PONG -> {
                                    Log.d(tag, "Received PONG during transfer from $remoteAddr")
                                    null
                                }

                                P2PProtocol.TYPE_TRANSFER_DATA -> {
                                    val decrypted = decryptIfNeeded(dataFrame, encryptionKey)
                                    val chunk = p2pJson.decodeFromString<TransferDataPayload>(
                                        String(decrypted, Charsets.UTF_8)
                                    )
                                    val chunkBytes = Base64.decode(chunk.data, Base64.NO_WRAP)
                                    dataBuffer.write(chunkBytes)
                                    receivedBytes += chunkBytes.size
                                    null
                                }

                                P2PProtocol.TYPE_TRANSFER_END -> {
                                    val decrypted = decryptIfNeeded(dataFrame, encryptionKey)
                                    val endMsg = p2pJson.decodeFromString<TransferEndPayload>(
                                        String(decrypted, Charsets.UTF_8)
                                    )
                                    Log.i(tag, "Transfer end: $receivedBytes bytes, checksum=${endMsg.checksum}")
                                    callback?.onTransferReceived(
                                        endMsg.transferId,
                                        "bills_export.json",
                                        dataBuffer.toByteArray(),
                                        offer.billCount
                                    )
                                    dataBuffer.reset()
                                    null // signal done
                                }

                                P2PProtocol.TYPE_DISCONNECT -> {
                                    break
                                }

                                else -> null
                            }

                            // TransferEnd reached — exit inner loop
                            if (dataFrame.type.toInt() and 0xFF == P2PProtocol.TYPE_TRANSFER_END) break
                        }
                    }

                    P2PProtocol.TYPE_DISCONNECT -> {
                        val msg = try {
                            p2pJson.decodeFromString<DisconnectPayload>(String(frame.payload, Charsets.UTF_8))
                        } catch (_: Exception) {
                            DisconnectPayload()
                        }
                        Log.d(tag, "Remote disconnect from $remoteAddr: ${msg.reason}")
                        break
                    }

                    else -> {
                        Log.w(tag, "Unknown frame type: ${P2PProtocol.typeName(frame.type)}")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            Log.d(tag, "Client connection timed out: $remoteAddr")
        } catch (e: Exception) {
            Log.e(tag, "Error handling client $remoteAddr", e)
            callback?.onError("处理客户端连接出错: ${e.message}")
        } finally {
            heartbeatJob?.cancel()
            encryptionKey?.fill(0)
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
            pendingPairRequests.remove(remoteAddr)
            pairedRemotes.remove(remoteAddr)
            callback?.onClientDisconnected(remoteAddr)
        }
    }

    /**
     * Start a heartbeat coroutine that sends PING every 30 seconds.
     */
    private fun launchHeartbeat(
        scope: CoroutineScope,
        outputStream: OutputStream,
        remoteAddr: String,
        writeLock: ReentrantLock,
        isPaired: AtomicBoolean
    ): Job {
        return scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L)
                if (!isPaired.get()) {
                    continue
                }
                try {
                    // PING is never encrypted
                    writeFrameLocked(outputStream, writeLock, P2PFrame(P2PProtocol.TYPE_PING.toByte(), ByteArray(0)))
                    Log.d(tag, "Sent PING to $remoteAddr")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(tag, "Heartbeat PING failed for $remoteAddr", e)
                    break
                }
            }
        }
    }

    private fun launchPendingPairTimeout(remoteAddr: String, pendingRequest: PendingPairRequest) {
        parentScope.launch(Dispatchers.IO) {
            delay(pendingPairTimeoutMs)

            if (pendingRequest.isPaired.get()) {
                return@launch
            }

            val removed = pendingPairRequests.remove(remoteAddr, pendingRequest)
            if (!removed) {
                return@launch
            }

            try {
                val reject = PairRejectPayload(reason = "Pairing request timed out")
                val payload = p2pJson.encodeToString(PairRejectPayload.serializer(), reject)
                    .toByteArray(Charsets.UTF_8)
                writeFrameLocked(
                    pendingRequest.outputStream,
                    pendingRequest.writeLock,
                    P2PFrame(P2PProtocol.TYPE_PAIR_REJECT.toByte(), payload)
                )
                Log.w(tag, "Pending pair request timed out for $remoteAddr")
            } catch (e: Exception) {
                Log.w(tag, "Failed to send timed-out pair reject to $remoteAddr", e)
            }
        }
    }

    /**
     * Send a frame, encrypting the payload if an encryption key is set and the
     * message type requires encryption.
     */
    private fun sendEncryptedFrame(
        output: OutputStream,
        writeLock: ReentrantLock,
        frame: P2PFrame,
        encryptionKey: ByteArray?
    ) {
        if (encryptionKey != null && P2PCrypto.shouldEncrypt(frame.type)) {
            val encrypted = P2PCrypto.encrypt(encryptionKey, frame.payload)
            writeFrameLocked(output, writeLock, P2PFrame(frame.type, encrypted))
        } else {
            writeFrameLocked(output, writeLock, frame)
        }
    }

    private fun writeFrameLocked(output: OutputStream, writeLock: ReentrantLock, frame: P2PFrame) {
        writeLock.withLock {
            FrameCodec.writeFrame(output, frame)
        }
    }

    /**
     * Decrypt a frame payload if an encryption key is set and the message type
     * requires encryption. Returns the raw payload otherwise.
     */
    private fun decryptIfNeeded(frame: P2PFrame, encryptionKey: ByteArray?): ByteArray {
        if (encryptionKey != null && P2PCrypto.shouldEncrypt(frame.type)) {
            return P2PCrypto.decrypt(encryptionKey, frame.payload)
        }
        return frame.payload
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        pendingPairRequests.clear()
        pairedRemotes.clear()
        Log.i(tag, "Server stopped")
    }

    private data class PendingPairRequest(
        val remoteAddr: String,
        val deviceName: String,
        val outputStream: OutputStream,
        val clientSocket: Socket,
        val writeLock: ReentrantLock,
        val isPaired: AtomicBoolean
    )
}
