package cn.edu.shmtu.terminal.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import kotlin.math.abs

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

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("海事终端") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BillOverviewSection(billOverview)

            IdentityOverviewCard(identities.size, accountCount)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToBill,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看账单")
                }
                OutlinedButton(
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

@Composable
private fun BillOverviewSection(overview: BillOverview?) {
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
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("净变化", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${if (overview.netChange >= 0) "+" else ""}¥%,.2f".format(overview.netChange),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (overview.netChange >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("交易笔数", style = MaterialTheme.typography.labelSmall)
                    Text("${overview.transactionCount} 笔", style = MaterialTheme.typography.titleSmall)
                }
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
