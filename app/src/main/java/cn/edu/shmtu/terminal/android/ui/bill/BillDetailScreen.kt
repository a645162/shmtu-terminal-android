package cn.edu.shmtu.terminal.android.ui.bill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 账单详情页 - 对齐 Tauri BillDetailDialog
 *
 * 字段(13 个,完全对齐 Tauri):
 * - 日期时间 dateTimeStrFormat
 * - 交易名称 type
 * - 交易号 transactionNo
 * - 对方账户 targetUser
 * - 位置 position(暂未持久化,显示 "—")
 * - 房间/窗口 room(暂未持久化)
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
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
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

        // Tauri BillDetailDialog 字段顺序(去掉 Tauri 的 13 个字段中 Android 端暂无的 synced_at / source_account_id,补 12 个)
        val fields = listOf(
            "日期时间" to item.dateTimeStrFormat.ifBlank { "—" },
            "交易名称" to item.type.ifBlank { "—" },
            "交易号" to item.transactionNo.ifBlank { "—" },
            "对方账户" to item.targetUser.ifBlank { "—" },
            "位置" to "—",
            "房间/窗口" to "—",
            "金额" to "¥${item.money}",
            "支付方式" to item.method.ifBlank { "—" },
            "状态" to item.status.ifBlank { "—" },
            "是否合并" to "否",
            "来源学号" to (item.accountLabel.ifBlank { item.accountId.toString() }),
            "同步时间" to "—"
        )
        val feedback = fields.joinToString("\n") { (k, v) -> "$k: $v" } +
            "\n备注: ${notes.ifBlank { "—" }}"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 大字金额(对齐 Tauri Dialog 顶部 amount 风格)
            Text(
                text = if (isIncome) "+¥${item.money}" else "-¥${item.money}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = if (isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            fields.forEachIndexed { index, (label, value) ->
                DetailRow(label = label, value = value)
                if (index < fields.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            // 备注区(对齐 Tauri 的备注编辑)
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
                if (!editing) {
                    Row {
                        IconButton(onClick = { viewModel.startEditNotes() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑备注")
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(feedback)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "复制全部字段")
                        }
                    }
                }
            }
            if (editing) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = viewModel::updateNotes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    placeholder = { Text("添加备注...") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.cancelEditNotes() }) { Text("取消") }
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Button(onClick = { viewModel.saveNotes() }) { Text("保存") }
                }
            } else {
                Text(
                    text = notes.ifBlank { "暂无备注" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (notes.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
