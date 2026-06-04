package cn.edu.shmtu.terminal.android.ui.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.Identity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityListScreen(
    onIdentityClick: (Long) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: IdentityListViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val editingIdentity by viewModel.editingIdentity.collectAsState()
    val editingDetailsIdentity by viewModel.editingDetailsIdentity.collectAsState()
    val deletingIdentity by viewModel.deletingIdentity.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var expandedMenuIdentity by remember { mutableLongStateOf(-1L) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号管理") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
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
                    SwipeableIdentityCard(
                        identity = identity,
                        onClick = { onIdentityClick(identity.id) },
                        onEdit = { viewModel.startEditIdentity(identity) },
                        onEditDetails = { viewModel.startEditDetails(identity) },
                        onDelete = { viewModel.startDeleteIdentity(identity) },
                        onLongClick = { expandedMenuIdentity = if (expandedMenuIdentity == identity.id) -1L else identity.id },
                        expanded = expandedMenuIdentity == identity.id,
                        onDismissMenu = { expandedMenuIdentity = -1L }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddIdentityDialog(
            defaultName = "身份${identities.size + 1}",
            onConfirm = { name ->
                viewModel.addIdentity(name)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editingIdentity?.let { identity ->
        EditIdentityDialog(
            identity = identity,
            onConfirm = { name ->
                viewModel.updateIdentity(identity.copy(remark = name))
            },
            onDismiss = { viewModel.cancelEdit() }
        )
    }

    editingDetailsIdentity?.let { identity ->
        EditIdentityDetailsDialog(
            identity = identity,
            onConfirm = { name, birthday, enrollmentDate, graduationDate ->
                viewModel.updateIdentityDetails(identity.copy(remark = name, birthday = birthday, enrollmentDate = enrollmentDate, graduationDate = graduationDate))
            },
            onDismiss = { viewModel.cancelEditDetails() }
        )
    }

    deletingIdentity?.let { identity ->
        DeleteConfirmDialog(
            title = "删除身份",
            message = "确定要删除「${identity.remark}」吗？所有关联账号也会被删除。",
            onConfirm = {
                viewModel.deleteIdentity(identity.id)
            },
            onDismiss = { viewModel.cancelDelete() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableIdentityCard(
    identity: Identity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onEditDetails: () -> Unit,
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
            val icon: ImageVector? = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Outlined.Edit
                SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.Delete
                else -> null
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
                icon?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = it,
                            contentDescription = text,
                            tint = tint
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = text, color = tint)
                    }
                }
            }
        },
        modifier = Modifier
    ) {
        Card(
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
            colors = CardDefaults.elevatedCardColors()
        ) {
            Box {
                ListItem(
                    headlineContent = { Text(identity.displayName()) },
                    supportingContent = {
                        val details = listOfNotNull(
                            identity.birthday.takeIf { it.isNotBlank() }?.let { "生日: $it" },
                            identity.enrollmentDate.takeIf { it.isNotBlank() }?.let { "入学: $it" },
                            identity.graduationDate.takeIf { it.isNotBlank() }?.let { "毕业: $it" }
                        )
                        if (details.isNotEmpty()) {
                            Text(details.joinToString(" | "))
                        }
                    },
                    overlineContent = {
                        Text("${identity.accountCount} 个账号")
                    }
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = onDismissMenu
                ) {
                    DropdownMenuItem(
                        text = { Text("进入账号管理") },
                        onClick = {
                            onDismissMenu()
                            onClick()
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
                        text = { Text("编辑详细信息") },
                        onClick = {
                            onDismissMenu()
                            onEditDetails()
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
fun AddIdentityDialog(
    defaultName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加身份") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("身份名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
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

@Composable
fun EditIdentityDialog(
    identity: Identity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(identity.remark) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑身份") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("身份名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
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

@Composable
fun EditIdentityDetailsDialog(
    identity: Identity,
    onConfirm: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(identity.remark) }
    var birthday by remember { mutableStateOf(identity.birthday) }
    var enrollmentDate by remember { mutableStateOf(identity.enrollmentDate) }
    var graduationDate by remember { mutableStateOf(identity.graduationDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑详细信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("身份名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = birthday,
                    onValueChange = { birthday = it },
                    label = { Text("生日（选填，格式：MM-DD）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = enrollmentDate,
                    onValueChange = { enrollmentDate = it },
                    label = { Text("入学时间（选填，格式：YYYY-MM）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = graduationDate,
                    onValueChange = { graduationDate = it },
                    label = { Text("毕业时间（选填，格式：YYYY-MM）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(
                            name.trim(),
                            birthday.trim(),
                            enrollmentDate.trim(),
                            graduationDate.trim()
                        )
                    }
                },
                enabled = name.isNotBlank()
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

private fun Identity.displayName(): String = remark.ifBlank { username }

@Composable
fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
