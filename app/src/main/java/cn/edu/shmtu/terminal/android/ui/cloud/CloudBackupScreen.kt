package cn.edu.shmtu.terminal.android.ui.cloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
    val context = LocalContext.current
    val status by viewModel.backupStatus.collectAsState()
    val message by viewModel.message.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val deviceFlowState by viewModel.deviceFlowState.collectAsState()
    val autoEnabled by viewModel.autoEnabled.collectAsState()
    val autoInterval by viewModel.autoIntervalMinutes.collectAsState()
    val maxKeep by viewModel.maxKeep.collectAsState()
    val remoteBackups by viewModel.remoteBackups.collectAsState()
    val loadingRemote by viewModel.loadingRemote.collectAsState()
    val googleLoggedIn by viewModel.googleLoggedIn.collectAsState()
    val oneDriveLoggedIn by viewModel.oneDriveLoggedIn.collectAsState()

    val webDavUrl by viewModel.webDavServerUrl.collectAsState()
    val webDavUser by viewModel.webDavUsername.collectAsState()
    val webDavPass by viewModel.webDavPassword.collectAsState()
    val webDavRoot by viewModel.webDavRoot.collectAsState()
    val googleClientId by viewModel.googleClientId.collectAsState()
    val googleClientSecret by viewModel.googleClientSecret.collectAsState()
    val oneDriveClientId by viewModel.oneDriveClientId.collectAsState()

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
            message?.let {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                    Text(text = it, modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("存储后端", style = MaterialTheme.typography.titleSmall)
                    Column(modifier = Modifier.selectableGroup()) {
                        ProviderOption("webdav", "WebDAV", "兼容坚果云、Nextcloud、自建 NAS",
                            selectedProvider == "webdav") { viewModel.selectProvider("webdav") }
                        ProviderOption("google_drive", "Google Drive", "通过 OAuth 授权（需填入 Client ID + Secret）",
                            selectedProvider == "google_drive") { viewModel.selectProvider("google_drive") }
                        ProviderOption("onedrive", "OneDrive", "通过 Microsoft OAuth 授权（需填入 Client ID）",
                            selectedProvider == "onedrive") { viewModel.selectProvider("onedrive") }
                    }
                }
            }
            when (selectedProvider) {
                "webdav" -> WebDavConfigPanel(webDavUrl, webDavUser, webDavPass, webDavRoot, viewModel)
                "google_drive" -> GoogleDriveConfigPanel(googleClientId, googleClientSecret, googleLoggedIn, deviceFlowState, viewModel, context)
                "onedrive" -> OneDriveConfigPanel(oneDriveClientId, oneDriveLoggedIn, deviceFlowState, viewModel, context)
            }
            when (val cs = connectionState) {
                is ConnectionState.Idle -> {}
                is ConnectionState.Testing -> StatusCard(loading = true, text = "正在测试连接与读写...")
                is ConnectionState.Connected -> StatusCard(icon = Icons.Default.Restore, text = cs.message, success = true)
                is ConnectionState.Failed -> StatusCard(icon = Icons.Default.Delete, text = cs.message, success = false)
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("自动备份", style = MaterialTheme.typography.titleSmall)
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
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
                        OutlinedTextField(value = autoPassword,
                            onValueChange = { autoPassword = it; viewModel.setAutoPassword(it) },
                            label = { Text("自动备份加密密码（留空则不加密）") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("最大保留数量", style = MaterialTheme.typography.bodyMedium)
                            Text("超过后自动删除最旧的备份",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedTextField(value = maxKeepInput,
                            onValueChange = {
                                maxKeepInput = it
                                it.toIntOrNull()?.let { v -> if (v in 1..100) viewModel.setMaxKeep(v) }
                            },
                            modifier = Modifier.size(width = 80.dp, height = 56.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true)
                    }
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("立即备份", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it },
                        label = { Text("加密密码（留空则不加密）") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { viewModel.backupNow(backupPassword.ifBlank { null }) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = status !is BackupStatus.Preparing && status !is BackupStatus.Uploading) {
                        Text("立即备份")
                    }
                    if (status is BackupStatus.Preparing || status is BackupStatus.Uploading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(when (val s = status) {
                            is BackupStatus.Preparing -> s.message
                            is BackupStatus.Uploading -> "上传中 ${s.transferred}/${s.total} 字节"
                            else -> ""
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (status is BackupStatus.Success) Text("✓ 备份成功", color = MaterialTheme.colorScheme.primary)
                    if (status is BackupStatus.Failed) Text("✗ 备份失败：${(status as BackupStatus.Failed).reason}",
                        color = MaterialTheme.colorScheme.error)
                }
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
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
                        RemoteBackupItem(meta = meta, restorePassword = restorePassword,
                            onRestorePasswordChange = { restorePassword = it },
                            onRestore = { viewModel.restoreFromMeta(meta, restorePassword.ifBlank { null }) },
                            onDelete = { viewModel.deleteRemoteBackup(meta.remotePath) })
                    }
                }
            }
        }
    }

    if (embedded) content()
    else {
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
        ) { innerPadding -> Box(modifier = Modifier.padding(innerPadding)) { content() } }
    }
}

