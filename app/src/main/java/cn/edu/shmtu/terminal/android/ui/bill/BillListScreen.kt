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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillListScreen(
    onBillClick: (Long) -> Unit,
    viewModel: BillListViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val bills by viewModel.bills.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedIdentityId by remember { mutableStateOf<Long?>(null) }
    var showSyncMenu by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text("账单") },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (selectedIdentityId != null) {
                        Box {
                            TextButton(onClick = { showSyncMenu = true }) {
                                Text("同步")
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = showSyncMenu,
                                onDismissRequest = { showSyncMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("增量更新") },
                                    onClick = {
                                        showSyncMenu = false
                                        selectedIdentityId?.let { viewModel.syncBills(it) { } }
                                    },
                                    enabled = !isSyncing
                                )
                                DropdownMenuItem(
                                    text = { Text("全量更新") },
                                    onClick = {
                                        showSyncMenu = false
                                        selectedIdentityId?.let { viewModel.fullSyncBills(it) }
                                    },
                                    enabled = !isSyncing
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            // 身份选择器
            if (identities.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedIdentityId == null,
                        onClick = {
                            selectedIdentityId = null
                            viewModel.selectIdentity(null)
                        },
                        label = { Text("全部") }
                    )
                    identities.forEach { identity ->
                        FilterChip(
                            selected = selectedIdentityId == identity.id,
                            onClick = {
                                selectedIdentityId = identity.id
                                viewModel.selectIdentity(identity.id)
                            },
                            label = { Text(identity.remark) }
                        )
                    }
                }
            }

            // 同步进度面板 - 对齐 Rust 版 SyncStatusPanel
            AnimatedVisibility(visible = syncProgress != null && isSyncing) {
                syncProgress?.let { progress ->
                    SyncStatusPanel(
                        progress = progress,
                        message = viewModel.getProgressMessage(progress)
                    )
                }
            }

            // 同步完成提示
            AnimatedVisibility(
                visible = syncProgress?.status is SyncStatus.Completed && !isSyncing
            ) {
                syncProgress?.let { progress ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = viewModel.getProgressMessage(progress),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TextButton(onClick = { viewModel.clearSyncProgress() }) {
                                Text("关闭")
                            }
                        }
                    }
                }
            }

            // 账单列表
            if (bills.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "暂无账单",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "选择身份后同步账单",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Text(
                            text = "共 ${bills.size} 条",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(bills, key = { it.id }) { bill ->
                        BillItemRow(bill = bill, onClick = { onBillClick(bill.id) })
                    }
                }
            }
        }

        // 同步中的全屏遮罩
        if (isSyncing && syncProgress?.status is SyncStatus.ProbingLogin) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * 同步状态面板 - 对齐 Rust 版 SyncStatusPanel
 * 固定底部右侧, 显示细粒度进度
 */
@Composable
private fun SyncStatusPanel(
    progress: SyncProgress,
    message: String
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = when (progress.status) {
                is SyncStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                is SyncStatus.Completed -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 账号进度
            if (progress.accountTotal > 1) {
                Text(
                    text = "账号 ${progress.accountIndex + 1}/${progress.accountTotal}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 状态消息
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )

            // 页面进度条 - 对齐 Rust 版 SyncStatus::Syncing { page, total }
            if (progress.status is SyncStatus.Syncing) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress.status.page.toFloat() / progress.status.total.toFloat().coerceAtLeast(1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "第 ${progress.status.page}/${progress.status.total} 页",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun BillItemRow(bill: BillItem, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick) {
        androidx.compose.material3.ListItem(
            headlineContent = {
                Text(
                    text = bill.type,
                    style = MaterialTheme.typography.titleSmall
                )
            },
            supportingContent = {
                Text(
                    text = "${bill.dateTimeStrFormat} · ${bill.targetUser}",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                val isIncome = bill.money.startsWith("-") || bill.money.contains("充值") || bill.money.contains("冲正")
                Text(
                    text = "¥${bill.money}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        )
    }
}
