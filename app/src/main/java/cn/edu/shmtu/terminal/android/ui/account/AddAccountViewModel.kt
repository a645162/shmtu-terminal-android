package cn.edu.shmtu.terminal.android.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.usecase.account.AddAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addAccountUseCase: AddAccountUseCase,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val identityId: Long = savedStateHandle.get<String>("identityId")?.toLongOrNull() ?: 0L

    private var _newAccountId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    val newAccountId = _newAccountId

    fun addAccount(label: String, userId: String, password: String) {
        viewModelScope.launch {
            val id = addAccountUseCase(identityId, label, userId, password)
            _newAccountId.value = id
        }
    }
}
