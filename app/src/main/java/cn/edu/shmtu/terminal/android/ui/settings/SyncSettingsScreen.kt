package cn.edu.shmtu.terminal.android.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import cn.edu.shmtu.terminal.android.data.sync.PeriodicBillSyncWorker
import cn.edu.shmtu.terminal.android.data.sync.AutoSyncStatusNotifier
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutoSyncStatus(
    val enabled: Boolean,
    val isRunning: Boolean,
    val nextRunInSeconds: Long?,
    val successRuns: Int,
    val failedRuns: Int
) {
    companion object {
        val EMPTY = AutoSyncStatus(
            enabled = false,
            isRunning = false,
            nextRunInSeconds = null,
            successRuns = 0,
            failedRuns = 0
        )
    }
}

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val store: FeatureSettingsStore,
    private val settingsDataStore: SettingsDataStore,
    private val notifier: AutoSyncStatusNotifier
) : ViewModel() {

    private val _status = MutableStateFlow(AutoSyncStatus.EMPTY)
    val status: StateFlow<AutoSyncStatus> = _status.asStateFlow()

    val autoSyncEnabled: StateFlow<Boolean> = store.autoSyncEnabled
    val autoSyncInterval: StateFlow<Int> = store.autoSyncInterval
    val autoSyncRange: StateFlow<String> = store.autoSyncRange
    val autoSyncPersistentNotification: StateFlow<Boolean> = store.autoSyncPersistentNotification
    val syncMaxPages: StateFlow<Int> = store.syncMaxPages
    val syncEarlyStop: StateFlow<Int> = store.syncEarlyStop
    val syncSkipGraduated: StateFlow<Boolean> = store.syncSkipGraduated
    val syncAutoMerge: StateFlow<Boolean> = store.syncAutoMerge
    val billMergeThresholdMinutes: StateFlow<Int> = settingsDataStore.billMergeThresholdMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, settingsDataStore.getBillMergeThresholdMinutes())

    fun setBillMergeThresholdMinutes(n: Int) = settingsDataStore.setBillMergeThresholdMinutes(n)

    fun setAutoSyncEnabled(v: Boolean) { store.setAutoSyncEnabled(v); notifier.refresh() }
    fun setAutoSyncInterval(n: Int) { store.setAutoSyncInterval(n); notifier.refresh() }
    fun setAutoSyncRange(v: String) = store.setAutoSyncRange(v)
    fun setAutoSyncPersistentNotification(v: Boolean) { store.setAutoSyncPersistentNotification(v); notifier.refresh() }
    fun setSyncMaxPages(n: Int) = store.setSyncMaxPages(n)
    fun setSyncEarlyStop(n: Int) = store.setSyncEarlyStop(n)
    fun setSyncSkipGraduated(v: Boolean) = store.setSyncSkipGraduated(v)
    fun setSyncAutoMerge(v: Boolean) = store.setSyncAutoMerge(v)

    /**
     * 周期性查询 WorkManager 的 unique work 状态 — 对齐 Tauri 端
     *  `get_auto_sync_status` 行为。
     *
     *  - enabled: FeatureSettingsStore.autoSyncEnabled
     *  - isRunning: 列表中存在 RUNNING/ENQUEUED
     *  - nextRunInSeconds: WorkInfo.nextScheduleTimeMillis 距现在的秒数
     *  - 累计成功/失败: 当前 WorkInfo 列表的 SUCCEEDED/FAILED+ CANCELLED 个数
     *    (WorkManager 默认仅保留最近 30 条, 接近 Tauri 端「近期累计」语义)
     */
    fun startStatusPolling(context: Context) {
        viewModelScope.launch {
            val wm = WorkManager.getInstance(context)
            while (true) {
                val infos = wm.getWorkInfosForUniqueWork(PeriodicBillSyncWorker.NAME).get()
                val enabled = store.autoSyncEnabled.value
                val isRunning = infos.any {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                }
                val now = System.currentTimeMillis()
                val nextRunInSeconds: Long? = infos
                    .mapNotNull { info ->
                        val n = runCatching { info.nextScheduleTimeMillis }.getOrNull()
                        if (n != null && n > 0L) (n - now) / 1000 else null
                    }
                    .minOrNull()
                    ?.coerceAtLeast(0L)
                val success = infos.count { it.state == WorkInfo.State.SUCCEEDED }
                val failed = infos.count {
                    it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED
                }
                _status.value = AutoSyncStatus(
                    enabled = enabled,
                    isRunning = isRunning,
                    nextRunInSeconds = nextRunInSeconds,
                    successRuns = success,
                    failedRuns = failed
                )
                delay(15_000)
            }
        }
    }
}

