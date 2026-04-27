package cn.edu.shmtu.terminal.android.ui.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val editingAccount by viewModel.editingAccount.collectAsState()
    var expandedMenuAccount by remember { mutableLongStateOf(-1L) }

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
                    SwipeableAccountCard(
                        account = account,
                        onLogin = { onLoginAccount(account.id) },
                        onEdit = { viewModel.startEditAccount(account) },
                        onDelete = { viewModel.deleteAccount(account.id) },
                        onLongClick = {
                            expandedMenuAccount = if (expandedMenuAccount == account.id) -1L else account.id
                        },
                        expanded = expandedMenuAccount == account.id,
                        onDismissMenu = { expandedMenuAccount = -1L }
                    )
                }
            }
        }
    }

    editingAccount?.let { account ->
        EditAccountDialog(
            account = account,
            onConfirm = { label, userId ->
                viewModel.updateAccount(account.id, label, userId)
            },
            onDismiss = { viewModel.cancelEditAccount() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAccountCard(
    account: Account,
    onLogin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    expanded: Boolean,
    onDismissMenu: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.25f },
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
    val direction = when {
        dismissState.targetValue != SwipeToDismissBoxValue.Settled -> dismissState.targetValue
        offset > 0f -> SwipeToDismissBoxValue.StartToEnd
        offset < 0f -> SwipeToDismissBoxValue.EndToStart
        else -> SwipeToDismissBoxValue.Settled
    }

    val loginLabel = if (account.loginStatus == LoginStatus.LOGGED_IN) "重新登录" else "登录"

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color by animateColorAsState(
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                },
                label = "swipe_color"
            )
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> R.drawable.ic_edit
                SwipeToDismissBoxValue.EndToStart -> R.drawable.ic_delete
                else -> 0
            }
            val text = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> "编辑"
                SwipeToDismissBoxValue.EndToStart -> "删除"
                else -> ""
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.CenterEnd
            }
            val tint = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onErrorContainer
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                if (icon != 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = text,
                            tint = tint
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = text, color = tint)
                    }
                }
            }
        }
    ) {
        Card(
            modifier = Modifier.combinedClickable(
                onClick = onLogin,
                onLongClick = onLongClick
            ),
            colors = CardDefaults.elevatedCardColors()
        ) {
            Box {
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
                    }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = onDismissMenu
                ) {
                    DropdownMenuItem(
                        text = { Text(loginLabel) },
                        onClick = {
                            onDismissMenu()
                            onLogin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            onDismissMenu()
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onDismissMenu()
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditAccountDialog(
    account: Account,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(account.label) }
    var userId by remember { mutableStateOf(account.userId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑账号") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxSize()
                )
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (label.isNotBlank() && userId.isNotBlank()) onConfirm(label, userId) },
                enabled = label.isNotBlank() && userId.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
