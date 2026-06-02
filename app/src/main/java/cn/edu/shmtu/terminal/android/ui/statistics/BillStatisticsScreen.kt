package cn.edu.shmtu.terminal.android.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import cn.edu.shmtu.terminal.android.ui.theme.CategoryColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.min

// 使用与 Rust 版对齐的分类颜色
private val CHART_COLORS = CategoryColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillStatisticsScreen(
    onBack: () -> Unit,
    viewModel: BillStatisticsViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val overview by viewModel.overview.collectAsState()
    val trend by viewModel.spendingTrend.collectAsState()
    val categories by viewModel.categoryBreakdown.collectAsState()
    val ranking by viewModel.targetUserRanking.collectAsState()
    val monthly by viewModel.monthlySummary.collectAsState()
    val customStart by viewModel.customStartDate.collectAsState()
    val customEnd by viewModel.customEndDate.collectAsState()
    var selectedIdentityId by remember { mutableStateOf<Long?>(null) }
    var selectedPeriod by remember { mutableStateOf(TimePeriod.THIS_MONTH) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("账单统计") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                IdentityFilterChips(
                    identities = identities,
                    selectedIdentityId = selectedIdentityId,
                    onSelected = {
                        selectedIdentityId = it
                        viewModel.selectIdentity(it)
                    }
                )
            }

            item {
                TimePeriodSelector(
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = {
                        selectedPeriod = it
                        viewModel.selectPeriod(it)
                    }
                )
            }

            if (selectedPeriod == TimePeriod.CUSTOM) {
                item {
                    CustomDateRangeSelector(
                        startDate = customStart,
                        endDate = customEnd,
                        onStartDateSelected = {
                            viewModel.setCustomDateRange(it, customEnd)
                        },
                        onEndDateSelected = {
                            viewModel.setCustomDateRange(customStart, it)
                        }
                    )
                }
            }

            item { OverviewCards(overview) }

            item { TrendChart(trend) }

            item { CategoryDonutChart(categories) }

            item { TargetUserRankingCard(ranking) }

            item { MonthlySummaryTable(monthly) }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun IdentityFilterChips(
    identities: List<cn.edu.shmtu.terminal.android.domain.model.Identity>,
    selectedIdentityId: Long?,
    onSelected: (Long?) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedIdentityId == null,
            onClick = { onSelected(null) },
            label = { Text("全部") }
        )
        identities.forEach { identity ->
            FilterChip(
                selected = selectedIdentityId == identity.id,
                onClick = { onSelected(identity.id) },
                label = { Text(identity.remark) }
            )
        }
    }
}

@Composable
private fun TimePeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = TimePeriod.entries.indexOf(selectedPeriod),
        edgePadding = 0.dp,
        divider = {}
    ) {
        TimePeriod.entries.forEach { period ->
            Tab(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                text = { Text(period.label) }
            )
        }
    }
}

