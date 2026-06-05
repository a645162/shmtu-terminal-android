package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import cn.edu.shmtu.terminal.android.data.dedupe.BillDedupeRepository
import kotlinx.coroutines.launch

@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    dedupeRepository: BillDedupeRepository,
    embedded: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var identityStatus by remember { mutableStateOf("尚未执行") }
    var accountStatus by remember { mutableStateOf("尚未执行") }
    var running by remember { mutableStateOf(false) }

    SettingsDetailScreen(
        title = "数据设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("身份级去重")
            Text(
                "针对当前身份的合并账单，按 transactionNo 去重并保留最早记录。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(identityStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = !running,
                onClick = {
                    scope.launch {
                        running = true
                        try {
                            val (kept, removed) = dedupeRepository.dedupeIdentity()
                            identityStatus = "完成：保留 $kept 条，删除 $removed 条重复记录"
                        } catch (e: Exception) {
                            identityStatus = "失败：${e.message}"
                        } finally {
                            running = false
                        }
                    }
                }
            ) {
                Text(if (running) "处理中..." else "执行身份级去重")
            }
        }

        SettingsCard {
            Text("账号级去重")
            Text(
                "针对当前身份下所有账号的原始账单按 transactionNo 去重。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(accountStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = !running,
                onClick = {
                    scope.launch {
                        running = true
                        try {
                            val (kept, removed) = dedupeRepository.dedupeAccount(0L)
                            accountStatus = "完成：保留 $kept 条，删除 $removed 条重复记录"
                        } catch (e: Exception) {
                            accountStatus = "失败：${e.message}"
                        } finally {
                            running = false
                        }
                    }
                }
            ) {
                Text(if (running) "处理中..." else "执行账号级去重")
            }
        }
    }
}
