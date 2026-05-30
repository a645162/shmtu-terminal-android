package cn.edu.shmtu.terminal.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import kotlin.math.abs

/**
 * 首页 - 对齐 Rust 版 HomePage
 *
 * 布局:
 * 1. 4x 统计卡片 (本月充值, 卡片余额, 今日消费, 本月消费)
 * 2. 最近 5 条交易
 * 3. 快捷操作按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBill: () -> Unit,
    onNavigateToAccount: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val billOverview by viewModel.billOverview.collectAsState()
    val accountCount by viewModel.accountCount.collectAsState()
    val todayExpense by viewModel.todayExpense.collectAsState()
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 统计卡片网格 - 对齐 Rust 版 stat_cards
            StatCardsSection(billOverview, todayExpense)

            // 2. 身份总览
            IdentityOverviewCard(identities.size, accountCount)

            // 3. 最近交易 - 对齐 Rust 版 recent_transactions
            RecentTransactionsCard(recentBills)

            // 4. 快捷操作
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onNavigateToBill,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看账单")
                }
                FilledTonalButton(
                    onClick = onNavigateToStatistics,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("账单统计")
                }
            }

            OutlinedButton(
                onClick = onNavigateToAccount,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("管理账号")
            }
        }
    }
}

/**
 * 统计卡片 - 对齐 Rust 版 stat_cards
 * 4 卡片: 本月充值(绿), 卡片余额(主色), 今日消费(红), 本月消费(红)
 */
@Composable
private fun StatCardsSection(overview: BillOverview?, todayExpense: Double) {
    if (overview == null) return

    // 第一行: 本月充值 + 卡片余额
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "本月充值",
            value = "¥%,.2f".format(overview.totalIncome),
            valueColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "卡片余额",
            value = "暂不可用",
            valueColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
    }

    // 第二行: 今日消费 + 本月消费
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "今日消费",
            value = "¥%,.2f".format(todayExpense),
            valueColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "本月消费",
            value = "¥%,.2f".format(overview.totalSpending),
            subtitle = if (overview.lastMonthSpending > 0) {
                val pct = ((overview.totalSpending - overview.lastMonthSpending) / overview.lastMonthSpending * 100).toInt()
                "${if (pct >= 0) "+" else ""}${pct}% 环比"
            } else null,
            valueColor = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun IdentityOverviewCard(identityCount: Int, accountCount: Int) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "身份总览",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$identityCount 个身份 · $accountCount 个账号",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 最近交易卡片 - 对齐 Rust 版 recent_transactions
 * 显示最近 5 条, 每条: 名称+日期(左), 金额(右, 收入绿色/支出红色)
 */
@Composable
private fun RecentTransactionsCard(bills: List<BillItem>) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "最近交易",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (bills.isEmpty()) {
                Text(
                    text = "暂无交易记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                bills.forEach { bill ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bill.type,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = bill.dateTimeStrFormat,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        val isIncome = bill.type.contains("充值") || bill.type.contains("冲正") ||
                                bill.type.contains("退款") || bill.type.contains("返还")
                        Text(
                            text = "¥${bill.money}",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
