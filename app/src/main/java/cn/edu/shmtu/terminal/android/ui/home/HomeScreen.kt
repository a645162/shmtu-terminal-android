package cn.edu.shmtu.terminal.android.ui.home

import android.content.ClipData

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.Badge
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.displayTitle
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.DailyTrend
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.StatisticsSummary
import cn.edu.shmtu.terminal.android.ui.component.AppDonutChart
import cn.edu.shmtu.terminal.android.ui.component.AppDonutSlice
import cn.edu.shmtu.terminal.android.ui.component.AppLineChart
import cn.edu.shmtu.terminal.android.ui.component.AppLineSeries
import cn.edu.shmtu.terminal.android.ui.settings.LocalFeatureStore
import cn.edu.shmtu.terminal.android.ui.statistics.CategoryDisplay
import cn.edu.shmtu.terminal.android.ui.theme.BrandForeground1
import cn.edu.shmtu.terminal.android.ui.theme.CategoryColors
import cn.edu.shmtu.terminal.android.ui.theme.GreenForeground3
import cn.edu.shmtu.terminal.android.ui.theme.RedForeground3
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 首页 - 对齐 Tauri 版 HomePage
 *
 * 布局(对齐 Rust HomePage.tsx):
 * 1. 标题区: "首页统计" + 副标题 + 右上角 [查看更多] [刷新统计] 两个 IconButton
 * 2. 4x 统计卡片 (今日消费 / 本月消费 / 本月充值 / 卡片余额)
 * 3. 趋势卡片 (近7天) + 分类饼图 (本月)
 * 4. 月度对比卡片
 * 5. 异常提醒 (忘拔卡)
 * 6. 最近 5 条交易 (支持点击查看详情 + 复制菜单)
 * 7. 底部快捷按钮 [查看账单] [切换身份]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBill: () -> Unit,
    onNavigateToMe: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onBillClick: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val featureStore = LocalFeatureStore.current
    val preferParsedBillDisplay by featureStore.preferParsedBillDisplay.collectAsState()
    val identities by viewModel.identities.collectAsState()
    val currentIdentity by viewModel.currentIdentity.collectAsState()
    val billOverview by viewModel.billOverview.collectAsState()
    val weeklyTrend by viewModel.weeklyTrend.collectAsState()
    val dailyTrend by viewModel.dailyTrend.collectAsState()
    val categoryBreakdown by viewModel.categoryBreakdown.collectAsState()
    val monthlySummary by viewModel.monthlySummary.collectAsState()
    val forgotCardRisk by viewModel.forgotCardRisk.collectAsState()
    val recentBills by viewModel.recentBills.collectAsState()
    val todaySummary by viewModel.todaySummary.collectAsState()
    val monthSummary by viewModel.monthSummary.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingStatistics by viewModel.isLoadingStatistics.collectAsState()
    val personAccount by viewModel.currentPersonAccount.collectAsState()
    val isRefreshingBalance by viewModel.isRefreshingBalance.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val configuration = LocalConfiguration.current
    val ultraWide = configuration.screenWidthDp >= 1200

    Scaffold(
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = {
                    Column {
                        Text("首页统计")
                        Text(
                            "快速查看近期消费、分类趋势和异常提醒",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToStatistics,
                        enabled = currentIdentity != null
                    ) {
                        Icon(Icons.Filled.OpenInFull, contentDescription = "查看更多")
                    }
                    IconButton(
                        onClick = { viewModel.refreshStatistics() },
                        enabled = currentIdentity != null && !isRefreshing
                    ) {
                        if (isRefreshing) {
                            SpinningRefreshIcon()
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新统计")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        if (ultraWide) {
            if (currentIdentity == null) {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    EmptyIdentityCard(
                        identityCount = identities.size,
                        onNavigateToMe = onNavigateToMe
                    )
                }
                return@Scaffold
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .padding(innerPadding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HomeDeskIntroCard()
                    BalanceCard(
                        personAccount = personAccount,
                        isRefreshing = isRefreshingBalance,
                        onRefresh = { viewModel.refreshCurrentBalance() }
                    )
                    StatCardsSection(
                        todaySummary = todaySummary,
                        monthSummary = monthSummary,
                        billOverview = billOverview,
                        isLoading = isLoadingStatistics,
                        wide = true
                    )
                    ForgotCardAlertCard(forgotCardRisk)
                    QuickActionsCard(
                        onNavigateToBill = onNavigateToBill,
                        onNavigateToMe = onNavigateToMe
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1.45f)
                        .padding(top = innerPadding.calculateTopPadding(), end = 20.dp, bottom = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TrendChartCard(
                            legacyData = weeklyTrend,
                            dailyTrend = dailyTrend,
                            isLoading = isLoadingStatistics,
                            modifier = Modifier.weight(1.2f)
                        )
                        CategoryPieCard(
                            data = categoryBreakdown,
                            isLoading = isLoadingStatistics,
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        MonthComparisonCard(
                            data = monthlySummary,
                            modifier = Modifier.weight(0.82f)
                        )
                        RecentTransactionsCard(
                            bills = recentBills,
                            onBillClick = onBillClick,
                            modifier = Modifier.weight(1.18f),
                            compact = true,
                            preferParsedDisplay = preferParsedBillDisplay
                        )
                    }
                }
            }
        } else {
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
                BalanceCard(
                    personAccount = personAccount,
                    isRefreshing = isRefreshingBalance,
                    onRefresh = { viewModel.refreshCurrentBalance() }
                )
                StatCardsSection(
                    todaySummary = todaySummary,
                    monthSummary = monthSummary,
                    billOverview = billOverview,
                    isLoading = isLoadingStatistics
                )
                TrendChartCard(
                    legacyData = weeklyTrend,
                    dailyTrend = dailyTrend,
                    isLoading = isLoadingStatistics
                )
                CategoryPieCard(categoryBreakdown, isLoading = isLoadingStatistics)
                MonthComparisonCard(monthlySummary)
                ForgotCardAlertCard(forgotCardRisk)
                RecentTransactionsCard(
                    bills = recentBills,
                    onBillClick = onBillClick,
                    preferParsedDisplay = preferParsedBillDisplay
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onNavigateToBill,
                        modifier = Modifier.weight(1f)
                    ) { Text("查看账单") }
                    FilledTonalButton(
                        onClick = onNavigateToMe,
                        modifier = Modifier.weight(1f)
                    ) { Text("切换身份") }
                }
            }
        }
    }
}

