package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.data.local.datastore.CaptchaMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val captchaMode by viewModel.captchaMode.collectAsState()
    val useLocalOcr by viewModel.useLocalOcr.collectAsState()
    val ocrServerUrl by viewModel.ocrServerUrl.collectAsState()

    var showOcrSettings by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("设置") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding)
        ) {
            ListItem(
                headlineContent = { Text("验证码处理") },
                supportingContent = { Text("选择验证码识别方式") }
            )
            HorizontalDivider()

            CaptchaModeSelector(
                mode = captchaMode,
                onModeChange = { viewModel.setCaptchaMode(it) }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = {
                    Text(
                        "OCR 高级设置",
                        color = if (captchaMode == CaptchaMode.AUTO_OCR)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                supportingContent = {
                    Text(
                        if (captchaMode == CaptchaMode.AUTO_OCR) "本地模型 / 远端服务器"
                        else "请先启用自动 OCR",
                        color = if (captchaMode == CaptchaMode.AUTO_OCR)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.outline
                    )
                },
                modifier = Modifier.clickable(
                    enabled = captchaMode == CaptchaMode.AUTO_OCR
                ) {
                    showOcrSettings = true
                }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("关于") },
                supportingContent = { Text("应用信息") },
                modifier = Modifier.clickable { onNavigateToAbout() }
            )
        }
    }

    if (showOcrSettings) {
        OcrSettingsDialog(
            useLocalOcr = useLocalOcr,
            onUseLocalOcrChange = { viewModel.setUseLocalOcr(it) },
            ocrServerUrl = ocrServerUrl,
            onUrlChange = { viewModel.setOcrServerUrl(it) },
            onDismiss = { showOcrSettings = false }
        )
    }

    if (showUrlDialog) {
        UrlEditDialog(
            initialUrl = ocrServerUrl,
            onConfirm = { viewModel.setOcrServerUrl(it) },
            onDismiss = { showUrlDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptchaModeSelector(
    mode: CaptchaMode,
    onModeChange: (CaptchaMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        CaptchaMode.MANUAL to "手动输入",
        CaptchaMode.AUTO_OCR to "自动 OCR"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        ListItem(
            headlineContent = { Text("处理方式") },
            supportingContent = {
                Text(
                    when (mode) {
                        CaptchaMode.MANUAL -> "手动输入"
                        CaptchaMode.AUTO_OCR -> "自动 OCR"
                    }
                )
            },
            trailingContent = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .clickable { expanded = true }
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onModeChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OcrSettingsDialog(
    useLocalOcr: Boolean,
    onUseLocalOcrChange: (Boolean) -> Unit,
    ocrServerUrl: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showUrlEditor by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OCR 高级设置") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("使用本地 OCR 模型") },
                    supportingContent = { Text("优先使用本地 NCNN 模型") },
                    trailingContent = {
                        Switch(
                            checked = useLocalOcr,
                            onCheckedChange = onUseLocalOcrChange
                        )
                    }
                )
                if (!useLocalOcr) {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("远端 OCR 服务器") },
                        supportingContent = { Text(ocrServerUrl) },
                        modifier = Modifier.clickable { showUrlEditor = true }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )

    if (showUrlEditor) {
        UrlEditDialog(
            initialUrl = ocrServerUrl,
            onConfirm = {
                onUrlChange(it)
                showUrlEditor = false
            },
            onDismiss = { showUrlEditor = false }
        )
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
