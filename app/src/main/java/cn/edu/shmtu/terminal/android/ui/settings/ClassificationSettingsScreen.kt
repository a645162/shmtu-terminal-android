package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import kotlinx.coroutines.launch

@Composable
fun ClassificationSettingsScreen(
    onBack: () -> Unit,
    rulesManager: BillRulesManager,
    embedded: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("尚未同步") }

    SettingsDetailScreen(
        title = "分类规则设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("规则来源")
            Text(
                "GitHub raw URL\nhttps://raw.githubusercontent.com/a645162/shmtu-terminal/main/database/bill/",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsCard {
            Text("包含文件")
            Text(
                "rules.toml\n type.toml\n position.toml\n schedule.toml",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsCard(emphasized = true) {
            Text("立即同步")
            Text(
                "从远程拉取最新分类规则并写入 `filesDir/bill/`。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                scope.launch {
                    val result = rulesManager.downloadAll()
                    status = if (result.allOk) {
                        "同步成功，共更新 ${result.perFile.size} 个文件"
                    } else {
                        result.perFile.entries.joinToString("\n") { (name, item) ->
                            when (item) {
                                is BillRulesManager.DownloadFileResult.Success -> "$name: 成功"
                                is BillRulesManager.DownloadFileResult.Failure -> "$name: ${item.reason}"
                            }
                        }
                    }
                }
            }) {
                Text("同步规则")
            }
        }
    }
}
