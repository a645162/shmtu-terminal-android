package cn.edu.shmtu.terminal.android.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: DebugSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            log = message
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.dismissMessage()
        }
    }

    SettingsDetailScreen(
        title = "调试面板",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("会话调试")
            Text("清理所有账号的一卡通和热水登录 Cookies，并重置登录状态。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = viewModel::clearAllCookies,
                enabled = !uiState.isClearingCookies,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isClearingCookies) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("清理所有账号 Cookies")
                }
            }
        }

        SettingsCard {
            Text("记录前端错误")
            Text("输入错误内容后会附加到 `filesDir/frontend_errors.log`。", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入错误信息") }
            )
            Button(onClick = {
                if (input.isBlank()) return@Button
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val line = "[$ts] $input\n"
                try {
                    val file = File(context.filesDir, "frontend_errors.log")
                    file.appendText(line)
                    log = "已写入 ${file.absolutePath}\n$line"
                    Toast.makeText(context, "已记录", Toast.LENGTH_SHORT).show()
                    input = ""
                } catch (e: Exception) {
                    log = "写入失败: ${e.message}"
                }
            }) {
                Text("写入日志")
            }
        }

        if (log.isNotBlank()) {
            SettingsCard {
                Text("最近一次结果")
                Text(log, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
