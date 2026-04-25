package cn.edu.shmtu.terminal.android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.LoginStatus

@Composable
fun AccountCard(
    account: Account,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors()
    ) {
        ListItem(
            headlineContent = { Text("${account.label} - ${account.userId}") },
            supportingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (account.loginStatus) {
                            LoginStatus.LOGGED_IN -> "已登录"
                            LoginStatus.LOGGED_OUT -> "未登录"
                            LoginStatus.ERROR -> "登录错误"
                        },
                        color = when (account.loginStatus) {
                            LoginStatus.LOGGED_IN -> MaterialTheme.colorScheme.primary
                            LoginStatus.LOGGED_OUT -> MaterialTheme.colorScheme.onSurfaceVariant
                            LoginStatus.ERROR -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        )
    }
}
