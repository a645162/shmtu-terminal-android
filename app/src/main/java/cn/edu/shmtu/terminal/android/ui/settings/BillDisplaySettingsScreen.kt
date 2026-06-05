package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BillDisplaySettingsViewModel @Inject constructor(
    private val store: FeatureSettingsStore
) : ViewModel() {
    val preferParsedBillDisplay = store.preferParsedBillDisplay
    fun setPreferParsedBillDisplay(value: Boolean) = store.setPreferParsedBillDisplay(value)
}

@Composable
fun BillDisplaySettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: BillDisplaySettingsViewModel = hiltViewModel()
) {
    val preferParsedBillDisplay by viewModel.preferParsedBillDisplay.collectAsState()

    SettingsDetailScreen(
        title = "消费展示设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard(emphasized = preferParsedBillDisplay) {
            Text("优先显示解析后的消费位置")
            Text(
                "开启后，首页最近交易、账单列表、详情页和统计里的账单卡片会优先显示解析出的楼栋/窗口；解析失败时自动回退原始消费类型。",
                style = MaterialTheme.typography.bodyMedium
            )
            BillDisplaySwitchRow(
                title = "使用解析结果替代原始消费类型",
                subtitle = "默认开启。关闭后统一显示原始消费类型，例如 NFC刷卡消费。",
                checked = preferParsedBillDisplay,
                onCheckedChange = viewModel::setPreferParsedBillDisplay
            )
        }

        SettingsCard {
            Text("当前生效")
            Text(
                if (preferParsedBillDisplay) {
                    "解析成功显示解析位置，解析失败显示原始值。"
                } else {
                    "统一显示原始消费类型，详情页仍保留解析位置和消费类型字段。"
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BillDisplaySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
