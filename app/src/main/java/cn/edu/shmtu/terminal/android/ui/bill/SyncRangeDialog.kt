package cn.edu.shmtu.terminal.android.ui.bill

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * 同步范围预设 - 对齐 Rust 版 SyncRangePreset
 */
enum class SyncRangePreset(val label: String, val description: String) {
    WEEK("最近一周", "只同步最近 7 天账单，最快。"),
    HALF_MONTH("最近半个月", "同步最近 15 天账单。"),
    MONTH("最近一个月", "同步最近 30 天账单，适合常规补账。"),
    HALF_YEAR("最近半年", "同步最近 6 个月账单。"),
    YEAR("最近一年", "同步最近 1 年账单。"),
    ALL("全部", "不设时间限制，完整抓取可访问账单。")
}

/**
 * 同步范围选择对话框 - 对齐 Rust 版 SyncRangeDialog
 */
@Composable
fun SyncRangeDialog(
    actionLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (SyncRangePreset) -> Unit
) {
    var selectedRange by remember { mutableStateOf(SyncRangePreset.MONTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择同步范围") },
        text = {
            Column {
                Text(
                    "$actionLabel 前，请先确认需要抓取的时间范围。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Column(Modifier.selectableGroup()) {
                    SyncRangePreset.entries.forEach { range ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedRange == range,
                                    onClick = { selectedRange = range },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedRange == range, onClick = null)
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(range.label, style = MaterialTheme.typography.bodyMedium)
                                Text(range.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedRange) }) { Text("开始同步") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
