package cn.edu.shmtu.terminal.android.ui.account

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.LoginStatus
import cn.edu.shmtu.terminal.android.domain.model.PersonAccount
import cn.edu.shmtu.terminal.android.ui.component.PasswordTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityDetailScreen(
    identityId: Long,
    onAddAccount: () -> Unit,
    onHotWater: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: IdentityDetailViewModel = hiltViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val identity by viewModel.identity.collectAsState()
    val editingAccount by viewModel.editingAccount.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val personAccountsMap by viewModel.personAccountsByAccountId.collectAsState()
    var expandedMenuAccount by remember { mutableLongStateOf(-1L) }
    var confirmingDelete by remember { mutableStateOf<Account?>(null) }
    var captchaInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.consumePendingSnackbarMessage()
    }

    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("身份详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null
                    )
                },
                text = { Text("添加账号") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (accounts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        IdentityDetailOverviewCard(
                            identity = identity,
                            accounts = accounts,
                            personAccountsMap = personAccountsMap
                        )
                    }

                    items(accounts, key = { it.id }) { account ->
                        SwipeableAccountCard(
                            account = account,
                            personAccount = personAccountsMap[account.id],
                            isRefreshingPersonAccount = uiState.refreshingAccountIds.contains(account.id),
                            onRefresh = { viewModel.refreshAccountBills(account) },
                            onRefreshPersonAccount = { viewModel.refreshPersonAccount(account) },
                            onHotWater = { onHotWater(account.id) },
                            onEdit = { viewModel.startEditAccount(account) },
                            onDelete = { confirmingDelete = account },
                            onLongClick = {
                                expandedMenuAccount = if (expandedMenuAccount == account.id) -1L else account.id
                            },
                            expanded = expandedMenuAccount == account.id,
                            onDismissMenu = { expandedMenuAccount = -1L }
                        )
                    }
                }
            }

            if (uiState.isSyncing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    editingAccount?.let { account ->
        val initialPassword = remember(account.id) { viewModel.getStoredPassword(account.id) }
        EditAccountDialog(
            account = account,
            initialPassword = initialPassword,
            onConfirm = { label, userId, password ->
                viewModel.updateAccount(account.id, label, userId, password)
            },
            onLoginAndSave = { label, userId, password ->
                viewModel.loginAndSave(account.id, label, userId, password)
            },
            isLoading = uiState.isLoggingInForSave,
            onDismiss = { viewModel.cancelEditAccount() }
        )
    }

    confirmingDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("删除账号") },
            text = { Text("确定要删除「${account.label} - ${account.userId}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(account.id)
                        confirmingDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (uiState.showCaptchaDialog) {
        CaptchaDialog(
            captchaImage = uiState.captchaImage,
            captchaInput = captchaInput,
            onCaptchaInputChange = { captchaInput = it },
            onConfirm = {
                if (captchaInput.isNotBlank()) {
                    viewModel.submitCaptcha(captchaInput)
                    captchaInput = ""
                }
            },
            onDismiss = {
                viewModel.dismissCaptchaDialog()
                captchaInput = ""
            },
            focusManager = focusManager
        )
    }
}

@Composable
private fun IdentityDetailOverviewCard(
    identity: Identity?,
    accounts: List<Account>,
    personAccountsMap: Map<Long, PersonAccount>
) {
    val cachedProfiles = personAccountsMap.size
    val balanceSum = personAccountsMap.values.sumOf { it.cashBalance }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ManageAccounts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "身份详情",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                        )
                        Text(
                            text = identity?.remark?.ifBlank { identity.username } ?: "当前身份",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "账号管理、余额刷新和一卡通档案都集中在这里。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OverviewMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "账号数量",
                        value = accounts.size.toString()
                    )
                    OverviewMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "详情缓存",
                        value = "$cachedProfiles/${accounts.size}"
                    )
                    OverviewMetricTile(
                        modifier = Modifier.weight(1f),
                        label = "余额合计",
                        value = if (cachedProfiles > 0) "%.2f 元".format(balanceSum) else "未获取"
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewMetricTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun CaptchaDialog(
    captchaImage: ByteArray?,
    captchaInput: String,
    onCaptchaInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    focusManager: FocusManager
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("请输入验证码") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "请输入下方验证码的计算结果",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                captchaImage?.let { imageData ->
                    val bitmap = remember(imageData) {
                        BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "验证码图片",
                            modifier = Modifier.height(80.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = captchaInput,
                    onValueChange = onCaptchaInputChange,
                    label = { Text("计算结果") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onConfirm()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = captchaInput.isNotBlank()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableAccountCard(
    account: Account,
    personAccount: PersonAccount?,
    isRefreshingPersonAccount: Boolean,
    onRefresh: () -> Unit,
    onRefreshPersonAccount: () -> Unit,
    onHotWater: () -> Unit,
    onEdit: () -> Unit,
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
        }
    ) {
        Card(
            modifier = Modifier.combinedClickable(
                onClick = onRefresh,
                onLongClick = onLongClick
            ),
            colors = CardDefaults.elevatedCardColors()
        ) {
            Column {
                Box {
                    ListItem(
                        headlineContent = { Text("${account.label} - ${account.userId}") },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                personAccount?.let { pa ->
                                    Text(
                                        text = buildString {
                                            append("余额 ")
                                            append(pa.cashBalanceRaw.ifBlank { "%.2f".format(pa.cashBalance) })
                                            append(" 元")
                                            if (pa.realNameAuthStatus.isNotBlank()) {
                                                append(" · ")
                                                append(pa.realNameAuthStatus)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
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
                            text = { Text("刷新账单") },
                            onClick = {
                                onDismissMenu()
                                onRefresh()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("拉取个人详情") },
                            onClick = {
                                onDismissMenu()
                                onRefreshPersonAccount()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("热水查询") },
                            onClick = {
                                onDismissMenu()
                                onHotWater()
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

                PersonAccountSection(
                    personAccount = personAccount,
                    isRefreshing = isRefreshingPersonAccount,
                    onRefresh = onRefreshPersonAccount
                )
            }
        }
    }
}

@Composable
private fun PersonAccountSection(
    personAccount: PersonAccount?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { expanded = !expanded },
                    onLongClick = onRefresh
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "个人账户详情",
                    style = MaterialTheme.typography.titleSmall
                )
                personAccount?.let { pa ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "余额 ${pa.cashBalanceRaw.ifBlank { "%.2f".format(pa.cashBalance) }} 元",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新个人详情",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp
                    else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (personAccount == null) {
                    Text(
                        text = "暂无缓存,长按标题或点击刷新按钮拉取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    PersonAccountDetailFields(personAccount)
                    if (personAccount.updatedAt > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "缓存于 ${formatTimestamp(personAccount.updatedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonAccountDetailFields(pa: PersonAccount) {
    val fields = listOf(
        "姓名" to pa.realName,
        "实名认证" to pa.realNameAuthStatus,
        "现金资金" to (pa.cashBalanceRaw.ifBlank { "%.2f 元".format(pa.cashBalance) }),
        "安全保护问题" to pa.securityQuestionStatus,
        "注册时间" to pa.registerDate,
        "学工号" to pa.studentId,
        "性别" to (buildString {
            append(pa.gender)
            if (pa.genderFromId.isNotBlank() && pa.genderFromId != pa.gender) {
                append(" (身份证推断: ")
                append(pa.genderFromId)
                append(")")
            }
        }),
        "手机号" to pa.phoneNum,
        "证件类型" to pa.idType,
        "证件号码" to pa.idNumber,
        "电子邮箱" to pa.email,
        "昵称" to pa.nickname,
        "班级" to pa.className,
        "备注" to pa.remark,
        "用户类型" to pa.userType,
    )
    fields.forEach { (label, value) ->
        if (value.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(96.dp)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EditAccountDialog(
    account: Account,
    initialPassword: String,
    onConfirm: (String, String, String) -> Unit,
    onLoginAndSave: (String, String, String) -> Unit,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(account.label) }
    var userId by remember { mutableStateOf(account.userId) }
    var password by remember { mutableStateOf(initialPassword) }

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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        if (label.isNotBlank() && userId.isNotBlank() && password.isNotBlank()) {
                            onLoginAndSave(label, userId, password)
                        }
                    },
                    enabled = label.isNotBlank() && userId.isNotBlank() && password.isNotBlank() && !isLoading
                ) {
                    Text("登录并保存")
                }
                TextButton(
                    onClick = {
                        if (label.isNotBlank() && userId.isNotBlank() && password.isNotBlank()) {
                            onConfirm(label, userId, password)
                        }
                    },
                    enabled = label.isNotBlank() && userId.isNotBlank() && password.isNotBlank() && !isLoading
                ) {
                    Text("保存")
                }
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                TextButton(onClick = onDismiss, enabled = !isLoading) {
                    Text("取消")
                }
            }
        }
    )
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}
