package cn.edu.shmtu.terminal.android.ui.bill

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.usecase.bill.CaptchaRequiredException
import cn.edu.shmtu.terminal.android.ui.component.CaptchaDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillListScreen(
    onBillClick: (Long) -> Unit,
    viewModel: BillListViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val totalFiltered by viewModel.totalFiltered.collectAsState()
    val totalPages by viewModel.totalPages.collectAsState()
    val currentPage by viewModel.page.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val pendingCaptcha by viewModel.pendingCaptcha.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedIdentityId by remember { mutableStateOf<Long?>(null) }
    var showSyncMenu by remember { mutableStateOf(false) }
    var showAccountPanel by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var pendingSyncAction by remember { mutableStateOf<PendingSyncAction?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("账单") },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (selectedIdentityId != null) {
                        Box {
                            TextButton(onClick = { showSyncMenu = true }) { Text("同步") }
                            androidx.compose.material3.DropdownMenu(showSyncMenu, { showSyncMenu = false }) {
                                DropdownMenuItem({ Text("增量更新（全部账号）") }, {
                                    showSyncMenu = false; selectedIdentityId?.let { pendingSyncAction = PendingSyncAction.IdentityIncremental(it) }
                                }, enabled = !isSyncing)
                                DropdownMenuItem({ Text("全量更新（全部账号）") }, {
                                    showSyncMenu = false; selectedIdentityId?.let { pendingSyncAction = PendingSyncAction.IdentityFull(it) }
                                }, enabled = !isSyncing)
                            }
                        }
                        if (accounts.isNotEmpty()) {
                            TextButton(onClick = { showAccountPanel = !showAccountPanel }) {
                                Text(if (showAccountPanel) "隐藏账号" else "账号")
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            // 身份选择器
            if (identities.isNotEmpty()) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedIdentityId == null, onClick = { selectedIdentityId = null; viewModel.selectIdentity(null) }, label = { Text("全部") })
                    identities.forEach { identity ->
                        FilterChip(selected = selectedIdentityId == identity.id, onClick = { selectedIdentityId = identity.id; viewModel.selectIdentity(identity.id) }, label = { Text(identity.remark) })
                    }
                }
            }

            // 筛选栏
            if (selectedIdentityId != null) {
                FilterBar(typeFilter, dateRange, searchInput, { viewModel.setTypeFilter(it) }, { viewModel.setDateRange(it) }, { searchInput = it }, { viewModel.search(searchInput) })
            }

            // 同步进度
            AnimatedVisibility(visible = syncProgress != null && isSyncing) {
                syncProgress?.let { SyncStatusPanel(it, viewModel.getProgressMessage(it)) }
            }
            AnimatedVisibility(visible = syncProgress?.status is SyncStatus.Completed && !isSyncing) {
                syncProgress?.let { progress ->
                    TerminalSyncStatusCard(
                        message = viewModel.getProgressMessage(progress),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClose = { viewModel.clearSyncProgress() }
                    )
                }
            }
            AnimatedVisibility(visible = syncProgress?.status is SyncStatus.Failed && !isSyncing) {
                syncProgress?.let { progress ->
                    TerminalSyncStatusCard(
                        message = viewModel.getProgressMessage(progress),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClose = { viewModel.clearSyncProgress() }
                    )
                }
            }

            // 账号级同步面板
            AnimatedVisibility(visible = showAccountPanel && accounts.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("账号级别同步", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        accounts.forEach { account ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(account.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(account.userId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(onClick = { pendingSyncAction = PendingSyncAction.AccountIncremental(account.id) }, enabled = !isSyncing, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("增量", style = MaterialTheme.typography.labelSmall) }
                                    TextButton(onClick = { pendingSyncAction = PendingSyncAction.AccountFull(account.id) }, enabled = !isSyncing, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("全量", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }

            // 账单列表
            if (bills.isEmpty() && selectedIdentityId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂无账单", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("选择身份后同步账单", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    item {
                        Text("共 $totalFiltered 条" + if (totalPages > 1) " · 第 $currentPage/$totalPages 页" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    items(bills, key = { it.id }) { BillItemRow(bill = it, onClick = { onBillClick(it.id) }) }
                }
                if (totalPages > 1) {
                    PaginationBar(currentPage, totalPages) { viewModel.setPage(it) }
                }
            }
        }

        if (isSyncing && syncProgress?.status is SyncStatus.ProbingLogin) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }

    // SyncRange 选择对话框 - 对齐 Rust 版 SyncRangeDialog
    if (pendingSyncAction != null) {
        val action = pendingSyncAction!!
        SyncRangeDialog(
            actionLabel = when (action) {
                is PendingSyncAction.IdentityIncremental -> "增量更新当前身份"
                is PendingSyncAction.IdentityFull -> "全量更新当前身份"
                is PendingSyncAction.AccountIncremental -> "增量更新账号"
                is PendingSyncAction.AccountFull -> "全量更新账号"
            },
            onDismiss = { pendingSyncAction = null },
            onConfirm = { range ->
                pendingSyncAction = null
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("已开始${range.label}范围同步")
                }
                when (action) {
                    is PendingSyncAction.IdentityIncremental -> viewModel.syncBills(action.identityId, range)
                    is PendingSyncAction.IdentityFull -> viewModel.fullSyncBills(action.identityId, range)
                    is PendingSyncAction.AccountIncremental -> viewModel.syncAccountBills(action.accountId, range)
                    is PendingSyncAction.AccountFull -> viewModel.fullSyncAccountBills(action.accountId, range)
                }
            }
        )
    }

    // 验证码弹窗 - 对齐 Rust 版 ManualCaptchaDialog
    if (pendingCaptcha != null) {
        val captcha = pendingCaptcha!!
        val imageData = remember(captcha.captchaImageBase64) {
            try { android.util.Base64.decode(captcha.captchaImageBase64, android.util.Base64.DEFAULT) } catch (_: Exception) { null }
        }
        CaptchaDialog(
            captchaImageData = imageData,
            onConfirm = { viewModel.submitCaptcha(it) },
            onDismiss = { viewModel.dismissCaptcha() }
        )
    }
}

@Composable
private fun TerminalSyncStatusCard(
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClose: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            TextButton(onClick = onClose) { Text("关闭") }
        }
    }
}

// ==================== 筛选栏 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    typeFilter: BillTypeFilter, dateRange: DateRangeFilter, searchInput: String,
    onTypeFilterChange: (BillTypeFilter) -> Unit, onDateRangeChange: (DateRangeFilter) -> Unit,
    onSearchInputChanged: (String) -> Unit, onSearch: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        TabRow(selectedTabIndex = BillTypeFilter.entries.indexOf(typeFilter), modifier = Modifier.fillMaxWidth()) {
            BillTypeFilter.entries.forEach { filter ->
                Tab(selected = filter == typeFilter, onClick = { onTypeFilterChange(filter) }, text = { Text(filter.label, style = MaterialTheme.typography.labelSmall) })
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var dateExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(dateExpanded, { dateExpanded = it }) {
                OutlinedTextField(dateRange.label, {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dateExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(120.dp), textStyle = MaterialTheme.typography.bodySmall)
                ExposedDropdownMenu(dateExpanded, { dateExpanded = false }) {
                    DateRangeFilter.entries.forEach { range ->
                        DropdownMenuItem({ Text(range.label) }, { onDateRangeChange(range); dateExpanded = false })
                    }
                }
            }
            OutlinedTextField(searchInput, onSearchInputChanged, placeholder = { Text("搜索...", style = MaterialTheme.typography.bodySmall) },
                singleLine = true, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = { TextButton(onClick = onSearch, contentPadding = PaddingValues(4.dp)) { Text("搜索", style = MaterialTheme.typography.labelSmall) } })
        }
    }
}

// ==================== 分页栏 ====================

@Composable
private fun PaginationBar(currentPage: Int, totalPages: Int, onPageChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onPageChange(1) }, enabled = currentPage > 1, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("首页", style = MaterialTheme.typography.labelSmall) }
        TextButton(onClick = { onPageChange(currentPage - 1) }, enabled = currentPage > 1, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("上一页", style = MaterialTheme.typography.labelSmall) }
        Text("$currentPage / $totalPages", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 12.dp))
        TextButton(onClick = { onPageChange(currentPage + 1) }, enabled = currentPage < totalPages, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("下一页", style = MaterialTheme.typography.labelSmall) }
        TextButton(onClick = { onPageChange(totalPages) }, enabled = currentPage < totalPages, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("末页", style = MaterialTheme.typography.labelSmall) }
    }
}

