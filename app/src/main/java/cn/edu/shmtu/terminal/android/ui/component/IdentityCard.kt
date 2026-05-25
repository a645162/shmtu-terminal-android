package cn.edu.shmtu.terminal.android.ui.component

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cn.edu.shmtu.terminal.android.domain.model.Identity

@Composable
fun IdentityCard(
    identity: Identity,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors()
    ) {
        ListItem(
            headlineContent = { Text(identity.remark) },
            supportingContent = { Text("${identity.accountCount} 个账号") }
        )
    }
}
