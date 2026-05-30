package cn.edu.shmtu.terminal.android.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncIdentityBillsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillListViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val billRepository: BillRepository,
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedIdentityId = MutableStateFlow<Long?>(null)

    val bills: StateFlow<List<BillItem>> = _selectedIdentityId.flatMapLatest { identityId ->
        if (identityId == null) {
            flowOf(emptyList())
        } else {
            billRepository.getBillsForIdentity(identityId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 同步进度 - 对齐 Rust 版 sync-progress 事件 */
    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    private val _isSyncingFlag = MutableStateFlow(false)

    /** 是否正在同步 */
    val isSyncing: StateFlow<Boolean> = combine(
        _syncProgress,
        _isSyncingFlag
    ) { progress, flag ->
        flag && progress?.status !is SyncStatus.Completed && progress?.status !is SyncStatus.Failed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun selectIdentity(identityId: Long?) {
        _selectedIdentityId.value = identityId
    }

    /** 增量同步（带进度） - 对齐 Rust 版 incremental_sync */
    fun syncBills(identityId: Long, onResult: (SyncResult) -> Unit) {
        viewModelScope.launch {
            _isSyncingFlag.value = true
            val result = syncIdentityBillsUseCase(identityId) { progress ->
                _syncProgress.value = progress
            }
            _isSyncingFlag.value = false
            onResult(result)
        }
    }

    /** 带进度的增量同步 - 通过 Repository */
    fun syncBillsWithProgress(identityId: Long) {
        viewModelScope.launch {
            _isSyncingFlag.value = true
            billRepository.syncIdentityBillsWithProgress(identityId)
            _isSyncingFlag.value = false
        }
    }

    /** 带进度的全量同步 - 对齐 Rust 版 full_sync */
    fun fullSyncBills(identityId: Long) {
        viewModelScope.launch {
            _isSyncingFlag.value = true
            billRepository.fullSyncIdentityWithProgress(identityId)
            _isSyncingFlag.value = false
        }
    }

    /** 清除进度状态 */
    fun clearSyncProgress() {
        _syncProgress.value = null
    }

    /**
     * 获取进度文案 - 对齐 Rust 版 SyncStatusPanel 的状态消息
     */
    fun getProgressMessage(progress: SyncProgress): String {
        return when (val status = progress.status) {
            is SyncStatus.ProbingLogin -> "正在探测登录状态..."
            is SyncStatus.GettingCaptcha -> "正在获取验证码..."
            is SyncStatus.LoggingIn -> "正在登录 ${progress.accountLabel}..."
            is SyncStatus.Syncing -> {
                val accountInfo = if (progress.accountTotal > 1) {
                    "账号 ${progress.accountIndex + 1}/${progress.accountTotal} · "
                } else ""
                "$accountInfo${progress.accountLabel} · 正在同步第 ${status.page}/${status.total} 页，新增 ${status.newCount} 条"
            }
            is SyncStatus.Persisting -> "正在保存 ${status.totalNew} 条记录..."
            is SyncStatus.Completed -> "同步完成，新增 ${status.totalNew} 条"
            is SyncStatus.Failed -> "同步失败: ${status.error}"
        }
    }
}
