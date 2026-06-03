package cn.edu.shmtu.terminal.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 首页 ViewModel - 对齐 Rust 版 HomePage
 *
 * 功能:
 * - 统计卡片: 今日消费、本月消费、本月充值、卡片余额
 * - 消费趋势折线图 (近7天)
 * - 分类占比饼图 (本月)
 * - 月度对比卡片
 * - 异常提醒 (忘拔卡)
 * - 最近 5 条交易
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val accountRepository: AccountRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _accountCount = MutableStateFlow(0)
    val accountCount: StateFlow<Int> = _accountCount

    /** 账单概览 (本月) */
    val billOverview: StateFlow<BillOverview?> = billRepository.getBillOverview(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 今日消费 */
    val todayExpense: StateFlow<Double> = billRepository.getSpendingTrend(
        null,
        LocalDate.now().format(DATE_FMT),
        LocalDate.now().format(DATE_FMT_END)
    ).combine(flowOf(Unit)) { trends, _ ->
        trends.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** 近7天消费趋势 - 对齐 Rust 版 ExpenseTrendChart */
    val weeklyTrend: StateFlow<List<SpendingTrend>> = billRepository.getSpendingTrend(
        null,
        LocalDate.now().minusDays(6).format(DATE_FMT),
        LocalDate.now().format(DATE_FMT_END)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 本月分类占比 - 对齐 Rust 版 CategoryPieChart */
    val categoryBreakdown: StateFlow<List<CategoryBreakdown>> = billRepository.getCategoryBreakdown(
        null,
        YearMonth.now().atDay(1).format(DATE_FMT),
        YearMonth.now().atEndOfMonth().format(DATE_FMT_END)
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 月度对比 - 对齐 Rust 版 MonthComparisonCard */
    val monthlySummary: StateFlow<List<MonthlySummary>> = billRepository.getMonthlySummary(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 忘拔卡异常提醒 - 对齐 Rust 版 forgot_card_stats */
    val forgotCardRisk: StateFlow<ForgotCardRisk> = identityRepository.getAllIdentities()
        .flatMapLatest { identities ->
            if (identities.isEmpty()) flowOf(ForgotCardRisk())
            else billRepository.getBillsForIdentity(identities.first().id)
                .combine(flowOf(Unit)) { bills, _ ->
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
    val recentBills: StateFlow<List<BillItem>> = identityRepository.getAllIdentities()
        .flatMapLatest { identities ->
            if (identities.isEmpty()) flowOf(emptyList())
            else billRepository.getBillsForIdentity(identities.first().id)
        }.combine(flowOf(Unit)) { bills, _ -> bills.take(5) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _accountCount.value = accountRepository.getAllAccounts().size
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
