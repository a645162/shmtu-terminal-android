package cn.edu.shmtu.terminal.android.ui.bill

import android.content.ClipData
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * 账单详情页 - 对齐 Tauri BillDetailDialog
 *
 * 字段(13 个,完全对齐 Tauri):
 * - 日期时间 dateTimeStrFormat
 * - 交易名称 type
 * - 交易号 transactionNo
 * - 对方账户 targetUser
 * - 位置 position/building
 * - 房间/窗口 room
 * - 金额 money
 * - 支付方式 method
 * - 状态 status
 * - 是否合并 isCombined(Android Room 单条存,固定否)
 * - 来源学号 accountLabel / accountId
 * - 同步时间(暂未持久化,显示 "—")
 * - 备注 notes(可编辑,内存态)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    billId: Long,
    onBack: () -> Unit,
    viewModel: BillDetailViewModel = hiltViewModel()
) {
    val bill by viewModel.bill.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val editing by viewModel.editingNotes.collectAsState()
    val sourceAccountLabel by viewModel.sourceAccountLabel.collectAsState()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val ultraWide = configuration.screenWidthDp >= 1200
    val wideLayout = configuration.screenWidthDp >= 900

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (bill == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (billId == 0L) "无效的账单 ID" else "加载中...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        val item = bill!!
        val isIncome = item.type.contains("充值") || item.type.contains("冲正") ||
            item.type.contains("退款") || item.type.contains("返还") || item.type.contains("存入") ||
            item.type.contains("转入")
        val resolvedBuilding = item.building?.takeIf { it.isNotBlank() }
            ?: item.position?.takeIf { it.isNotBlank() }
        val resolvedRoom = item.room?.takeIf { it.isNotBlank() }
        val resolvedPlace = listOfNotNull(resolvedBuilding, resolvedRoom).joinToString("/")
            .ifBlank { null }
        val summaryTitle = resolvedPlace ?: item.type.ifBlank { "未分类交易" }

        // Tauri BillDetailDialog 字段顺序(去掉 Tauri 的 13 个字段中 Android 端暂无的 synced_at / source_account_id,补 12 个)
        val fields = listOf(
            "日期时间" to item.dateTimeStrFormat.ifBlank { "—" },
            "交易名称" to item.type.ifBlank { "—" },
            "交易号" to item.transactionNo.ifBlank { "—" },
            "对方账户" to item.targetUser.ifBlank { "—" },
            "位置" to (resolvedBuilding ?: "—"),
            "房间/窗口" to (resolvedRoom ?: "—"),
            "金额" to "¥${item.money}",
            "支付方式" to item.method.ifBlank { "—" },
            "状态" to item.status.ifBlank { "—" },
            "是否合并" to "否",
            "来源学号" to sourceAccountLabel,
            "同步时间" to "—"
        )
        val feedback = fields.joinToString("\n") { (k, v) -> "$k: $v" } +
            "\n备注: ${notes.ifBlank { "—" }}"
        val amountText = if (isIncome) "+¥${item.money}" else "-¥${item.money}"
        val amountColor = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

        if (wideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                BillDetailSummaryPanel(
                    modifier = Modifier.width(if (ultraWide) 360.dp else 320.dp),
                    title = summaryTitle,
                    amountText = amountText,
                    amountColor = amountColor,
                    status = item.status.ifBlank { "未知状态" },
                    account = sourceAccountLabel,
                    targetUser = item.targetUser.ifBlank { "—" },
                    resolvedPlace = resolvedPlace,
                    dateTime = item.dateTimeStrFormat.ifBlank { "—" },
                    onCopy = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipData.newPlainText("bill-detail", feedback).toClipEntry()
                            )
                        }
                    },
                    onStartEdit = { viewModel.startEditNotes() }
                )
                BillDetailFieldsPanel(
                    modifier = Modifier.weight(1f),
                    fields = fields,
                    notes = notes,
                    editing = editing,
                    ultraWide = ultraWide,
                    onUpdateNotes = viewModel::updateNotes,
                    onCancelEdit = viewModel::cancelEditNotes,
                    onSaveNotes = viewModel::saveNotes
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BillDetailSummaryPanel(
                    modifier = Modifier.fillMaxWidth(),
                    title = summaryTitle,
                    amountText = amountText,
                    amountColor = amountColor,
                    status = item.status.ifBlank { "未知状态" },
                    account = sourceAccountLabel,
                    targetUser = item.targetUser.ifBlank { "—" },
                    resolvedPlace = resolvedPlace,
                    dateTime = item.dateTimeStrFormat.ifBlank { "—" },
                    onCopy = {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipData.newPlainText("bill-detail", feedback).toClipEntry()
                            )
                        }
                    },
                    onStartEdit = { viewModel.startEditNotes() }
                )
                BillDetailFieldsPanel(
                    modifier = Modifier.fillMaxWidth(),
                    fields = fields,
                    notes = notes,
                    editing = editing,
                    ultraWide = false,
                    onUpdateNotes = viewModel::updateNotes,
                    onCancelEdit = viewModel::cancelEditNotes,
                    onSaveNotes = viewModel::saveNotes
                )
            }
        }
    }
}

@Composable
private fun BillDetailSummaryPanel(
    modifier: Modifier,
    title: String,
    amountText: String,
    amountColor: androidx.compose.ui.graphics.Color,
    status: String,
    account: String,
    targetUser: String,
    resolvedPlace: String?,
    dateTime: String,
    onCopy: () -> Unit,
    onStartEdit: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusTonePill(status = status)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(amountText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = amountColor)
            }
            resolvedPlace?.let {
                DetailHighlightCard(label = "解析位置", value = it)
            }
            DetailHighlightCard(label = "对方账户", value = targetUser)
            DetailHighlightCard(label = "来源账号", value = account)
            DetailHighlightCard(label = "发生时间", value = dateTime)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onStartEdit) { Text("编辑备注") }
                TextButton(onClick = onCopy) { Text("复制全部") }
            }
        }
    }
}

@Composable
private fun BillDetailFieldsPanel(
    modifier: Modifier,
    fields: List<Pair<String, String>>,
    notes: String,
    editing: Boolean,
    ultraWide: Boolean,
    onUpdateNotes: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveNotes: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("消费详细信息", style = MaterialTheme.typography.titleLarge)
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (ultraWide) 2 else 1),
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(fields) { (label, value) ->
                    DetailFieldCard(label = label, value = value)
                }
            }
            HorizontalDivider()
            BillNotesCard(
                notes = notes,
                editing = editing,
                onUpdateNotes = onUpdateNotes,
                onCancelEdit = onCancelEdit,
                onSaveNotes = onSaveNotes
            )
        }
    }
}

@Composable
private fun DetailHighlightCard(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun StatusTonePill(status: String) {
    val color = when {
        status.contains("成功") -> MaterialTheme.colorScheme.primary
        status.contains("失败") -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.10f),
        tonalElevation = 0.dp
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun DetailFieldCard(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun BillNotesCard(
    notes: String,
    editing: Boolean,
    onUpdateNotes: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveNotes: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "备注",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (editing) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (editing) {
            OutlinedTextField(
                value = notes,
                onValueChange = onUpdateNotes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("添加备注...") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCancelEdit) { Text("取消") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSaveNotes) { Text("保存") }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Text(
                    text = notes.ifBlank { "暂无备注" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notes.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }
    }
}
