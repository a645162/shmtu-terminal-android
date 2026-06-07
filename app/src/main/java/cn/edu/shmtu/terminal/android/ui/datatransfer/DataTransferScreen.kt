package cn.edu.shmtu.terminal.android.ui.datatransfer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.ExportFormat
import cn.edu.shmtu.terminal.android.domain.model.Identity

/**
 * 数据传输页面 - 对齐 Rust 版 DataTransferDialog
 * 三个标签页: 导出, 导入, 快照管理
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataTransferScreen(
    onBack: () -> Unit,
    viewModel: DataTransferViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val importState by viewModel.importState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("导出", "导入", "快照")

    // 导出：CreateDocument launcher，让用户选择保存位置
    var pendingExportParams by remember { mutableStateOf<Triple<Long, ExportFormat, String>?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val params = pendingExportParams
        if (uri != null && params != null) {
            viewModel.exportDataToUri(params.first, params.second, params.third, uri)
        }
        pendingExportParams = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("数据传输") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> ExportTab(
                    identities = identities,
                    exportState = exportState,
                    onExport = { identityId, format, sourceType ->
                        val ext = when (format) {
                            ExportFormat.CSV -> "csv"
                            ExportFormat.JSON -> "json"
                            ExportFormat.QIANJI -> "json"
                        }
                        val fileName = "bills_${System.currentTimeMillis()}.$ext"
                        pendingExportParams = Triple(identityId, format, sourceType)
                        createDocumentLauncher.launch(fileName)
                    }
                )
                1 -> ImportTab(
                    importState = importState,
                    onImport = { uri -> viewModel.importData(uri) }
                )
                2 -> SnapshotTab(viewModel = viewModel)
            }
        }
    }
}

/**
 * 导出标签页 - 对齐 Rust 版 export_ui
 * 格式选择: CSV / JSON / 钱迹格式
 * 身份选择器
 * 数据来源: 身份合并数据 / 账号原始数据
 */
@Composable
private fun ExportTab(
    identities: List<Identity>,
    exportState: ExportState,
    onExport: (Long, ExportFormat, String) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.JSON) }
    var selectedIdentityId by remember { mutableStateOf<Long?>(null) }
    var sourceType by remember { mutableStateOf("identity") }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 格式选择 - 对齐 Rust 版 format_selector
        Text("导出格式", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedFormat == ExportFormat.CSV,
                onClick = { selectedFormat = ExportFormat.CSV },
                label = { Text("CSV") }
            )
            FilterChip(
                selected = selectedFormat == ExportFormat.JSON,
                onClick = { selectedFormat = ExportFormat.JSON },
                label = { Text("JSON") }
            )
            FilterChip(
                selected = selectedFormat == ExportFormat.QIANJI,
                onClick = { selectedFormat = ExportFormat.QIANJI },
                label = { Text("钱迹格式") }
            )
        }

        // 身份选择 - 对齐 Rust 版 identity_selector
        if (identities.isNotEmpty()) {
            Text("选择身份", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                identities.forEach { identity ->
                    FilterChip(
                        selected = selectedIdentityId == identity.id,
                        onClick = { selectedIdentityId = identity.id },
                        label = { Text(identity.remark) }
                    )
                }
            }
        }

        // 数据来源 - 对齐 Rust 版 source_type_selector
        Text("数据来源", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = sourceType == "identity",
                onClick = { sourceType = "identity" },
                label = { Text("身份合并数据") }
            )
            FilterChip(
                selected = sourceType == "account",
                onClick = { sourceType = "account" },
                label = { Text("账号原始数据") }
            )
        }

        // 导出按钮
        Button(
            onClick = {
                selectedIdentityId?.let { onExport(it, selectedFormat, sourceType) }
            },
            enabled = selectedIdentityId != null && exportState !is ExportState.Exporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("导出")
        }

        // 导出状态
        when (exportState) {
            is ExportState.Exporting -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is ExportState.Success -> Text(
                "导出成功: ${exportState.filePath}",
                color = MaterialTheme.colorScheme.primary
            )
            is ExportState.Error -> Text(
                "导出失败: ${exportState.message}",
                color = MaterialTheme.colorScheme.error
            )
            else -> {}
        }
    }
}

/**
 * 导入标签页 - 对齐 Rust 版 import_ui
 * JSON 文件选择 + 身份选择
 */
@Composable
private fun ImportTab(
    importState: ImportState,
    onImport: (Uri) -> Unit
) {
    val context = LocalContext.current
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        selectedFileUri = uri
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("导入 JSON 数据", style = MaterialTheme.typography.titleMedium)
        Text(
            "仅支持从本应用导出的 JSON 格式文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 文件选择
        OutlinedButton(
            onClick = { filePickerLauncher.launch(arrayOf("application/json")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedFileUri != null) "已选择文件" else "选择 JSON 文件")
        }

        // 导入按钮
        Button(
            onClick = { selectedFileUri?.let { onImport(it) } },
            enabled = selectedFileUri != null && importState !is ImportState.Importing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("导入")
        }

        when (importState) {
            is ImportState.Importing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is ImportState.Success -> Text(
                "导入成功: ${importState.count} 条记录",
                color = MaterialTheme.colorScheme.primary
            )
            is ImportState.Error -> Text(
                "导入失败: ${importState.message}",
                color = MaterialTheme.colorScheme.error
            )
            else -> {}
        }
    }
}

/**
 * 快照管理标签页 - 对齐 Rust 版 snapshot_management
 * 创建快照 / 列表 / 恢复
 */
@Composable
private fun SnapshotTab(viewModel: DataTransferViewModel) {
    val snapshots by viewModel.snapshots.collectAsState()

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("快照管理", style = MaterialTheme.typography.titleMedium)
        Text(
            "快照包含所有账单数据的完整备份",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { viewModel.createSnapshot() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("创建快照")
        }

        if (snapshots.isEmpty()) {
            Text(
                "暂无快照",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            snapshots.forEach { snapshot ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                snapshot.createdAt,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                formatFileSize(snapshot.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        OutlinedButton(onClick = { viewModel.restoreSnapshot(snapshot.filename) }) {
                            Text("恢复")
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
