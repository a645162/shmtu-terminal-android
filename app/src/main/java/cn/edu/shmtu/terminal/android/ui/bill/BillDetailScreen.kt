package cn.edu.shmtu.terminal.android.ui.bill

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    billId: Long,
    onBack: () -> Unit,
    viewModel: BillDetailViewModel = hiltViewModel()
) {
    val bill = viewModel.billValue

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单详情") }
            )
        }
    ) { innerPadding ->
        bill?.let { item ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                BillDetailRow("来源账号", item.accountLabel)
                BillDetailRow("日期时间", item.dateTimeStrFormat)
                BillDetailRow("消费类型", item.type)
                BillDetailRow("交易号", item.transactionNo)
                BillDetailRow("对方", item.targetUser)
                BillDetailRow("金额", "¥${item.money}")
                BillDetailRow("方式", item.method)
                BillDetailRow("状态", item.status)
            }
        }
    }
}

@Composable
private fun BillDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
