package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val store = LocalFeatureStore.current
    val rangeOptions = listOf(
        "week" to "最近一周",
        "half_month" to "半个月",
        "month" to "一个月",
        "half_year" to "半年",
        "year" to "一年",
        "all" to "全部"
    )

    SettingsDetailScreen(
        title = "同步设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("同步页数上限")
            Text("当前最多拉取 ${store.syncMaxPages.value} 页账单数据。", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = store.syncMaxPages.value.toFloat(),
                onValueChange = { store.setSyncMaxPages(it.toInt()) },
                valueRange = 10f..500f
            )
        }

        SettingsCard {
            Text("提前停止阈值")
            Text("连续 ${store.syncEarlyStop.value} 页无有效新数据时提前结束。", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = store.syncEarlyStop.value.toFloat(),
                onValueChange = { store.setSyncEarlyStop(it.toInt()) },
                valueRange = 1f..20f
            )
        }

        SettingsCard {
            Text("同步策略")
            SettingsSwitchRow(
                title = "跳过已毕业账号",
                subtitle = "减少无效请求和失败重试。",
                checked = store.syncSkipGraduated.value,
                onCheckedChange = { store.setSyncSkipGraduated(it) }
            )
            SettingsSwitchRow(
                title = "同步后自动合并",
                subtitle = "同步结束后自动做账单合并处理。",
                checked = store.syncAutoMerge.value,
                onCheckedChange = { store.setSyncAutoMerge(it) }
            )
        }

        SettingsCard(emphasized = store.autoSyncEnabled.value) {
            Text("自动同步")
            SettingsSwitchRow(
                title = "启用定时账单同步",
                subtitle = "在后台按固定间隔自动检查并执行同步。",
                checked = store.autoSyncEnabled.value,
                onCheckedChange = { store.setAutoSyncEnabled(it) }
            )
            if (store.autoSyncEnabled.value) {
                Text("检查间隔: ${store.autoSyncInterval.value} 分钟", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = store.autoSyncInterval.value.toFloat(),
                    onValueChange = { store.setAutoSyncInterval(it.toInt()) },
                    valueRange = 5f..1440f
                )
                Text("自动同步范围", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rangeOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = store.autoSyncRange.value == value,
                            onClick = { store.setAutoSyncRange(value) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
