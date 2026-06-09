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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
                title = { Text("身份管理") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                }
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
            EmptyIdentityState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onAddIdentity = { showAddDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    IdentityOverviewCard(
                        identityCount = identities.size,
                        totalAccountCount = identities.sumOf { it.accountCount },
                        latestIdentity = identities.firstOrNull()?.displayName().orEmpty(),
                        onAddIdentity = { showAddDialog = true }
                    )
                }

                item {
                    SectionHeader(
                        title = "身份列表",
                        subtitle = "左滑删除，右滑编辑，长按查看更多操作"
                    )
                }

                items(identities, key = { it.id }) { identity ->
                    SwipeableIdentityCard(
                        identity = identity,
                        onClick = { onIdentityClick(identity.id) },
                        onEdit = { viewModel.startEditIdentity(identity) },
                        onEditDetails = { viewModel.startEditDetails(identity) },
                        onDelete = { viewModel.startDeleteIdentity(identity) },
                        onLongClick = {
                            expandedMenuIdentity =
                                if (expandedMenuIdentity == identity.id) -1L else identity.id
                        },
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
                viewModel.updateIdentityDetails(
                    identity.copy(
                        remark = name,
                        birthday = birthday,
                        enrollmentDate = enrollmentDate,
                        graduationDate = graduationDate
                    )
                )
            },
            onDismiss = { viewModel.cancelEditDetails() }
        )
    }

    deletingIdentity?.let { identity ->
        DeleteConfirmDialog(
            title = "删除身份",
            message = "确定要删除「${identity.displayName()}」吗？所有关联账号也会被删除。",
            onConfirm = {
                viewModel.deleteIdentity(identity.id)
            },
            onDismiss = { viewModel.cancelDelete() }
        )
    }
}

@Composable
private fun EmptyIdentityState(
    modifier: Modifier = Modifier,
    onAddIdentity: () -> Unit
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ManageAccounts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "还没有身份档案",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "创建身份后可以集中管理账号、补充个人信息，并按身份区分账单数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = onAddIdentity) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("创建第一个身份")
                }
            }
        }
    }
}

@Composable
private fun IdentityOverviewCard(
    identityCount: Int,
    totalAccountCount: Int,
    latestIdentity: String,
    onAddIdentity: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "身份中心",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "统一管理你的身份和账号",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = if (latestIdentity.isBlank()) {
                            "可以为不同场景建立独立身份。"
                        } else {
                            "最近创建或展示的身份：$latestIdentity"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OverviewMetricCard(
                        modifier = Modifier.weight(1f),
                        value = identityCount.toString(),
                        label = "身份数量"
                    )
                    OverviewMetricCard(
                        modifier = Modifier.weight(1f),
                        value = totalAccountCount.toString(),
                        label = "关联账号"
                    )
                }

                FilledTonalButton(
                    onClick = onAddIdentity,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("新增身份")
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        positionalThreshold = { totalDistance -> totalDistance * 0.25f }
    )

    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onEdit()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.EndToStart -> {
                onDelete()
                dismissState.reset()
            }
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

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
            SwipeActionBackground(direction = direction)
        }
    ) {
        IdentityManagementCard(
            identity = identity,
            onClick = onClick,
            onEdit = onEdit,
            onEditDetails = onEditDetails,
            onDelete = onDelete,
            onLongClick = onLongClick,
            expanded = expanded,
            onDismissMenu = onDismissMenu
        )
    }
}

@Composable
private fun SwipeActionBackground(direction: SwipeToDismissBoxValue) {
    val containerColor by animateColorAsState(
        when (direction) {
            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        label = "identity_swipe_background"
    )
    val icon: ImageVector? = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Icons.Outlined.Edit
        SwipeToDismissBoxValue.EndToStart -> Icons.Outlined.Delete
        SwipeToDismissBoxValue.Settled -> null
    }
    val label = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> "编辑身份"
        SwipeToDismissBoxValue.EndToStart -> "删除身份"
        SwipeToDismissBoxValue.Settled -> ""
    }
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    val tint = if (direction == SwipeToDismissBoxValue.EndToStart) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (icon != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = tint,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun IdentityManagementCard(
    identity: Identity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onEditDetails: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    expanded: Boolean,
    onDismissMenu: () -> Unit
) {
    Card(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IdentityAvatar(identity.displayName())
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = identity.displayName(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = identity.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = onLongClick) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "更多操作"
                        )
                    }
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
                            text = { Text("编辑名称") },
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onClick,
                    label = { Text("${identity.accountCount} 个账号") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                if (identity.hasAnyDetails()) {
                    AssistChip(
                        onClick = onEditDetails,
                        label = { Text("已补充档案") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.School,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            val detailItems = identity.detailItems()
            if (detailItems.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detailItems.forEach { item ->
                        IdentityDetailRow(
                            icon = item.icon,
                            label = item.label,
                            value = item.value
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit) {
                        Text("编辑")
                    }
                    FilledTonalButton(onClick = onClick) {
                        Text("管理账号")
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun IdentityAvatar(label: String) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun IdentityDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class IdentityDetailItem(
    val icon: ImageVector,
    val label: String,
    val value: String
)

private fun Identity.hasAnyDetails(): Boolean {
    return birthday.isNotBlank() || enrollmentDate.isNotBlank() || graduationDate.isNotBlank()
}

private fun Identity.detailItems(): List<IdentityDetailItem> {
    return listOfNotNull(
        birthday.takeIf { it.isNotBlank() }?.let {
            IdentityDetailItem(Icons.Outlined.Cake, "生日", it)
        },
        enrollmentDate.takeIf { it.isNotBlank() }?.let {
            IdentityDetailItem(Icons.Outlined.CalendarMonth, "入学", it)
        },
        graduationDate.takeIf { it.isNotBlank() }?.let {
            IdentityDetailItem(Icons.Outlined.School, "毕业", it)
        }
    )
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "身份用于归类账号和个人档案，建议使用容易识别的名称。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "修改显示名称不会影响原始学号或账号数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
