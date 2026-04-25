package cn.edu.shmtu.terminal.android.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.R
import cn.edu.shmtu.terminal.android.domain.model.Identity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityListScreen(
    onIdentityClick: (Long) -> Unit,
    onAddAccount: (Long) -> Unit,
    viewModel: IdentityListViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("账号管理") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_person_add),
                        contentDescription = null
                    )
                },
                text = { Text("添加身份") }
            )
        }
    ) { innerPadding ->
        if (identities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无身份",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击右下角按钮添加身份",
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
                items(identities, key = { it.id }) { identity ->
                    IdentityCard(
                        identity = identity,
                        onClick = { onIdentityClick(identity.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddIdentityDialog(
            onConfirm = { name ->
                viewModel.addIdentity(name)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun IdentityCard(
    identity: Identity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors()
    ) {
        ListItem(
            headlineContent = { Text(identity.name) },
            supportingContent = { Text("${identity.accountCount} 个账号") }
        )
    }
}

@Composable
fun AddIdentityDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加身份") },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("身份名称") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
