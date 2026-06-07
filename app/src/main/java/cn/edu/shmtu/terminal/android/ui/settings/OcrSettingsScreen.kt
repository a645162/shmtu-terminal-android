package cn.edu.shmtu.terminal.android.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: OcrSettingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val useLocalOcr by settingsViewModel.useLocalOcr.collectAsState()
    val ocrServerUrl by settingsViewModel.ocrServerUrl.collectAsState()
    val ocrRetryCount by viewModel.ocrRetryCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showLoadSourceDialog by remember { mutableStateOf(false) }
    var showDownloadSourceDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var showUrlEditor by remember { mutableStateOf(false) }
    var showDeleteModelsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            if (it.startsWith("模型下载") || it.startsWith("下载失败")) {
                Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            }
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    if (embedded) {
        SettingsDetailBody {
            OcrSettingsContent(
                uiState = uiState,
                useLocalOcr = useLocalOcr,
                ocrServerUrl = ocrServerUrl,
                ocrRetryCount = ocrRetryCount,
                showLoadSourceDialog = showLoadSourceDialog,
                onShowLoadSourceDialogChange = { showLoadSourceDialog = it },
                showDownloadSourceDialog = showDownloadSourceDialog,
                onShowDownloadSourceDialogChange = { showDownloadSourceDialog = it },
                showUrlEditor = showUrlEditor,
                onShowUrlEditorChange = { showUrlEditor = it },
                onReleaseModel = viewModel::releaseModel,
                onVerifyDownloadedModels = viewModel::verifyDownloadedModels,
                onDeleteDownloadedModels = { showDeleteModelsDialog = true },
                onRefreshStatus = viewModel::refreshStatus,
                onSetUseLocalOcr = settingsViewModel::setUseLocalOcr,
                onSetOcrRetryCount = viewModel::setOcrRetryCount
            )
        }
    } else {
        androidx.compose.material3.Scaffold(
            topBar = {
                MediumTopAppBar(
                    title = { Text("OCR 设置") },
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.refreshStatus() }) {
                            Text("刷新")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OcrSettingsContent(
                    uiState = uiState,
                    useLocalOcr = useLocalOcr,
                    ocrServerUrl = ocrServerUrl,
                    ocrRetryCount = ocrRetryCount,
                    showLoadSourceDialog = showLoadSourceDialog,
                    onShowLoadSourceDialogChange = { showLoadSourceDialog = it },
                    showDownloadSourceDialog = showDownloadSourceDialog,
                    onShowDownloadSourceDialogChange = { showDownloadSourceDialog = it },
                    showUrlEditor = showUrlEditor,
                    onShowUrlEditorChange = { showUrlEditor = it },
                    onReleaseModel = viewModel::releaseModel,
                    onVerifyDownloadedModels = viewModel::verifyDownloadedModels,
                    onDeleteDownloadedModels = { showDeleteModelsDialog = true },
                    onRefreshStatus = viewModel::refreshStatus,
                    onSetUseLocalOcr = settingsViewModel::setUseLocalOcr,
                    onSetOcrRetryCount = viewModel::setOcrRetryCount
                )
            }
        }
    }

    // Load source selection dialog
    if (showLoadSourceDialog) {
        val options = buildList {
            if (uiState.hasBuiltInModel) add("从内置资源加载" to true)
            if (uiState.hasDownloadedModel) add("从本地已下载模型加载" to false)
            if (isEmpty()) add("无可用模型" to null)
        }
        AlertDialog(
            onDismissRequest = { showLoadSourceDialog = false },
            title = { Text("选择加载方式") },
            text = {
                Column {
                    options.forEach { (label, fromAssets) ->
                        TextButton(
                            onClick = {
                                showLoadSourceDialog = false
                                if (fromAssets != null) {
                                    showDeviceDialog = fromAssets to true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = fromAssets != null
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLoadSourceDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Device selection dialog
    showDeviceDialog?.let { (fromAssets, _) ->
        if (!uiState.gpuSupported) {
            LaunchedEffect(Unit) {
                viewModel.loadModel(fromAssets = fromAssets, useGpu = false)
                showDeviceDialog = null
            }
        } else {
            AlertDialog(
                onDismissRequest = { showDeviceDialog = null },
                title = { Text("选择运行设备") },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                viewModel.loadModel(fromAssets = fromAssets, useGpu = false)
                                showDeviceDialog = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("CPU")
                        }
                        TextButton(
                            onClick = {
                                viewModel.loadModel(fromAssets = fromAssets, useGpu = true)
                                showDeviceDialog = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("GPU")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDeviceDialog = null }) {
                        Text("取消")
                    }
                }
            )
        }
    }

    // Download source selection dialog
    if (showDownloadSourceDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadSourceDialog = false },
            title = { Text("选择下载源") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.downloadModel(SHMTU_NCNN_Model.ModelSource.GITEE)
                            showDownloadSourceDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("从 Gitee 下载 (国内推荐)")
                    }
                    TextButton(
                        onClick = {
                            viewModel.downloadModel(SHMTU_NCNN_Model.ModelSource.GITHUB)
                            showDownloadSourceDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("从 GitHub 下载")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadSourceDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showUrlEditor) {
        UrlEditDialog(
            initialUrl = ocrServerUrl,
            onConfirm = {
                settingsViewModel.setOcrServerUrl(it)
                showUrlEditor = false
            },
            onDismiss = { showUrlEditor = false }
        )
    }

    if (showDeleteModelsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteModelsDialog = false },
            title = { Text("删除所有本地模型") },
            text = { Text("会删除当前已下载的全部本地模型文件，不影响应用内置模型。继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownloadedModels()
                        showDeleteModelsDialog = false
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteModelsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun OcrSettingsContent(
    uiState: OcrSettingsUiState,
    useLocalOcr: Boolean,
    ocrServerUrl: String,
    ocrRetryCount: Int,
    showLoadSourceDialog: Boolean,
    onShowLoadSourceDialogChange: (Boolean) -> Unit,
    showDownloadSourceDialog: Boolean,
    onShowDownloadSourceDialogChange: (Boolean) -> Unit,
    showUrlEditor: Boolean,
    onShowUrlEditorChange: (Boolean) -> Unit,
    onReleaseModel: () -> Unit,
    onVerifyDownloadedModels: () -> Unit,
    onDeleteDownloadedModels: () -> Unit,
    onRefreshStatus: () -> Unit,
    onSetUseLocalOcr: (Boolean) -> Unit,
    onSetOcrRetryCount: (Int) -> Unit
) {
    SettingsCard {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OCR 状态", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onRefreshStatus) { Text("刷新") }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Model status section
            ListItem(
                headlineContent = { Text("模型状态") },
                supportingContent = {
                    Text(
                        when (uiState.modelStatus) {
                            cn.edu.shmtu.cas.ocr.SHMTU_NCNN.ModelStatus.NOT_LOADED -> "未加载"
                            cn.edu.shmtu.cas.ocr.SHMTU_NCNN.ModelStatus.LOADED_CPU -> "已加载 (CPU)"
                            cn.edu.shmtu.cas.ocr.SHMTU_NCNN.ModelStatus.LOADED_GPU -> "已加载 (GPU)"
                        },
                        color = if (uiState.modelStatus == cn.edu.shmtu.cas.ocr.SHMTU_NCNN.ModelStatus.NOT_LOADED)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                }
            )
            HorizontalDivider()

            // Built-in model
            ListItem(
                headlineContent = { Text("内置模型") },
                supportingContent = {
                    Text(
                        if (uiState.hasBuiltInModel) "可用" else "不可用",
                        color = if (uiState.hasBuiltInModel)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            )
            HorizontalDivider()

            // Downloaded model
            ListItem(
                headlineContent = { Text("本地已下载模型") },
                supportingContent = {
                    Text(
                        if (uiState.hasDownloadedModel) "已下载" else "未下载",
                        color = if (uiState.hasDownloadedModel)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            )
            HorizontalDivider()

            // GPU support
            ListItem(
                headlineContent = { Text("GPU 加速") },
                supportingContent = {
                    Text(
                        if (uiState.gpuSupported) "支持 Vulkan" else "不支持",
                        color = if (uiState.gpuSupported)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            HorizontalDivider()

            // OCR preference
            ListItem(
                headlineContent = { Text("优先使用本地模型") },
                supportingContent = { Text("关闭后将使用远程 OCR 服务器") },
                trailingContent = {
                    Switch(
                        checked = useLocalOcr,
                        onCheckedChange = onSetUseLocalOcr
                    )
                }
            )
            HorizontalDivider()

            if (!useLocalOcr) {
                ListItem(
                    headlineContent = { Text("远程 OCR 服务器") },
                    supportingContent = { Text(ocrServerUrl) },
                    modifier = Modifier.clickable { onShowUrlEditorChange(true) }
                )
                HorizontalDivider()
            }

            // 验证码错误重试次数 — 对齐 Tauri `ocr_retry_count` Slider
            ListItem(
                headlineContent = { Text("验证码错误重试次数") },
                supportingContent = {
                    Text(
                        "识别失败后自动重新尝试的次数。当前 $ocrRetryCount 次。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Slider(
                    value = ocrRetryCount.toFloat(),
                    onValueChange = { onSetOcrRetryCount(it.toInt().coerceIn(1, 20)) },
                    valueRange = 1f..20f,
                    steps = 18
                )
            }
            HorizontalDivider()
        }
    }

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onShowLoadSourceDialogChange(true) },
                enabled = uiState.modelStatus == cn.edu.shmtu.cas.ocr.SHMTU_NCNN.ModelStatus.NOT_LOADED && !uiState.isLoadingModel,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isLoadingModel) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("加载模型")
                }
            }

            Button(
                onClick = onReleaseModel,
                enabled = uiState.modelStatus != cn.edu.shmtu.cas.ocr.SHMTU_NCNN.ModelStatus.NOT_LOADED,
                modifier = Modifier.weight(1f)
            ) {
                Text("释放模型")
            }
        }

        Button(
            onClick = { onShowDownloadSourceDialogChange(true) },
            enabled = !uiState.isDownloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("下载模型")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onVerifyDownloadedModels,
                enabled = uiState.hasDownloadedModel && !uiState.isDownloading && !uiState.isLoadingModel && !uiState.isVerifyingSha256,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isVerifyingSha256) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("验证 SHA256")
                }
            }

            TextButton(
                onClick = onDeleteDownloadedModels,
                enabled = uiState.hasDownloadedModel && !uiState.isDownloading && !uiState.isLoadingModel && !uiState.isVerifyingSha256,
                modifier = Modifier.align(Alignment.CenterVertically)
            ) {
                Text("删除所有本地模型")
            }
        }

        if (uiState.isDownloading) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "文件进度: ${uiState.downloadCurrentFile}/${uiState.downloadTotalFiles}",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { (uiState.overallDownloadProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "当前文件: ${uiState.downloadCurrentFileName ?: "准备中..."}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "当前文件下载进度: ${uiState.currentFileProgress}%",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { (uiState.currentFileProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun UrlEditDialog(
    initialUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑服务器地址") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("地址") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onConfirm(url) },
                enabled = url.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
