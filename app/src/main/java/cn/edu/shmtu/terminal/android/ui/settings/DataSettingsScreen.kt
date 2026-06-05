package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.shmtu.terminal.android.data.dedupe.BillDedupeRepository
import cn.edu.shmtu.terminal.android.domain.model.Account
import cn.edu.shmtu.terminal.android.domain.repository.AccountRepository
import cn.edu.shmtu.terminal.android.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataSettingsViewModel @Inject constructor(
    private val dedupeRepository: BillDedupeRepository,
    identityRepository: IdentityRepository,
    accountRepository: AccountRepository
) : ViewModel() {
    val currentIdentityId: StateFlow<Long?> = identityRepository.getCurrentIdentityId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val identities: StateFlow<List<cn.edu.shmtu.terminal.android.domain.model.Identity>> =
        identityRepository.getAllIdentities()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 当前身份下的账号列表;当前身份切换时自动重订阅。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val accounts: StateFlow<List<Account>> = currentIdentityId
        .flatMapLatest { identityId ->
            if (identityId == null) flowOf(emptyList())
            else accountRepository.getAccountsByIdentity(identityId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun dedupeIdentity(): Pair<Int, Int> {
        val id = currentIdentityId.value
            ?: error("当前没有可用身份，无法执行去重")
        return dedupeRepository.dedupeIdentity()
            .also { _ -> /* id 仅为避免编译器警告 */ }
    }

    suspend fun dedupeAccount(identityId: Long): Pair<Int, Int> =
        dedupeRepository.dedupeAccount(identityId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    dedupeRepository: BillDedupeRepository,
    embedded: Boolean = false,
    viewModel: DataSettingsViewModel = hiltViewModel()
) {
    // 保留入参以兼容 SettingsScreen.kt:329 的旧调用点（实际去重走 viewModel 持有的 dedupeRepository）
    @Suppress("UNUSED_VARIABLE") val legacyRepo = dedupeRepository
    val scope = rememberCoroutineScope()
    val identities by viewModel.identities.collectAsState()
    val currentIdentityId by viewModel.currentIdentityId.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    var identityStatus by remember { mutableStateOf("尚未执行") }
    var accountStatus by remember { mutableStateOf("尚未执行") }
    var running by remember { mutableStateOf(false) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var accountExpanded by remember { mutableStateOf(false) }

    SettingsDetailScreen(
        title = "数据设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("身份级去重")
            Text(
                "针对当前身份的合并账单，按 transactionNo 去重并保留最早记录。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                identityStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                enabled = !running && currentIdentityId != null,
                onClick = {
                    scope.launch {
                        running = true
                        try {
                            val (kept, removed) = viewModel.dedupeIdentity()
                            identityStatus = "完成：保留 $kept 条，删除 $removed 条重复记录"
                        } catch (e: Exception) {
                            identityStatus = "失败：${e.message}"
                        } finally {
                            running = false
                        }
                    }
                }
            ) {
                Text(if (running) "处理中..." else "执行身份级去重")
            }
        }

        SettingsCard(emphasized = true) {
            Text("账号级去重")
            Text(
                "对指定账号的账单按 transactionNo 去重并保留最早记录。需先在「我」页面切换到目标身份。",
                style = MaterialTheme.typography.bodyMedium
            )

            val currentIdentity = identities.find { it.id == currentIdentityId }
            Text(
                if (currentIdentity != null) {
                    "当前身份：${displayName(currentIdentity)}（ID #${currentIdentity.id}）"
                } else {
                    "尚未选择身份。请在「我」页面添加或切换身份后再回来执行账号级去重。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (accounts.isEmpty()) {
                Text(
                    "当前身份下还没有账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val selectedAccountLabel = accounts
                    .find { it.id == selectedAccountId }
                    ?.let { "${it.label}（ID #${it.id}）" }
                    ?: "请选择要执行去重的账号"

                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = !accountExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedAccountLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("目标账号") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.label}（ID #${account.id}）") },
                                onClick = {
                                    selectedAccountId = account.id
                                    accountExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Text(
                accountStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                enabled = !running && currentIdentityId != null && selectedAccountId != null,
                onClick = {
                    val identityId = currentIdentityId ?: return@Button
                    scope.launch {
                        running = true
                        try {
                            val (kept, removed) = viewModel.dedupeAccount(identityId)
                            val acctLabel = accounts.find { it.id == selectedAccountId }
                                ?.let { "${it.label}（ID #${it.id}）" } ?: "账号 #$selectedAccountId"
                            accountStatus = "完成（$acctLabel）：保留 $kept 条，删除 $removed 条重复记录"
                        } catch (e: Exception) {
                            accountStatus = "失败：${e.message}"
                        } finally {
                            running = false
                        }
                    }
                }
            ) {
                Text(if (running) "处理中..." else "执行账号级去重")
            }
        }
    }
}

private fun displayName(identity: cn.edu.shmtu.terminal.android.domain.model.Identity): String =
    identity.remark.ifBlank { identity.username }
