package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    val homeTrendRange by store.homeTrendRange.collectAsState()
    val homeCategoryRange by store.homeCategoryRange.collectAsState()
    fun optionLabel(options: List<Pair<String, String>>, value: String): String =
        options.firstOrNull { it.first == value }?.second ?: value

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
                        selected = homeTrendRange == value,
                        onClick = { store.setHomeTrendRange(value) },
                        label = { Text(label) }
                    )
                }
            }
            SettingsExampleBlock {
                SettingsExampleLine("选择“今天”", "首页趋势图只看今天的消费变化，适合快速确认当天流水。")
                SettingsExampleLine("选择“近 7 天”", "更适合观察这一周的消费波动，而不是只看某一天。")
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
                        selected = homeCategoryRange == value,
                        onClick = { store.setHomeCategoryRange(value) },
                        label = { Text(label) }
                    )
                }
            }
            SettingsExampleBlock {
                SettingsExampleLine("选择“本月”", "分类图会按本月累计，适合看最近的主要花销都在哪些类型。")
                SettingsExampleLine("选择“全年”", "更适合看长期消费结构，比如食堂和洗澡谁占比更高。")
            }
        }

        SettingsCard {
            Text("当前生效")
            Text(
                "趋势图: ${optionLabel(trendOptions, homeTrendRange)}    分类图: ${optionLabel(categoryOptions, homeCategoryRange)}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
