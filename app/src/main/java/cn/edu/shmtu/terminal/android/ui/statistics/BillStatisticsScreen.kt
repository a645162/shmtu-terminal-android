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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.StatisticsSummary
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import cn.edu.shmtu.terminal.android.ui.theme.BrandForeground1
import cn.edu.shmtu.terminal.android.ui.theme.GreenForeground3
import cn.edu.shmtu.terminal.android.ui.theme.RedForeground3
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.min

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
                        item { CategoryLegend(categories = categories) }
                    }
                    StatisticsTab.POSITION -> {
                        item { PositionRankingCard(ranking = ranking) }
                    }
                    StatisticsTab.COMPARE -> {
                        item { MonthlySummaryTable(monthly) }
                        item { OverviewCards(overview) }
                    }
                    StatisticsTab.FORGOT -> {
                        item { ForgotCardHint() }
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
                TrendCanvas(data)
            }
        }
    }
}

@Composable
private fun TrendCanvas(data: List<SpendingTrend>) {
    val textMeasurer = rememberTextMeasurer()
    val maxVal = (data.maxOfOrNull { it.amount } ?: 1.0).coerceAtLeast(1.0)
    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        val w = size.width
        val h = size.height
        val padLeft = 44f
        val padBottom = 22f
        val chartW = w - padLeft
        val chartH = h - padBottom
        for (i in 0..4) {
            val y = padBottom / 2 + chartH * (1f - i / 4f)
            drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(padLeft, y), Offset(w, y), 1f)
            drawText(
                textMeasurer, "¥%,.0f".format(maxVal * i / 4),
                topLeft = Offset(0f, y - 6f),
                style = TextStyle(fontSize = 9.sp, color = Color.Gray)
            )
        }
        val path = Path()
        data.forEachIndexed { index, item ->
            val x = padLeft + (chartW / (data.size - 1).coerceAtLeast(1)) * index
            val y = padBottom / 2 + chartH * (1f - (item.amount / maxVal).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(RedForeground3, 3f, Offset(x, y))
        }
        drawPath(path, RedForeground3, style = Stroke(width = 2f))
        val step = if (data.size <= 4) 1 else data.size / 4
        data.forEachIndexed { index, item ->
            if (index % step == 0 || index == data.lastIndex) {
                val x = padLeft + (chartW / (data.size - 1).coerceAtLeast(1)) * index
                drawText(
                    textMeasurer, item.date.substring(5),
                    topLeft = Offset(x - 16f, h - 14f),
                    style = TextStyle(fontSize = 8.sp, color = Color.Gray)
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
                    Canvas(modifier = Modifier.size(140.dp)) {
                        val radius = min(size.width, size.height) / 2f - 16f
                        val innerRadius = radius * 0.55f
                        var startAngle = -90f
                        categories.forEach { item ->
                            val sweepAngle = item.percentage * 360f
                            val color = CategoryDisplay.color(item.type)
                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = Offset(size.width / 2 - radius, size.height / 2 - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = radius - innerRadius)
                            )
                            startAngle += sweepAngle
                        }
                    }
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
private fun CategoryLegend(categories: List<CategoryBreakdown>) {
    if (categories.isEmpty()) return
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("分类图例", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(categories) { _, item ->
                    val color = CategoryDisplay.color(item.type)
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color) }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${CategoryDisplay.displayName(item.type)} ${(item.percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
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
private fun ForgotCardHint() {
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
            Text(
                "请到首页异常提醒卡片查看具体统计,或在账单按类型过滤热水/洗浴记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
