package cn.edu.shmtu.terminal.android.ui.bill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.BillRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 账单详情 ViewModel - 对齐 Tauri 端 BillDetailDialog
 *
 * 从 BillRepository 加载指定 billId 的完整字段。
 * 备注编辑功能(目前只更新内存,后续可加 update_bill_notes use case 写回 DB)。
 */
@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val identityRepository: IdentityRepository,
    private val billRepository: BillRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val billId: Long = savedStateHandle.get<String>("billId")?.toLongOrNull() ?: 0L

    private val _bill = MutableStateFlow<BillItem?>(null)
    val bill: StateFlow<BillItem?> = _bill.asStateFlow()

    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private val _editingNotes = MutableStateFlow(false)
    val editingNotes: StateFlow<Boolean> = _editingNotes.asStateFlow()

    private val _sourceAccountLabel = MutableStateFlow("—")
    val sourceAccountLabel: StateFlow<String> = _sourceAccountLabel.asStateFlow()

    init {
        loadBill()
    }

    private fun loadBill() {
        viewModelScope.launch {
            val identityId = identityRepository.getCurrentIdentityId().first() ?: return@launch
            val list = billRepository.getBillsForIdentity(identityId).first()
            val found = list.firstOrNull { it.id == billId }
            _bill.value = found
            if (found != null) {
                _notes.value = found.notes.orEmpty()
                val account = accountRepository.getAccountById(found.accountId)
                _sourceAccountLabel.value = account?.userId
                    ?: found.accountLabel.ifBlank { found.accountId.toString() }
            }
        }
    }

    fun startEditNotes() {
        _editingNotes.value = true
    }

    fun cancelEditNotes() {
        _editingNotes.value = false
        _notes.value = _bill.value?.notes.orEmpty()
    }

    fun updateNotes(value: String) {
        _notes.value = value
    }

    fun saveNotes() {
        // 简化:Android 端暂不写回 DB(后续可加 update_bill_notes use case)
        _editingNotes.value = false
    }
}
