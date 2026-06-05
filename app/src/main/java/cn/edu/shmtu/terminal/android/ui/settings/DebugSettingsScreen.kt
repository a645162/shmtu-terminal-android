package cn.edu.shmtu.terminal.android.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }

    SettingsDetailScreen(
        title = "调试面板",
        onBack = onBack,
        embedded = embedded
    ) {
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
