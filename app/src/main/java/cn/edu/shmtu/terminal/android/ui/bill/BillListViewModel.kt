package cn.edu.shmtu.terminal.android.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
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
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** 账单类型筛选 - 对齐 Rust 版 BILL_TYPE_OPTIONS */
enum class BillTypeFilter(val label: String) {
    ALL("全部"),
    SUCCESS("交易成功"),
    NOT_PAID("待支付"),
    FAILURE("交易失败")
}

/** 日期范围 - 对齐 Rust 版 DATE_RANGE_OPTIONS */
enum class DateRangeFilter(val label: String) {
    ALL("全部时间"),
    TODAY("今天"),
    WEEK("本周"),
    MONTH("本月")
}

private const val PAGE_SIZE = 30

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillListViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val accountRepository: AccountRepository,
    private val billRepository: BillRepository,
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedIdentityId = MutableStateFlow<Long?>(null)

    /** 当前身份下的账号列表 */
    val accounts: StateFlow<List<Account>> = _selectedIdentityId.flatMapLatest { identityId ->
        if (identityId == null) flowOf(emptyList())
        else accountRepository.getAccountsByIdentity(identityId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==================== 筛选状态 ====================

    private val _typeFilter = MutableStateFlow(BillTypeFilter.ALL)
    val typeFilter: StateFlow<BillTypeFilter> = _typeFilter.asStateFlow()

    private val _dateRange = MutableStateFlow(DateRangeFilter.ALL)
    val dateRange: StateFlow<DateRangeFilter> = _dateRange.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _page = MutableStateFlow(1)
    val page: StateFlow<Int> = _page.asStateFlow()

    // ==================== 筛选后的账单 ====================

    private val allBills: StateFlow<List<BillItem>> = _selectedIdentityId.flatMapLatest { identityId ->
        if (identityId == null) flowOf(emptyList())
        else billRepository.getBillsForIdentity(identityId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 应用筛选条件 */
    private fun List<BillItem>.applyFilters(typeFilter: BillTypeFilter, dateRange: DateRangeFilter, query: String): List<BillItem> {
        return filter { bill ->
            val typeMatch = when (typeFilter) {
                BillTypeFilter.ALL -> true
                BillTypeFilter.SUCCESS -> bill.status == "交易成功"
                BillTypeFilter.NOT_PAID -> bill.status == "待支付"
                BillTypeFilter.FAILURE -> bill.status == "交易失败"
            }
            val dateMatch = when (dateRange) {
                DateRangeFilter.ALL -> true
                DateRangeFilter.TODAY -> bill.dateTimeStrFormat.startsWith(LocalDate.now().toString())
                DateRangeFilter.WEEK -> bill.dateTimeStrFormat >= LocalDate.now().minusDays(6).toString()
                DateRangeFilter.MONTH -> bill.dateTimeStrFormat >= YearMonth.now().atDay(1).toString()
            }
            val searchMatch = query.isBlank() ||
                bill.type.contains(query, ignoreCase = true) ||
                bill.targetUser.contains(query, ignoreCase = true) ||
                bill.transactionNo.contains(query, ignoreCase = true)
            typeMatch && dateMatch && searchMatch
        }
    }

    /** 筛选后总数 */
    val totalFiltered: StateFlow<Int> = combine(allBills, _typeFilter, _dateRange, _searchQuery) { bills, tf, dr, q ->
        bills.applyFilters(tf, dr, q).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalPages: StateFlow<Int> = totalFiltered.let { flow ->
        combine(flow) { total -> maxOf(1, (total[0] + PAGE_SIZE - 1) / PAGE_SIZE) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    /** 筛选 + 分页后的账单 */
    val bills: StateFlow<List<BillItem>> = combine(
        allBills, _typeFilter, _dateRange, _searchQuery, _page
    ) { bills, tf, dr, q, page ->
        bills.applyFilters(tf, dr, q)
            .drop((page - 1) * PAGE_SIZE)
            .take(PAGE_SIZE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==================== 同步状态 ====================

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    private val _isSyncingFlag = MutableStateFlow(false)

    val isSyncing: StateFlow<Boolean> = combine(_syncProgress, _isSyncingFlag) { progress, flag ->
        flag && progress?.status !is SyncStatus.Completed && progress?.status !is SyncStatus.Failed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ==================== 操作 ====================

    fun selectIdentity(identityId: Long?) {
        _selectedIdentityId.value = identityId
        _page.value = 1
    }

    fun setTypeFilter(filter: BillTypeFilter) { _typeFilter.value = filter; _page.value = 1 }
    fun setDateRange(range: DateRangeFilter) { _dateRange.value = range; _page.value = 1 }
    fun search(query: String) { _searchQuery.value = query; _page.value = 1 }
    fun setPage(page: Int) { _page.value = page.coerceIn(1, totalPages.value) }

    fun syncBills(identityId: Long, onResult: (SyncResult) -> Unit = {}) {
        viewModelScope.launch {
            _isSyncingFlag.value = true
            val result = syncIdentityBillsUseCase(identityId) { _syncProgress.value = it }
            _isSyncingFlag.value = false
            onResult(result)
        }
    }

    fun syncAccountBills(accountId: Long) {
        viewModelScope.launch {
            _isSyncingFlag.value = true
            billRepository.syncAccountBillsWithProgress(accountId)
            _isSyncingFlag.value = false
        }
    }

    fun fullSyncBills(identityId: Long) {
        viewModelScope.launch {
            _isSyncingFlag.value = true
            billRepository.fullSyncIdentityWithProgress(identityId)
            _isSyncingFlag.value = false
        }
    }

    fun fullSyncAccountBills(accountId: Long) {
        viewModelScope.launch {
            _isSyncingFlag.value = true
            billRepository.fullSyncAccountWithProgress(accountId)
            _isSyncingFlag.value = false
        }
    }

    fun clearSyncProgress() { _syncProgress.value = null }

    fun getProgressMessage(progress: SyncProgress): String = when (val status = progress.status) {
        is SyncStatus.ProbingLogin -> "正在探测登录状态..."
        is SyncStatus.GettingCaptcha -> "正在获取验证码..."
        is SyncStatus.LoggingIn -> "正在登录 ${progress.accountLabel}..."
        is SyncStatus.Syncing -> {
            val info = if (progress.accountTotal > 1) "账号 ${progress.accountIndex + 1}/${progress.accountTotal} · " else ""
            "$info${progress.accountLabel} · 第 ${status.page}/${status.total} 页，新增 ${status.newCount} 条"
        }
        is SyncStatus.Persisting -> "正在保存 ${status.totalNew} 条记录..."
        is SyncStatus.Completed -> "同步完成，新增 ${status.totalNew} 条"
        is SyncStatus.Failed -> "同步失败: ${status.error}"
    }
}
