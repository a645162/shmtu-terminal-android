package cn.edu.shmtu.terminal.android.ui.bill

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.sync.SyncRangePreset as CasSyncRangePreset
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.SyncProgress
import cn.edu.shmtu.terminal.android.domain.model.SyncResult
import cn.edu.shmtu.terminal.android.domain.model.SyncStatus
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.usecase.bill.CaptchaRequiredException
import cn.edu.shmtu.terminal.android.domain.usecase.bill.FullSyncAccountBillsUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.bill.FullSyncIdentityBillsUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncAccountBillsUseCase
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
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase,
    private val syncAccountBillsUseCase: SyncAccountBillsUseCase,
    private val fullSyncIdentityBillsUseCase: FullSyncIdentityBillsUseCase,
    private val fullSyncAccountBillsUseCase: FullSyncAccountBillsUseCase,
) : ViewModel() {
    private val tag = "BillListViewModel"

    val currentIdentity: StateFlow<Identity?> = identityRepository.getCurrentIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentIdentityId: StateFlow<Long?> = identityRepository.getCurrentIdentityId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 当前身份下的账号列表 */
    val accounts: StateFlow<List<Account>> = currentIdentityId.flatMapLatest { identityId ->
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

    private val allBills: StateFlow<List<BillItem>> = currentIdentityId.flatMapLatest { identityId ->
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

    // ==================== 验证码状态 - 对齐 Rust 版 PendingManualSync ====================

    /** 待处理的验证码请求（非 null 表示需要用户输入验证码） */
    private val _pendingCaptcha = MutableStateFlow<CaptchaRequiredException?>(null)
    val pendingCaptcha: StateFlow<CaptchaRequiredException?> = _pendingCaptcha.asStateFlow()

    // ==================== 同步状态 ====================

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    private val _isSyncingFlag = MutableStateFlow(false)

    val isSyncing: StateFlow<Boolean> = combine(_syncProgress, _isSyncingFlag) { progress, flag ->
        flag && progress?.status !is SyncStatus.Completed && progress?.status !is SyncStatus.Failed
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ==================== 操作 ====================

    fun setTypeFilter(filter: BillTypeFilter) { _typeFilter.value = filter; _page.value = 1 }
    fun setDateRange(range: DateRangeFilter) { _dateRange.value = range; _page.value = 1 }
    fun search(query: String) { _searchQuery.value = query; _page.value = 1 }
    fun setPage(page: Int) { _page.value = page.coerceIn(1, totalPages.value) }

    fun syncBills(identityId: Long, range: SyncRangePreset, onResult: (SyncResult) -> Unit = {}) {
        viewModelScope.launch {
            Log.d(tag, "syncBills identityId=$identityId range=$range")
            beginSync("增量更新当前身份", range)
            var capturedCaptcha: CaptchaRequiredException? = null
            try {
                val result = syncIdentityBillsUseCase(identityId, range.toCasRange()) { _syncProgress.value = it }
                completeSync(result, fallbackLabel = "当前身份")
                onResult(result)
            } catch (e: CaptchaRequiredException) {
                capturedCaptcha = e
            } finally {
                _isSyncingFlag.value = false
            }
            capturedCaptcha?.let { e ->
                Log.d(tag, "syncBills pending captcha accountId=${e.accountId} label=${e.accountLabel} purpose=${e.purpose}")
                _syncProgress.value = SyncProgress(status = SyncStatus.GettingCaptcha, accountLabel = e.accountLabel)
                _pendingCaptcha.value = e
            }
        }
    }

    fun syncAccountBills(accountId: Long, range: SyncRangePreset) {
        viewModelScope.launch {
            Log.d(tag, "syncAccountBills accountId=$accountId range=$range")
            val account = accountRepository.getAccountById(accountId)
            if (account == null) {
                _syncProgress.value = SyncProgress(
                    status = SyncStatus.Failed("账号不存在"),
                    accountLabel = "账号"
                )
                return@launch
            }
            beginSync("增量更新账号 ${account.label}", range)
            var capturedCaptcha: CaptchaRequiredException? = null
            try {
                val result = syncAccountBillsUseCase(account, range.toCasRange()) { _syncProgress.value = it }
                completeSync(result, fallbackLabel = account.label)
            } catch (e: CaptchaRequiredException) {
                capturedCaptcha = e
            } finally {
                _isSyncingFlag.value = false
            }
            capturedCaptcha?.let { e ->
                Log.d(tag, "syncAccountBills pending captcha accountId=${e.accountId} label=${e.accountLabel} purpose=${e.purpose}")
                _syncProgress.value = SyncProgress(status = SyncStatus.GettingCaptcha, accountLabel = e.accountLabel)
                _pendingCaptcha.value = e
            }
        }
    }

    fun fullSyncBills(identityId: Long, range: SyncRangePreset) {
        viewModelScope.launch {
            Log.d(tag, "fullSyncBills identityId=$identityId range=$range")
            beginSync("全量更新当前身份", range)
            var capturedCaptcha: CaptchaRequiredException? = null
            try {
                val result = fullSyncIdentityBillsUseCase(identityId, range.toCasRange()) { _syncProgress.value = it }
                completeSync(result, fallbackLabel = "当前身份")
            } catch (e: CaptchaRequiredException) {
                capturedCaptcha = e
            } finally {
                _isSyncingFlag.value = false
            }
            capturedCaptcha?.let { e ->
                Log.d(tag, "fullSyncBills pending captcha accountId=${e.accountId} label=${e.accountLabel} purpose=${e.purpose}")
                _syncProgress.value = SyncProgress(status = SyncStatus.GettingCaptcha, accountLabel = e.accountLabel)
                _pendingCaptcha.value = e
            }
        }
    }

    fun fullSyncAccountBills(accountId: Long, range: SyncRangePreset) {
        viewModelScope.launch {
            Log.d(tag, "fullSyncAccountBills accountId=$accountId range=$range")
            val account = accountRepository.getAccountById(accountId)
            if (account == null) {
                _syncProgress.value = SyncProgress(
                    status = SyncStatus.Failed("账号不存在"),
                    accountLabel = "账号"
                )
                return@launch
            }
            beginSync("全量更新账号 ${account.label}", range)
            var capturedCaptcha: CaptchaRequiredException? = null
            try {
                val result = fullSyncAccountBillsUseCase(account, range.toCasRange()) { _syncProgress.value = it }
                completeSync(result, fallbackLabel = account.label)
            } catch (e: CaptchaRequiredException) {
                capturedCaptcha = e
            } finally {
                _isSyncingFlag.value = false
            }
            capturedCaptcha?.let { e ->
                Log.d(tag, "fullSyncAccountBills pending captcha accountId=${e.accountId} label=${e.accountLabel} purpose=${e.purpose}")
                _syncProgress.value = SyncProgress(status = SyncStatus.GettingCaptcha, accountLabel = e.accountLabel)
                _pendingCaptcha.value = e
            }
        }
    }

    /** 提交验证码并继续同步 - 对齐 Rust 版 sync_with_captcha */
    fun submitCaptcha(captchaCode: String) {
        val captcha = _pendingCaptcha.value ?: return
        viewModelScope.launch {
            Log.d(
                tag,
                "submitCaptcha accountId=${captcha.accountId} label=${captcha.accountLabel} fullSync=${captcha.isFullSync} range=${captcha.syncRange} purpose=${captcha.purpose}"
            )
            val account = accountRepository.getAccountById(captcha.accountId) ?: run {
                _pendingCaptcha.value = null; _isSyncingFlag.value = false; return@launch
            }
            try {
                _pendingCaptcha.value = null
                _isSyncingFlag.value = true
                val result = syncAccountBillsUseCase.syncWithCaptcha(
                    account = account,
                    captchaCode = captchaCode,
                    execution = captcha.execution,
                    syncRange = captcha.syncRange,
                    fullSync = captcha.isFullSync,
                ) { _syncProgress.value = it }
                completeSync(result, fallbackLabel = account.label)
            } catch (e: CaptchaRequiredException) {
                Log.d(tag, "submitCaptcha captcha required again accountId=${e.accountId} label=${e.accountLabel}")
                _syncProgress.value = SyncProgress(status = SyncStatus.GettingCaptcha, accountLabel = e.accountLabel)
                _pendingCaptcha.value = e
                return@launch
            }
            _isSyncingFlag.value = false
        }
    }

    /** 刷新验证码 - 对齐 Rust 版 refresh_captcha */
    fun refreshCaptcha() {
        val captcha = _pendingCaptcha.value ?: return
        viewModelScope.launch {
            val newCaptcha = syncAccountBillsUseCase.refreshCaptcha(captcha.accountId)
            if (newCaptcha != null) {
                _pendingCaptcha.value = CaptchaRequiredException(
                    captchaImageBase64 = newCaptcha.captchaImageBase64,
                    execution = newCaptcha.execution,
                    accountId = captcha.accountId,
                    accountLabel = captcha.accountLabel,
                    syncRange = captcha.syncRange,
                    isFullSync = captcha.isFullSync,
                )
            }
        }
    }

    /** 取消验证码 */
    fun dismissCaptcha() {
        _pendingCaptcha.value = null
        _isSyncingFlag.value = false
        _syncProgress.value = null
    }

    fun clearSyncProgress() { _syncProgress.value = null }

    fun getProgressMessage(progress: SyncProgress): String = when (val status = progress.status) {
        is SyncStatus.ProbingLogin -> {
            if (progress.accountTotal <= 0 && progress.accountLabel.isNotBlank()) {
                "正在启动 ${progress.accountLabel}..."
            } else {
                "正在探测登录状态..."
            }
        }
        is SyncStatus.GettingCaptcha -> if (progress.accountLabel.isNotBlank()) "账号 ${progress.accountLabel} 需要验证码" else "正在获取验证码..."
        is SyncStatus.LoggingIn -> "正在登录 ${progress.accountLabel}..."
        is SyncStatus.Syncing -> {
            val info = if (progress.accountTotal > 1) "账号 ${progress.accountIndex + 1}/${progress.accountTotal} · " else ""
            "$info${progress.accountLabel} · 第 ${status.page}/${status.total} 页，新增 ${status.newCount} 条"
        }
        is SyncStatus.Persisting -> "正在保存 ${status.totalNew} 条记录..."
        is SyncStatus.Completed -> "同步完成，新增 ${status.totalNew} 条"
        is SyncStatus.Failed -> "同步失败: ${status.error}"
    }

    private fun beginSync(actionLabel: String, range: SyncRangePreset) {
        _pendingCaptcha.value = null
        _isSyncingFlag.value = true
        _syncProgress.value = SyncProgress(
            status = SyncStatus.ProbingLogin,
            accountTotal = 0,
            accountLabel = "$actionLabel（范围：${range.label}）",
        )
    }

    private fun completeSync(result: SyncResult, fallbackLabel: String) {
        val currentStatus = _syncProgress.value?.status
        _syncProgress.value = when {
            !result.success -> SyncProgress(
                status = SyncStatus.Failed(result.errorMessage ?: "同步失败"),
                accountLabel = fallbackLabel,
            )
            currentStatus is SyncStatus.Completed -> _syncProgress.value
                ?: SyncProgress(status = SyncStatus.Completed(result.newCount), accountLabel = fallbackLabel)
            else -> SyncProgress(
                status = SyncStatus.Completed(result.newCount),
                accountLabel = fallbackLabel,
            )
        }
    }

    private fun SyncRangePreset.toCasRange(): CasSyncRangePreset = when (this) {
        SyncRangePreset.WEEK -> CasSyncRangePreset.Week
        SyncRangePreset.HALF_MONTH -> CasSyncRangePreset.HalfMonth
        SyncRangePreset.MONTH -> CasSyncRangePreset.Month
        SyncRangePreset.HALF_YEAR -> CasSyncRangePreset.HalfYear
        SyncRangePreset.YEAR -> CasSyncRangePreset.Year
        SyncRangePreset.ALL -> CasSyncRangePreset.All
    }
}
