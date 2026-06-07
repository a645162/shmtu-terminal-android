package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.edu.shmtu.terminal.android.data.p2p.P2PForegroundService
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore

/**
 * P2P Settings screen.
 * P1-3: Migrated from direct SharedPreferences to SettingsDataStore for consistency.
 */
@Composable
fun P2PSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    settingsDataStore: SettingsDataStore
) {
    val context = LocalContext.current
    val autoStart by settingsDataStore.p2pAutoStart.collectAsState(initial = false)
    val autoAccept by settingsDataStore.p2pAutoAccept.collectAsState(initial = false)
    val autoReconnect by settingsDataStore.p2pAutoReconnect.collectAsState(initial = false)
    val deviceName by settingsDataStore.p2pDeviceName.collectAsState(initial = "")
    val port by settingsDataStore.p2pPort.collectAsState(initial = 19827)

    SettingsDetailScreen(
        title = "P2P 互传",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("基本设置", style = MaterialTheme.typography.titleSmall)

            // Auto-start server toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("自动启动服务", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "应用启动时自动开启点对点传输服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoStart,
                    onCheckedChange = { checked ->
                        settingsDataStore.setP2PAutoStart(checked)
                        if (checked) {
                            P2PForegroundService.start(context)
                        } else {
                            P2PForegroundService.stop(context)
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
                    Text("自动接受连接", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "开启后，收到配对请求将直接接受，不再弹出确认对话框。默认关闭。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoAccept,
                    onCheckedChange = { checked ->
                        settingsDataStore.setP2PAutoAccept(checked)
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
                    Text("自动尝试重连", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "连接断开后自动尝试恢复已配对会话，包括对方先发起建立的会话。默认关闭。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoReconnect,
                    onCheckedChange = { checked ->
                        settingsDataStore.setP2PAutoReconnect(checked)
                    }
                )
            }
        }

        SettingsCard {
            Text("设备信息", style = MaterialTheme.typography.titleSmall)

            // Device name input
            OutlinedTextField(
                value = deviceName,
                onValueChange = { newName ->
                    settingsDataStore.setP2PDeviceName(newName)
                },
                label = { Text("设备名称") },
                placeholder = { Text("在配对时显示的名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Port configuration
            OutlinedTextField(
                value = port.toString(),
                onValueChange = { newPort ->
                    val num = newPort.filter { it.isDigit() }
                    val portNum = num.toIntOrNull()
                    if (portNum != null && portNum in 1..65535) {
                        settingsDataStore.setP2PPort(portNum)
                    }
                },
                label = { Text("端口号") },
                placeholder = { Text("19827") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        SettingsCard {
            Text("说明", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "点对点互传功能允许两台在同一局域网内的设备直接传输账单数据。" +
                        "修改端口号后需重启服务才能生效。" +
                        "默认端口为 19827，如遇冲突可自行修改。点对点服务使用独立端口，与远程访问服务（8080）互不影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
