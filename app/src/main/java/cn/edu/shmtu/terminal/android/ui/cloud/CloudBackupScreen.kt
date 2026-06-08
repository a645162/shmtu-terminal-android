package cn.edu.shmtu.terminal.android.ui.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.data.cloud.BackupStatus
import cn.edu.shmtu.terminal.android.data.cloud.CloudBackupRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    viewModel: CloudBackupViewModel = hiltViewModel()
) {
    val status by viewModel.backupStatus.collectAsState()
    val history by viewModel.backupHistory.collectAsState()
    val message by viewModel.message.collectAsState()

    var serverUrl by remember { mutableStateOf(viewModel.getWebDavServerUrl()) }
    var username by remember { mutableStateOf(viewModel.getWebDavUsername()) }
    var password by remember { mutableStateOf("") }
    var backupRoot by remember { mutableStateOf(viewModel.getWebDavRoot()) }
    var backupPassword by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf("webdav") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("云备份") },
                navigationIcon = { OutlinedButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("存储后端", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("webdav" to "WebDAV", "google_drive" to "Google Drive", "onedrive" to "OneDrive")
                            .forEach { (id, name) ->
                                FilterChip(
                                    selected = selectedProvider == id,
                                    onClick = { selectedProvider = id },
                                    label = { Text(name) },
                                    enabled = id == "webdav"
                                )
                            }
                    }
                    Text("当前阶段：仅 WebDAV 可用。Google Drive / OneDrive 已留接口，需集成 OAuth 后启用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (selectedProvider == "webdav") {
                ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WebDAV 配置", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it },
                            label = { Text("服务器地址") },
                            placeholder = { Text("https://dav.example.com/remote.php/dav") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = username, onValueChange = { username = it },
                            label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = password, onValueChange = { password = it },
                            label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = backupRoot, onValueChange = { backupRoot = it },
                            label = { Text("远端根目录") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                viewModel.configureWebDav(serverUrl, username, password, backupRoot)
                                viewModel.testConnection("webdav")
                            }, modifier = Modifier.weight(1f)) { Text("测试连接") }
                            OutlinedButton(onClick = {
                                viewModel.saveWebDavConfig(serverUrl, username, password, backupRoot)
                            }, modifier = Modifier.weight(1f)) { Text("保存配置") }
                        }
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
                    Button(onClick = { viewModel.backupNow(selectedProvider, backupPassword.ifBlank { null }) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = status !is BackupStatus.Preparing && status !is BackupStatus.Uploading) {
                        Text("立即备份")
                    }
                    if (status is BackupStatus.Preparing || status is BackupStatus.Uploading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        val txt = when (val s = status) {
                            is BackupStatus.Preparing -> s.message
                            is BackupStatus.Uploading -> "上传中 ${s.transferred}/${s.total} 字节"
                            else -> ""
                        }
                        Text(txt, style = MaterialTheme.typography.bodySmall)
                    }
                    if (status is BackupStatus.Success) {
                        Text("✓ 备份成功", color = MaterialTheme.colorScheme.primary)
                    }
                    if (status is BackupStatus.Failed) {
                        Text("✗ 备份失败：${(status as BackupStatus.Failed).reason}",
                            color = MaterialTheme.colorScheme.error)
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("备份历史", style = MaterialTheme.typography.titleSmall)
                    if (history.isEmpty()) {
                        Text("暂无备份", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        history.takeLast(10).reversed().forEach { record ->
                            BackupHistoryItem(record = record, onRestore = {
                                viewModel.restoreBackup(selectedProvider, record, backupPassword.ifBlank { null })
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupHistoryItem(record: CloudBackupRecord, onRestore: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(record.fileName, style = MaterialTheme.typography.bodyMedium)
            Text("${dateFormat.format(Date(record.uploadedAt))} · ${formatSize(record.size)}" +
                if (record.encrypted) " · 🔒" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onRestore) { Text("恢复") }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
