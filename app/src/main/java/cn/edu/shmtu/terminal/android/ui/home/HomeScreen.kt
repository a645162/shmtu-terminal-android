package cn.edu.shmtu.terminal.android.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.ui.theme.CategoryColors
import kotlin.math.min

/**
 * 首页 - 对齐 Rust 版 HomePage
 *
 * 布局:
 * 1. 4x 统计卡片 (本月充值, 卡片余额, 今日消费, 本月消费)
 * 2. 近7天消费趋势折线图
 * 3. 本月分类占比饼图
 * 4. 月度对比卡片
 * 5. 异常提醒 (忘拔卡)
 * 6. 最近 5 条交易
 * 7. 快捷操作按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBill: () -> Unit,
    onNavigateToMe: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val currentIdentity by viewModel.currentIdentity.collectAsState()
    val billOverview by viewModel.billOverview.collectAsState()
    val todayExpense by viewModel.todayExpense.collectAsState()
    val weeklyTrend by viewModel.weeklyTrend.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val monthlySummary by viewModel.monthlySummary.collectAsState()
    val forgotCardRisk by viewModel.forgotCardRisk.collectAsState()
    val recentBills by viewModel.recentBills.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("海事终端") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentIdentity == null) {
                EmptyIdentityCard(
                    identityCount = identities.size,
                    onNavigateToMe = onNavigateToMe
                )
                return@Column
            }
            StatCardsSection(billOverview, todayExpense)
            TrendChartCard(weeklyTrend)
            CategoryPieCard(categoryBreakdown)
            MonthComparisonCard(monthlySummary)
            ForgotCardAlertCard(forgotCardRisk)
            IdentityOverviewCard(
                currentIdentity = currentIdentity,
                identityCount = identities.size
            )
            RecentTransactionsCard(recentBills)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onNavigateToBill,
                    modifier = Modifier.weight(1f)
                ) { Text("查看账单") }
                FilledTonalButton(
                    onClick = onNavigateToStatistics,
                    modifier = Modifier.weight(1f)
                ) { Text("账单统计") }
            }
            OutlinedButton(
                onClick = onNavigateToMe,
                modifier = Modifier.fillMaxWidth()
            ) { Text("切换身份") }
        }
    }
}

// ==================== 统计卡片 ====================

@Composable
private fun StatCardsSection(overview: BillOverview?, todayExpense: Double) {
    if (overview == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("本月充值", "¥%,.2f".format(overview.totalIncome), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        StatCard("卡片余额", "暂不可用", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("今日消费", "¥%,.2f".format(todayExpense), MaterialTheme.colorScheme.error, Modifier.weight(1f))
        val subtitle = if (overview.lastMonthSpending > 0) {
            val pct = ((overview.totalSpending - overview.lastMonthSpending) / overview.lastMonthSpending * 100).toInt()
            "${if (pct >= 0) "+" else ""}${pct}% 环比"
        } else null
        StatCard("本月消费", "¥%,.2f".format(overview.totalSpending), MaterialTheme.colorScheme.error, Modifier.weight(1f), subtitle)
    }
}

@Composable
private fun StatCard(
    title: String, value: String, valueColor: Color,
    modifier: Modifier = Modifier, subtitle: String? = null
) {
    ElevatedCard(modifier = modifier, colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.Start) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ==================== 消费趋势折线图 ====================

@Composable
private fun TrendChartCard(data: List<SpendingTrend>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("近7天消费趋势", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (data.isEmpty()) {
                EmptyChartPlaceholder()
            } else {
                val textMeasurer = rememberTextMeasurer()
                val maxVal = data.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
                Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    val w = size.width
                    val h = size.height
                    val padLeft = 44f
                    val padBottom = 20f
                    val chartW = w - padLeft
                    val chartH = h - padBottom

                    for (i in 0..4) {
                        val y = padBottom / 2 + chartH * (1f - i / 4f)
                        drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(padLeft, y), Offset(w, y), 1f)
                        drawText(textMeasurer, "¥%,.0f".format(maxVal * i / 4), topLeft = Offset(0f, y - 6f),
                            style = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, color = Color.Gray))
                    }

                    val path = Path()
                    data.forEachIndexed { index, item ->
                        val x = padLeft + (chartW / (data.size - 1).coerceAtLeast(1)) * index
                        val y = padBottom / 2 + chartH * (1f - (item.amount / maxVal).toFloat())
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(Color(0xFFE86452), 3f, Offset(x, y))
                    }
                    drawPath(path, Color(0xFFE86452), style = Stroke(width = 2f))

                    val step = if (data.size <= 4) 1 else data.size / 4
                    data.forEachIndexed { index, item ->
                        if (index % step == 0 || index == data.lastIndex) {
                            val x = padLeft + (chartW / (data.size - 1).coerceAtLeast(1)) * index
                            drawText(textMeasurer, item.date.substring(5), topLeft = Offset(x - 16f, h - 14f),
                                style = androidx.compose.ui.text.TextStyle(fontSize = 8.sp, color = Color.Gray))
                        }
                    }
                }
            }
        }
    }
}

// ==================== 分类占比饼图 ====================

@Composable
private fun CategoryPieCard(data: List<CategoryBreakdown>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本月消费分类", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (data.isEmpty()) {
                EmptyChartPlaceholder()
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        val radius = min(size.width, size.height) / 2f - 12f
                        val innerRadius = radius * 0.6f
                        var startAngle = -90f
                        data.forEachIndexed { index, item ->
                            val sweepAngle = item.percentage * 360f
                            drawArc(CategoryColors[index % CategoryColors.size], startAngle, sweepAngle, false,
                                Offset(size.width / 2 - radius, size.height / 2 - radius), Size(radius * 2, radius * 2),
                                style = Stroke(width = radius - innerRadius))
                            startAngle += sweepAngle
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        data.take(6).forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                                Canvas(modifier = Modifier.size(10.dp)) { drawCircle(CategoryColors[index % CategoryColors.size]) }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${item.type} ${(item.percentage * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 月度对比卡片 ====================

@Composable
private fun MonthComparisonCard(data: List<MonthlySummary>) {
    if (data.isEmpty()) return
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("月度对比", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            val recent = data.take(3)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("月份", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("支出", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text("收入", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            recent.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.month, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("¥%,.0f".format(item.spending), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    Text("¥%,.0f".format(item.income), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
}

// ==================== 忘拔卡异常提醒 ====================

@Composable
private fun ForgotCardAlertCard(risk: ForgotCardRisk) {
    val hasRisk = risk.count > 0
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = if (hasRisk) CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        else CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("异常提醒", style = MaterialTheme.typography.titleMedium)
                Badge {
                    Text(if (hasRisk) "${risk.count} 条" else "正常")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("疑似忘拔卡统计", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${risk.count} 次", style = MaterialTheme.typography.titleMedium)
            Text("累计金额 ¥%,.2f".format(risk.totalAmount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasRisk) {
                Text("建议到统计详情里继续核对洗浴消费记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else {
                Text("当前没有检测到明显的忘拔卡高风险记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ==================== 身份总览 ====================

@Composable
private fun IdentityOverviewCard(currentIdentity: cn.edu.shmtu.terminal.android.domain.model.Identity?, identityCount: Int) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("当前身份", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                currentIdentity?.remark?.ifBlank { currentIdentity.username } ?: "未选择身份",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "${currentIdentity?.accountCount ?: 0} 个账号 · 共 $identityCount 个身份",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyIdentityCard(identityCount: Int, onNavigateToMe: () -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("还没有当前身份", style = MaterialTheme.typography.titleMedium)
            Text(
                if (identityCount == 0) "先创建一个身份，再查看首页统计和账单。"
                else "请先在“我”里切换到一个身份。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onNavigateToMe) {
                Text(if (identityCount == 0) "去创建身份" else "去切换身份")
            }
        }
    }
}

// ==================== 最近交易 ====================

@Composable
private fun RecentTransactionsCard(bills: List<BillItem>) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("最近交易", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (bills.isEmpty()) {
                Text("暂无交易记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                bills.forEach { bill ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bill.type, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(bill.dateTimeStrFormat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        val isIncome = bill.type.contains("充值") || bill.type.contains("冲正") || bill.type.contains("退款") || bill.type.contains("返还")
                        Text("¥${bill.money}", style = MaterialTheme.typography.titleSmall, color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ==================== 通用组件 ====================

@Composable
private fun EmptyChartPlaceholder() {
    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text("暂无数据", color = MaterialTheme.colorScheme.outline)
    }
}
