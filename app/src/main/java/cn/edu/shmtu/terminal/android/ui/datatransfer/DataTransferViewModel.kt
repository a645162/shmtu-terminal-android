package cn.edu.shmtu.terminal.android.ui.datatransfer

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.ExportFormat
import cn.edu.shmtu.terminal.android.domain.model.ExportParams
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.SnapshotInfo
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.usecase.export.ExportDataUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.export.ImportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

// 导出状态
sealed class ExportState {
    data object Idle : ExportState()
    data object Exporting : ExportState()
    data class Success(val filePath: String) : ExportState()
    data class Error(val message: String) : ExportState()
}

// 导入状态
sealed class ImportState {
    data object Idle : ImportState()
    data object Importing : ImportState()
    data class Success(val count: Int) : ImportState()
    data class Error(val message: String) : ImportState()
}

@HiltViewModel
class DataTransferViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val importDataUseCase: ImportDataUseCase,
    private val application: Application
) : ViewModel() {

    private val context: Context get() = application.applicationContext

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _snapshots = MutableStateFlow<List<SnapshotInfo>>(emptyList())
    val snapshots: StateFlow<List<SnapshotInfo>> = _snapshots.asStateFlow()

    init {
        loadSnapshots()
    }

    fun exportData(identityId: Long, format: ExportFormat, sourceType: String) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            val ext = when (format) {
                ExportFormat.CSV -> "csv"
                ExportFormat.JSON -> "json"
                ExportFormat.QIANJI -> "json"
            }
            val fileName = "export_${System.currentTimeMillis()}.$ext"
            val exportDir = File(context.filesDir, "export").apply { mkdirs() }
            val filePath = File(exportDir, fileName).absolutePath

            val result = exportDataUseCase(ExportParams(
                identityId = identityId,
                format = format,
                sourceType = sourceType,
                filePath = filePath
            ))
            _exportState.value = result.fold(
                onSuccess = { ExportState.Success(filePath) },
                onFailure = { ExportState.Error(it.message ?: "导出失败") }
            )
        }
    }

    /**
     * 导出数据到指定 URI（SAF CreateDocument 选择的保存位置）
     * 先在内部生成文件，再复制到用户选择的位置
     */
    fun exportDataToUri(identityId: Long, format: ExportFormat, sourceType: String, destUri: Uri) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting
            try {
                val ext = when (format) {
                    ExportFormat.CSV -> "csv"
                    ExportFormat.JSON -> "json"
                    ExportFormat.QIANJI -> "json"
                }
                val fileName = "export_${System.currentTimeMillis()}.$ext"
                val exportDir = File(context.filesDir, "export").apply { mkdirs() }
                val filePath = File(exportDir, fileName).absolutePath

                val result = exportDataUseCase(ExportParams(
                    identityId = identityId,
                    format = format,
                    sourceType = sourceType,
                    filePath = filePath
                ))

                result.fold(
                    onSuccess = {
                        context.contentResolver.openOutputStream(destUri)?.use { output ->
                            File(it).inputStream().use { input -> input.copyTo(output) }
                        }
                        File(filePath).delete()
                        _exportState.value = ExportState.Success("已保存")
                    },
                    onFailure = { _exportState.value = ExportState.Error(it.message ?: "导出失败") }
                )
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "导出失败")
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState.Importing
            try {
                val tempFile = File(context.cacheDir, "import_temp.json")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                // Find the first identity for import target
                val identities = identityRepository.getAllIdentities().let {
                    // Get first identity id
                    val list = it.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()).value
                    list.firstOrNull()?.id ?: 0L
                }
                val result = importDataUseCase(tempFile.absolutePath, identities)
                _importState.value = result.fold(
                    onSuccess = { ImportState.Success(it) },
                    onFailure = { ImportState.Error(it.message ?: "导入失败") }
                )
                tempFile.delete()
            } catch (e: Exception) {
                _importState.value = ImportState.Error(e.message ?: "导入失败")
            }
        }
    }

    /**
     * 创建快照 - 对齐 Rust 版 create_snapshot
     * ZIP of Data/ directory (excludes snapshot/, models/, export/ subdirs)
     */
    fun createSnapshot() {
        viewModelScope.launch {
            try {
                val snapshotDir = File(context.filesDir, "snapshots").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val zipFile = File(snapshotDir, "snapshot_$timestamp.zip")

                ZipOutputStream(zipFile.outputStream()).use { zos ->
                    val databasesDir = File(context.filesDir, "databases")
                    if (databasesDir.exists()) {
                        databasesDir.walkTopDown().forEach { file ->
                            if (file.isFile) {
                                val entry = ZipEntry(file.relativeTo(databasesDir).path)
                                zos.putNextEntry(entry)
                                file.inputStream().use { it.copyTo(zos) }
                                zos.closeEntry()
                            }
                        }
                    }
                }

                loadSnapshots()
            } catch (e: Exception) {
                // silent fail
            }
        }
    }

    fun restoreSnapshot(filename: String) {
        viewModelScope.launch {
            try {
                val snapshotDir = File(context.filesDir, "snapshots")
                val zipFile = File(snapshotDir, filename)
                if (!zipFile.exists()) return@launch

                ZipInputStream(zipFile.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(context.filesDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zis.copyTo(it) }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } catch (e: Exception) {
                // silent fail
            }
        }
    }

    private fun loadSnapshots() {
        val snapshotDir = File(context.filesDir, "snapshots")
        if (!snapshotDir.exists()) {
            _snapshots.value = emptyList()
            return
        }

        _snapshots.value = snapshotDir.listFiles()
            ?.filter { it.extension == "zip" }
            ?.sortedByDescending { it.name }
            ?.map { file ->
                SnapshotInfo(
                    filename = file.name,
                    createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(file.lastModified())),
                    sizeBytes = file.length()
                )
            } ?: emptyList()
    }
}