// ==================== 同步状态面板 ====================

@Composable
private fun SyncStatusPanel(progress: SyncProgress, message: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = when (progress.status) {
            is SyncStatus.Failed -> MaterialTheme.colorScheme.errorContainer
            is SyncStatus.Completed -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        })) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            if (progress.accountTotal > 1) Text("账号 ${progress.accountIndex + 1}/${progress.accountTotal}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (progress.status is SyncStatus.Syncing) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(progress = { progress.status.page.toFloat() / progress.status.total.toFloat().coerceAtLeast(1f) }, modifier = Modifier.fillMaxWidth())
                Text("第 ${progress.status.page}/${progress.status.total} 页", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            }
        }
    }
}

// ==================== 账单行 ====================

@Composable
fun BillItemRow(bill: BillItem, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick) {
        androidx.compose.material3.ListItem(
            headlineContent = { Text(bill.type, style = MaterialTheme.typography.titleSmall) },
            supportingContent = { Text("${bill.dateTimeStrFormat} · ${bill.targetUser}", style = MaterialTheme.typography.bodySmall) },
            trailingContent = {
                val isIncome = bill.type.contains("充值") || bill.type.contains("冲正") || bill.type.contains("退款")
                Text("¥${bill.money}", style = MaterialTheme.typography.titleMedium, color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        )
    }
}

// ==================== 同步动作类型 ====================

/** 待执行的同步动作 - 对齐 Rust 版 pendingSyncAction */
sealed class PendingSyncAction {
    data class IdentityIncremental(val identityId: Long) : PendingSyncAction()
    data class IdentityFull(val identityId: Long) : PendingSyncAction()
    data class AccountIncremental(val accountId: Long) : PendingSyncAction()
    data class AccountFull(val accountId: Long) : PendingSyncAction()
}
