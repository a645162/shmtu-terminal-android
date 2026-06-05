package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    dedupeRepository: cn.edu.shmtu.terminal.android.data.dedupe.BillDedupeRepository
) {
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val scope = rememberCoroutineScope()
    var identityStatus by remember { mutableStateOf("") }
    var accountStatus by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(if (isWide) 32.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("身份级去重", style = MaterialTheme.typography.titleMedium)
                    Text("针对当前身份的合并账单, 按 transactionNo 去重 (保留最早的)", style = MaterialTheme.typography.bodySmall)
                    Text(identityStatus.ifEmpty { "点击下方按钮执行" }, style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !running, onClick = {
                        scope.launch {
                            running = true
                            try {
                                val (kept, removed) = dedupeRepository.dedupeIdentity()
                                identityStatus = "OK 身份级去重完成: 保留 $kept, 删除 $removed 条重复"
                            } catch (e: Exception) {
                                identityStatus = "X 失败: ${e.message}"
                            } finally { running = false }
                        }
                    }) { Text(if (running) "进行中..." else "身份级别去重") }
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("账号级去重", style = MaterialTheme.typography.titleMedium)
                    Text("针对当前身份下所有账号的原始账单, 按 transactionNo 去重", style = MaterialTheme.typography.bodySmall)
                    Text(accountStatus.ifEmpty { "点击下方按钮执行" }, style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !running, onClick = {
                        scope.launch {
                            running = true
                            try {
                                val (kept, removed) = dedupeRepository.dedupeAccount(0L)
                                accountStatus = "OK 账号级去重完成: 保留 $kept, 删除 $removed 条重复"
                            } catch (e: Exception) {
                                accountStatus = "X 失败: ${e.message}"
                            } finally { running = false }
                        }
                    }) { Text(if (running) "进行中..." else "账号级别去重") }
                }
            }
        }
    }
}