// ==================== 顶栏 旋转图标 ====================

@Composable
private fun SpinningRefreshIcon() {
    val transition = rememberInfiniteTransition(label = "refresh-spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh-spin-deg"
    )
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawContext.transform.rotate(rotation, Offset(cx, cy))
            drawArc(
                color = BrandForeground1,
                startAngle = 0f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
                size = Size(size.width * 0.8f, size.height * 0.8f),
                style = Stroke(width = 2.5f)
            )
        }
    }
}

@Suppress("unused")
private fun DrawScope.drawRotatedArc(degrees: Float, color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    drawContext.transform.rotate(degrees, Offset(cx, cy))
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 280f,
        useCenter = false,
        topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
        size = Size(size.width * 0.8f, size.height * 0.8f),
        style = Stroke(width = 2.5f)
    )
}

// ==================== 统计卡片 ====================

@Composable
private fun StatCardsSection(
    todaySummary: StatisticsSummary?,
    monthSummary: StatisticsSummary?,
    billOverview: BillOverview?,
    isLoading: Boolean,
    wide: Boolean = false
) {
    val todayExpenseText = when {
        todaySummary != null -> "¥%,.2f".format(abs(todaySummary.totalExpense))
        billOverview != null -> "¥%,.2f".format(billOverview.totalSpending)
        else -> "¥0.00"
    }
    val monthExpenseText = when {
        monthSummary != null -> "¥%,.2f".format(abs(monthSummary.totalExpense))
        billOverview != null -> "¥%,.2f".format(billOverview.totalSpending)
        else -> "¥0.00"
    }
    val monthIncomeText = when {
        monthSummary != null -> "¥%,.2f".format(monthSummary.totalIncome)
        billOverview != null -> "¥%,.2f".format(billOverview.totalIncome)
        else -> "¥0.00"
    }
    val isFirstLoad = isLoading && todaySummary == null && monthSummary == null && billOverview == null

    if (wide) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "今日消费",
                    value = if (isFirstLoad) "加载中..." else todayExpenseText,
                    valueColor = RedForeground3,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "本月消费",
                    value = if (isFirstLoad) "加载中..." else monthExpenseText,
                    valueColor = RedForeground3,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "本月充值",
                    value = if (isFirstLoad) "加载中..." else monthIncomeText,
                    valueColor = GreenForeground3,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "卡片余额",
                    value = "暂不可用",
                    valueColor = BrandForeground1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "今日消费",
                value = if (isFirstLoad) "加载中..." else todayExpenseText,
                valueColor = RedForeground3,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "本月消费",
                value = if (isFirstLoad) "加载中..." else monthExpenseText,
                valueColor = RedForeground3,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "本月充值",
                value = if (isFirstLoad) "加载中..." else monthIncomeText,
                valueColor = GreenForeground3,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "卡片余额",
                value = "暂不可用",
                valueColor = BrandForeground1,
                modifier = Modifier.weight(1f)
            )
        }
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
private fun TrendChartCard(
    legacyData: List<SpendingTrend>,
    dailyTrend: List<DailyTrend>,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    // 优先使用新 DailyTrend (双线), 否则使用老 SpendingTrend
    val useDaily = dailyTrend.isNotEmpty()
    ElevatedCard(modifier = modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("近7天消费趋势", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoading && !useDaily && legacyData.isEmpty()) {
                SkeletonChartBlock(height = 140.dp)
            } else if (!useDaily && legacyData.isEmpty()) {
                EmptyChartPlaceholder()
            } else {
                if (useDaily) {
                    AppLineChart(
                        labels = dailyTrend.map { it.date.substring(5) },
                        series = listOf(
                            AppLineSeries(
                                color = RedForeground3,
                                values = dailyTrend.map { it.expense.toFloat() },
                            ),
                            AppLineSeries(
                                color = GreenForeground3,
                                values = dailyTrend.map { it.income.toFloat() },
                            ),
                        ),
                    )
                } else {
                    AppLineChart(
                        labels = legacyData.map { it.date.substring(5) },
                        series = listOf(
                            AppLineSeries(
                                color = RedForeground3,
                                values = legacyData.map { it.amount.toFloat() },
                            ),
                        ),
                        height = 140.dp,
                    )
                }
            }
        }
    }
}

