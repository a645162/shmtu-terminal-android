package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.classifier.MealClassifier
import cn.edu.shmtu.cas.classifier.PositionTranslator
import cn.edu.shmtu.cas.classifier.RuleSummary
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import cn.edu.shmtu.terminal.android.domain.repository.ReclassifyMissSample
import cn.edu.shmtu.terminal.android.domain.repository.ReclassifyProgress
import cn.edu.shmtu.terminal.android.domain.repository.ReclassifyResult
import cn.edu.shmtu.terminal.android.domain.usecase.bill.ReclassifyBillsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_RULES_URL =
    "https://raw.githubusercontent.com/a645162/shmtu-terminal/main/database/bill"

/** UI 状态: 重算历史账单 */
sealed interface ReclassifyState {
    data object Idle : ReclassifyState
    data class Running(val progress: ReclassifyProgress) : ReclassifyState
    data class Done(val result: ReclassifyResult) : ReclassifyState
    data class Failed(val error: String) : ReclassifyState
}

/** 当前生效规则快照(分类 + 位置) */
data class ActiveRulesSnapshot(
    val files: List<BillRulesManager.RuleFileSnapshot>,
    val classifierRules: List<RuleSummary>,
    val positionKeywords: List<Triple<String, String, String>>,  // (keyword, building, room)
    val mealRules: List<MealClassifier.ScheduleRule>,
    val classifierRuleCount: Int,
    val positionKeywordCount: Int,
    val mealRuleCount: Int,
    val classifierSource: String,
    val positionSource: String,
    val mealSource: String
) {
    val classifierTotalKeywords: Int get() = classifierRules.sumOf { it.totalKeywords }
    val positionTotalKeywords: Int get() = positionKeywords.size
}

