package cn.edu.shmtu.terminal.android.ui.bill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cn.edu.shmtu.terminal.android.domain.model.BillItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BillDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val billId: Long = savedStateHandle.get<String>("billId")?.toLongOrNull() ?: 0L

    var billValue by mutableStateOf<BillItem?>(null)
        private set
}
