package cn.edu.shmtu.terminal.android.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.data.cloud.BackupStatus
import cn.edu.shmtu.terminal.android.data.cloud.CloudBackupMeta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    viewModel: CloudBackupViewModel = hiltViewModel(),
    embedded: Boolean = false
) {
    val status by viewModel.backupStatus.collectAsState()
    val message by viewModel.message.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val autoEnabled by viewModel.autoEnabled.collectAsState()
    val autoInterval by viewModel.autoIntervalMinutes.collectAsState()
    val maxKeep by viewModel.maxKeep.collectAsState()
    val remoteBackups by viewModel.remoteBackups.collectAsState()
    val loadingRemote by viewModel.loadingRemote.collectAsState()

    // WebDAV 字段
    val webDavUrl by viewModel.webDavServerUrl.collectAsState()
    val webDavUser by viewModel.webDavUsername.collectAsState()
    val webDavPass by viewModel.webDavPassword.collectAsState()
    val webDavRoot by viewModel.webDavRoot.collectAsState()

    var backupPassword by remember { mutableStateOf("") }
    var autoPassword by remember { mutableStateOf("") }
    var maxKeepInput by remember { mutableStateOf(maxKeep.toString()) }
    var restorePassword by remember { mutableStateOf("") }

    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (embedded) 0.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 消息提示
            message?.let {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // === 1. 存储后端选择 ===
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("存储后端", style = MaterialTheme.typography.titleSmall)
                    Column(modifier = Modifier.selectableGroup()) {
                        ProviderOption(
                            id = "webdav",
                            label = "WebDAV",
                            desc = "兼容坚果云、Nextcloud、自建 NAS",
                            selected = selectedProvider == "webdav",
                            onSelect = { viewModel.selectProvider("webdav") }
                        )
                        ProviderOption(
                            id = "google_drive",
                            label = "Google Drive",
                            desc = "通过 OAuth 授权访问 Google 云端硬盘",
                            selected = selectedProvider == "google_drive",
                            onSelect = { viewModel.selectProvider("google_drive") }
                        )
                        ProviderOption(
                            id = "onedrive",
                            label = "OneDrive",
                            desc = "通过 Microsoft OAuth 访问 OneDrive",
                            selected = selectedProvider == "onedrive",
                            onSelect = { viewModel.selectProvider("onedrive") }
                        )
                    }
                }
            }

            // === 2. Provider 配置面板 ===
            when (selectedProvider) {
                "webdav" -> WebDavConfigPanel(viewModel = viewModel)
                "google_drive" -> GoogleDriveConfigPanel(viewModel = viewModel)
                "onedrive" -> OneDriveConfigPanel(viewModel = viewModel)
            }

            // === 3. 连接测试结果 ===
            when (val cs = connectionState) {
                is ConnectionState.Idle -> {}
                is ConnectionState.Testing -> {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("正在测试连接与读写...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is ConnectionState.Connected -> {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(cs.message, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is ConnectionState.Failed -> {
                    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.CloudOff, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(cs.message, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // === 4. 自动备份 ===
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("自动备份", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("定时自动备份", style = MaterialTheme.typography.bodyMedium)
                            Text("开启后按设定间隔自动备份到云端",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = autoEnabled, onCheckedChange = { viewModel.setAutoEnabled(it) })
                    }
                    if (autoEnabled) {
                        Text("备份间隔", style = MaterialTheme.typography.labelMedium)
                        val intervals = listOf(
                            30 to "30分钟", 60 to "1小时", 180 to "3小时",
                            360 to "6小时", 720 to "12小时", 1440 to "每天"
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            intervals.forEach { (mins, label) ->
                                FilterChip(
                                    selected = autoInterval == mins,
                                    onClick = { viewModel.setAutoInterval(mins) },
                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = autoPassword, onValueChange = { autoPassword = it; viewModel.setAutoPassword(it) },
                            label = { Text("自动备份加密密码（留空则不加密）") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                    }
                    // 最大保留数
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("最大保留数量", style = MaterialTheme.typography.bodyMedium)
                            Text("超过后自动删除最旧的备份",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(
                            value = maxKeepInput,
                            onValueChange = {
                                maxKeepInput = it
                                it.toIntOrNull()?.let { v -> if (v in 1..100) viewModel.setMaxKeep(v) }
                            },
                            modifier = Modifier.size(width = 80.dp, height = 56.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            }

            // === 5. 立即备份 ===
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("立即备份", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text("加密密码（留空则不加密）") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = { viewModel.backupNow(backupPassword.ifBlank { null }) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = status !is BackupStatus.Preparing && status !is BackupStatus.Uploading
                    ) { Text("立即备份") }
                    if (status is BackupStatus.Preparing || status is BackupStatus.Uploading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(when (val s = status) {
                            is BackupStatus.Preparing -> s.message
                            is BackupStatus.Uploading -> "上传中 ${s.transferred}/${s.total} 字节"
                            else -> ""
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (status is BackupStatus.Success) Text("✓ 备份成功", color = MaterialTheme.colorScheme.primary)
                    if (status is BackupStatus.Failed) Text("✗ 备份失败：${(status as BackupStatus.Failed).reason}", color = MaterialTheme.colorScheme.error)
                }
            }

            // === 6. 远程备份列表 ===
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("远程备份", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { viewModel.refreshRemoteBackups() }, enabled = !loadingRemote) {
                            if (loadingRemote) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                    if (remoteBackups.isEmpty() && !loadingRemote) {
                        Text("暂无远程备份，点击刷新按钮拉取列表",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    remoteBackups.forEach { meta ->
                        RemoteBackupItem(
                            meta = meta,
                            restorePassword = restorePassword,
                            onRestorePasswordChange = { restorePassword = it },
                            onRestore = { viewModel.restoreFromMeta(meta, restorePassword.ifBlank { null }) },
                            onDelete = { viewModel.deleteRemoteBackup(meta.remotePath) }
                        )
                    }
                }
            }
        }
    }

    if (embedded) {
        content()
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("云备份") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) { content() }
        }
    }
}

// === Provider 选择项 ===
@Composable
private fun ProviderOption(
    id: String, label: String, desc: String,
    selected: Boolean, onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// === WebDAV 配置面板 ===
@Composable
private fun WebDavConfigPanel(viewModel: CloudBackupViewModel) {
    val url by viewModel.webDavServerUrl.collectAsState()
    val user by viewModel.webDavUsername.collectAsState()
    val pass by viewModel.webDavPassword.collectAsState()
    val root by viewModel.webDavRoot.collectAsState()

    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WebDAV 配置", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = url, onValueChange = { viewModel.updateWebDavServerUrl(it) },
                label = { Text("服务器地址") },
                placeholder = { Text("https://dav.example.com/remote.php/dav") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = user, onValueChange = { viewModel.updateWebDavUsername(it) },
                label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = pass, onValueChange = { viewModel.updateWebDavPassword(it) },
                label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = root, onValueChange = { viewModel.updateWebDavRoot(it) },
                label = { Text("远端根目录") }, placeholder = { Text("shmtu-backup") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.testConnection() }, modifier = Modifier.weight(1f)) {
                    Text("测试连接")
                }
                OutlinedButton(onClick = { viewModel.saveWebDavConfig() }, modifier = Modifier.weight(1f)) {
                    Text("保存配置")
                }
            }
        }
    }
}

// === Google Drive 配置面板 ===
@Composable
private fun GoogleDriveConfigPanel(viewModel: CloudBackupViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google Drive", style = MaterialTheme.typography.titleSmall)
            Text("通过 Google OAuth2 授权访问你的 Google Drive。" +
                "授权后应用仅可在指定文件夹内读写备份文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { /* TODO: 启动 Google OAuth 流程 */ }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("登录 Google 账号")
            }
            OutlinedButton(onClick = { viewModel.testConnection() }, modifier = Modifier.fillMaxWidth(),
                enabled = false) { Text("测试连接（需先登录）") }
        }
    }
}

// === OneDrive 配置面板 ===
@Composable
private fun OneDriveConfigPanel(viewModel: CloudBackupViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OneDrive", style = MaterialTheme.typography.titleSmall)
            Text("通过 Microsoft OAuth2 授权访问你的 OneDrive。" +
                "授权后应用仅可在应用文件夹内读写备份文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { /* TODO: 启动 Microsoft OAuth 流程 */ }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("登录 Microsoft 账号")
            }
            OutlinedButton(onClick = { viewModel.testConnection() }, modifier = Modifier.fillMaxWidth(),
                enabled = false) { Text("测试连接（需先登录）") }
        }
    }
}

// === 远程备份条目 ===
@Composable
private fun RemoteBackupItem(
    meta: CloudBackupMeta,
    restorePassword: String,
    onRestorePasswordChange: (String) -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT) }
    val isEncrypted = meta.name.endsWith(".enc")

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(meta.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append(dateFormat.format(Date(meta.lastModified)))
                        append(" · ")
                        append(formatSize(meta.size))
                        if (isEncrypted) append(" · 🔒 加密")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp))
            }
        }
        if (isEncrypted) {
            OutlinedTextField(
                value = restorePassword, onValueChange = onRestorePasswordChange,
                label = { Text("解密密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
        }
        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
            Text("恢复此备份")
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
