package cn.edu.shmtu.terminal.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.DailyTrend
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.StatisticsSummary
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 首页 ViewModel - 对齐 Rust/Tauri 版 HomePage + refreshStatistics
 *
 * 功能:
 * - 统计卡片: 今日消费、本月消费、本月充值、卡片余额
 * - 消费趋势折线图 (近7天) + 收入/支出双线 (对齐 Rust 版 DailyTrend)
 * - 分类占比饼图 (本月)
 * - 月度对比卡片
 * - 异常提醒 (忘拔卡)
 * - 最近 5 条交易 (支持点击查看详情 / 复制)
 * - 主动刷新统计: 重新拉取所有统计,反馈 isRefreshing / isLoadingStatistics
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentIdentity: StateFlow<Identity?> = identityRepository.getCurrentIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 用于触发主动刷新:每次值变化都会重新拉取所有统计流 */
    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger.asStateFlow()

    /** 全局刷新态(单次操作,如点击"刷新统计"按钮) - 对齐 Rust refreshStatistics */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 统计加载骨架态(初次加载或主动刷新过程中为 true) - 对齐 Rust isLoadingStatistics */
    val isLoadingStatistics: StateFlow<Boolean> = combine(
        _isRefreshing,
        currentIdentity
    ) { refreshing, identity -> refreshing && identity != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 今日统计 - 对齐 Rust loadTodaySummary / todaySummary */
    val todaySummary: StateFlow<StatisticsSummary?> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(null)
            else billRepository.getStatisticsSummary(
                identityId,
                LocalDate.now().format(DATE_FMT),
                LocalDate.now().format(DATE_FMT_END)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 本月统计 - 对齐 Rust loadMonthSummary / monthSummary */
    val monthSummary: StateFlow<StatisticsSummary?> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(null)
            else billRepository.getStatisticsSummary(
                identityId,
                YearMonth.now().atDay(1).format(DATE_FMT),
                YearMonth.now().atEndOfMonth().format(DATE_FMT_END)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 账单概览 (本月) - 保留旧版字段 (lastMonthSpending/lastMonthIncome 等) */
    val billOverview: StateFlow<BillOverview?> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(null)
            else billRepository.getBillOverview(identityId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 今日消费 (旧版 SpendingTrend 路径) - 用于旧 Card UI 的兜底 */
    val todayExpense: StateFlow<Double> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(0.0)
            else billRepository.getSpendingTrend(
                identityId,
                LocalDate.now().format(DATE_FMT),
                LocalDate.now().format(DATE_FMT_END)
            ).map { trends -> trends.sumOf { it.amount } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** 近7天消费趋势(老 SpendingTrend 模型,只含支出) */
    val weeklyTrend: StateFlow<List<SpendingTrend>> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(emptyList())
            else billRepository.getSpendingTrend(
                identityId,
                LocalDate.now().minusDays(6).format(DATE_FMT),
                LocalDate.now().format(DATE_FMT_END)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 近7天双线趋势(新 DailyTrend 模型) - 对齐 Rust dailyTrend (含 expense / income) */
    val dailyTrend: StateFlow<List<DailyTrend>> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(emptyList())
            else billRepository.getDailyTrend(
                identityId,
                LocalDate.now().minusDays(6).format(DATE_FMT),
                LocalDate.now().format(DATE_FMT_END)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 本月分类占比 - 对齐 Rust CategoryPieChart */
    val categoryBreakdown: StateFlow<List<CategoryBreakdown>> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(emptyList())
            else billRepository.getCategoryBreakdown(
                identityId,
                YearMonth.now().atDay(1).format(DATE_FMT),
                YearMonth.now().atEndOfMonth().format(DATE_FMT_END)
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 月度对比 - 对齐 Rust MonthComparisonCard */
    val monthlySummary: StateFlow<List<MonthlySummary>> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(emptyList())
            else billRepository.getMonthlySummary(identityId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 忘拔卡异常提醒 - 对齐 Rust forgot_card_stats (本实现基于热水/洗浴关键词统计) */
    val forgotCardRisk: StateFlow<ForgotCardRisk> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(ForgotCardRisk())
            else billRepository.getBillsForIdentity(identityId)
                .map { bills ->
                    val suspicious = bills.filter { bill ->
                        bill.type.contains("洗浴") || bill.type.contains("热水")
                    }.groupBy { bill ->
                        bill.dateTimeStrFormat.substringBefore(" ")
                    }.count { (_, billsForDay) ->
                        billsForDay.size > 2 && billsForDay.sumOf { it.money.toDouble() } > 10.0
                    }
                    val totalAmount = bills.filter {
                        it.type.contains("洗浴") || it.type.contains("热水")
                    }.sumOf { it.money.toDouble() }
                    ForgotCardRisk(count = suspicious, totalAmount = totalAmount)
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ForgotCardRisk())

    /** 最近 5 条交易 */
    val recentBills: StateFlow<List<BillItem>> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(emptyList())
            else billRepository.getBillsForIdentity(identityId)
        }.map { bills -> bills.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 主动刷新统计 - 对齐 Rust refreshStatistics
     *
     * 触发 _refreshTrigger,所有依赖该 trigger 的统计流会自动重新订阅并拉取最新数据;
     * 同时短暂置位 _isRefreshing 触发按钮"刷新中..."状态。
     */
    fun refreshStatistics() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val identityId = identityRepository.getCurrentIdentityId().first()
                if (identityId != null) {
                    // 主动拉取一次今天/本月/趋势/分类数据(订阅后 emit 一次,确保 UI 立即更新)
                    billRepository.getStatisticsSummary(
                        identityId,
                        LocalDate.now().format(DATE_FMT),
                        LocalDate.now().format(DATE_FMT_END)
                    ).first()
                    billRepository.getStatisticsSummary(
                        identityId,
                        YearMonth.now().atDay(1).format(DATE_FMT),
                        YearMonth.now().atEndOfMonth().format(DATE_FMT_END)
                    ).first()
                    billRepository.getDailyTrend(
                        identityId,
                        LocalDate.now().minusDays(6).format(DATE_FMT),
                        LocalDate.now().format(DATE_FMT_END)
                    ).first()
                    billRepository.getCategoryBreakdown(
                        identityId,
                        YearMonth.now().atDay(1).format(DATE_FMT),
                        YearMonth.now().atEndOfMonth().format(DATE_FMT_END)
                    ).first()
                }
                // 递增 trigger 让所有相关 Flow 重新订阅
                _refreshTrigger.value = _refreshTrigger.value + 1
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_FMT_END = DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59")
    }
}

/** 忘拔卡风险统计 */
data class ForgotCardRisk(
    val count: Int = 0,
    val totalAmount: Double = 0.0
)
