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
    fun onPairRequest(
        remoteAddr: String,
        deviceName: String,
        pairCode: String,
        reconnectIps: List<String>,
        reconnectPort: Int?
    )
    fun onTransferProgress(sessionId: String, fileName: String, bytesTransferred: Long, totalBytes: Long) {}
    fun onTransferReceived(sessionId: String, fileName: String, data: ByteArray, billCount: Int)
    fun onPairAccepted(
        sessionId: String,
        remoteAddr: String,
        remoteDeviceName: String,
        pairCode: String,
        reconnectIps: List<String>,
        reconnectPort: Int?
    )
    fun findTrustedSession(
        remoteAddr: String,
        remoteDeviceName: String,
        pairCode: String
    ): String? = null
    fun takePendingIncomingTransfer(sessionId: String, transferId: String): P2PManager.PendingIncomingTransfer? = null
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
    private val acceptedSessions = ConcurrentHashMap<String, AcceptedSession>()
    private val pendingTransfers = ConcurrentHashMap<String, PendingTransfer>()

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
        return acceptPairInternal(pending, sessionId, removePending = true)
    }

    private fun acceptPairInternal(
        pending: PendingPairRequest,
        sessionId: String,
        removePending: Boolean
    ): Boolean {
        try {
            Log.i(tag, "Sending PairAccept to ${pending.remoteAddr}, session=$sessionId")
            val accept = PairAcceptPayload(
                deviceName = pending.deviceName,
                sessionId = sessionId
            )
            val payload = p2pJson.encodeToString(PairAcceptPayload.serializer(), accept)
                .toByteArray(Charsets.UTF_8)
            writeFrameLocked(pending.outputStream, pending.writeLock, P2PFrame(P2PProtocol.TYPE_PAIR_ACCEPT.toByte(), payload))
            if (removePending) {
                pendingPairRequests.remove(pending.remoteAddr)
            }
            pairedRemotes[pending.remoteAddr] = true
            pending.isPaired.set(true)
            pending.sessionId = sessionId
            acceptedSessions[sessionId] = AcceptedSession(
                sessionId = sessionId,
                remoteAddr = pending.remoteAddr,
                remotePort = pending.reconnectPort ?: P2PProtocol.DEFAULT_PORT,
                outputStream = pending.outputStream,
                writeLock = pending.writeLock,
                encryptionKeyRef = pending.encryptionKeyRef,
                clientSocket = pending.clientSocket,
                pairCode = pending.pairCode
            )
            callback?.onPairAccepted(
                sessionId = sessionId,
                remoteAddr = pending.remoteAddr,
                remoteDeviceName = pending.remoteDeviceName,
                pairCode = pending.pairCode,
                reconnectIps = pending.reconnectIps,
                reconnectPort = pending.reconnectPort
            )
            Log.i(tag, "Pair accepted for ${pending.remoteAddr}, session=$sessionId")
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

    suspend fun sendTransfer(
        sessionId: String,
        data: ByteArray,
        billCount: Int,
        fileName: String = "shmtu_transfer.zip",
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val session = acceptedSessions[sessionId]
            ?: return@withContext Result.failure(Exception("服务端会话不存在或已断开"))

        try {
            val transferId = java.util.UUID.randomUUID().toString()
            val offer = TransferOfferPayload(
                transferId = transferId,
                description = "Bill data transfer",
                totalSize = data.size.toLong(),
                billCount = billCount
            )
            val offerPayload = p2pJson.encodeToString(TransferOfferPayload.serializer(), offer)
                .toByteArray(Charsets.UTF_8)
            sendEncryptedFrame(
                session.outputStream,
                session.writeLock,
                P2PFrame(P2PProtocol.TYPE_TRANSFER_OFFER.toByte(), offerPayload),
                session.encryptionKeyRef.get()
            )

            val accepted = waitForTransferResponse(session, transferId)
            if (!accepted) {
                return@withContext Result.failure(Exception("对方拒绝接收"))
            }

            onProgress(0L, data.size.toLong())
            sendTransferViaDedicatedChannel(session, transferId, data, billCount, onProgress)
            Log.i(tag, "Server-side transfer complete: session=$sessionId file=$fileName bytes=${data.size}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Server-side transfer failed for session=$sessionId", e)
            Result.failure(e)
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
        val encryptionKeyRef = java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)

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

                        val trustedSessionId = callback?.findTrustedSession(
                            remoteAddr = remoteAddr,
                            remoteDeviceName = req.deviceName,
                            pairCode = req.pairCode
                        )
                        if (!trustedSessionId.isNullOrBlank()) {
                            val trustedRequest = PendingPairRequest(
                                remoteAddr = remoteAddr,
                                deviceName = ourDeviceName,
                                remoteDeviceName = req.deviceName,
                                pairCode = req.pairCode,
                                reconnectIps = req.listenIps,
                                reconnectPort = req.listenPort,
                                outputStream = outStream,
                                clientSocket = socket,
                                writeLock = writeLock,
                                isPaired = isPairConfirmed,
                                encryptionKeyRef = encryptionKeyRef
                            )
                            if (acceptPairInternal(trustedRequest, trustedSessionId, removePending = false)) {
                                isPaired = true
                                continue
                            }
                        }

                        if (req.pairCode.equals(expectedPairCode, ignoreCase = true)) {
                            // Valid pair code - notify callback for user confirmation
                            val pendingRequest = PendingPairRequest(
                                remoteAddr = remoteAddr,
                                deviceName = ourDeviceName,
                                remoteDeviceName = req.deviceName,
                                pairCode = req.pairCode,
                                reconnectIps = req.listenIps,
                                reconnectPort = req.listenPort,
                                outputStream = outStream,
                                clientSocket = socket,
                                writeLock = writeLock,
                                isPaired = isPairConfirmed,
                                encryptionKeyRef = encryptionKeyRef
                            )
                            pendingPairRequests[remoteAddr] = pendingRequest
                            Log.i(tag, "Pending pair request stored for $remoteAddr, awaiting confirmation")
                            callback?.onPairRequest(
                                remoteAddr = remoteAddr,
                                deviceName = req.deviceName,
                                pairCode = req.pairCode,
                                reconnectIps = req.listenIps,
                                reconnectPort = req.listenPort
                            )
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
                        encryptionKeyRef.set(derivedKey)
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
                        val acceptedSession = acceptedSessions.values.firstOrNull { it.clientSocket == socket }
                        val acceptedSessionId = acceptedSession?.sessionId
                        if (acceptedSessionId != null) {
                            pendingTransfers[pendingTransferKey(acceptedSessionId, offer.transferId)] = PendingTransfer(
                                sessionId = acceptedSessionId,
                                transferId = offer.transferId,
                                totalSize = offer.totalSize,
                                billCount = offer.billCount
                            )
                        }
                    }

                    P2PProtocol.TYPE_TRANSFER_ACCEPT,
                    P2PProtocol.TYPE_TRANSFER_REJECT -> {
                        val acceptedSession = acceptedSessions.values.firstOrNull { it.clientSocket == socket }
                        if (acceptedSession != null) {
                            acceptedSession.responseQueue.offer(frame)
                        } else {
                            Log.w(tag, "Received transfer response for unknown accepted session: $remoteAddr")
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
                        if ((frame.type.toInt() and 0xFF) == P2PProtocol.TYPE_TRANSFER_CHANNEL_OPEN) {
                            handleTransferChannel(
                                firstFrame = frame,
                                inputStream = inStream,
                                outputStream = outStream,
                                writeLock = writeLock,
                                remoteAddr = remoteAddr
                            )
                        } else {
                            Log.w(tag, "Unknown frame type: ${P2PProtocol.typeName(frame.type)}")
                        }
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
            encryptionKeyRef.getAndSet(null)?.fill(0)
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            try { socket.close() } catch (_: Exception) {}
            pendingPairRequests.remove(remoteAddr)
            pairedRemotes.remove(remoteAddr)
            acceptedSessions.entries.removeIf { (_, session) -> session.clientSocket == socket }
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

    private fun waitForTransferResponse(session: AcceptedSession, transferId: String): Boolean {
        while (true) {
            val frame = session.responseQueue.take()
            when (frame.type.toInt() and 0xFF) {
                P2PProtocol.TYPE_TRANSFER_ACCEPT -> {
                    val payload = decryptIfNeeded(frame, session.encryptionKeyRef.get())
                    val accept = p2pJson.decodeFromString<TransferAcceptPayload>(String(payload, Charsets.UTF_8))
                    if (accept.transferId == transferId) {
                        return true
                    }
                }
                P2PProtocol.TYPE_TRANSFER_REJECT -> {
                    val payload = decryptIfNeeded(frame, session.encryptionKeyRef.get())
                    val reject = p2pJson.decodeFromString<TransferRejectPayload>(String(payload, Charsets.UTF_8))
                    if (reject.transferId == transferId) {
                        return false
                    }
                }
                P2PProtocol.TYPE_DISCONNECT -> {
                    throw IllegalStateException("连接已断开")
                }
            }
        }
    }

    private fun sendTransferViaDedicatedChannel(
        session: AcceptedSession,
        transferId: String,
        data: ByteArray,
        billCount: Int,
        onProgress: (Long, Long) -> Unit
    ) {
        val pairCode = session.pairCode ?: throw IllegalStateException("缺少配对码")
        val salt = P2PCrypto.generateSalt()
        val transferKey = P2PCrypto.deriveKey(pairCode, salt)
        val transferSocket = Socket(session.remoteAddr, session.remotePort)
        transferSocket.soTimeout = 30000
        val transferInput = transferSocket.getInputStream()
        val transferOutput = transferSocket.getOutputStream()
        val openPayload = TransferChannelOpenPayload(
            sessionId = session.sessionId,
            transferId = transferId,
            pairCode = pairCode,
            salt = Base64.encodeToString(salt, Base64.NO_WRAP)
        )
        val openBytes = p2pJson.encodeToString(TransferChannelOpenPayload.serializer(), openPayload)
            .toByteArray(Charsets.UTF_8)
        FrameCodec.writeFrame(transferOutput, P2PFrame(P2PProtocol.TYPE_TRANSFER_CHANNEL_OPEN.toByte(), openBytes))
        val readyFrame = readEncryptedFrame(transferInput, transferKey)
            ?: throw IllegalStateException("传输通道未就绪")
        val ready = p2pJson.decodeFromString<TransferChannelReadyPayload>(String(readyFrame.payload, Charsets.UTF_8))
        if (ready.transferId != transferId) {
            throw IllegalStateException("传输通道返回的 transferId 不匹配")
        }

        val chunkSize = 32 * 1024
        var offset = 0
        var sequence = 0
        val total = data.size.toLong()

        try {
            while (offset < data.size) {
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
                    ReentrantLock(),
                    P2PFrame(P2PProtocol.TYPE_TRANSFER_DATA.toByte(), payload),
                    transferKey
                )
                offset = end
                sequence++
                onProgress(offset.toLong(), total)
            }

            val endPayload = TransferEndPayload(
                transferId = transferId,
                checksum = P2PClient.computeChecksum(data)
            )
            val endBytes = p2pJson.encodeToString(TransferEndPayload.serializer(), endPayload)
                .toByteArray(Charsets.UTF_8)
            sendEncryptedFrame(
                transferOutput,
                ReentrantLock(),
                P2PFrame(P2PProtocol.TYPE_TRANSFER_END.toByte(), endBytes),
                transferKey
            )
            val resultFrame = readEncryptedFrame(transferInput, transferKey)
                ?: throw IllegalStateException("未收到传输结果")
            val result = p2pJson.decodeFromString<TransferChannelResultPayload>(String(resultFrame.payload, Charsets.UTF_8))
            if (!result.success) {
                throw IllegalStateException(result.reason.ifBlank { "接收端校验失败" })
            }
            onProgress(total, total)
        } finally {
            transferKey.fill(0)
            try { transferInput.close() } catch (_: Exception) {}
            try { transferOutput.close() } catch (_: Exception) {}
            try { transferSocket.close() } catch (_: Exception) {}
        }
    }

    private fun handleTransferChannel(
        firstFrame: P2PFrame,
        inputStream: InputStream,
        outputStream: OutputStream,
        writeLock: ReentrantLock,
        remoteAddr: String
    ) {
        val open = p2pJson.decodeFromString<TransferChannelOpenPayload>(String(firstFrame.payload, Charsets.UTF_8))
        val pending = pendingTransfers.remove(pendingTransferKey(open.sessionId, open.transferId))
            ?: callback?.takePendingIncomingTransfer(open.sessionId, open.transferId)?.let {
                PendingTransfer(
                    sessionId = it.sessionId,
                    transferId = it.transferId,
                    totalSize = it.totalSize,
                    billCount = it.itemCount
                )
            }
            ?: throw IllegalStateException("没有待接收的传输任务")
        val salt = Base64.decode(open.salt, Base64.NO_WRAP)
        val transferKey = P2PCrypto.deriveKey(open.pairCode, salt)
        try {
            val ready = TransferChannelReadyPayload(transferId = open.transferId)
            val readyBytes = p2pJson.encodeToString(TransferChannelReadyPayload.serializer(), ready)
                .toByteArray(Charsets.UTF_8)
            sendEncryptedFrame(
                outputStream,
                writeLock,
                P2PFrame(P2PProtocol.TYPE_TRANSFER_CHANNEL_READY.toByte(), readyBytes),
                transferKey
            )

            val dataBuffer = java.io.ByteArrayOutputStream()
            var receivedBytes = 0L
            while (true) {
                val frame = readEncryptedFrame(inputStream, transferKey) ?: break
                when (frame.type.toInt() and 0xFF) {
                    P2PProtocol.TYPE_TRANSFER_DATA -> {
                        val chunk = p2pJson.decodeFromString<TransferDataPayload>(String(frame.payload, Charsets.UTF_8))
                        val chunkBytes = Base64.decode(chunk.data, Base64.NO_WRAP)
                        dataBuffer.write(chunkBytes)
                        receivedBytes += chunkBytes.size
                        callback?.onTransferProgress(
                            sessionId = pending.sessionId,
                            fileName = "shmtu_transfer.zip",
                            bytesTransferred = receivedBytes,
                            totalBytes = pending.totalSize
                        )
                    }
                    P2PProtocol.TYPE_TRANSFER_END -> {
                        val end = p2pJson.decodeFromString<TransferEndPayload>(String(frame.payload, Charsets.UTF_8))
                        val bytes = dataBuffer.toByteArray()
                        val checksum = P2PClient.computeChecksum(bytes)
                        val success = checksum == end.checksum
                        if (success) {
                            callback?.onTransferReceived(
                                pending.sessionId,
                                "shmtu_transfer.zip",
                                bytes,
                                pending.billCount
                            )
                        }
                        val result = TransferChannelResultPayload(
                            transferId = open.transferId,
                            success = success,
                            reason = if (success) "" else "Checksum mismatch: expected=${end.checksum}, actual=$checksum"
                        )
                        val resultBytes = p2pJson.encodeToString(TransferChannelResultPayload.serializer(), result)
                            .toByteArray(Charsets.UTF_8)
                        sendEncryptedFrame(
                            outputStream,
                            writeLock,
                            P2PFrame(P2PProtocol.TYPE_TRANSFER_CHANNEL_RESULT.toByte(), resultBytes),
                            transferKey
                        )
                        return
                    }
                    else -> throw IllegalStateException("传输通道消息类型错误: ${P2PProtocol.typeName(frame.type)}")
                }
            }
            throw IllegalStateException("传输通道意外关闭: $remoteAddr")
        } finally {
            transferKey.fill(0)
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

    private fun readEncryptedFrame(input: InputStream, encryptionKey: ByteArray?): P2PFrame? {
        val frame = FrameCodec.readFrame(input) ?: return null
        return if (encryptionKey != null && P2PCrypto.shouldEncrypt(frame.type)) {
            P2PFrame(frame.type, P2PCrypto.decrypt(encryptionKey, frame.payload))
        } else {
            frame
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        pendingPairRequests.clear()
        pairedRemotes.clear()
        pendingTransfers.clear()
        Log.i(tag, "Server stopped")
    }

    private data class PendingPairRequest(
        val remoteAddr: String,
        val deviceName: String,
        val remoteDeviceName: String,
        val pairCode: String,
        val reconnectIps: List<String>,
        val reconnectPort: Int?,
        val outputStream: OutputStream,
        val clientSocket: Socket,
        val writeLock: ReentrantLock,
        val isPaired: AtomicBoolean,
        val encryptionKeyRef: java.util.concurrent.atomic.AtomicReference<ByteArray?>,
        var sessionId: String? = null
    )

    private data class AcceptedSession(
        val sessionId: String,
        val remoteAddr: String,
        val remotePort: Int,
        val outputStream: OutputStream,
        val writeLock: ReentrantLock,
        val encryptionKeyRef: java.util.concurrent.atomic.AtomicReference<ByteArray?>,
        val clientSocket: Socket,
        val pairCode: String? = null,
        val responseQueue: java.util.concurrent.LinkedBlockingQueue<P2PFrame> = java.util.concurrent.LinkedBlockingQueue()
    )

    private data class PendingTransfer(
        val sessionId: String,
        val transferId: String,
        val totalSize: Long,
        val billCount: Int
    )

    private fun pendingTransferKey(sessionId: String, transferId: String): String = "$sessionId:$transferId"
}
