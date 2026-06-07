package cn.edu.shmtu.terminal.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.edu.shmtu.terminal.android.data.webserver.SettingsDataStoreWebExt
import cn.edu.shmtu.terminal.android.data.webserver.WebServerService

/**
 * Web Server 设置界面
 */
@Composable
fun WebServerSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    webServerSettings: SettingsDataStoreWebExt
) {
    val context = LocalContext.current
    val enabled by webServerSettings.webServerEnabled.collectAsState(initial = false)
    val port by webServerSettings.webServerPort.collectAsState(initial = 8080)
    val token by webServerSettings.webServerToken.collectAsState(initial = "")
    val authToken by webServerSettings.webServerAuthToken.collectAsState(initial = "")
    var portInput by remember(port) { mutableStateOf(port.toString()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    SettingsDetailScreen(
        title = "远程访问 (Web Server)",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("基本设置", style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用 Web 服务", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "在局域网内可通过浏览器访问账单数据 (需 token 鉴权)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { checked ->
                        webServerSettings.setWebServerEnabled(checked)
                        if (checked) {
                            val portValue = portInput.toIntOrNull() ?: 8080
                            webServerSettings.setWebServerPort(portValue)
                            WebServerService.start(context)
                            statusMessage = "已请求启动服务"
                        } else {
                            WebServerService.stop(context)
                            statusMessage = "已请求停止服务"
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("监听端口", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "建议使用 1024-65535 之间未被占用的端口,默认 8080",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = portInput,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() } && value.length <= 5) {
                            portInput = value
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                    singleLine = true
                )
            }

            Button(
                onClick = {
                    val portValue = portInput.toIntOrNull() ?: 8080
                    webServerSettings.setWebServerPort(portValue)
                    WebServerService.start(context)
                    statusMessage = "已请求启动服务,端口: $portValue"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text("启动服务")
            }
            TextButton(
                onClick = {
                    WebServerService.stop(context)
                    statusMessage = "已请求停止服务"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("停止服务")
            }
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        SettingsCard {
            Text("访问信息", style = MaterialTheme.typography.titleSmall)
            Text(
                "当前 URL",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (token.isBlank()) "(服务未启动)" else token,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { copyToClipboard(context, token, "URL") }) {
                    Text("复制 URL")
                }
            }
            Text(
                "鉴权 Token (Bearer)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (authToken.isBlank()) "(尚未生成)" else authToken,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { copyToClipboard(context, authToken, "Token") }) {
                    Text("复制 Token")
                }
            }
        }

        SettingsCard {
            Text("使用说明", style = MaterialTheme.typography.titleSmall)
            Text(
                "1. 启动后,在与本机同一局域网的设备浏览器中打开 URL\n" +
                    "2. 鉴权方式:URL 末尾加 ?token=xxx 或请求头 Authorization: Bearer xxx\n" +
                    "3. 关闭应用前台服务后 Web 服务将停止",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun copyToClipboard(context: Context, value: String, label: String) {
    if (value.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, value))
}
