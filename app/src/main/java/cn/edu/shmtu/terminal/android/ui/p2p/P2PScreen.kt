package cn.edu.shmtu.terminal.android.ui.p2p

import android.Manifest
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.data.p2p.P2PInfo
import cn.edu.shmtu.terminal.android.data.p2p.P2PSession
import cn.edu.shmtu.terminal.android.data.p2p.P2PTransferProgress
import cn.edu.shmtu.terminal.android.data.p2p.P2PProtocol
import cn.edu.shmtu.terminal.android.data.p2p.QRPayload
import cn.edu.shmtu.terminal.android.domain.model.Identity
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2PScreen(
    onBack: () -> Unit,
    onNavigateToQRScan: () -> Unit = {},
    navController: androidx.navigation.NavHostController? = null,
    viewModel: P2PViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val status by viewModel.status.collectAsState()

    // Observe QR scan result returned from QRScanScreen via savedStateHandle (stored as JSON string)
    val savedStateHandle = navController?.currentBackStackEntry?.savedStateHandle
    if (savedStateHandle != null) {
        LaunchedEffect(savedStateHandle) {
            savedStateHandle.getStateFlow<String?>("p2p_qr_scan_result", null)
                .collect { payloadJson ->
                    if (!payloadJson.isNullOrBlank()) {
                        try {
                            val payload = cn.edu.shmtu.terminal.android.data.p2p.p2pJson
                                .decodeFromString<cn.edu.shmtu.terminal.android.data.p2p.QRPayload>(payloadJson)
                            viewModel.setScannedQRPayload(payload)
                        } catch (e: Exception) {
                            Log.e("P2PScreen", "Failed to parse QR scan result: ${e.message}")
                        }
                        savedStateHandle["p2p_qr_scan_result"] = null
                    }
                }
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("本机二维码", "手动配对", "已配对", "传输")

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.lastMessage) {
        uiState.lastMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Handle scanned QR payload result
    val scannedQRPayload = uiState.scannedQRPayload
    var showQRConfirmDialog by remember { mutableStateOf(false) }

    if (scannedQRPayload != null) {
        showQRConfirmDialog = true
    }

    if (showQRConfirmDialog && scannedQRPayload != null) {
        QRScanConfirmDialog(
            qrPayload = scannedQRPayload,
            sameSubnet = uiState.scannedSameSubnet,
            onConfirm = {
                viewModel.connectToPeerFromQR(scannedQRPayload)
                showQRConfirmDialog = false
                viewModel.clearScannedQRPayload()
            },
            onDismiss = {
                showQRConfirmDialog = false
                viewModel.clearScannedQRPayload()
            }
        )
    }

    // Active transfer for the progress dialog
    val activeTransfer = uiState.transferProgress.firstOrNull { !it.isComplete }

    if (activeTransfer != null) {
        TransferProgressDialog(
            fileName = activeTransfer.fileName,
            bytesTransferred = activeTransfer.bytesTransferred,
            totalBytes = activeTransfer.totalBytes,
            direction = activeTransfer.direction,
            onDismiss = {}
        )
    }

    // Show identity selection dialog when data is received via P2P
    val pendingImport = uiState.pendingImport
    if (pendingImport != null && uiState.identities.isNotEmpty()) {
        IdentitySelectionDialog(
            identities = uiState.identities,
            billCount = pendingImport.billCount,
            onSelect = { identityId ->
                viewModel.importBillsToIdentity(pendingImport, identityId)
            },
            onDismiss = {
                viewModel.dismissImportDialog()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("点对点互传") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> QRTab(
                    status = status,
                    qrPayloadJson = uiState.qrPayloadJson,
                    onStartServer = { viewModel.startServer() },
                    onStopServer = { viewModel.stopServer() }
                )
                1 -> ConnectTab(
                    isConnecting = uiState.isConnecting,
                    connectError = uiState.connectError,
                    connectErrorDetail = uiState.connectErrorDetail,
                    onConnect = { host, port, code -> viewModel.connectToPeer(host, port, code) },
                    onClearError = { viewModel.clearConnectError() },
                    onNavigateToQRScan = onNavigateToQRScan,
                    onQRScanned = { payload -> viewModel.setScannedQRPayload(payload) }
                )
                2 -> PairedTab(
                    sessions = status.sessions,
                    isSending = uiState.isSending,
                    sendError = uiState.sendError,
                    onSendBills = { viewModel.sendBills(it) },
                    onDisconnect = { viewModel.disconnect(it) },
                    onClearError = { viewModel.clearSendError() }
                )
                3 -> TransferTab(
                    progressList = uiState.transferProgress
                )
            }
        }
    }
}

/**
 * Confirmation dialog shown after scanning a P2P QR code.
 * Displays the remote device info and asks the user to confirm pairing.
 * Shows a warning if the IP is not on the same subnet.
 */
@Composable
private fun QRScanConfirmDialog(
    qrPayload: QRPayload,
    sameSubnet: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认配对") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("检测到点对点设备：")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "配对码: ${qrPayload.pairCode.chunked(3).joinToString(" ")}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "端口: ${qrPayload.port}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (qrPayload.ips.isNotEmpty()) {
                            Text(
                                text = "IP: ${qrPayload.ips.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        Text(
                            text = "协议版本: ${qrPayload.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Subnet mismatch warning
                if (!sameSubnet) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF3E0), // Light orange
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "⚠️ 未匹配到同网段 IP，可能无法连接。请确认两台设备在同一局域网内。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "✅ 已匹配同网段 IP",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32)
                    )
                }

                Text(
                    text = if (sameSubnet) "确认连接并配对？" else "仍然尝试连接并配对？",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (sameSubnet) "确认配对" else "仍然配对")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * Dialog for selecting which identity to import received bills into.
 */
@Composable
private fun IdentitySelectionDialog(
    identities: List<Identity>,
    billCount: Int,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIdentityId by remember { mutableStateOf(identities.firstOrNull()?.id) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导入目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "收到 $billCount 条账单，请选择导入到哪个身份：",
                    style = MaterialTheme.typography.bodyMedium
                )
                identities.forEach { identity ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedIdentityId == identity.id,
                            onClick = { selectedIdentityId = identity.id }
                        )
                        Column {
                            Text(
                                text = identity.remark.ifBlank { identity.username },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (identity.remark.isNotBlank()) {
                                Text(
                                    text = identity.username,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedIdentityId?.let { onSelect(it) } },
                enabled = selectedIdentityId != null
            ) {
                Text("导入")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * Tab 1: Display QR code, pair code, and server status.
 */
@Composable
private fun QRTab(
    status: cn.edu.shmtu.terminal.android.data.p2p.P2PStatus,
    qrPayloadJson: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Server status card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (status.isRunning) "服务运行中" else "服务未启动",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (status.isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 0.dp
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(
                                text = if (status.isRunning) "在线" else "离线",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (status.isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (status.isRunning) {
                    OutlinedButton(
                        onClick = onStopServer,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("停止服务")
                    }
                } else {
                    Button(
                        onClick = onStartServer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("启动服务并生成二维码")
                    }
                }
            }
        }

        // QR code display
        if (status.isRunning && status.info != null) {
            val info = status.info
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("让另一台设备扫描此二维码", style = MaterialTheme.typography.bodyMedium)

                    // Generate QR bitmap
                    val qrBitmap = remember(qrPayloadJson) {
                        if (qrPayloadJson.isBlank()) null
                        else generateQRBitmap(qrPayloadJson, 512)
                    }

                    if (qrBitmap != null) {
                        Surface(
                            modifier = Modifier.size(260.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            tonalElevation = 2.dp
                        ) {
                            Box(
                                modifier = Modifier.padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "点对点二维码",
                                    modifier = Modifier.size(228.dp)
                                )
                            }
                        }
                    }

                    // Pair code display
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "配对码",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.pairCode.chunked(3).joinToString(" "),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // IP addresses
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "本机 IP 地址",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        info.ips.forEach { ip ->
                            Text(
                                text = "$ip:${info.port}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }
            }
        }

        // Instructions when server is not running
        if (!status.isRunning) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("使用说明", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "1. 点击上方按钮启动点对点服务\n" +
                                "2. 在另一台设备上扫描二维码或手动输入 IP 和配对码\n" +
                                "3. 确认配对后即可发送或接收账单数据\n" +
                                "4. 确保两台设备在同一局域网内",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Tab 2: Manual IP + port + pair_code input to connect to a peer, plus QR scan button.
 */
@Composable
private fun ConnectTab(
    isConnecting: Boolean,
    connectError: String?,
    connectErrorDetail: String? = null,
    onConnect: (String, Int, String) -> Unit,
    onClearError: () -> Unit,
    onNavigateToQRScan: () -> Unit = {},
    onQRScanned: (QRPayload) -> Unit = {}
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf(P2PProtocol.DEFAULT_PORT.toString()) }
    var pairCode by remember { mutableStateOf("") }

    val context = LocalContext.current

    // Camera permission launcher for QR scan
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onNavigateToQRScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "配对连接",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // QR scan button
        FilledTonalButton(
            onClick = {
                // Check camera permission first
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    onNavigateToQRScan()
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("扫描二维码配对")
        }

        Text(
            text = "或手动输入对方设备的 IP 地址、端口和配对码：",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("IP 地址") },
            placeholder = { Text("例如 192.168.1.100") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnecting
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("端口") },
            placeholder = { Text(P2PProtocol.DEFAULT_PORT.toString()) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnecting
        )

        OutlinedTextField(
            value = pairCode,
            onValueChange = { input ->
                // Only allow uppercase letters and digits; auto-truncate at 6 chars
                val filtered = input.uppercase()
                    .filter { it.isLetterOrDigit() }
                    .take(6)
                pairCode = filtered
            },
            label = { Text("配对码") },
            placeholder = { Text("6 位大写字母或数字") },
            supportingText = {
                if (pairCode.isNotEmpty() && pairCode.length < 6) {
                    Text("还差 ${6 - pairCode.length} 位", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isConnecting
        )

        Button(
            onClick = {
                val portNum = port.toIntOrNull() ?: P2PProtocol.DEFAULT_PORT
                onConnect(host.trim(), portNum, pairCode.trim())
            },
            enabled = host.isNotBlank() && pairCode.isNotBlank() && !isConnecting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("连接中...")
            } else {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("连接并配对")
            }
        }

        if (connectError != null) {
            var showDetailDialog by remember { mutableStateOf(false) }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = connectError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onClearError) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (connectErrorDetail != null) {
                        OutlinedButton(
                            onClick = { showDetailDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("查看详情（用于反馈问题）", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (showDetailDialog && connectErrorDetail != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDetailDialog = false },
                    title = { Text("配对失败详情") },
                    text = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .padding(12.dp)
                            ) {
                                item {
                                    Text(
                                        text = connectErrorDetail,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showDetailDialog = false }) { Text("关闭") }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("错误详情", connectErrorDetail))
                                android.widget.Toast.makeText(context, "已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("复制")
                        }
                    }
                )
            }
        }

        // Instructions
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("提示", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "确保两台设备在同一局域网内。如果无法连接，请检查防火墙设置或尝试使用其他 IP 地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Tab 3: List of paired devices with "发送账单" button each.
 */
@Composable
private fun PairedTab(
    sessions: List<P2PSession>,
    isSending: Boolean,
    sendError: String?,
    onSendBills: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "已配对设备",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (sendError != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = sendError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无已配对设备",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "请先通过二维码或手动输入配对码连接设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            sessions.forEach { session ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = session.remoteDevice,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = session.remoteAddr,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.12f),
                                tonalElevation = 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "已配对",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onSendBills(session.sessionId) },
                                enabled = !isSending && session.canSendBills,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("发送账单")
                            }
                            OutlinedButton(
                                onClick = { onDisconnect(session.sessionId) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("断开")
                            }
                        }

                        if (!session.canSendBills) {
                            Text(
                                text = "该会话由对方主动连接建立，仅支持接收；如需发送，请由本机主动扫描对方二维码重新配对。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab 4: Transfer progress and history.
 */
@Composable
private fun TransferTab(
    progressList: List<P2PTransferProgress>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "传输记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        if (progressList.isEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无传输记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "配对后发送或接收账单数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            progressList.forEach { progress ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = progress.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (progress.isComplete) Color(0xFF4CAF50).copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 0.dp
                            ) {
                                Text(
                                    text = if (progress.isComplete) "完成"
                                    else if (progress.direction == cn.edu.shmtu.terminal.android.data.p2p.TransferDirection.SEND) "发送中"
                                    else "接收中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (progress.isComplete) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        if (!progress.isComplete) {
                            LinearProgressIndicator(
                                progress = { progress.progressFraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF4CAF50)
                            )
                        }

                        Text(
                            text = formatProgressText(progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PairRequestDialog(
    deviceName: String,
    pairCode: String,
    remoteAddr: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onReject,
        title = { Text("配对请求") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("收到来自另一台设备的配对请求：")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "设备名称: $deviceName",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "IP 地址: $remoteAddr",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )
                        Text(
                            text = "配对码: $pairCode",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Text(
                    text = "请确认配对码与对方设备上显示的一致。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("接受")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onReject) {
                Text("拒绝")
            }
        }
    )
}

@Composable
fun TransferProgressDialog(
    fileName: String,
    bytesTransferred: Long,
    totalBytes: Long,
    direction: cn.edu.shmtu.terminal.android.data.p2p.TransferDirection,
    onDismiss: () -> Unit
) {
    val progressFraction = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f
    val isComplete = totalBytes > 0 && bytesTransferred >= totalBytes

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (isComplete) onDismiss() },
        title = {
            Text(if (isComplete) "传输完成" else if (direction == cn.edu.shmtu.terminal.android.data.p2p.TransferDirection.SEND) "正在发送" else "正在接收")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${formatFileSize(bytesTransferred)} / ${formatFileSize(totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            if (isComplete) {
                Button(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

private fun generateQRBitmap(content: String, size: Int): android.graphics.Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}

private fun formatProgressText(progress: P2PTransferProgress): String {
    val direction = if (progress.direction == cn.edu.shmtu.terminal.android.data.p2p.TransferDirection.SEND) "发送" else "接收"
    val percent = (progress.progressFraction * 100).toInt()
    return "$direction: ${formatFileSize(progress.bytesTransferred)} / ${formatFileSize(progress.totalBytes)} ($percent%)"
}
