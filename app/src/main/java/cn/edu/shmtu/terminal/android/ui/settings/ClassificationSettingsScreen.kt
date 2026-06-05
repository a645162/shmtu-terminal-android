package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import kotlinx.coroutines.launch

private const val DEFAULT_RULES_URL =
    "https://raw.githubusercontent.com/a645162/shmtu-terminal/main/database/bill"

@Composable
fun ClassificationSettingsScreen(
    onBack: () -> Unit,
    rulesManager: BillRulesManager,
    embedded: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val store = LocalFeatureStore.current
    val currentUrl by store.rulesUpdateUrl.collectAsState()
    var urlDraft by remember(currentUrl) { mutableStateOf(currentUrl) }
    var status by remember { mutableStateOf("尚未同步") }

    SettingsDetailScreen(
        title = "分类规则设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("规则来源")
            Text(
                "规则文件存放在远程 base 路径下，4 个 toml 文件名固定。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "包含文件: rules.toml / type.toml / position.toml / schedule.toml",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard(emphasized = true) {
            Text("远程规则 base URL")
            Text(
                "可填写自建 GitHub/Gitee raw 仓库地址；留空时使用默认 URL。",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                singleLine = true,
                label = { Text("Remote base URL") },
                placeholder = { Text("https://raw.githubusercontent.com/.../database/bill") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = urlDraft.trim() != currentUrl,
                    onClick = { store.setRulesUpdateUrl(urlDraft.trim()) }
                ) {
                    Text("保存 URL")
                }
                TextButton(
                    onClick = {
                        urlDraft = DEFAULT_RULES_URL
                        store.setRulesUpdateUrl(DEFAULT_RULES_URL)
                    }
                ) {
                    Text("恢复默认")
                }
            }
            Text(
                "当前生效：${rulesManager.currentRemoteBase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard(emphasized = true) {
            Text("立即同步")
            Text(
                "从当前 base URL 拉取 4 个 toml 规则并写入 `filesDir/bill/`，写盘前自动备份为 .bak。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
