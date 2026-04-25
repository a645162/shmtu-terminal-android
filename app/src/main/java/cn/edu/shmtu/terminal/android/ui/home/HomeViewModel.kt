package cn.edu.shmtu.terminal.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.model.LoginStatus
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val accountRepository: AccountRepository,
    private val billRepository: BillRepository
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
