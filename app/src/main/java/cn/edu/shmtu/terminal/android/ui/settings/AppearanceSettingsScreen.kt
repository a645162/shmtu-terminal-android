package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val store: FeatureSettingsStore,
    @Suppress("unused") private val legacyStore: SettingsDataStore
) : ViewModel() {
    val themeMode = store.themeMode
    val decimalPlaces = store.decimalPlaces
    fun setThemeMode(v: String) = store.setThemeMode(v)
    fun setDecimalPlaces(n: Int) = store.setDecimalPlaces(n)
}

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: AppearanceViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val decimalPlaces by viewModel.decimalPlaces.collectAsState()
    val themeOptions = listOf(
        "system" to "跟随系统",
        "light" to "浅色",
        "dark" to "深色"
    )

    SettingsDetailScreen(
        title = "界面设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("主题模式")
            Text("决定应用整体明暗风格。", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                themeOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = themeMode == value,
                        onClick = { viewModel.setThemeMode(value) },
                        label = { Text(label) }
                    )
                }
            }
            SettingsExampleBlock {
                SettingsExampleLine("跟随系统", "白天和夜间会跟着系统主题自动切换。")
                SettingsExampleLine("固定浅色 / 深色", "适合希望应用始终保持同一种观感的场景。")
            }
        }

        SettingsCard {
            Text("统计小数位数")
            Text(
                "当前保留 $decimalPlaces 位小数，用于金额和统计结果展示。",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = decimalPlaces.toFloat(),
                onValueChange = { viewModel.setDecimalPlaces(it.toInt()) },
                valueRange = 0f..4f,
                steps = 3
            )
        }

        SettingsCard {
            Text("预览")
            Text("金额示例会跟随当前小数位设置变化。", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            Text(
                String.format("%.${decimalPlaces}f", 12.34567),
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
            )
        }
    }
}
