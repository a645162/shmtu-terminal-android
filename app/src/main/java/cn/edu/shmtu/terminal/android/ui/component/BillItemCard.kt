package cn.edu.shmtu.terminal.android.ui.component

import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cn.edu.shmtu.terminal.android.domain.model.BillItem

@Composable
fun BillItemCard(
    bill: BillItem,
    onClick: () -> Unit
) {
    OutlinedCard(onClick = onClick) {
        ListItem(
            headlineContent = {
                Text(
                    text = bill.type,
                    style = MaterialTheme.typography.titleSmall
                )
            },
            supportingContent = {
                Text(
                    text = "${bill.dateTimeStrFormat} · ${bill.targetUser}",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Text(
                    text = "¥${bill.money}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}
