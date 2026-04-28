package cn.edu.shmtu.terminal.android.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillOverview
import cn.edu.shmtu.terminal.android.domain.model.CategoryBreakdown
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.MonthlySummary
import cn.edu.shmtu.terminal.android.domain.model.SpendingTrend
import cn.edu.shmtu.terminal.android.domain.model.TargetUserRanking
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

enum class TimePeriod(val label: String) {
    THIS_WEEK("本周"),
    THIS_MONTH("本月"),
    THIS_SEMESTER("本学期"),
    CUSTOM("自定义")
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class BillStatisticsViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedIdentityId = MutableStateFlow<Long?>(null)
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_MONTH)
    private val _customStartDate = MutableStateFlow<LocalDate?>(null)
    private val _customEndDate = MutableStateFlow<LocalDate?>(null)

    private val dateRange: Pair<String, String>
        get() = when (_selectedPeriod.value) {
            TimePeriod.THIS_WEEK -> {
                val today = LocalDate.now()
                val start = today.minusDays(today.dayOfWeek.value.toLong() - 1)
                start.format(DATE_FMT) to today.format(DATE_FMT_END)
            }
            TimePeriod.THIS_MONTH -> {
                val now = YearMonth.now()
                now.atDay(1).format(DATE_FMT) to now.atEndOfMonth().format(DATE_FMT_END)
            }
            TimePeriod.THIS_SEMESTER -> {
                val now = YearMonth.now()
                val semesterStart = if (now.monthValue >= 9) {
                    YearMonth.of(now.year, 9).atDay(1)
                } else if (now.monthValue >= 2) {
                    YearMonth.of(now.year, 2).atDay(1)
                } else {
                    YearMonth.of(now.year - 1, 9).atDay(1)
                }
                semesterStart.format(DATE_FMT) to now.atEndOfMonth().format(DATE_FMT_END)
            }
            TimePeriod.CUSTOM -> {
                val start = _customStartDate.value ?: YearMonth.now().atDay(1)
                val end = _customEndDate.value ?: YearMonth.now().atEndOfMonth()
                start.format(DATE_FMT) to end.format(DATE_FMT_END)
            }
        }

    val overview: StateFlow<BillOverview?> = _selectedIdentityId.flatMapLatest { identityId ->
        billRepository.getBillOverview(identityId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val spendingTrend: StateFlow<List<SpendingTrend>> = combine(
        _selectedIdentityId,
        _selectedPeriod
    ) { identityId, _ ->
        val (start, end) = dateRange
        billRepository.getSpendingTrend(identityId, start, end)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categoryBreakdown: StateFlow<List<CategoryBreakdown>> = combine(
        _selectedIdentityId,
        _selectedPeriod
    ) { identityId, _ ->
        val (start, end) = dateRange
        billRepository.getCategoryBreakdown(identityId, start, end)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val targetUserRanking: StateFlow<List<TargetUserRanking>> = combine(
        _selectedIdentityId,
        _selectedPeriod
    ) { identityId, _ ->
        val (start, end) = dateRange
        billRepository.getTargetUserRanking(identityId, start, end, 10)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val monthlySummary: StateFlow<List<MonthlySummary>> = _selectedIdentityId.flatMapLatest { identityId ->
        billRepository.getMonthlySummary(identityId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectIdentity(identityId: Long?) {
        _selectedIdentityId.value = identityId
    }

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    val customStartDate: StateFlow<LocalDate?> = _customStartDate.asStateFlow()
    val customEndDate: StateFlow<LocalDate?> = _customEndDate.asStateFlow()

    fun setCustomDateRange(start: LocalDate?, end: LocalDate?) {
        _customStartDate.value = start
        _customEndDate.value = end
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_FMT_END = DateTimeFormatter.ofPattern("yyyy-MM-dd 23:59:59")
    }
}
