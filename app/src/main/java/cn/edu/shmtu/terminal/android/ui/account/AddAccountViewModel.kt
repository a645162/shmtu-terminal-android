package cn.edu.shmtu.terminal.android.ui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.usecase.account.AddAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val addAccountUseCase: AddAccountUseCase,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val identityId: Long = savedStateHandle.get<String>("identityId")?.toLongOrNull() ?: 0L

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState

    fun addAccount(label: String, userId: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, errorMessage = null)

            val allAccounts = accountRepository.getAllAccounts()
            if (allAccounts.any { it.userId == userId }) {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    errorMessage = "学号 $userId 已存在，不允许重复添加"
                )
                return@launch
            }

            val id = addAccountUseCase(identityId, label, userId, password)
            if (id > 0) {
                _uiState.value = _uiState.value.copy(isChecking = false, success = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    errorMessage = "添加失败，请重试"
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun resetSuccess() {
        _uiState.value = AddAccountUiState()
    }
}

data class AddAccountUiState(
    val isChecking: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null
)
