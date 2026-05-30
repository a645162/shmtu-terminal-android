package cn.edu.shmtu.terminal.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.Identity
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
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 首页 ViewModel - 对齐 Rust 版 HomePage
 *
 * 新增:
 * - todayExpense: 今日消费
 * - recentBills: 最近 5 条交易
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

    val billOverview: StateFlow<BillOverview?> = billRepository.getBillOverview(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _accountCount = MutableStateFlow(0)
    val accountCount: StateFlow<Int> = _accountCount

    /** 今日消费 - 对齐 Rust 版 todaySummary.total_expense */
    val todayExpense: StateFlow<Double> = billRepository.getSpendingTrend(
        null,
        LocalDate.now().format(DATE_FMT),
        LocalDate.now().format(DATE_FMT_END)
    ).combine(flowOf(Unit)) { trends, _ ->
        trends.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    /** 最近 5 条交易 - 对齐 Rust 版 recent_transactions */
    val recentBills: StateFlow<List<BillItem>> = identityRepository.getAllIdentities()
        .flatMapLatest { identities ->
            if (identities.isEmpty()) {
                flowOf(emptyList())
            } else {
                // 取第一个身份的最近账单
                billRepository.getBillsForIdentity(identities.first().id)
            }
        }.combine(flowOf(Unit)) { bills, _ ->
            bills.take(5)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