@Composable
private fun OverviewCards(overview: BillOverview?) {
    if (overview == null) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.elevatedCardColors()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("本月支出", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "¥%,.2f".format(overview.totalSpending),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    val pct = if (overview.lastMonthSpending > 0) {
                        ((overview.totalSpending - overview.lastMonthSpending) / overview.lastMonthSpending * 100).toInt()
                    } else 0
                    Text(
                        "${if (pct >= 0) "↑" else "↓"}${abs(pct)}% 环比",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            ElevatedCard(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.elevatedCardColors()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("本月收入", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "¥%,.2f".format(overview.totalIncome),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val pct = if (overview.lastMonthIncome > 0) {
                        ((overview.totalIncome - overview.lastMonthIncome) / overview.lastMonthIncome * 100).toInt()
                    } else 0
                    Text(
                        "${if (pct >= 0) "↑" else "↓"}${abs(pct)}% 环比",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("净变化", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${if (overview.netChange >= 0) "+" else ""}¥%,.2f".format(overview.netChange),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (overview.netChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("日均消费", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "¥%,.2f".format(overview.dailyAverage),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("交易笔数", style = MaterialTheme.typography.labelMedium)
                    Text("${overview.transactionCount} 笔", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun TrendChart(data: List<SpendingTrend>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("消费趋势", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                val textMeasurer = rememberTextMeasurer()
                val maxVal = data.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val padding = 40f
                    val chartW = w - padding
                    val chartH = h - padding

                    for (i in 0..4) {
                        val y = padding / 2 + chartH * (1f - i / 4f)
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(padding, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "¥%,.0f".format(maxVal * i / 4),
                            topLeft = Offset(0f, y - 6f),
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        )
                    }

                    val path = Path()
                    data.forEachIndexed { index, item ->
                        val x = padding + (chartW / (data.size - 1).coerceAtLeast(1)) * index
                        val y = padding / 2 + chartH * (1f - (item.amount / maxVal).toFloat())
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(
                            color = Color(0xFFE86452),
                            radius = 4f,
                            center = Offset(x, y)
                        )
                    }
                    drawPath(path, Color(0xFFE86452), style = Stroke(width = 2f))
                }
            }
        }
    }
}

@Composable
private fun CategoryDonutChart(data: List<CategoryBreakdown>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("分类占比", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (data.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无数据", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(modifier = Modifier.size(140.dp)) {
                        val radius = min(size.width, size.height) / 2f - 16f
                        val innerRadius = radius * 0.6f
                        var startAngle = -90f

                        data.forEachIndexed { index, item ->
                            val sweepAngle = item.percentage * 360f
                            drawArc(
                                color = CHART_COLORS[index % CHART_COLORS.size],
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
                    Column(modifier = Modifier.weight(1f)) {
                        data.forEachIndexed { index, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Canvas(modifier = Modifier.size(12.dp)) {
                                    drawCircle(color = CHART_COLORS[index % CHART_COLORS.size])
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${item.type} ${(item.percentage * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetUserRankingCard(data: List<TargetUserRanking>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("消费排行", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (data.isEmpty()) {
                Text("暂无数据", color = MaterialTheme.colorScheme.outline)
            } else {
                val maxAmount = data.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
                data.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            item.targetUser,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Canvas(modifier = Modifier
                            .weight(1f)
                            .height(16.dp)) {
                            val barWidth = (item.amount / maxAmount).toFloat() * size.width
                            drawRoundRect(
                                color = CHART_COLORS[index % CHART_COLORS.size],
                                topLeft = Offset.Zero,
                                size = Size(barWidth, size.height)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
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
private fun MonthlySummaryTable(data: List<MonthlySummary>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("月度汇总", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (data.isEmpty()) {
                Text("暂无数据", color = MaterialTheme.colorScheme.outline)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("月份", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Text("支出", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("收入", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                data.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.month, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            "¥%,.2f".format(item.spending),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End
                        )
                        Text(
                            "¥%,.2f".format(item.income),
                            style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun CustomDateRangeSelector(
    startDate: LocalDate?,
    endDate: LocalDate?,
    onStartDateSelected: (LocalDate?) -> Unit,
    onEndDateSelected: (LocalDate?) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ElevatedCard(
            modifier = Modifier
                .weight(1f)
                .clickable { showStartPicker = true },
            colors = CardDefaults.elevatedCardColors()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("开始日期", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = startDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "点击选择",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (startDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }
        }
        ElevatedCard(
            modifier = Modifier
                .weight(1f)
                .clickable { showEndPicker = true },
            colors = CardDefaults.elevatedCardColors()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("结束日期", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = endDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "点击选择",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (endDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showStartPicker) {
        DatePickerModal(
            initialDate = startDate,
            onDateSelected = {
                onStartDateSelected(it)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        DatePickerModal(
            initialDate = endDate,
            onDateSelected = {
                onEndDateSelected(it)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
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
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = state.selectedDateMillis?.let { millis ->
                        Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                    onDateSelected(selected)
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    ) {
        DatePicker(state = state)
    }
}
