package cn.edu.shmtu.terminal.android.ui.p2p

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.p2p.P2PManager
import cn.edu.shmtu.terminal.android.data.p2p.P2PPairRequest
import cn.edu.shmtu.terminal.android.data.p2p.P2PPendingImport
import cn.edu.shmtu.terminal.android.data.p2p.P2PSession
import cn.edu.shmtu.terminal.android.data.p2p.P2PStatus
import cn.edu.shmtu.terminal.android.data.p2p.P2PTransferProgress
import cn.edu.shmtu.terminal.android.data.p2p.QRPayload
import cn.edu.shmtu.terminal.android.domain.model.Identity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class P2PUiState(
    val status: P2PStatus = P2PStatus(),
    val pairRequests: List<P2PPairRequest> = emptyList(),
    val transferProgress: List<P2PTransferProgress> = emptyList(),
    val qrPayloadJson: String = "",
    val isConnecting: Boolean = false,
    val connectError: String? = null,
    val connectErrorDetail: String? = null,
    val sendError: String? = null,
    val sendErrorDetail: String? = null,
    val isSending: Boolean = false,
    val lastMessage: String? = null,
    // Identity selection for P2P import
    val pendingImport: P2PPendingImport? = null,
    val identities: List<Identity> = emptyList(),
    // QR scan result
    val scannedQRPayload: QRPayload? = null,
    // Whether the scanned QR's IP is on the same subnet
    val scannedSameSubnet: Boolean = true
)

