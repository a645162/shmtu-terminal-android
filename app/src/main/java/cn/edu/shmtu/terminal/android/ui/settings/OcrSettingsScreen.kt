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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.cas.ocr.OcrModelInfo
import cn.edu.shmtu.cas.ocr.OcrV2TagCatalog
import cn.edu.shmtu.cas.ocr.SHMTU_NCNN_Model
import cn.edu.shmtu.terminal.android.data.local.datastore.CaptchaMode
import cn.edu.shmtu.terminal.android.data.local.datastore.OcrServerType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrSettingsScreen(
    onBack: () -> Unit,
    onOpenOcrTest: () -> Unit,
    embedded: Boolean = false,
    viewModel: OcrSettingsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val captchaMode by settingsViewModel.captchaMode.collectAsState()
    val useLocalOcr by settingsViewModel.useLocalOcr.collectAsState()
    val ocrServerType by settingsViewModel.ocrServerType.collectAsState()
    val ocrServerUrl by settingsViewModel.ocrServerUrl.collectAsState()
    val ocrHttpServerUrl by settingsViewModel.ocrHttpServerUrl.collectAsState()
    val ocrRetryCount by viewModel.ocrRetryCount.collectAsState()
    val ocrModelVersion by viewModel.ocrModelVersion.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showLoadSourceDialog by remember { mutableStateOf(false) }
    var showDownloadSourceDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var showUrlEditor by remember { mutableStateOf(false) }
    var showDeleteModelsDialog by remember { mutableStateOf(false) }
    var showAdvancedOcrDialog by remember { mutableStateOf(false) }

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
                captchaMode = captchaMode,
                useLocalOcr = useLocalOcr,
                ocrServerType = ocrServerType,
                ocrServerUrl = ocrServerUrl,
                ocrHttpServerUrl = ocrHttpServerUrl,
                ocrRetryCount = ocrRetryCount,
                ocrModelVersion = ocrModelVersion,
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
                onSetOcrServerType = settingsViewModel::setOcrServerType,
                onSetOcrServerUrl = settingsViewModel::setOcrServerUrl,
                onSetOcrHttpServerUrl = settingsViewModel::setOcrHttpServerUrl,
                onSetOcrRetryCount = viewModel::setOcrRetryCount,
                onSetOcrModelVersion = viewModel::setOcrModelVersion,
                onSetCaptchaMode = settingsViewModel::setCaptchaMode,
                onShowAdvancedOcrDialog = { showAdvancedOcrDialog = true },
                onOpenOcrTest = onOpenOcrTest,
            )
        }
    } else {
        androidx.compose.material3.Scaffold(
            topBar = {
                MediumTopAppBar(
                    title = { Text("验证码设置") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
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
                    captchaMode = captchaMode,
                    useLocalOcr = useLocalOcr,
                    ocrServerType = ocrServerType,
                    ocrServerUrl = ocrServerUrl,
                    ocrHttpServerUrl = ocrHttpServerUrl,
                    ocrRetryCount = ocrRetryCount,
                    ocrModelVersion = ocrModelVersion,
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
                    onSetOcrServerType = settingsViewModel::setOcrServerType,
                    onSetOcrServerUrl = settingsViewModel::setOcrServerUrl,
                    onSetOcrHttpServerUrl = settingsViewModel::setOcrHttpServerUrl,
                    onSetOcrRetryCount = viewModel::setOcrRetryCount,
                    onSetOcrModelVersion = viewModel::setOcrModelVersion,
                    onSetCaptchaMode = settingsViewModel::setCaptchaMode,
                    onShowAdvancedOcrDialog = { showAdvancedOcrDialog = true },
                    onOpenOcrTest = onOpenOcrTest,
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
        val isHttp = ocrServerType == OcrServerType.HTTP
        UrlEditDialog(
            initialUrl = if (isHttp) ocrHttpServerUrl else ocrServerUrl,
            isHttp = isHttp,
            onConfirm = { url ->
                if (isHttp) settingsViewModel.setOcrHttpServerUrl(url) else settingsViewModel.setOcrServerUrl(url)
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

    // Advanced OCR model settings dialog
    if (showAdvancedOcrDialog) {
        OcrModelAdvancedDialog(
            uiState = uiState,
            onRefreshTags = viewModel::refreshTags,
            onSelectTag = viewModel::selectTag,
            onSelectBackbone = viewModel::selectBackbone,
            onSelectPrecision = viewModel::selectPrecision,
            onDismiss = { showAdvancedOcrDialog = false },
        )
    }
}

@Composable
private fun OcrSettingsContent(
    uiState: OcrSettingsUiState,
    captchaMode: CaptchaMode,
    useLocalOcr: Boolean,
    ocrServerType: OcrServerType,
    ocrServerUrl: String,
    ocrHttpServerUrl: String,
    ocrRetryCount: Int,
    ocrModelVersion: SHMTU_NCNN_Model.ModelVersion,
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
    onSetOcrServerType: (OcrServerType) -> Unit,
    onSetOcrServerUrl: (String) -> Unit,
    onSetOcrHttpServerUrl: (String) -> Unit,
    onSetOcrRetryCount: (Int) -> Unit,
    onSetOcrModelVersion: (SHMTU_NCNN_Model.ModelVersion) -> Unit,
    onSetCaptchaMode: (CaptchaMode) -> Unit,
    onShowAdvancedOcrDialog: () -> Unit,
    onOpenOcrTest: () -> Unit,
) {
    // ===== 验证码模式选择 =====
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("验证码", style = MaterialTheme.typography.titleLarge)
        }
        ListItem(
            headlineContent = { Text("验证码处理方式") },
            supportingContent = {
                Text(
                    when (captchaMode) {
                        CaptchaMode.MANUAL -> "登录时弹出验证码图片，手动输入计算结果"
                        CaptchaMode.AUTO_OCR -> "自动识别验证码，无需手动输入"
                    }
                )
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = captchaMode == CaptchaMode.MANUAL,
                    onClick = { onSetCaptchaMode(CaptchaMode.MANUAL) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("手动输入") }
                SegmentedButton(
                    selected = captchaMode == CaptchaMode.AUTO_OCR,
                    onClick = { onSetCaptchaMode(CaptchaMode.AUTO_OCR) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("自动识别") }
            }
        }
        HorizontalDivider()
    }

    // ===== OCR 配置（仅自动识别模式下显示）=====
    if (captchaMode == CaptchaMode.AUTO_OCR) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OCR 验证码识别", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onRefreshStatus) { Text("刷新") }
        }
        // Model version selector (v1 = 3-resnet legacy, v2 = single TriSlot)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ListItem(
                headlineContent = { Text("版本") },
                supportingContent = { Text("v2 为默认 TriSlot 单模型推理，v1 为旧版 3-resnet") }
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                SegmentedButton(
                    selected = ocrModelVersion == SHMTU_NCNN_Model.ModelVersion.V1,
                    onClick = { onSetOcrModelVersion(SHMTU_NCNN_Model.ModelVersion.V1) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("v1") }
                SegmentedButton(
                    selected = ocrModelVersion == SHMTU_NCNN_Model.ModelVersion.V2,
                    onClick = { onSetOcrModelVersion(SHMTU_NCNN_Model.ModelVersion.V2) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("v2") }
            }
        }
        HorizontalDivider()

        // v2 model status summary
        if (ocrModelVersion == SHMTU_NCNN_Model.ModelVersion.V2) {
            ListItem(
                headlineContent = { Text("模型状态") },
                supportingContent = {
                    if (uiState.hasDownloadedModel) {
                        val currentModel = uiState.models
                            .firstOrNull { it.backbone == uiState.selectedBackbone }
                        val modelName = currentModel?.displayName
                            ?: currentModel?.assetStem
                            ?: uiState.selectedBackbone
                        val sizeStr = currentModel
                            ?.modelSizeM?.let { "%.2f".format(it) } ?: "?"
                        Text(
                            "已就绪 · $modelName ${sizeStr}M · ${uiState.selectedPrecision}",
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "未下载",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
            HorizontalDivider()
        } else {
            // v1 status section (original layout)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }
        }

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
            supportingContent = { Text("关闭后将直接使用远程 OCR 服务器；开启时本地失败也会回退到远程") },
            trailingContent = {
                Switch(
                    checked = useLocalOcr,
                    onCheckedChange = onSetUseLocalOcr
                )
            }
        )
        HorizontalDivider()

        // 远程 OCR 服务器配置（始终可见）
        ListItem(
            headlineContent = { Text("远程 OCR 协议") },
            supportingContent = { Text("RESTful HTTP 为默认推荐方式") }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = ocrServerType == OcrServerType.HTTP,
                    onClick = { onSetOcrServerType(OcrServerType.HTTP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("RESTful") }
                SegmentedButton(
                    selected = ocrServerType == OcrServerType.TCP,
                    onClick = { onSetOcrServerType(OcrServerType.TCP) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("TCP") }
            }
        }
        HorizontalDivider()

        // 根据协议类型显示对应的地址配置
        when (ocrServerType) {
            OcrServerType.HTTP -> {
                ListItem(
                    headlineContent = { Text("HTTP 服务器地址") },
                    supportingContent = { Text(ocrHttpServerUrl) },
                    modifier = Modifier.clickable { onShowUrlEditorChange(true) }
                )
            }
            OcrServerType.TCP -> {
                ListItem(
                    headlineContent = { Text("TCP 服务器地址") },
                    supportingContent = { Text(ocrServerUrl) },
                    modifier = Modifier.clickable { onShowUrlEditorChange(true) }
                )
            }
        }
        HorizontalDivider()

        // 验证码错误重试次数
        ListItem(
            headlineContent = { Text("验证码错误重试次数") },
            supportingContent = {
                Text(
                    "识别失败后自动重新尝试的次数。当前 $ocrRetryCount 次。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        )
        Column(
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

        // Download button + Advanced settings button (v2 simplified layout)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onShowDownloadSourceDialogChange(true) },
                enabled = !uiState.isDownloading,
                modifier = Modifier.weight(1f)
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

            if (ocrModelVersion == SHMTU_NCNN_Model.ModelVersion.V2) {
                OutlinedButton(onClick = onShowAdvancedOcrDialog, modifier = Modifier.weight(1f)) {
                    Text("高级设置")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenOcrTest,
                modifier = Modifier.weight(1f),
            ) {
                Text("打开识别测试")
            }
            Text(
                text = "直接测试主 app 当前本地 / 远程 OCR 链路",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically),
            )
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
    } // end if AUTO_OCR
}

/** Advanced OCR model settings dialog with tag dropdown, model radio list, and precision selector. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrModelAdvancedDialog(
    uiState: OcrSettingsUiState,
    onRefreshTags: () -> Unit,
    onSelectTag: (String) -> Unit,
    onSelectBackbone: (String) -> Unit,
    onSelectPrecision: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OCR 模型高级设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Release Tag dropdown + refresh
                Text("Release Tag", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var tagExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = tagExpanded,
                        onExpandedChange = { tagExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = uiState.selectedTag,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tagExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            label = { Text("Tag") },
                        )
                        ExposedDropdownMenu(
                            expanded = tagExpanded,
                            onDismissRequest = { tagExpanded = false },
                        ) {
                            uiState.tags.forEach { entry ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(entry.tag)
                                            entry.publishedAt?.let {
                                                Text(
                                                    it.take(10),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectTag(entry.tag)
                                        tagExpanded = false
                                    },
                                )
                            }
                            if (uiState.tags.isEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (uiState.isTagsLoading) "加载中..." else "无可用 tag",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = {},
                                    enabled = false,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onRefreshTags, enabled = !uiState.isTagsLoading) {
                        if (uiState.isTagsLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "刷新",
                            )
                        }
                    }
                }
                uiState.tagsError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                HorizontalDivider()

                // Model list (radio group)
                Text("模型", style = MaterialTheme.typography.labelLarge)
                if (uiState.isModelsLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (uiState.modelsError != null) {
                    Text(uiState.modelsError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (uiState.models.isEmpty()) {
                    Text("无可用模型", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    uiState.models.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectBackbone(model.backbone) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = model.backbone == uiState.selectedBackbone,
                                onClick = { onSelectBackbone(model.backbone) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    buildString {
                                        append(model.assetStem)
                                        model.modelSizeM?.let { append("  ${"%.2f".format(it)}M") }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                // subtitle: backbone + metrics
                                val subtitle = buildList {
                                    add(model.backbone)
                                    model.metrics?.let { m ->
                                        m.valAccExpression?.let { add("val %.2f%%".format(it * 100)) }
                                        m.testAccExpression?.let { add("test %.2f%%".format(it * 100)) }
                                    }
                                }
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        subtitle.joinToString("  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Precision selector
                Text("精度", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onSelectPrecision("fp16") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RadioButton(
                            selected = uiState.selectedPrecision == "fp16",
                            onClick = { onSelectPrecision("fp16") },
                        )
                        Text("fp16")
                    }
                    Row(
                        modifier = Modifier
                            .clickable { onSelectPrecision("fp32") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RadioButton(
                            selected = uiState.selectedPrecision == "fp32",
                            onClick = { onSelectPrecision("fp32") },
                        )
                        Text("fp32")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun UrlEditDialog(
    initialUrl: String,
    isHttp: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isHttp) "编辑 HTTP 服务器地址" else "编辑 TCP 服务器地址") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = {
                    Text(if (isHttp) "地址 (如 http://192.168.1.100:21600)" else "地址 (如 192.168.1.100:21601)")
                },
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
