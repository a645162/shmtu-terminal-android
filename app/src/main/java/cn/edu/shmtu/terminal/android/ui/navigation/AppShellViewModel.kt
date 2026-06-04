package cn.edu.shmtu.terminal.android.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.Identity
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppShellViewModel @Inject constructor(
    identityRepository: IdentityRepository
) : ViewModel() {
    val currentIdentity: StateFlow<Identity?> = identityRepository.getCurrentIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
