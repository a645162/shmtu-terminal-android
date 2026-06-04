package cn.edu.shmtu.terminal.android.ui.bill

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.usecase.bill.CaptchaRequiredException
import cn.edu.shmtu.terminal.android.ui.component.BillItemCard
import cn.edu.shmtu.terminal.android.ui.component.CaptchaDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillListScreen(
    onBillClick: (Long) -> Unit,
    viewModel: BillListViewModel = hiltViewModel()
) {
    val currentIdentity by viewModel.currentIdentity.collectAsState()
    val currentIdentityId by viewModel.currentIdentityId.collectAsState()
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
                    if (currentIdentityId != null) {
                        Box {
                            TextButton(onClick = { showSyncMenu = true }) { Text("同步") }
                            androidx.compose.material3.DropdownMenu(showSyncMenu, { showSyncMenu = false }) {
                                DropdownMenuItem({ Text("增量更新（全部账号）") }, {
                                    showSyncMenu = false; currentIdentityId?.let { pendingSyncAction = PendingSyncAction.IdentityIncremental(it) }
                                }, enabled = !isSyncing)
                                DropdownMenuItem({ Text("全量更新（全部账号）") }, {
                                    showSyncMenu = false; currentIdentityId?.let { pendingSyncAction = PendingSyncAction.IdentityFull(it) }
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
            if (currentIdentity != null) {
                IdentityScopeCard(
                    title = currentIdentity!!.remark.ifBlank { currentIdentity!!.username },
                    subtitle = "当前身份 · ${currentIdentity!!.accountCount} 个账号"
                )
                BillSummaryDeck(
                    totalFiltered = totalFiltered,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    typeFilter = typeFilter,
                    dateRange = dateRange
                )
            }

            // 筛选栏
            if (currentIdentityId != null) {
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
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("账号级同步", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "对单个账号执行增量或全量同步，适合定位异常数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        accounts.forEach { account ->
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(account.label, style = MaterialTheme.typography.bodyLarge)
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
            }

            // 账单列表
            if (currentIdentityId == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconBubble(Icons.Outlined.Inventory2, MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("暂无账单", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("请先在“我”里切换身份", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(bills, key = { it.id }) { bill ->
                        BillItemRow(bill = bill, onClick = { onBillClick(bill.id) })
                    }
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

@Composable
private fun IdentityScopeCard(title: String, subtitle: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.82f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
                Text("账单列表", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f)
                )
            }
        }
    }
}

@Composable
private fun BillSummaryDeck(
    totalFiltered: Int,
    currentPage: Int,
    totalPages: Int,
    typeFilter: BillTypeFilter,
    dateRange: DateRangeFilter
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryStatCard("筛选结果", totalFiltered.toString(), Modifier.weight(1f))
        SummaryStatCard("当前页", "$currentPage/$totalPages", Modifier.weight(1f))
        SummaryStatCard("筛选", "${typeFilter.label} · ${dateRange.label}", Modifier.weight(1f), compact = true)
    }
}

@Composable
private fun SummaryStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
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
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Icons.Outlined.FilterAlt, MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("筛选与搜索", style = MaterialTheme.typography.titleSmall)
                    Text("按状态、时间和关键词快速缩小范围", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TabRow(
                selectedTabIndex = BillTypeFilter.entries.indexOf(typeFilter),
                modifier = Modifier.fillMaxWidth()
            ) {
                BillTypeFilter.entries.forEach { filter ->
                    Tab(selected = filter == typeFilter, onClick = { onTypeFilterChange(filter) }, text = { Text(filter.label, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                var dateExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(dateExpanded, { dateExpanded = it }) {
                    OutlinedTextField(
                        dateRange.label,
                        {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dateExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).width(130.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    ExposedDropdownMenu(dateExpanded, { dateExpanded = false }) {
                        DateRangeFilter.entries.forEach { range ->
                            DropdownMenuItem({ Text(range.label) }, { onDateRangeChange(range); dateExpanded = false })
                        }
                    }
                }
                OutlinedTextField(
                    searchInput,
                    onSearchInputChanged,
                    placeholder = { Text("搜索商户、类型、流水号", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall,
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.ManageSearch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = { TextButton(onClick = onSearch, contentPadding = PaddingValues(4.dp)) { Text("搜索", style = MaterialTheme.typography.labelSmall) } }
                )
            }
        }
    }
}

// ==================== 分页栏 ====================

@Composable
private fun PaginationBar(currentPage: Int, totalPages: Int, onPageChange: (Int) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onPageChange(1) }, enabled = currentPage > 1, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("首页", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { onPageChange(currentPage - 1) }, enabled = currentPage > 1, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("上一页", style = MaterialTheme.typography.labelSmall) }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                ) {
                    Text(
                        "$currentPage / $totalPages",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                TextButton(onClick = { onPageChange(currentPage + 1) }, enabled = currentPage < totalPages, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("下一页", style = MaterialTheme.typography.labelSmall) }
                TextButton(onClick = { onPageChange(totalPages) }, enabled = currentPage < totalPages, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("末页", style = MaterialTheme.typography.labelSmall) }
            }
        }
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
    BillItemCard(bill = bill, onClick = onClick)
}

@Composable
private fun IconBubble(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color
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
