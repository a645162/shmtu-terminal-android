package cn.edu.shmtu.terminal.android.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.DailyTrend
import cn.edu.shmtu.terminal.android.domain.model.ForgotCardStats
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

    // 触发器:每次任何选择器(身份/时段/分类/自定义日期)变化都 +1,作为手动"刷新一下"信号,
    // 主要用来打破 Room 缓存 + 强制 dao Flow 重新发射(对某些 emit-once 类型的查询有效)。
    private val _refreshTick = MutableStateFlow(0)

    /**
     * 计算当前选定时段对应的"实际"起止日期(用于"今天/本周/本月"等单月统计的归一化)。
     * 返回 Pair(start, end) 字符串。
     */
    private fun effectiveDateRange(): Pair<String, String> {
        if (_selectedPeriod.value == StatisticsPeriod.CUSTOM) {
            val now = LocalDate.now()
            val start = _customStartDate.value ?: YearMonth.from(now).atDay(1)
            val end = _customEndDate.value ?: now
            return start.format(DATE_ONLY) to end.format(DATE_END)
        }
        return _selectedPeriod.value.resolve()
    }

    val overview: StateFlow<BillOverview?> = combine(
        _selectedIdentityId, _refreshTick
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId -> billRepository.getBillOverview(identityId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val spendingTrend: StateFlow<List<SpendingTrend>> = combine(
        _selectedIdentityId, dateRangeFlow, _refreshTick
    ) { identityId, range, _ ->
        billRepository.getSpendingTrend(identityId, range.first, range.second)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyTrend: StateFlow<List<DailyTrend>> = combine(
        _selectedIdentityId, dateRangeFlow, _refreshTick
    ) { identityId, range, _ ->
        billRepository.getDailyTrend(identityId, range.first, range.second)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryBreakdown: StateFlow<List<CategoryBreakdown>> = combine(
        _selectedIdentityId, dateRangeFlow, _selectedCategory, _refreshTick
    ) { identityId, range, category, _ ->
        val raw = billRepository.getCategoryBreakdown(identityId, range.first, range.second)
        // 按 selectedCategory 过滤(仅显示选中分类)
        if (category == "all") raw
        else kotlinx.coroutines.flow.flow { raw.collect { all -> emit(all.filter { it.type == category }) } }
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val targetUserRanking: StateFlow<List<TargetUserRanking>> = combine(
        _selectedIdentityId, dateRangeFlow, _selectedCategory, _refreshTick
    ) { identityId, range, category, _ ->
        // 选了具体分类时,把 range 收窄到该分类首次出现到现在(避免乱序)
        val (s, e) = effectiveDateRange()
        billRepository.getTargetUserRanking(identityId, s, e, if (category == "all") 10 else 30)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummary: StateFlow<List<MonthlySummary>> = combine(
        _selectedIdentityId, _refreshTick
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId -> billRepository.getMonthlySummary(identityId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statisticsSummary: StateFlow<StatisticsSummary?> = combine(
        _selectedIdentityId, dateRangeFlow, _refreshTick
    ) { identityId, range, _ ->
        billRepository.getStatisticsSummary(identityId, range.first, range.second)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val forgotCardStats: StateFlow<ForgotCardStats> = combine(
        _selectedIdentityId, dateRangeFlow, _refreshTick
    ) { identityId, range, _ ->
        billRepository.getForgotCardStats(identityId, range.first, range.second)
    }.flatMapLatest { it }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ForgotCardStats(0, 0.0, emptyList())
    )

    // ==================== 选择器操作 ====================

    fun selectIdentity(id: Long?) {
        _selectedIdentityId.value = id
        refreshStatistics()
    }

    fun selectPeriod(period: StatisticsPeriod) {
        _selectedPeriod.value = period
        refreshStatistics()
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
        refreshStatistics()
    }

    /**
     * 主动触发所有数据流重新拉取(对齐 Tauri refreshStatistics 行为)
     * +1 tick → 所有 combine 都会重新发射 → 重新订阅底层 Flow → Room DAO 再次查询
     */
    fun refreshStatistics() {
        android.util.Log.d(
            "StatisticsVM",
            "refreshStatistics: tick=${_refreshTick.value} period=${_selectedPeriod.value} identity=${_selectedIdentityId.value} category=${_selectedCategory.value}"
        )
        _refreshTick.value = _refreshTick.value + 1
    }

    fun setCustomDateRange(start: LocalDate?, end: LocalDate?) {
        _customStartDate.value = start
        _customEndDate.value = end
        if (start != null || end != null) {
            _selectedPeriod.value = StatisticsPeriod.CUSTOM
        }
        refreshStatistics()
    }

    companion object {
        private val DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_END = DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59")
    }
}
