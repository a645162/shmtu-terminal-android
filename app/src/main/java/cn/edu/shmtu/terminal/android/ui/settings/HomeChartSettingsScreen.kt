package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun HomeChartSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val store = LocalFeatureStore.current
    val trendOptions = listOf(
        "today" to "今天",
        "week" to "本周",
        "recent_7_days" to "近 7 天",
        "month" to "本月"
    )
    val categoryOptions = listOf(
        "week" to "本周",
        "month" to "本月",
        "half_year" to "半年",
        "year" to "全年"
    )

    SettingsDetailScreen(
        title = "首页图表设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("趋势图默认范围")
            Text("打开首页后优先展示的趋势时间窗口。", style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trendOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = store.homeTrendRange.value == value,
                        onClick = { store.setHomeTrendRange(value) },
                        label = { Text(label) }
                    )
                }
            }
        }

        SettingsCard {
            Text("分类图默认范围")
            Text("控制分类统计图默认聚合的时间区间。", style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categoryOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = store.homeCategoryRange.value == value,
                        onClick = { store.setHomeCategoryRange(value) },
                        label = { Text(label) }
                    )
                }
            }
        }

        SettingsCard {
            Text("当前生效")
            Text(
                "趋势图: ${store.homeTrendRange.value}    分类图: ${store.homeCategoryRange.value}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
