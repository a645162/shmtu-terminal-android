package cn.edu.shmtu.terminal.android.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IdentityListViewModel @Inject constructor(
    private val identityRepository: IdentityRepository
) : ViewModel() {

    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addIdentity(name: String) {
        viewModelScope.launch {
            identityRepository.addIdentity(name)
        }
    }

    fun deleteIdentity(id: Long) {
        viewModelScope.launch {
            identityRepository.deleteIdentity(id)
        }
    }
}
