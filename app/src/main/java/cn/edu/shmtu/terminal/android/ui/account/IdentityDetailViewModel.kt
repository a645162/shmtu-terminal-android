package cn.edu.shmtu.terminal.android.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
import cn.edu.shmtu.terminal.android.domain.usecase.account.DeleteAccountUseCase
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncIdentityBillsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IdentityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val identityRepository: IdentityRepository,
    private val accountRepository: AccountRepository,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase
) : ViewModel() {

    private val identityId: Long = savedStateHandle.get<String>("identityId")?.toLongOrNull() ?: 0L

    val accounts: StateFlow<List<Account>> = if (identityId == 0L) {
        flowOf(emptyList<Account>()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    } else {
        accountRepository.getAccountsByIdentity(identityId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    suspend fun getIdentity(): Identity? {
        return identityRepository.getIdentityById(identityId)
    }

    fun syncBills(onResult: (SyncResult) -> Unit) {
        viewModelScope.launch {
            val result = syncIdentityBillsUseCase(identityId)
            onResult(result)
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            deleteAccountUseCase(accountId, identityId)
        }
    }
}
