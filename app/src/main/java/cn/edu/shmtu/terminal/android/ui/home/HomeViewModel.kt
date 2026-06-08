package cn.edu.shmtu.terminal.android.ui.home

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.cas.parser.PersonAccountParser
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.DailyTrend
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.PersonAccount
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.StatisticsSummary
import cn.edu.shmtu.terminal.android.data.remote.EpayAdapter
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
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
    private val billRepository: BillRepository,
    private val accountRepository: AccountRepository,
    private val epayAdapter: EpayAdapter,
    application: Application
) : ViewModel() {

    // 绕开 KSP 跨包解析 bug: 直接用 Application 读 SharedPreferences, 不注入 FeatureSettingsStore
    private val sp = application.getSharedPreferences("feature_settings", Context.MODE_PRIVATE)

    /** 首页趋势图默认时间范围 - 同步自 FeatureSettingsStore.homeTrendRange, 每次手动触发刷新 */
    private val _homeChartRange = MutableStateFlow(sp.getString("home_trend", "week") ?: "week")
    val homeChartRange: StateFlow<String> = _homeChartRange.asStateFlow()

    /** 触发从 SP 重读 */
    fun reloadHomeChartRange() {
        _homeChartRange.value = sp.getString("home_trend", "week") ?: "week"
    }

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentIdentity: StateFlow<Identity?> = identityRepository.getCurrentIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 当前 identity 下的所有账号 - 用于选第一个拉取一卡通余额 */
    val currentIdentityAccounts = identityRepository.getCurrentIdentityId()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else accountRepository.getAccountsByIdentity(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 当前选中身份/账号的"一卡通个人账户详情"缓存
     *
     * 策略: 优先取当前 identity 下第一个有缓存 PersonAccount 的账号;
     * 若都没有则从 cachedPersonAccounts Flow 中选第一个,都没有时为 null。
     * UI 层根据此值显示余额卡片,点击"刷新"按钮时调用 [refreshCurrentBalance]。
     */
    val currentPersonAccount: StateFlow<PersonAccount?> = currentIdentityAccounts
        .flatMapLatest { accounts ->
            if (accounts.isEmpty()) flowOf(null)
            else {
                val flows = accounts.map { acc ->
                    accountRepository.observeCachedPersonAccount(acc.id).map { acc.id to it }
                }
                combine(flows) { array ->
                    array.mapNotNull { (id, pa) -> pa?.let { id to it } }
                        .firstOrNull()
                        ?.second
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isRefreshingBalance = MutableStateFlow(false)
    val isRefreshingBalance: StateFlow<Boolean> = _isRefreshingBalance.asStateFlow()

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

    /** 忘拔卡异常提醒 - 对齐 Rust forgot_card_stats (洗澡消费恰好 5 元) */
    val forgotCardRisk: StateFlow<ForgotCardRisk> = combine(
        identityRepository.getCurrentIdentityId(),
        _refreshTrigger
    ) { identityId, _ -> identityId }
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(ForgotCardRisk())
            else billRepository.getForgotCardStats(
                identityId,
                YearMonth.now().atDay(1).format(DATE_FMT),
                YearMonth.now().atEndOfMonth().format(DATE_FMT_END)
            ).map { stats ->
                ForgotCardRisk(count = stats.count, totalAmount = stats.totalAmount)
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

    /**
     * 刷新一卡通余额
     *
     * 对当前 identity 下的所有账号逐个尝试拉取 [cn.edu.shmtu.cas.auth.EpayAuth.getPersonAccountHtml] 并解析。
     * 任一成功即更新缓存;失败时仅打 warn 日志,UI 仍可读取 Room 缓存。
     */
    fun refreshCurrentBalance() {
        if (_isRefreshingBalance.value) return
        viewModelScope.launch {
            _isRefreshingBalance.value = true
            try {
                val accounts = currentIdentityAccounts.value
                if (accounts.isEmpty()) {
                    Log.w("HomeViewModel", "refreshCurrentBalance: no accounts under current identity")
                    return@launch
                }
                for (acc in accounts) {
                    val result = epayAdapter.fetchPersonAccountHtml(acc.id)
                    if (result.isSuccess) {
                        val html = result.getOrThrow()
                        val info = runCatching { PersonAccountParser().parse(html) }.getOrNull()
                        if (info != null) {
                            accountRepository.savePersonAccount(acc.id, info)
                            Log.d("HomeViewModel", "refreshCurrentBalance: saved for ${acc.id}")
                        }
                    } else {
                        Log.w("HomeViewModel",
                            "refreshCurrentBalance: ${acc.id} fetch failed: ${result.exceptionOrNull()?.message}")
                    }
                }
            } finally {
                _isRefreshingBalance.value = false
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