@HiltViewModel
class ClassificationSettingsViewModel @Inject constructor(
    private val reclassifyBillsUseCase: ReclassifyBillsUseCase,
    private val epayAdapter: cn.edu.shmtu.terminal.android.data.remote.EpayAdapter,
    private val rulesManager: BillRulesManager,
) : ViewModel() {

    private val _reclassifyState = MutableStateFlow<ReclassifyState>(ReclassifyState.Idle)
    val reclassifyState: StateFlow<ReclassifyState> = _reclassifyState.asStateFlow()

    /** 触发懒加载后再读,返回当前生效规则快照 */
    fun loadActiveRules(): ActiveRulesSnapshot {
        val files = rulesManager.inspectAllFiles()
        val clfLoaded = epayAdapter.loadClassifier()
        val posLoaded = epayAdapter.loadPositionTranslator()
        val mealLoaded = epayAdapter.loadMealClassifier()
        val clf = clfLoaded.classifier
        val pos = posLoaded.translator
        return ActiveRulesSnapshot(
            files = files,
            classifierRules = clf?.getAllRules()?.sortedBy { it.key } ?: emptyList(),
            positionKeywords = pos?.getAllKeywords()?.entries?.map { (k, v) ->
                Triple(k, v.position, v.room)
            }?.sortedBy { it.first } ?: emptyList(),
            mealRules = mealLoaded.classifier.getAllRules(),
            classifierRuleCount = clfLoaded.ruleCount,
            positionKeywordCount = posLoaded.keywordCount,
            mealRuleCount = mealLoaded.scheduleCount,
            classifierSource = clfLoaded.source,
            positionSource = posLoaded.source,
            mealSource = mealLoaded.source
        )
    }

    fun reclassify() {
        if (_reclassifyState.value is ReclassifyState.Running) return
        _reclassifyState.value = ReclassifyState.Running(
            ReclassifyProgress(
                processed = 0,
                total = 0,
                currentDbIndex = 0,
                totalDbs = 0
            )
        )
        viewModelScope.launch {
            try {
                val result = reclassifyBillsUseCase { progress ->
                    _reclassifyState.value = ReclassifyState.Running(progress)
                }
                _reclassifyState.value = ReclassifyState.Done(result)
            } catch (e: Exception) {
                _reclassifyState.value = ReclassifyState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}

@Composable
fun ClassificationSettingsScreen(
    onBack: () -> Unit,
    rulesManager: BillRulesManager,
    embedded: Boolean = false,
    viewModel: ClassificationSettingsViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val store = LocalFeatureStore.current
    val currentUrl by store.rulesUpdateUrl.collectAsState()
    var urlDraft by remember(currentUrl) { mutableStateOf(currentUrl) }
    var status by remember { mutableStateOf("尚未同步") }

    val reclassifyState by viewModel.reclassifyState.collectAsState()

    SettingsDetailScreen(
        title = "分类规则设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("规则来源")
            Text(
                "规则文件存放在远程 base 路径下，4 个 toml 文件名固定。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "包含文件: rules.toml / type.toml / position.toml / schedule.toml",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ====== 新增: 当前生效的分类规则概览(默认折叠) ======
        var snapshot by remember { mutableStateOf<ActiveRulesSnapshot?>(null) }
        // 进入页面时主动触发懒加载(不阻塞 UI)
        remember { snapshot = viewModel.loadActiveRules() }
        snapshot?.let { RulesOverviewCard(it) }

        SettingsCard(emphasized = true) {
            Text("远程规则 base URL")
            Text(
                "可填写自建 GitHub/Gitee raw 仓库地址；留空时使用默认 URL。",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                singleLine = true,
                label = { Text("Remote base URL") },
                placeholder = { Text("https://raw.githubusercontent.com/.../database/bill") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = urlDraft.trim() != currentUrl,
                    onClick = { store.setRulesUpdateUrl(urlDraft.trim()) }
                ) {
                    Text("保存 URL")
                }
                TextButton(
                    onClick = {
                        urlDraft = DEFAULT_RULES_URL
                        store.setRulesUpdateUrl(DEFAULT_RULES_URL)
                    }
                ) {
                    Text("恢复默认")
                }
            }
            Text(
                "当前生效：${rulesManager.currentRemoteBase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsCard(emphasized = true) {
            Text("立即同步")
            Text(
                "从当前 base URL 拉取 4 个 toml 规则并写入 `filesDir/bill/`，写盘前自动备份为 .bak。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = {
                scope.launch {
                    val result = rulesManager.downloadAll()
                    status = if (result.allOk) {
                        "同步成功，共更新 ${result.perFile.size} 个文件"
                    } else {
                        result.perFile.entries.joinToString("\n") { (name, item) ->
                            when (item) {
                                is BillRulesManager.DownloadFileResult.Success -> "$name: 成功"
                                is BillRulesManager.DownloadFileResult.Failure -> "$name: ${item.reason}"
                            }
                        }
                    }
                    snapshot = viewModel.loadActiveRules()
                }
            }) {
                Text("同步规则")
            }
        }

        SettingsCard(emphasized = true) {
            Text("重算历史账单")
            Text(
                "用修复后的位置规则(position.toml)与分类器把数据库里已有账单的 " +
                        "building / room / position / category 重新计算并写回。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "不重新走 CAS 登录，不重新拉取账单。处理时间取决于历史账单数量。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Button(
                    enabled = reclassifyState !is ReclassifyState.Running,
                    onClick = { viewModel.reclassify() }
                ) {
                    Text("重算历史账单")
                }
                if (reclassifyState is ReclassifyState.Running) {
                    CircularProgressIndicator(
                        modifier = Modifier,
                        strokeWidth = 2.dp
                    )
                }
            }
            when (val s = reclassifyState) {
                is ReclassifyState.Idle -> {
                    Text(
                        "尚未执行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is ReclassifyState.Running -> {
                    val progress = s.progress
                    if (progress.total > 0) {
                        LinearProgressIndicator(
                            progress = { progress.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正在重算… ${progress.processed} / ${progress.total} " +
                                    "（数据库 ${progress.currentDbIndex.coerceAtLeast(1)} / ${progress.totalDbs.coerceAtLeast(1)}）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        progress.currentTargetUser?.let { target ->
                            Text(
                                "当前: $target",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            "正在统计重算总量…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ReclassifyState.Done -> {
                    val r = s.result
                    Text(
                        "完成 — 扫描 ${r.totalScanned} 条 / 翻译 ${r.translated} 条 / " +
                                "分类更新 ${r.categoryUpdated} 条 / 未命中 ${r.missed} 条 / 耗时 ${r.durationMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (r.missedSamples.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        MissedSamplesCard(
                            samples = r.missedSamples,
                            totalMissed = r.missed
                        )
                    }
                }
                is ReclassifyState.Failed -> {
                    Text(
                        "失败: ${s.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MissedSamplesCard(samples: List<ReclassifyMissSample>, totalMissed: Int) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("未命中 targetUser", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "显示 ${samples.size} 个聚合项，总未命中 $totalMissed 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起" else "展开")
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    samples.forEach { sample ->
                        MissedSampleRow(sample)
                    }
                }
            }
        }
    }
}

// ============== 规则概览 Composable(默认折叠,详情二级折叠) ==============

@Composable
private fun RulesOverviewCard(snapshot: ActiveRulesSnapshot) {
    var expanded by remember { mutableStateOf(false) }
    var filesExpanded by remember { mutableStateOf(false) }
    var classifierExpanded by remember { mutableStateOf(false) }
    var positionExpanded by remember { mutableStateOf(false) }
    var mealExpanded by remember { mutableStateOf(false) }

    SettingsCard(emphasized = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("当前生效的分类规则")
                Text(
                    "文件 ${snapshot.files.size} 个 · 分类 ${snapshot.classifierRuleCount} 条 · " +
                            "位置 ${snapshot.positionKeywordCount} 条 · 时段 ${snapshot.mealRuleCount} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "classifier: ${snapshot.classifierSource} / position: ${snapshot.positionSource} / meal: ${snapshot.mealSource}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起" else "展开")
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📄 规则文件 (${snapshot.files.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { filesExpanded = !filesExpanded }) {
                        Text(if (filesExpanded) "收起详情" else "查看详情")
                    }
                }
                AnimatedVisibility(visible = filesExpanded) {
                    Column {
                        snapshot.files.forEach { RuleFileRow(it) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ====== 分类规则列表 ======
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📦 分类规则 (${snapshot.classifierRules.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { classifierExpanded = !classifierExpanded }) {
                        Text(if (classifierExpanded) "收起详情" else "查看详情")
                    }
                }
                AnimatedVisibility(visible = classifierExpanded) {
                    Column {
                        if (snapshot.classifierRules.isEmpty()) {
                            Text(
                                "（无 — 分类器未加载）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            snapshot.classifierRules.forEach { rule ->
                                ClassifierRuleRow(rule)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // ====== 位置翻译规则列表 ======
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📍 位置翻译 (${snapshot.positionKeywords.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { positionExpanded = !positionExpanded }) {
                        Text(if (positionExpanded) "收起详情" else "查看详情")
                    }
                }
                AnimatedVisibility(visible = positionExpanded) {
                    Column {
                        if (snapshot.positionKeywords.isEmpty()) {
                            Text(
                                "（无 — 位置翻译器未加载）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            snapshot.positionKeywords.forEach { (keyword, building, room) ->
                                PositionKeywordRow(keyword, building, room)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🕒 时段规则 (${snapshot.mealRuleCount})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { mealExpanded = !mealExpanded }) {
                        Text(if (mealExpanded) "收起详情" else "查看详情")
                    }
                }
                AnimatedVisibility(visible = mealExpanded) {
                    Column {
                        snapshot.mealRules.forEach { MealRuleRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissedSampleRow(sample: ReclassifyMissSample) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(sample.targetUser, style = MaterialTheme.typography.bodyMedium)
            Text(
                "出现 ${sample.count} 次 · 示例 type: ${sample.sampleType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RuleFileRow(file: BillRulesManager.RuleFileSnapshot) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                "source: ${file.activeSource}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "localExists=${file.localExists} localBytes=${file.localBytes} readable=${file.readable}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ClassifierRuleRow(rule: RuleSummary) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${rule.displayName} (${rule.key})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "共 ${rule.totalKeywords} 关键词",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "匹配字段: ${rule.matchField}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (rule.matchNames.isNotEmpty()) {
                Text(
                    "item_type: ${rule.matchNames.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (rule.matchTargets.isNotEmpty()) {
                Text(
                    "target_user: ${rule.matchTargets.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PositionKeywordRow(keyword: String, building: String, room: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("「$keyword」", style = MaterialTheme.typography.bodyMedium)
            Text(
                "→ $building / $room",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MealRuleRow(rule: MealClassifier.ScheduleRule) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "${rule.validDate.startDate} ~ ${rule.validDate.endDate}",
                style = MaterialTheme.typography.bodyMedium
            )
            rule.timetable.allSlots().forEach { slot ->
                Text(
                    "${slot.name}: ${slot.startTime} - ${slot.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
