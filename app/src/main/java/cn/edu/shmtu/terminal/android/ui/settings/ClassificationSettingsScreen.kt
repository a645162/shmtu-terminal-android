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
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassificationSettingsScreen(
    onBack: () -> Unit,
    rulesManager: BillRulesManager
) {
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类规则设置") },
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
                    Text("规则来源", style = MaterialTheme.typography.titleMedium)
                    Text("GitHub raw URL:", style = MaterialTheme.typography.bodySmall)
                    Text("https://raw.githubusercontent.com/a645162/shmtu-terminal/main/database/bill/", style = MaterialTheme.typography.bodySmall)
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("4 个文件", style = MaterialTheme.typography.titleMedium)
                    Text("- rules.toml (合并 type+position+schedule)\n- type.toml (13 条类型)\n- position.toml (19 条位置)\n- schedule.toml (4 个用餐时段)", style = MaterialTheme.typography.bodySmall)
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("立即同步", style = MaterialTheme.typography.titleMedium)
                    Text(status.ifEmpty { "点击下方按钮从 GitHub 拉取最新规则, 写盘到 filesDir/bill/" }, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        scope.launch {
                            val r = rulesManager.downloadAll()
                            status = if (r.allOk) {
                                "OK 同步成功 (${r.perFile.size} 个文件)"
                            } else {
                                val failed = r.perFile.entries.mapNotNull { (name, res) ->
                                    if (res is BillRulesManager.DownloadFileResult.Failure) "$name: ${res.reason}" else null
                                }.joinToString("\n")
                                "X 失败:\n$failed"
                            }
                        }
                    }) { Text("立即同步") }
                }
            }
        }
    }
}
