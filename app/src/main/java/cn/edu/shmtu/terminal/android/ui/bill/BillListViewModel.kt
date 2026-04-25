package cn.edu.shmtu.terminal.android.ui.bill

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncIdentityBillsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BillListViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val billRepository: BillRepository,
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedIdentityId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    val bills: StateFlow<List<BillItem>> = _selectedIdentityId.flatMapLatest { identityId ->
        if (identityId == null) {
            flowOf(emptyList())
        } else {
            billRepository.getBillsForIdentity(identityId)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectIdentity(identityId: Long?) {
        _selectedIdentityId.value = identityId
    }

    fun syncBills(identityId: Long, onResult: (SyncResult) -> Unit) {
        viewModelScope.launch {
            val result = syncIdentityBillsUseCase(identityId)
            onResult(result)
        }
    }
}
