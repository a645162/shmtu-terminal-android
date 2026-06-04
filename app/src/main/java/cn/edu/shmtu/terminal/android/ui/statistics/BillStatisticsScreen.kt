package cn.edu.shmtu.terminal.android.ui.statistics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.ForgotCardStats
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.StatisticsSummary
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import cn.edu.shmtu.terminal.android.ui.component.BillItemCard
import cn.edu.shmtu.terminal.android.ui.component.AppDonutChart
import cn.edu.shmtu.terminal.android.ui.component.AppDonutSlice
import cn.edu.shmtu.terminal.android.ui.component.AppLineChart
import cn.edu.shmtu.terminal.android.ui.component.AppLineSeries
import cn.edu.shmtu.terminal.android.ui.theme.BrandForeground1
import cn.edu.shmtu.terminal.android.ui.theme.GreenForeground3
import cn.edu.shmtu.terminal.android.ui.theme.RedForeground3
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 账单统计页 - 对齐 Tauri StatisticsDialog
 *
 * 头部 3 个 Dropdown:时间段(11+1) / 身份 / 分类("all" + 实际分类)
 * 5 个 Tab:总览 / 分类分析 / 位置分布 / 月度对比 / 忘记拔卡
 * 点击饼图扇区 → 切换到"分类分析" tab + 锁定该分类
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillStatisticsScreen(
    onBack: () -> Unit,
    viewModel: BillStatisticsViewModel = hiltViewModel()
) {
    val currentIdentity by viewModel.currentIdentity.collectAsState()
    val identities by viewModel.identities.collectAsState()
    val overview by viewModel.overview.collectAsState()
    val spendingTrend by viewModel.spendingTrend.collectAsState()
    val categories by viewModel.categoryBreakdown.collectAsState()
    val ranking by viewModel.targetUserRanking.collectAsState()
    val monthly by viewModel.monthlySummary.collectAsState()
    val statisticsSummary by viewModel.statisticsSummary.collectAsState()
    val forgotCardStats by viewModel.forgotCardStats.collectAsState()
    val categoryBills by viewModel.categoryBills.collectAsState()
    val selectedIdentityId by viewModel.selectedIdentityId.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val customStart by viewModel.customStartDate.collectAsState()
    val customEnd by viewModel.customEndDate.collectAsState()

    var selectedTab by remember { mutableStateOf(StatisticsTab.OVERVIEW) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStatistics() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新统计")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            FilterRow(
                identities = identities,
                selectedIdentityId = selectedIdentityId,
                onSelectIdentity = { viewModel.selectIdentity(it) },
                selectedPeriod = selectedPeriod,
                onSelectPeriod = { viewModel.selectPeriod(it) },
                selectedCategory = selectedCategory,
                availableCategoryKeys = categories.map { it.type },
                onSelectCategory = { viewModel.selectCategory(it) }
            )
            AnimatedVisibility(visible = selectedPeriod == StatisticsPeriod.CUSTOM) {
                CustomDateRangeRow(
                    startDate = customStart,
                    endDate = customEnd,
                    onStartDateSelected = { viewModel.setCustomDateRange(it, customEnd) },
                    onEndDateSelected = { viewModel.setCustomDateRange(customStart, it) }
                )
            }
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                StatisticsTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    StatisticsTab.OVERVIEW -> {
                        item { SummaryCard(statisticsSummary) }
                        item { OverviewCards(overview) }
                        item { TrendCard(spendingTrend) }
                        item {
                            CategoryDonutChart(
                                categories = categories,
                                onCategoryClick = {
                                    viewModel.selectCategory(it)
                                    selectedTab = StatisticsTab.CATEGORY
                                }
                            )
                        }
                    }
                    StatisticsTab.CATEGORY -> {
                        item {
                            CategoryFilteredHeader(
                                selectedCategory = selectedCategory,
                                availableCategories = categories,
                                onClear = { viewModel.selectCategory("all") }
                            )
                        }
                        item {
                            CategoryBarChartCard(
                                categories = if (selectedCategory == "all") categories
                                else categories.filter { it.type == selectedCategory }
                            )
                        }
                        item {
                            CategoryDonutChart(
                                categories = if (selectedCategory == "all") categories
                                else categories.filter { it.type == selectedCategory },
                                onCategoryClick = { viewModel.selectCategory(it) }
                            )
                        }
                        // 分类图例 - 可点击切换 selectedCategory(对齐 Tauri 分类图例)
                        item {
                            CategoryLegend(
                                categories = categories,
                                selectedCategory = selectedCategory,
                                onClickCategory = { viewModel.selectCategory(it) }
                            )
                        }
                        // 选中具体分类时,显示该分类的消费明细表格(对齐 Tauri 消费明细)
                        if (selectedCategory != "all") {
                            item {
                                CategoryBillsTableCard(
                                    category = selectedCategory,
                                    bills = categoryBills,
                                    onBillClick = { /* TODO: navigate to bill detail */ }
                                )
                            }
                        }
                    }
                    StatisticsTab.POSITION -> {
                        item { PositionRankingCard(ranking = ranking) }
                    }
                    StatisticsTab.COMPARE -> {
                        item { MonthlySummaryTable(monthly) }
                        item { OverviewCards(overview) }
                    }
                    StatisticsTab.FORGOT -> {
                        item { ForgotCardCard(forgotCardStats) }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

private enum class StatisticsTab(val label: String) {
    OVERVIEW("总览"),
    CATEGORY("分类分析"),
    POSITION("位置分布"),
    COMPARE("月度对比"),
    FORGOT("忘记拔卡")
}

// ==================== 头部 3 个 Dropdown ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    identities: List<Identity>,
    selectedIdentityId: Long?,
    onSelectIdentity: (Long?) -> Unit,
    selectedPeriod: StatisticsPeriod,
    onSelectPeriod: (StatisticsPeriod) -> Unit,
    selectedCategory: String,
    availableCategoryKeys: List<String>,
    onSelectCategory: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PeriodDropdown(
            selected = selectedPeriod,
            onSelect = onSelectPeriod,
            modifier = Modifier.weight(1f)
        )
        IdentityDropdown(
            identities = identities,
            selectedId = selectedIdentityId,
            onSelect = onSelectIdentity,
            modifier = Modifier.weight(1f)
        )
        CategoryDropdown(
            selected = selectedCategory,
            availableKeys = availableCategoryKeys,
            onSelect = onSelectCategory,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodDropdown(
    selected: StatisticsPeriod,
    onSelect: (StatisticsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        DropdownChip(
            label = selected.label,
            expanded = expanded,
            onClick = { expanded = true },
            onDismiss = { expanded = false },
            items = StatisticsPeriod.entries.map { it to it.label },
            onItemSelected = { onSelect(it as StatisticsPeriod); expanded = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdentityDropdown(
    identities: List<Identity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentName = identities.firstOrNull { it.id == selectedId }?.let {
        it.remark.ifBlank { it.username }
    } ?: "选择身份"
    Box(modifier = modifier) {
        DropdownChip(
            label = currentName,
            expanded = expanded,
            onClick = { expanded = true },
            onDismiss = { expanded = false },
            items = identities.map { it.id to (it.remark.ifBlank { it.username }) },
            onItemSelected = { onSelect(it as Long?); expanded = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: String,
    availableKeys: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = if (selected == "all") "全部分类" else CategoryDisplay.displayName(selected)
    val seen = LinkedHashSet<String>()
    val allItems = mutableListOf<Pair<Any, String>>()
    allItems.add("all" to "全部分类")
    for (k in availableKeys) {
        if (seen.add(k)) allItems.add(k to CategoryDisplay.displayName(k))
    }
    Box(modifier = modifier) {
        DropdownChip(
            label = displayText,
            expanded = expanded,
            onClick = { expanded = true },
            onDismiss = { expanded = false },
            items = allItems,
            onItemSelected = { onSelect(it as String); expanded = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownChip(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    items: List<Pair<Any, String>>,
    onItemSelected: (Any) -> Unit
) {
    Box {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismiss
        ) {
            items.forEach { (key, text) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onItemSelected(key) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDateRangeRow(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onStartDateSelected: (LocalDate?) -> Unit,
    onEndDateSelected: (LocalDate?) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DatePickerTrigger(
            label = "开始日期",
            date = startDate,
            modifier = Modifier.weight(1f),
            onClick = { showStartPicker = true }
        )
        DatePickerTrigger(
            label = "结束日期",
            date = endDate,
            modifier = Modifier.weight(1f),
            onClick = { showEndPicker = true }
        )
    }
    if (showStartPicker) {
        DatePickerModal(
            initialDate = startDate,
            onDateSelected = { onStartDateSelected(it); showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        DatePickerModal(
            initialDate = endDate,
            onDateSelected = { onEndDateSelected(it); showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}

@Composable
private fun DatePickerTrigger(
    label: String,
    date: LocalDate?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = date?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "点击选择",
                style = MaterialTheme.typography.bodyLarge,
                color = if (date != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = initialDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selected = state.selectedDateMillis?.let { millis ->
                    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                }
                onDateSelected(selected)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        DatePicker(state = state)
    }
}

// ==================== 总览 Tab ====================

@Composable
private fun SummaryCard(summary: StatisticsSummary?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("统计摘要", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (summary == null) {
                Text("加载中...", color = MaterialTheme.colorScheme.outline)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryItem("总消费", "¥%,.2f".format(abs(summary.totalExpense)), RedForeground3)
                    SummaryItem("总充值", "¥%,.2f".format(summary.totalIncome), GreenForeground3)
                    SummaryItem("净支出", "¥%,.2f".format(summary.netExpense), BrandForeground1)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryItem("日均", "¥%,.2f".format(summary.dailyAverage), null)
                    SummaryItem("支出笔数", "${summary.expenseCount}", null)
                    SummaryItem("充值笔数", "${summary.incomeCount}", null)
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun OverviewCards(overview: BillOverview?) {
    if (overview == null) return
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OverviewItem(
                    title = "本月支出",
                    value = "¥%,.2f".format(overview.totalSpending),
                    delta = if (overview.lastMonthSpending > 0) {
                        ((overview.totalSpending - overview.lastMonthSpending) / overview.lastMonthSpending * 100).toInt()
                    } else 0,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                OverviewItem(
                    title = "本月收入",
                    value = "¥%,.2f".format(overview.totalIncome),
                    delta = if (overview.lastMonthIncome > 0) {
                        ((overview.totalIncome - overview.lastMonthIncome) / overview.lastMonthIncome * 100).toInt()
                    } else 0,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("净变化", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "%,+.2f".format(overview.netChange),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (overview.netChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Column {
                    Text("日均消费", style = MaterialTheme.typography.labelMedium)
                    Text("¥%,.2f".format(overview.dailyAverage), style = MaterialTheme.typography.titleMedium)
                }
                Column {
                    Text("交易笔数", style = MaterialTheme.typography.labelMedium)
                    Text("${overview.transactionCount} 笔", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun OverviewItem(title: String, value: String, delta: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(
            "${if (delta >= 0) "↑" else "↓"}${abs(delta)}% 环比",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun TrendCard(data: List<SpendingTrend>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("消费趋势", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (data.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                AppLineChart(
                    labels = data.map { it.date.substring(5) },
                    series = listOf(
                        AppLineSeries(
                            color = RedForeground3,
                            values = data.map { it.amount.toFloat() },
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun CategoryDonutChart(
    categories: List<CategoryBreakdown>,
    onCategoryClick: (String) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("分类占比", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (categories.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppDonutChart(
                        slices = categories.map { item ->
                            AppDonutSlice(
                                label = item.type,
                                value = item.amount.toFloat(),
                                color = CategoryDisplay.color(item.type),
                            )
                        },
                        modifier = Modifier.size(140.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        categories.take(6).forEach { item ->
                            CategoryLegendRow(
                                type = item.type,
                                percentage = item.percentage,
                                onClick = { onCategoryClick(item.type) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(type: String, percentage: Float, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(CategoryDisplay.color(type)) }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "${CategoryDisplay.displayName(type)} ${(percentage * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

// ==================== 分类分析 Tab ====================

@Composable
private fun CategoryFilteredHeader(
    selectedCategory: String,
    availableCategories: List<CategoryBreakdown>,
    onClear: () -> Unit
) {
    if (selectedCategory == "all") return
    val match = availableCategories.firstOrNull { it.type == selectedCategory }
    if (match == null) return
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("已筛选: ${CategoryDisplay.displayName(selectedCategory)}", style = MaterialTheme.typography.titleSmall)
                Text("¥%,.2f · 占比 %.1f%%".format(match.amount, match.percentage * 100), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onClear) { Text("清除") }
        }
    }
}

@Composable
private fun CategoryBarChartCard(categories: List<CategoryBreakdown>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("分类金额排行", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (categories.isEmpty()) {
                Text("暂无数据", color = MaterialTheme.colorScheme.outline)
            } else {
                val maxAmount = categories.maxOf { it.amount }.coerceAtLeast(1.0)
                categories.take(8).forEach { item ->
                    val color = CategoryDisplay.color(item.type)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            CategoryDisplay.displayName(item.type),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(60.dp),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Canvas(modifier = Modifier.weight(1f).height(14.dp)) {
                            val barW = (item.amount / maxAmount).toFloat() * size.width
                            drawRoundRect(color = color, topLeft = Offset.Zero, size = Size(barW, size.height))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "¥%,.2f".format(item.amount),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLegend(
    categories: List<CategoryBreakdown>,
    selectedCategory: String,
    onClickCategory: (String) -> Unit
) {
    if (categories.isEmpty()) return
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("分类图例", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            // 模仿 Tauri 分类图例:可点击的 Chip,选中时实心,未选中时描边
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { item ->
                    val color = CategoryDisplay.color(item.type)
                    val isSelected = item.type == selectedCategory
                    Surface(
                        onClick = { onClickCategory(if (isSelected) "all" else item.type) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = if (isSelected) color else MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, color)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(if (isSelected) androidx.compose.ui.graphics.Color.White else color)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${CategoryDisplay.displayName(item.type)} ${(item.percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 选中具体分类时,在分类分析 Tab 底部显示该分类的所有消费明细(对齐 Tauri 消费明细表格)
 * 用现有 BillItemCard 列表渲染,点击行 → 跳账单详情(待接入路由)
 */
@Composable
private fun CategoryBillsTableCard(
    category: String,
    bills: List<BillItem>,
    onBillClick: (Long) -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${CategoryDisplay.displayName(category)} — 消费明细",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "共 ${bills.size} 笔",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (bills.isEmpty()) {
                Text(
                    "暂无该分类的消费记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bills.take(50).forEach { bill ->
                        BillItemCard(bill = bill, onClick = { onBillClick(bill.id) })
                    }
                    if (bills.size > 50) {
                        Text(
                            "... 还有 ${bills.size - 50} 笔,前往账单页按分类查看全部",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

// ==================== 位置分布 Tab ====================

@Composable
private fun PositionRankingCard(ranking: List<TargetUserRanking>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("位置/商户排行(支出)", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (ranking.isEmpty()) {
                Text("暂无数据", color = MaterialTheme.colorScheme.outline)
            } else {
                val maxAmount = ranking.maxOf { it.amount }.coerceAtLeast(1.0)
                ranking.take(10).forEachIndexed { index, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            item.targetUser.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Canvas(modifier = Modifier.weight(1f).height(12.dp)) {
                            val barW = (item.amount / maxAmount).toFloat() * size.width
                            drawRoundRect(color = RedForeground3, topLeft = Offset.Zero, size = Size(barW, size.height))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "¥%,.2f".format(item.amount),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

// ==================== 月度对比 Tab ====================

@Composable
private fun MonthlySummaryTable(monthly: List<MonthlySummary>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("月度汇总", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            if (monthly.isEmpty()) {
                Text("暂无数据", color = MaterialTheme.colorScheme.outline)
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("月份", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("支出", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("收入", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                monthly.take(6).forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.month, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text(
                            "¥%,.2f".format(item.spending),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            "¥%,.2f".format(item.income),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

// ==================== 忘记拔卡 Tab ====================

@Composable
private fun ForgotCardCard(stats: ForgotCardStats) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("忘记拔卡提醒", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "洗澡上限为 5 元,消费恰好 5 元意味着水龙头一直开着,可能忘记拔卡。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (stats.count == 0) {
                Text(
                    "当前时间范围内没有发现忘记拔卡的记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "共 ${stats.count} 次，累计 ¥${"%.2f".format(stats.totalAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                stats.items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${item.date} ${item.time}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                item.targetUser.ifBlank { "淋浴/热水" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "¥${"%.2f".format(item.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