@Composable
private fun ProviderOption(id: String, label: String, desc: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WebDavConfigPanel(url: String, user: String, pass: String, root: String, viewModel: CloudBackupViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("WebDAV 配置", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = url, onValueChange = { viewModel.updateWebDavServerUrl(it) },
                label = { Text("服务器地址") }, placeholder = { Text("https://dav.example.com/remote.php/dav") },
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
                Button(onClick = { viewModel.testConnection() }, modifier = Modifier.weight(1f)) { Text("测试连接") }
                OutlinedButton(onClick = { viewModel.saveWebDavConfig() }, modifier = Modifier.weight(1f)) { Text("保存配置") }
            }
        }
    }
}

@Composable
private fun GoogleDriveConfigPanel(
    clientId: String, clientSecret: String, loggedIn: Boolean, deviceFlowState: DeviceFlowState,
    viewModel: CloudBackupViewModel, context: Context
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Google Drive 配置", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = clientId, onValueChange = { viewModel.updateGoogleClientId(it) },
                label = { Text("Client ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = clientSecret, onValueChange = { viewModel.updateGoogleClientSecret(it) },
                label = { Text("Client Secret") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("提示：在 Google Cloud Console 创建 OAuth Client（应用类型：TVs and Limited Input devices）",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("状态：${when {
                loggedIn -> "✓ 已登录"
                clientId.isBlank() || clientSecret.isBlank() -> "⚠ 待配置 Client ID + Secret"
                else -> "未登录"
            }}", style = MaterialTheme.typography.bodySmall,
                color = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            DeviceFlowStatusView(deviceFlowState, context)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.startDeviceFlowLogin() },
                    enabled = clientId.isNotBlank() && clientSecret.isNotBlank() &&
                        deviceFlowState !is DeviceFlowState.WaitingForAuth && deviceFlowState !is DeviceFlowState.Loading,
                    modifier = Modifier.weight(1f)) { Text(if (loggedIn) "重新登录" else "登录 Google") }
                OutlinedButton(onClick = { viewModel.testConnection() },
                    enabled = loggedIn, modifier = Modifier.weight(1f)) { Text("测试连接") }
            }
            if (loggedIn) {
                OutlinedButton(onClick = { viewModel.logoutGoogle() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("登出 Google Drive")
                }
            }
        }
    }
}

@Composable
private fun OneDriveConfigPanel(
    clientId: String, loggedIn: Boolean, deviceFlowState: DeviceFlowState,
    viewModel: CloudBackupViewModel, context: Context
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("OneDrive 配置", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = clientId, onValueChange = { viewModel.updateOneDriveClientId(it) },
                label = { Text("Client ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("提示：在 Azure Portal 注册应用（应用类型：Mobile and desktop applications，公用客户端）",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("状态：${when {
                loggedIn -> "✓ 已登录"
                clientId.isBlank() -> "⚠ 待配置 Client ID"
                else -> "未登录"
            }}", style = MaterialTheme.typography.bodySmall,
                color = if (loggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            DeviceFlowStatusView(deviceFlowState, context)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.startDeviceFlowLogin() },
                    enabled = clientId.isNotBlank() &&
                        deviceFlowState !is DeviceFlowState.WaitingForAuth && deviceFlowState !is DeviceFlowState.Loading,
                    modifier = Modifier.weight(1f)) { Text(if (loggedIn) "重新登录" else "登录 Microsoft") }
                OutlinedButton(onClick = { viewModel.testConnection() },
                    enabled = loggedIn, modifier = Modifier.weight(1f)) { Text("测试连接") }
            }
            if (loggedIn) {
                OutlinedButton(onClick = { viewModel.logoutOneDrive() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("登出 OneDrive")
                }
            }
        }
    }
}

@Composable
private fun DeviceFlowStatusView(state: DeviceFlowState, context: Context) {
    when (state) {
        is DeviceFlowState.Idle -> {}
        is DeviceFlowState.Loading -> StatusCard(loading = true, text = "正在连接 OAuth 服务器...")
        is DeviceFlowState.WaitingForAuth -> {
            ElevatedCard(modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请在浏览器中打开下面的链接，输入以下代码授权：",
                        style = MaterialTheme.typography.bodyMedium)
                    Text(state.info.verificationUrl, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(state.info.userCode,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("url", state.info.verificationUrl))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制链接")
                        }
                        Button(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("code", state.info.userCode))
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制代码")
                        }
                        Button(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.info.verificationUrl))
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }, modifier = Modifier.weight(1f)) { Text("打开") }
                    }
                    Text("⏳ 等待授权中（代码有效期 ${state.info.expiresInSec / 60} 分钟）...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        is DeviceFlowState.Success -> StatusCard(icon = Icons.Default.Restore, text = "✓ 授权成功", success = true)
        is DeviceFlowState.Failed -> StatusCard(icon = Icons.Default.Delete, text = "✗ ${state.message}", success = false)
    }
}

@Composable
private fun StatusCard(loading: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, text: String, success: Boolean? = null) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(20.dp),
                    tint = when (success) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface })
            }
            Text(text, style = MaterialTheme.typography.bodyMedium,
                color = when (success) { true -> MaterialTheme.colorScheme.primary; false -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurface })
        }
    }
}

@Composable
private fun RemoteBackupItem(
    meta: CloudBackupMeta, restorePassword: String,
    onRestorePasswordChange: (String) -> Unit,
    onRestore: () -> Unit, onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT) }
    val isEncrypted = meta.name.endsWith(".enc")
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
            OutlinedTextField(value = restorePassword, onValueChange = onRestorePasswordChange,
                label = { Text("解密密码") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true)
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