// ==================== 分类占比饼图 ====================

@Composable
private fun CategoryPieCard(data: List<CategoryBreakdown>, isLoading: Boolean, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("本月消费分类", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoading && data.isEmpty()) {
                SkeletonChartBlock(height = 140.dp)
            } else if (data.isEmpty()) {
                EmptyChartPlaceholder()
            } else {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppDonutChart(
                        slices = data.mapIndexed { index, item ->
                            AppDonutSlice(
                                label = CategoryDisplay.displayName(item.type),
                                value = item.amount.toFloat(),
                                color = CategoryColors[index % CategoryColors.size],
                            )
                        },
                        modifier = Modifier.size(120.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        data.take(6).forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                                Canvas(modifier = Modifier.size(10.dp)) { drawCircle(CategoryColors[index % CategoryColors.size]) }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "${CategoryDisplay.displayName(item.type)} ${(item.percentage * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
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
private fun MonthComparisonCard(data: List<MonthlySummary>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    ElevatedCard(modifier = modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
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
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (hasRisk) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else GreenForeground3.copy(alpha = 0.14f),
                    tonalElevation = 0.dp
                ) {
                    Text(
                        text = if (hasRisk) "${risk.count} 条" else "正常",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasRisk) MaterialTheme.colorScheme.error else GreenForeground3,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
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
private fun RecentTransactionsCard(
    bills: List<BillItem>,
    onBillClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    preferParsedDisplay: Boolean = true
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ElevatedCard(modifier = modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors()) {
        Column(modifier = Modifier.padding(if (compact) 14.dp else 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("最近交易", style = MaterialTheme.typography.titleMedium)
                Text(
                    "长按复制 · 点击查看详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (bills.isEmpty()) {
                Text("暂无交易记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                bills.take(if (compact) 8 else bills.size).forEach { bill ->
                    BillRow(
                        bill = bill,
                        preferParsedDisplay = preferParsedDisplay,
                        onClick = { onBillClick(bill.id) },
                        onCopyTarget = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipData.newPlainText("bill-target-user", bill.targetUser).toClipEntry()
                                )
                            }
                        },
                        onCopyMoney = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipData.newPlainText("bill-money", "¥${bill.money}").toClipEntry()
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDeskIntroCard() {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("首页工作台", style = MaterialTheme.typography.titleLarge)
            Text(
                "概览、提醒和趋势分区展示，重要信息一眼可见，查看近况和明细更顺手。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BalanceCard(
    personAccount: cn.edu.shmtu.terminal.android.domain.model.PersonAccount?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val cashText = when {
        personAccount == null -> "点击刷新"
        personAccount.cashBalanceRaw.isNotBlank() -> "${personAccount.cashBalanceRaw} 元"
        else -> "%.2f 元".format(personAccount.cashBalance)
    }
    val subtitle = when {
        personAccount == null -> "尚未获取一卡通余额"
        personAccount.realName.isNotBlank() -> personAccount.realName +
            (if (personAccount.studentId.isNotBlank()) " · ${personAccount.studentId}" else "")
        else -> "当前选中账号的现金资金"
    }
    val updatedLabel = personAccount?.updatedAt?.takeIf { it > 0 }?.let {
        "更新于 " + java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRefresh),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "当前余额",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                )
                Text(
                    text = cashText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1
                )
                if (updatedLabel != null) {
                    Text(
                        text = updatedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
            if (isRefreshing) {
                SpinningRefreshIcon()
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "刷新余额",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionsCard(
    onNavigateToBill: () -> Unit,
    onNavigateToMe: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("快捷入口", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onNavigateToBill,
                    modifier = Modifier.weight(1f)
                ) { Text("查看账单") }
                FilledTonalButton(
                    onClick = onNavigateToMe,
                    modifier = Modifier.weight(1f)
                ) { Text("切换身份") }
            }
        }
    }
}

@Composable
private fun BillRow(
    bill: BillItem,
    preferParsedDisplay: Boolean,
    onClick: () -> Unit,
    onCopyTarget: () -> Unit,
    onCopyMoney: () -> Unit
) {
    val isIncome = bill.type.contains("充值") || bill.type.contains("冲正") ||
        bill.type.contains("退款") || bill.type.contains("返还")
    var menuVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { menuVisible = true }
            )
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bill.displayTitle(preferParsedDisplay),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Text(
                    bill.dateTimeStrFormat,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                "¥${bill.money}",
                style = MaterialTheme.typography.titleSmall,
                color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        DropdownMenu(
            expanded = menuVisible,
            onDismissRequest = { menuVisible = false }
        ) {
            DropdownMenuItem(
                text = { Text("复制对方账户") },
                onClick = {
                    onCopyTarget()
                    menuVisible = false
                }
            )
            DropdownMenuItem(
                text = { Text("复制金额") },
                onClick = {
                    onCopyMoney()
                    menuVisible = false
                }
            )
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

@Composable
private fun SkeletonChartBlock(height: Dp) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
                shape = RoundedCornerShape(8.dp)
            )
    )
}
