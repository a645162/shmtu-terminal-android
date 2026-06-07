package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.data.notification.WebhookType

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    var testResult by remember { mutableStateOf<String?>(null) }
    var lastTestSuccess by remember { mutableStateOf(false) }

    SettingsDetailScreen(
        title = "通知设置",
        onBack = onBack,
        embedded = embedded
    ) {
        // 通知类型开关
        SettingsCard {
            Text("通知类型", style = MaterialTheme.typography.titleSmall)
            Text(
                "分别控制各类通知的开关，关闭后将不再发送对应通知。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SwitchRow(
                title = "同步完成",
                description = "服务器同步完成后通知结果",
                checked = config.syncCompleteEnabled,
                onCheckedChange = { v -> viewModel.update { it.copy(syncCompleteEnabled = v) } }
            )
            SwitchRow(
                title = "发现新消费",
                description = "新账单发现时发送通知",
                checked = config.newBillsFoundEnabled,
                onCheckedChange = { v -> viewModel.update { it.copy(newBillsFoundEnabled = v) } }
            )
            SwitchRow(
                title = "点对点传输",
                description = "文件传输完成通知",
                checked = config.transferCompleteEnabled,
                onCheckedChange = { v -> viewModel.update { it.copy(transferCompleteEnabled = v) } }
            )
            SwitchRow(
                title = "配对请求",
                description = "收到新设备配对请求时通知",
                checked = config.persistentStatusEnabled,
                onCheckedChange = { v -> viewModel.update { it.copy(persistentStatusEnabled = v) } }
            )
            SwitchRow(
                title = "常驻状态",
                description = "后台服务的持续状态通知",
                checked = config.persistentStatusEnabled,
                onCheckedChange = { v -> viewModel.update { it.copy(persistentStatusEnabled = v) } }
            )
        }

        // 通知样式
        SettingsCard {
            Text("通知样式", style = MaterialTheme.typography.titleSmall)
            SwitchRow(
                title = "浮动通知 (Heads-up)",
                description = "重要通知在屏幕顶部弹出",
                checked = config.useHeadsUp,
                onCheckedChange = { v -> viewModel.update { it.copy(useHeadsUp = v) } }
            )
            SwitchRow(
                title = "夜间静音",
                description = "在指定时段内静默通知（不响铃不振动）",
                checked = config.silentOnNight,
                onCheckedChange = { v -> viewModel.update { it.copy(silentOnNight = v) } }
            )

            if (config.silentOnNight) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = config.nightStartHour.toString(),
                        onValueChange = { txt ->
                            val n = txt.filter { it.isDigit() }.toIntOrNull()
                            if (n != null && n in 0..23) {
                                viewModel.update { it.copy(nightStartHour = n) }
                            }
                        },
                        label = { Text("开始小时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = config.nightEndHour.toString(),
                        onValueChange = { txt ->
                            val n = txt.filter { it.isDigit() }.toIntOrNull()
                            if (n != null && n in 0..23) {
                                viewModel.update { it.copy(nightEndHour = n) }
                            }
                        },
                        label = { Text("结束小时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }

        // 重要新账单阈值
        SettingsCard {
            Text("重要新账单阈值", style = MaterialTheme.typography.titleSmall)
            Text(
                "仅当消费金额大于等于此值时触发新账单通知，0 表示所有新账单都通知。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = config.newBillThresholdAmount.toString(),
                onValueChange = { txt ->
                    val v = txt.toDoubleOrNull()
                    if (v != null && v >= 0.0) {
                        viewModel.update { it.copy(newBillThresholdAmount = v) }
                    }
                },
                label = { Text("金额 (元)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }

        // Webhook 转发
        SettingsCard {
            Text("Webhook 转发", style = MaterialTheme.typography.titleSmall)
            Text(
                "将通知同时推送到飞书/企业微信机器人等外部平台。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SwitchRow(
                title = "启用 Webhook",
                description = "开启后通知会同时通过 Webhook 转发",
                checked = config.webhookEnabled,
                onCheckedChange = { v -> viewModel.update { it.copy(webhookEnabled = v) } }
            )

            if (config.webhookEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Webhook 类型", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WebhookType.values().forEach { type ->
                            FilterChip(
                                selected = config.webhookType == type,
                                onClick = { viewModel.setWebhookType(type) },
                                label = {
                                    Text(
                                        when (type) {
                                            WebhookType.NONE -> "关闭"
                                            WebhookType.FEISHU -> "飞书"
                                            WebhookType.WECHAT_WORK -> "企业微信"
                                            WebhookType.CUSTOM -> "自定义"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = config.webhookUrl,
                        onValueChange = { txt ->
                            viewModel.update { it.copy(webhookUrl = txt) }
                        },
                        label = { Text("Webhook URL") },
                        placeholder = { Text("https://open.feishu.cn/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = config.webhookMessageTemplate,
                        onValueChange = { txt ->
                            viewModel.update { it.copy(webhookMessageTemplate = txt) }
                        },
                        label = { Text("消息模板") },
                        placeholder = { Text("支持 {time} {amount} {merchant} 占位符") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.testWebhook { success, msg ->
                                    lastTestSuccess = success
                                    testResult = msg
                                }
                            }
                        ) {
                            Text("测试发送")
                        }
                        if (testResult != null) {
                            Text(
                                testResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (lastTestSuccess) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }
        }

        SettingsCard {
            Text("说明", style = MaterialTheme.typography.titleSmall)
            Text(
                "通知通过系统通道发出，应用首次启动时会请求通知权限（Android 13+）。" +
                        "Webhook 转发依赖网络可用性，飞书/企业微信机器人需要管理员提前在群组中创建。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