@HiltViewModel
class P2PViewModel @Inject constructor(
    private val p2pManager: P2PManager
) : ViewModel() {

    private val tag = "P2PViewModel"

    private val _uiState = MutableStateFlow(P2PUiState())
    val uiState: StateFlow<P2PUiState> = _uiState.asStateFlow()

    val status: StateFlow<P2PStatus> = p2pManager.status
    val pairRequests: StateFlow<List<P2PPairRequest>> = p2pManager.pairRequests
    val transferProgress: StateFlow<List<P2PTransferProgress>> = p2pManager.transferProgress

    init {
        viewModelScope.launch {
            p2pManager.status.collect { status ->
                _uiState.value = _uiState.value.copy(
                    status = status,
                    qrPayloadJson = if (status.isRunning) p2pManager.generateQRPayloadJson() else ""
                )
            }
        }
        viewModelScope.launch {
            p2pManager.pairRequests.collect { requests ->
                _uiState.value = _uiState.value.copy(pairRequests = requests)
            }
        }
        viewModelScope.launch {
            p2pManager.transferProgress.collect { progress ->
                _uiState.value = _uiState.value.copy(transferProgress = progress)
            }
        }
        // Collect pending import events from P2PManager
        viewModelScope.launch {
            p2pManager.pendingImport.collect { pending ->
                val identities = p2pManager.getIdentitiesForImport()
                if (identities.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        lastMessage = "没有可用的身份，无法导入账单"
                    )
                } else if (identities.size == 1) {
                    // Only one identity — import directly without dialog
                    importBillsToIdentity(pending, identities.first().id)
                } else {
                    // Multiple identities — show selection dialog
                    _uiState.value = _uiState.value.copy(
                        pendingImport = pending,
                        identities = identities
                    )
                }
            }
        }
    }

    fun startServer() {
        p2pManager.startServer()
    }

    fun stopServer() {
        p2pManager.stopServer()
    }

    fun connectToPeer(host: String, port: Int, pairCode: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true, connectError = null, connectErrorDetail = null)
            Log.i(tag, "=== connectToPeer: host=$host port=$port pairCode='$pairCode' ===")
            val result = p2pManager.connectToPeer(host, port, pairCode)
            val ex = result.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                isConnecting = false,
                connectError = if (result.isFailure) friendlyError(ex) else null,
                connectErrorDetail = if (result.isFailure) detailError(ex, host, port) else null,
                lastMessage = if (result.isSuccess) "已成功配对 ${result.getOrNull()?.remoteDevice}" else null
            )
        }
    }

    /**
     * Convert a thrown exception to a user-friendly short message.
     */
    private fun friendlyError(ex: Throwable?): String {
        val raw = ex?.message ?: ex?.javaClass?.simpleName ?: "未知错误"
        return when {
            raw.contains("Connection refused", ignoreCase = true) -> "连接被拒绝：目标设备未启动服务或端口被占用"
            raw.contains("Connection timed out", ignoreCase = true) -> "连接超时：无法到达目标设备"
            raw.contains("Pair code mismatch", ignoreCase = true) -> "配对码错误：请确认与对方设备显示的一致"
            raw.contains("Pair rejected", ignoreCase = true) -> "配对被拒绝：对方拒绝了配对请求"
            raw.contains("Encryption verification failed", ignoreCase = true) -> "加密验证失败：配对码错误或被篡改"
            raw.contains("Peer does not support encryption", ignoreCase = true) -> "对方设备不支持加密"
            raw.contains("Failed to connect", ignoreCase = true) -> "连接失败：网络不通或目标不可达"
            raw.contains("no route to host", ignoreCase = true) -> "找不到目标主机：请检查 IP 地址"
            raw.contains("Software caused connection abort", ignoreCase = true) -> "连接被中断"
            else -> "配对失败：$raw"
        }
    }

    /**
     * Build a detailed technical error string for the "View details" dialog.
     * Includes local diagnostics, remote endpoint, and the underlying stack trace.
     */
    private fun detailError(ex: Throwable?, host: String, port: Int): String {
        val sb = StringBuilder()
        sb.appendLine("时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        sb.appendLine("目标：$host:$port")
        sb.appendLine("本地 IP：${p2pManager.getLocalIPs().joinToString(", ")}")
        sb.appendLine()
        sb.appendLine("错误类型：${ex?.javaClass?.name ?: "未知"}")
        sb.appendLine("错误信息：${ex?.message ?: "无"}")
        sb.appendLine()
        if (ex != null) {
            sb.appendLine("堆栈跟踪：")
            var cur: Throwable? = ex
            var depth = 0
            while (cur != null && depth < 5) {
                sb.appendLine("--- Cause #$depth ---")
                sb.appendLine(cur.stackTraceToString())
                cur = cur.cause
                depth++
            }
        }
        return sb.toString()
    }

    /**
     * Connect using a scanned QR payload: auto-select best IP on same subnet.
     * If no same-subnet IP is found, shows a warning in the confirm dialog but still allows connecting.
     */
    fun connectToPeerFromQR(qrPayload: QRPayload) {
        Log.i(tag, "=== connectToPeerFromQR ===")
        Log.i(tag, "Scanned QR: ips=${qrPayload.ips} port=${qrPayload.port} pairCode='${qrPayload.pairCode}' version=${qrPayload.version}")
        val localIps = p2pManager.getLocalIPs()
        Log.i(tag, "Local IPs: $localIps")
        val selection = p2pManager.selectBestIp(qrPayload.ips)
        Log.i(tag, "IP selection: ${selection.ip}, sameSubnet=${selection.sameSubnet}")
        if (selection.ip == null) {
            Log.w(tag, "No valid IP found in QR payload")
            _uiState.value = _uiState.value.copy(
                connectError = "二维码中未包含有效 IP 地址"
            )
            return
        }
        _uiState.value = _uiState.value.copy(scannedSameSubnet = selection.sameSubnet)
        connectToPeer(selection.ip, qrPayload.port, qrPayload.pairCode)
    }

    /**
     * Set the scanned QR payload result from the camera scanner.
     */
    fun setScannedQRPayload(payload: QRPayload?) {
        _uiState.value = _uiState.value.copy(scannedQRPayload = payload)
    }

    /**
     * Clear the scanned QR payload after it has been processed.
     */
    fun clearScannedQRPayload() {
        _uiState.value = _uiState.value.copy(scannedQRPayload = null)
    }

    fun acceptPairing(remoteAddr: String) {
        p2pManager.acceptPairRequest(remoteAddr)
    }

    fun rejectPairing(remoteAddr: String) {
        p2pManager.rejectPairRequest(remoteAddr)
    }

    fun sendBills(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, sendError = null)
            val result = p2pManager.sendBills(sessionId)
            _uiState.value = _uiState.value.copy(
                isSending = false,
                sendError = if (result.isFailure) result.exceptionOrNull()?.message else null,
                lastMessage = if (result.isSuccess) "账单发送成功" else null
            )
        }
    }

    fun disconnect(sessionId: String) {
        p2pManager.disconnectSession(sessionId)
    }

    /**
     * Import received bills into the selected identity.
     */
    fun importBillsToIdentity(pendingImport: P2PPendingImport, identityId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingImport = null)
            val result = p2pManager.importBills(pendingImport.data, identityId)
            _uiState.value = _uiState.value.copy(
                lastMessage = if (result.isSuccess) {
                    "已导入 ${result.getOrDefault(0)} 条账单"
                } else {
                    "导入失败: ${result.exceptionOrNull()?.message}"
                }
            )
        }
    }

    /**
     * Dismiss the identity selection dialog without importing.
     */
    fun dismissImportDialog() {
        _uiState.value = _uiState.value.copy(pendingImport = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(lastMessage = null)
    }

    fun clearConnectError() {
        _uiState.value = _uiState.value.copy(connectError = null)
    }

    fun clearSendError() {
        _uiState.value = _uiState.value.copy(sendError = null)
    }

    fun parseQRContent(content: String): QRPayload? {
        return p2pManager.parseQRPayloadJson(content)
    }

    override fun onCleared() {
        super.onCleared()
        // P2PManager is a @Singleton — do NOT call destroy() here.
        // The server lifecycle is managed by the UI (start/stop buttons).
        // P2PManager.destroy() should only be called from Application.onTerminate()
        // or similar app-level lifecycle events.
    }
}
