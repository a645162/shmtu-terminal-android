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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.usecase.bill.CaptchaRequiredException
import cn.edu.shmtu.terminal.android.ui.component.BillItemCard
import cn.edu.shmtu.terminal.android.ui.component.CaptchaDialog
import cn.edu.shmtu.terminal.android.ui.settings.LocalFeatureStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillListScreen(
    onBillClick: (Long) -> Unit,
    viewModel: BillListViewModel = hiltViewModel()
) {
    val featureStore = LocalFeatureStore.current
    val preferParsedBillDisplay by featureStore.preferParsedBillDisplay.collectAsState()
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
    var showCompactFilters by rememberSaveable { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var pendingSyncAction by remember { mutableStateOf<PendingSyncAction?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val wideLayout = configuration.screenWidthDp >= 960
    val ultraWideLayout = configuration.screenWidthDp >= 1200

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
        if (wideLayout) {
            WideBillListLayout(
                modifier = Modifier.padding(innerPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
                currentIdentity = currentIdentity,
                currentIdentityId = currentIdentityId,
                totalFiltered = totalFiltered,
                currentPage = currentPage,
                totalPages = totalPages,
                typeFilter = typeFilter,
                dateRange = dateRange,
                ultraWideLayout = ultraWideLayout,
                searchInput = searchInput,
                onTypeFilterChange = { viewModel.setTypeFilter(it) },
                onDateRangeChange = { viewModel.setDateRange(it) },
                onSearchInputChanged = { searchInput = it },
                onSearch = { viewModel.search(searchInput) },
                syncProgress = syncProgress,
                isSyncing = isSyncing,
                showAccountPanel = showAccountPanel,
                accounts = accounts,
                onAccountPanelToggle = { showAccountPanel = !showAccountPanel },
                onIdentityIncremental = { currentIdentityId?.let { pendingSyncAction = PendingSyncAction.IdentityIncremental(it) } },
                onIdentityFull = { currentIdentityId?.let { pendingSyncAction = PendingSyncAction.IdentityFull(it) } },
                onAccountIncremental = { pendingSyncAction = PendingSyncAction.AccountIncremental(it) },
                onAccountFull = { pendingSyncAction = PendingSyncAction.AccountFull(it) },
                bills = bills,
                preferParsedDisplay = preferParsedBillDisplay,
                onBillClick = onBillClick,
                onPageChange = { viewModel.setPage(it) },
                onClearSyncProgress = { viewModel.clearSyncProgress() },
                progressMessage = syncProgress?.let { viewModel.getProgressMessage(it) }
            )
        } else {
            CompactBillListLayout(
                modifier = Modifier.padding(innerPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
                currentIdentity = currentIdentity,
                currentIdentityId = currentIdentityId,
                totalFiltered = totalFiltered,
                currentPage = currentPage,
                totalPages = totalPages,
                typeFilter = typeFilter,
                dateRange = dateRange,
                searchInput = searchInput,
                onTypeFilterChange = { viewModel.setTypeFilter(it) },
                onDateRangeChange = { viewModel.setDateRange(it) },
                onSearchInputChanged = { searchInput = it },
                onSearch = { viewModel.search(searchInput) },
                showFilterPanel = showCompactFilters,
                onFilterPanelToggle = { showCompactFilters = !showCompactFilters },
                syncProgress = syncProgress,
                isSyncing = isSyncing,
                showAccountPanel = showAccountPanel,
                accounts = accounts,
                onAccountPanelToggle = { showAccountPanel = !showAccountPanel },
                onIdentityIncremental = { currentIdentityId?.let { pendingSyncAction = PendingSyncAction.IdentityIncremental(it) } },
                onIdentityFull = { currentIdentityId?.let { pendingSyncAction = PendingSyncAction.IdentityFull(it) } },
                onAccountIncremental = { pendingSyncAction = PendingSyncAction.AccountIncremental(it) },
                onAccountFull = { pendingSyncAction = PendingSyncAction.AccountFull(it) },
                bills = bills,
                preferParsedDisplay = preferParsedBillDisplay,
                onBillClick = onBillClick,
                onPageChange = { viewModel.setPage(it) },
                onClearSyncProgress = { viewModel.clearSyncProgress() },
                progressMessage = syncProgress?.let { viewModel.getProgressMessage(it) }
            )
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
private fun CompactBillListLayout(
    modifier: Modifier,
    currentIdentity: cn.edu.shmtu.terminal.android.domain.model.Identity?,
    currentIdentityId: Long?,
    totalFiltered: Int,
    currentPage: Int,
    totalPages: Int,
    typeFilter: BillTypeFilter,
    dateRange: DateRangeFilter,
    searchInput: String,
    onTypeFilterChange: (BillTypeFilter) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onSearchInputChanged: (String) -> Unit,
    onSearch: () -> Unit,
    showFilterPanel: Boolean,
    onFilterPanelToggle: () -> Unit,
    syncProgress: SyncProgress?,
    isSyncing: Boolean,
    showAccountPanel: Boolean,
    accounts: List<cn.edu.shmtu.terminal.android.domain.model.Account>,
    onAccountPanelToggle: () -> Unit,
    onIdentityIncremental: () -> Unit,
    onIdentityFull: () -> Unit,
    onAccountIncremental: (Long) -> Unit,
    onAccountFull: (Long) -> Unit,
    bills: List<BillItem>,
    preferParsedDisplay: Boolean,
    onBillClick: (Long) -> Unit,
    onPageChange: (Int) -> Unit,
    onClearSyncProgress: () -> Unit,
    progressMessage: String?
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        if (currentIdentity != null) {
            CompactBillControlCard(
                title = currentIdentity.remark.ifBlank { currentIdentity.username },
                subtitle = "当前身份 · ${currentIdentity.accountCount} 个账号",
                totalFiltered = totalFiltered,
                typeFilter = typeFilter,
                dateRange = dateRange,
                searchInput = searchInput,
                onTypeFilterChange = onTypeFilterChange,
                onDateRangeChange = onDateRangeChange,
                onSearchInputChanged = onSearchInputChanged,
                onSearch = onSearch,
                showFilterPanel = showFilterPanel,
                onFilterPanelToggle = onFilterPanelToggle
            )
        }

        if (currentIdentityId == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconBubble(Icons.Outlined.Inventory2, MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("暂无账单", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text("请先在“当前身份”里切换身份", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item {
                    SyncPanels(syncProgress, isSyncing, onClearSyncProgress, progressMessage)
                }

                item {
                    AccountSyncPanel(
                        visible = showAccountPanel,
                        accounts = accounts,
                        isSyncing = isSyncing,
                        onAccountIncremental = onAccountIncremental,
                        onAccountFull = onAccountFull
                    )
                }

                if (bills.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    IconBubble(Icons.Outlined.Inventory2, MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("当前筛选下暂无账单", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "调整筛选条件或执行同步后再试",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(bills, key = { it.id }) { bill ->
                        BillItemRow(
                            bill = bill,
                            onClick = { onBillClick(bill.id) },
                            preferParsedDisplay = preferParsedDisplay
                        )
                    }
                }
            }

            if (totalPages > 1) {
                PaginationBar(currentPage, totalPages, onPageChange, compact = true)
            }
        }
    }
}

@Composable
private fun WideBillListLayout(
    modifier: Modifier,
    currentIdentity: cn.edu.shmtu.terminal.android.domain.model.Identity?,
    currentIdentityId: Long?,
    totalFiltered: Int,
    currentPage: Int,
    totalPages: Int,
    typeFilter: BillTypeFilter,
    dateRange: DateRangeFilter,
    ultraWideLayout: Boolean,
    searchInput: String,
    onTypeFilterChange: (BillTypeFilter) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onSearchInputChanged: (String) -> Unit,
    onSearch: () -> Unit,
    syncProgress: SyncProgress?,
    isSyncing: Boolean,
    showAccountPanel: Boolean,
    accounts: List<cn.edu.shmtu.terminal.android.domain.model.Account>,
    onAccountPanelToggle: () -> Unit,
    onIdentityIncremental: () -> Unit,
    onIdentityFull: () -> Unit,
    onAccountIncremental: (Long) -> Unit,
    onAccountFull: (Long) -> Unit,
    bills: List<BillItem>,
    preferParsedDisplay: Boolean,
    onBillClick: (Long) -> Unit,
    onPageChange: (Int) -> Unit,
    onClearSyncProgress: () -> Unit,
    progressMessage: String?
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (ultraWideLayout) 20.dp else 18.dp)
    ) {
        LazyColumn(
            modifier = Modifier.width(if (ultraWideLayout) 360.dp else 330.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                if (currentIdentity != null) {
                    IdentityScopeCard(
                        title = currentIdentity.remark.ifBlank { currentIdentity.username },
                        subtitle = "当前身份 · ${currentIdentity.accountCount} 个账号",
                        compact = true
                    )
                }
            }
            item {
                BillSummaryDeck(
                    totalFiltered = totalFiltered,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    typeFilter = typeFilter,
                    dateRange = dateRange,
                    compact = true
                )
            }
            if (currentIdentityId != null) {
                item {
                    FilterBar(
                        typeFilter = typeFilter,
                        dateRange = dateRange,
                        searchInput = searchInput,
                        onTypeFilterChange = onTypeFilterChange,
                        onDateRangeChange = onDateRangeChange,
                        onSearchInputChanged = onSearchInputChanged,
                        onSearch = onSearch,
                        compact = true
                    )
                }
            }
            item {
                SyncPanels(
                    syncProgress = syncProgress,
                    isSyncing = isSyncing,
                    onClearSyncProgress = onClearSyncProgress,
                    progressMessage = progressMessage,
                    compact = true
                )
            }
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("同步操作", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "同步入口放左侧，列表区域保持连续可读。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = onIdentityIncremental, enabled = currentIdentityId != null && !isSyncing) { Text("全部增量") }
                            TextButton(onClick = onIdentityFull, enabled = currentIdentityId != null && !isSyncing) { Text("全部全量") }
                            TextButton(onClick = onAccountPanelToggle, enabled = accounts.isNotEmpty()) { Text(if (showAccountPanel) "隐藏账号" else "账号面板") }
                        }
                    }
                }
            }
            item {
                AccountSyncPanel(
                    visible = showAccountPanel,
                    accounts = accounts,
                    isSyncing = isSyncing,
                    onAccountIncremental = onAccountIncremental,
                    onAccountFull = onAccountFull,
                    compact = true
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconBubble(Icons.Outlined.Inventory2, MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("账单明细", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (bills.isEmpty()) "当前筛选下暂无记录" else if (ultraWideLayout) "超宽平板下切换为桌面化工作区，右侧优先展示更多项目" else "横屏下优先展示列表，减少滚动切换成本",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (totalPages > 1) {
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
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                BillListContent(
                    currentIdentityId = currentIdentityId,
                    bills = bills,
                    preferParsedDisplay = preferParsedDisplay,
                    onBillClick = onBillClick,
                    totalPages = totalPages,
                    currentPage = currentPage,
                    onPageChange = onPageChange,
                    compact = true,
                    ultraWide = ultraWideLayout
                )
            }
        }
    }
}

@Composable
private fun SyncPanels(
    syncProgress: SyncProgress?,
    isSyncing: Boolean,
    onClearSyncProgress: () -> Unit,
    progressMessage: String?,
    compact: Boolean = false
) {
    AnimatedVisibility(visible = syncProgress != null && isSyncing) {
        syncProgress?.let { SyncStatusPanel(it, progressMessage.orEmpty(), compact = compact) }
    }
    AnimatedVisibility(visible = syncProgress?.status is SyncStatus.Completed && !isSyncing) {
        syncProgress?.let {
            TerminalSyncStatusCard(
                message = progressMessage.orEmpty(),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClose = onClearSyncProgress,
                compact = compact
            )
        }
    }
    AnimatedVisibility(visible = syncProgress?.status is SyncStatus.Failed && !isSyncing) {
        syncProgress?.let {
            TerminalSyncStatusCard(
                message = progressMessage.orEmpty(),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                onClose = onClearSyncProgress,
                compact = compact
            )
        }
    }
}

@Composable
private fun AccountSyncPanel(
    visible: Boolean,
    accounts: List<cn.edu.shmtu.terminal.android.domain.model.Account>,
    isSyncing: Boolean,
    onAccountIncremental: (Long) -> Unit,
    onAccountFull: (Long) -> Unit,
    compact: Boolean = false
) {
    AnimatedVisibility(visible = visible && accounts.isNotEmpty()) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 0.dp else 16.dp,
                    vertical = 6.dp
                ),
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
                                TextButton(onClick = { onAccountIncremental(account.id) }, enabled = !isSyncing, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("增量", style = MaterialTheme.typography.labelSmall) }
                                TextButton(onClick = { onAccountFull(account.id) }, enabled = !isSyncing, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("全量", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillListContent(
    currentIdentityId: Long?,
    bills: List<BillItem>,
    preferParsedDisplay: Boolean,
    onBillClick: (Long) -> Unit,
    totalPages: Int,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    compact: Boolean = false,
    ultraWide: Boolean = false
) {
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
                    Text("请先在“当前身份”里切换身份", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            if (ultraWide) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = if (compact) 2.dp else 16.dp,
                        vertical = if (compact) 4.dp else 8.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(bills, key = { it.id }) { bill ->
                        BillItemRow(
                            bill = bill,
                            onClick = { onBillClick(bill.id) },
                            compact = compact,
                            preferParsedDisplay = preferParsedDisplay
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        horizontal = if (compact) 2.dp else 16.dp,
                        vertical = if (compact) 4.dp else 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(bills, key = { it.id }) { bill ->
                        BillItemRow(
                            bill = bill,
                            onClick = { onBillClick(bill.id) },
                            compact = compact,
                            preferParsedDisplay = preferParsedDisplay
                        )
                    }
                }
            }
            if (totalPages > 1) {
                PaginationBar(currentPage, totalPages, onPageChange, compact = compact)
            }
        }
    }
}

@Composable
private fun TerminalSyncStatusCard(
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClose: () -> Unit,
    compact: Boolean = false,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 0.dp else 16.dp,
                vertical = 4.dp
            ),
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
private fun IdentityScopeCard(title: String, subtitle: String, compact: Boolean = false) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 0.dp else 16.dp,
                vertical = 8.dp
            ),
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
    dateRange: DateRangeFilter,
    compact: Boolean = false
) {
    if (compact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryStatCard("筛选结果", totalFiltered.toString(), Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryStatCard("当前页", "$currentPage/$totalPages", Modifier.weight(1f))
                SummaryStatCard("筛选", "${typeFilter.label} · ${dateRange.label}", Modifier.weight(1f), compact = true)
            }
        }
    } else {
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
}

@Composable
private fun CompactBillControlCard(
    title: String,
    subtitle: String,
    totalFiltered: Int,
    typeFilter: BillTypeFilter,
    dateRange: DateRangeFilter,
    searchInput: String,
    onTypeFilterChange: (BillTypeFilter) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onSearchInputChanged: (String) -> Unit,
    onSearch: () -> Unit,
    showFilterPanel: Boolean,
    onFilterPanelToggle: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text("账单列表", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBubble(Icons.Outlined.FilterAlt, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("筛选与搜索", style = MaterialTheme.typography.titleSmall)
                        Text(
                            buildCompactFilterSummary(typeFilter, dateRange, searchInput),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onFilterPanelToggle, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (showFilterPanel) "收起" else "展开")
                }
            }

            AnimatedVisibility(showFilterPanel) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryStatCard("筛选结果", totalFiltered.toString(), Modifier.weight(1f))
                        SummaryStatCard(
                            "当前筛选",
                            buildCompactFilterValue(typeFilter, dateRange),
                            Modifier.weight(1f),
                            compact = true
                        )
                    }
                    FilterBar(
                        typeFilter = typeFilter,
                        dateRange = dateRange,
                        searchInput = searchInput,
                        onTypeFilterChange = onTypeFilterChange,
                        onDateRangeChange = onDateRangeChange,
                        onSearchInputChanged = onSearchInputChanged,
                        onSearch = onSearch,
                        compact = true,
                        embedded = true
                    )
                }
            }
        }
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

private fun buildCompactFilterSummary(
    typeFilter: BillTypeFilter,
    dateRange: DateRangeFilter,
    searchInput: String
): String {
    val parts = buildList {
        if (typeFilter != BillTypeFilter.ALL) add(typeFilter.label)
        if (dateRange != DateRangeFilter.ALL) add(dateRange.label)
        if (searchInput.isNotBlank()) add("关键词")
    }
    return if (parts.isEmpty()) {
        "默认筛选，向上滑动时会一起收走"
    } else {
        "已启用 ${parts.joinToString(" · ")}"
    }
}

private fun buildCompactFilterValue(
    typeFilter: BillTypeFilter,
    dateRange: DateRangeFilter
): String {
    val parts = buildList {
        if (typeFilter != BillTypeFilter.ALL) add(typeFilter.label)
        if (dateRange != DateRangeFilter.ALL) add(dateRange.label)
    }
    return if (parts.isEmpty()) "默认" else parts.joinToString(" · ")
}

// ==================== 筛选栏 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    typeFilter: BillTypeFilter, dateRange: DateRangeFilter, searchInput: String,
    onTypeFilterChange: (BillTypeFilter) -> Unit, onDateRangeChange: (DateRangeFilter) -> Unit,
    onSearchInputChanged: (String) -> Unit, onSearch: () -> Unit,
    compact: Boolean = false,
    embedded: Boolean = false
) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(if (embedded) 0.dp else 14.dp)) {
            if (!embedded) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Outlined.FilterAlt, MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("筛选与搜索", style = MaterialTheme.typography.titleSmall)
                        Text("按状态、时间和关键词快速缩小范围", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            PrimaryTabRow(
                selectedTabIndex = BillTypeFilter.entries.indexOf(typeFilter),
                modifier = Modifier.fillMaxWidth()
            ) {
                BillTypeFilter.entries.forEach { filter ->
                    Tab(selected = filter == typeFilter, onClick = { onTypeFilterChange(filter) }, text = { Text(filter.label, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var dateExpanded by remember { mutableStateOf(false) }
                if (compact) {
                    ExposedDropdownMenuBox(dateExpanded, { dateExpanded = it }) {
                        OutlinedTextField(
                            dateRange.label,
                            {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dateExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
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
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Outlined.ManageSearch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = { TextButton(onClick = onSearch, contentPadding = PaddingValues(4.dp)) { Text("搜索", style = MaterialTheme.typography.labelSmall) } }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExposedDropdownMenuBox(dateExpanded, { dateExpanded = it }) {
                            OutlinedTextField(
                                dateRange.label,
                                {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dateExpanded) },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .width(130.dp),
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
                                    Icons.AutoMirrored.Outlined.ManageSearch,
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
    }

    if (embedded) {
        content()
    } else {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (compact) 0.dp else 16.dp,
                    vertical = 8.dp
                ),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            content()
        }
    }
}

// ==================== 分页栏 ====================

@Composable
private fun PaginationBar(currentPage: Int, totalPages: Int, onPageChange: (Int) -> Unit, compact: Boolean = false) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) 2.dp else 16.dp,
                vertical = if (compact) 8.dp else 10.dp
            ),
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
private fun SyncStatusPanel(progress: SyncProgress, message: String, compact: Boolean = false) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 0.dp else 16.dp, vertical = 4.dp),
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
fun BillItemRow(
    bill: BillItem,
    onClick: () -> Unit,
    compact: Boolean = false,
    preferParsedDisplay: Boolean = true
) {
    BillItemCard(
        bill = bill,
        onClick = onClick,
        compact = compact,
        preferParsedDisplay = preferParsedDisplay
    )
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
