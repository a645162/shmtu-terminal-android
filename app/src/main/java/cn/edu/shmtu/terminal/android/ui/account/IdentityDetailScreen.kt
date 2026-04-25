package cn.edu.shmtu.terminal.android.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.R
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.LoginStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityDetailScreen(
    identityId: Long,
    onAddAccount: () -> Unit,
    onLoginAccount: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: IdentityDetailViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("身份详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_home),
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddAccount,
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_add),
                        contentDescription = null
                    )
                },
                text = { Text("添加账号") }
            )
        }
    ) { innerPadding ->
        if (accounts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无账号",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角按钮添加账号",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.id }) { account ->
                    AccountCardWithLogin(
                        account = account,
                        onLogin = { onLoginAccount(account.id) },
                        onDelete = { viewModel.deleteAccount(account.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCardWithLogin(
    account: Account,
    onLogin: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
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
                    if (account.lastSyncTime != null) {
                        Text(
                            text = "上次同步: ${formatTimestamp(account.lastSyncTime)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            trailingContent = {
                if (account.loginStatus != LoginStatus.LOGGED_IN) {
                    Button(onClick = onLogin) {
                        Text("登录")
                    }
                }
            }
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