@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: SyncSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val autoSyncInterval by viewModel.autoSyncInterval.collectAsState()
    val autoSyncRange by viewModel.autoSyncRange.collectAsState()
    val autoSyncPersistentNotification by viewModel.autoSyncPersistentNotification.collectAsState()
    val syncMaxPages by viewModel.syncMaxPages.collectAsState()
    val syncEarlyStop by viewModel.syncEarlyStop.collectAsState()
    val syncSkipGraduated by viewModel.syncSkipGraduated.collectAsState()
    val syncAutoMerge by viewModel.syncAutoMerge.collectAsState()
    val billMergeThresholdMinutes by viewModel.billMergeThresholdMinutes.collectAsState()
    val status by viewModel.status.collectAsState()
    var nextRunCountdown by remember { mutableStateOf(status.nextRunInSeconds) }

    LaunchedEffect(Unit) { viewModel.startStatusPolling(context) }
    LaunchedEffect(status.nextRunInSeconds) { nextRunCountdown = status.nextRunInSeconds }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            nextRunCountdown = nextRunCountdown?.let { if (it <= 0L) 0L else it - 1 }
        }
    }

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
            Text("当前最多拉取 $syncMaxPages 页账单数据。", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = syncMaxPages.toFloat(),
                onValueChange = { viewModel.setSyncMaxPages(it.toInt()) },
                valueRange = 10f..500f
            )
            SettingsExampleBlock {
                SettingsExampleLine("页数较小", "例如 30 页，适合日常补拉，速度更快。")
                SettingsExampleLine("页数较大", "例如 200 页，适合首次同步或很久没同步后补齐历史数据。")
            }
        }

        SettingsCard {
            Text("提前停止阈值")
            Text("连续 $syncEarlyStop 页无有效新数据时提前结束。", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = syncEarlyStop.toFloat(),
                onValueChange = { viewModel.setSyncEarlyStop(it.toInt()) },
                valueRange = 1f..20f
            )
            SettingsExampleBlock {
                SettingsExampleLine("阈值 = 3", "如果连续 3 页都没拉到新账单，就提前结束，不再继续翻页。")
                SettingsExampleLine("阈值更高", "更保守，适合担心中间有零散新账单时使用。")
            }
        }

        SettingsCard {
            Text("同步策略")
            SettingsSwitchRow(
                title = "跳过已毕业账号",
                subtitle = "减少无效请求和失败重试。",
                checked = syncSkipGraduated,
                onCheckedChange = { viewModel.setSyncSkipGraduated(it) }
            )
            SettingsSwitchRow(
                title = "同步后自动合并",
                subtitle = "同步结束后自动做账单合并处理。",
                checked = syncAutoMerge,
                onCheckedChange = { viewModel.setSyncAutoMerge(it) }
            )
            Text(
                text = "洗澡账单合并阈值：${if (billMergeThresholdMinutes == 0) "禁用" else "$billMergeThresholdMinutes 分钟"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "相邻两笔洗澡/热水账单时间间隔 < 阈值时，自动首尾合并（订单号和时间变成列表）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = billMergeThresholdMinutes.toFloat(),
                onValueChange = { viewModel.setBillMergeThresholdMinutes(it.toInt()) },
                valueRange = 0f..60f,
                steps = 12
            )
            SettingsExampleBlock {
                SettingsExampleLine("跳过已毕业账号", "开启后，不再反复尝试长期失效的账号，减少报错和等待。")
                SettingsExampleLine("同步后自动合并", "开启后，同步完成会立刻做去重和分类整理；关闭后只拉原始账单。")
                SettingsExampleLine("合并阈值（0=禁用）", "仅对洗澡/热水类账单生效。例：阈值=15 表示两个连续洗澡记录间隔 < 15 分钟时合并。")
            }
        }

        SettingsCard(emphasized = autoSyncEnabled) {
            Text("自动同步")
            SettingsSwitchRow(
                title = "启用定时账单同步",
                subtitle = "在后台按固定间隔自动检查并执行同步。",
                checked = autoSyncEnabled,
                onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
            )
            SettingsSwitchRow(
                title = "常驻通知",
                subtitle = "在通知栏显示自动同步状态通知。默认开启，可手动关闭。",
                checked = autoSyncPersistentNotification,
                onCheckedChange = { viewModel.setAutoSyncPersistentNotification(it) }
            )
            if (autoSyncEnabled) {
                Text("检查间隔: $autoSyncInterval 分钟", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = autoSyncInterval.toFloat(),
                    onValueChange = { viewModel.setAutoSyncInterval(it.toInt()) },
                    valueRange = 5f..1440f
                )
                Text("自动同步范围", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rangeOptions.forEach { (value, label) ->
                        FilterChip(
                            selected = autoSyncRange == value,
                            onClick = { viewModel.setAutoSyncRange(value) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            SettingsExampleBlock {
                SettingsExampleLine("间隔 60 分钟", "表示应用大约每小时检查一次是否需要同步。")
                SettingsExampleLine("范围选“半个月”", "自动同步时优先只补最近半个月的数据，减少无关历史请求。")
            }
        }

        SettingsCard {
            Text("自动同步状态")
            Text(
                "WorkManager 周期任务的当前执行情况与最近运行结果。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text("当前状态：${if (status.isRunning) "运行中" else "未运行"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("距离下次同步：${formatCountdown(nextRunCountdown)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("累计成功/失败（近 30 次）：${status.successRuns} / ${status.failedRuns}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatCountdown(seconds: Long?): String {
    if (seconds == null) return "未计划"
    val s = seconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val remain = s % 60
    return when {
        hours > 0 -> "${hours}小时 ${minutes}分钟"
        minutes > 0 -> "${minutes}分钟 ${remain}秒"
        else -> "${remain}秒"
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
