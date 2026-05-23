package cn.edu.shmtu.terminal.android.ui.account

import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import cn.edu.shmtu.terminal.android.domain.repository.SyncResult
import cn.edu.shmtu.terminal.android.domain.usecase.bill.SyncIdentityBillsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IdentityListUiState(
    val syncingIdentityId: Long? = null,
    val syncMessage: String? = null
)

@HiltViewModel
class IdentityListViewModel @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val accountRepository: AccountRepository,
    private val syncIdentityBillsUseCase: SyncIdentityBillsUseCase
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(IdentityListUiState())
    val uiState: StateFlow<IdentityListUiState> = _uiState.asStateFlow()

    private val _editingIdentity = MutableStateFlow<Identity?>(null)
    val editingIdentity: StateFlow<Identity?> = _editingIdentity.asStateFlow()

    private val _editingDetailsIdentity = MutableStateFlow<Identity?>(null)
    val editingDetailsIdentity: StateFlow<Identity?> = _editingDetailsIdentity.asStateFlow()

    private val _deletingIdentity = MutableStateFlow<Identity?>(null)
    val deletingIdentity: StateFlow<Identity?> = _deletingIdentity.asStateFlow()

    fun syncIdentityBills(identityId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncingIdentityId = identityId, syncMessage = null)

            val result = syncIdentityBillsUseCase(identityId)

            val identity = identityRepository.getIdentityById(identityId)
            val name = identity?.name ?: "身份"

            _uiState.value = _uiState.value.copy(
                syncingIdentityId = null,
                syncMessage = if (result.success)
                    "$name: 同步成功，新增 ${result.newCount} 条记录"
                else
                    "$name: 同步失败 - ${result.errorMessage}"
            )
        }
    }

    fun clearSyncMessage() {
        _uiState.value = _uiState.value.copy(syncMessage = null)
    }

    fun addIdentity(name: String? = null) {
        viewModelScope.launch {
            val currentCount = identities.value.size
            val resolvedName = name?.takeIf { it.isNotBlank() } ?: "身份${currentCount + 1}"
            identityRepository.addIdentity(name = resolvedName)
        }
    }

    fun startEditIdentity(identity: Identity) {
        _editingIdentity.value = identity
    }

    fun updateIdentity(id: Long, name: String, birthday: String = "", enrollmentDate: String = "", graduationDate: String = "") {
        viewModelScope.launch {
            identityRepository.updateIdentity(id, name, birthday, enrollmentDate, graduationDate)
            _editingIdentity.value = null
        }
    }

    fun cancelEdit() {
        _editingIdentity.value = null
    }

    fun startEditDetails(identity: Identity) {
        _editingDetailsIdentity.value = identity
    }

    fun updateIdentityDetails(id: Long, name: String, birthday: String, enrollmentDate: String, graduationDate: String) {
        viewModelScope.launch {
            identityRepository.updateIdentity(id, name, birthday, enrollmentDate, graduationDate)
            _editingDetailsIdentity.value = null
        }
    }

    fun cancelEditDetails() {
        _editingDetailsIdentity.value = null
    }

    fun startDeleteIdentity(identity: Identity) {
        _deletingIdentity.value = identity
    }

    fun deleteIdentity(id: Long) {
        viewModelScope.launch {
            identityRepository.deleteIdentity(id)
            _deletingIdentity.value = null
        }
    }

    fun cancelDelete() {
        _deletingIdentity.value = null
    }
}
