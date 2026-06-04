package cn.edu.shmtu.terminal.android.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.DailyTrend
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.StatisticsSummary
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 账单统计 ViewModel - 对齐 Tauri StatisticsDialog
 *
 * 三个选择器:
 * 1. 时间段 [StatisticsPeriod] (12 个: 11 固定 + 1 自定义)
 * 2. 身份 [Identity]
 * 3. 分类 [String] ("all" + 当前实际分类列表)
 *
 * 所有数据流(overview/trend/categoryBreakdown/targetUserRanking/monthlySummary/statisticsSummary)
 * 都依赖 (selectedIdentityId, selectedPeriod, customStart, customEnd) 重新订阅。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillStatisticsViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    // ==================== 三个选择器 ====================

    private val _selectedIdentityId = MutableStateFlow<Long?>(null)
    val selectedIdentityId: StateFlow<Long?> = _selectedIdentityId.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(StatisticsPeriod.MONTH)
    val selectedPeriod: StateFlow<StatisticsPeriod> = _selectedPeriod.asStateFlow()

    private val _selectedCategory = MutableStateFlow("all")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _customStartDate = MutableStateFlow<LocalDate?>(null)
    val customStartDate: StateFlow<LocalDate?> = _customStartDate.asStateFlow()

    private val _customEndDate = MutableStateFlow<LocalDate?>(null)
    val customEndDate: StateFlow<LocalDate?> = _customEndDate.asStateFlow()

    // ==================== 身份 / 当前身份 ====================

    val currentIdentity: StateFlow<Identity?> = identityRepository.getCurrentIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 首次启动时,默认选中当前身份
    init {
        viewModelScope.launch {
            val cur = identityRepository.getCurrentIdentity().first()
            if (cur != null) {
                _selectedIdentityId.value = cur.id
            }
        }
    }

    // ==================== 时间区间推算 ====================

    private fun dateRangeForSelection(): Pair<String, String> {
        if (_selectedPeriod.value == StatisticsPeriod.CUSTOM) {
            val now = LocalDate.now()
            val start = _customStartDate.value ?: YearMonth.from(now).atDay(1)
            val end = _customEndDate.value ?: now
            return start.format(DATE_ONLY) to end.format(DATE_END)
        }
        return _selectedPeriod.value.resolve()
    }

    private val dateRangeFlow = combine(
        _selectedPeriod,
        _customStartDate,
        _customEndDate
    ) { _, _, _ -> dateRangeForSelection() }

    // ==================== 数据流(对齐 Tauri 各 load 函数) ====================

    val overview: StateFlow<BillOverview?> = _selectedIdentityId.flatMapLatest { identityId ->
        billRepository.getBillOverview(identityId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val spendingTrend: StateFlow<List<SpendingTrend>> = combine(
        _selectedIdentityId, dateRangeFlow
    ) { identityId, range ->
        billRepository.getSpendingTrend(identityId, range.first, range.second)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyTrend: StateFlow<List<DailyTrend>> = combine(
        _selectedIdentityId, dateRangeFlow
    ) { identityId, range ->
        billRepository.getDailyTrend(identityId, range.first, range.second)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryBreakdown: StateFlow<List<CategoryBreakdown>> = combine(
        _selectedIdentityId, dateRangeFlow
    ) { identityId, range ->
        billRepository.getCategoryBreakdown(identityId, range.first, range.second)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val targetUserRanking: StateFlow<List<TargetUserRanking>> = combine(
        _selectedIdentityId, dateRangeFlow
    ) { identityId, range ->
        billRepository.getTargetUserRanking(identityId, range.first, range.second, 10)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummary: StateFlow<List<MonthlySummary>> = _selectedIdentityId.flatMapLatest { identityId ->
        billRepository.getMonthlySummary(identityId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statisticsSummary: StateFlow<StatisticsSummary?> = combine(
        _selectedIdentityId, dateRangeFlow
    ) { identityId, range ->
        billRepository.getStatisticsSummary(identityId, range.first, range.second)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ==================== 选择器操作 ====================

    fun selectIdentity(id: Long?) {
        _selectedIdentityId.value = id
    }

    fun selectPeriod(period: StatisticsPeriod) {
        _selectedPeriod.value = period
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setCustomDateRange(start: LocalDate?, end: LocalDate?) {
        _customStartDate.value = start
        _customEndDate.value = end
        if (start != null || end != null) {
            _selectedPeriod.value = StatisticsPeriod.CUSTOM
        }
    }

    companion object {
        private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_END = DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59")
    }
}
