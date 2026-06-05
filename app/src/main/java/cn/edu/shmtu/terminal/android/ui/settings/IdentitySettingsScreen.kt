package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
class IdentitySettingsViewModel @Inject constructor(
    private val store: FeatureSettingsStore,
    identityRepository: IdentityRepository
) : ViewModel() {
    val identityStartupMode: StateFlow<String> = store.identityStartupMode
    val defaultIdentityId: StateFlow<Long> = store.defaultIdentityId
    val identities: StateFlow<List<Identity>> = identityRepository.getAllIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStartupMode(mode: String) = store.setIdentityStartupMode(mode)
    fun setDefaultIdentityId(id: Long) = store.setDefaultIdentityId(id)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitySettingsScreen(
    onBack: () -> Unit,
    embedded: Boolean = false,
    viewModel: IdentitySettingsViewModel = hiltViewModel()
) {
    val mode by viewModel.identityStartupMode.collectAsState()
    val defaultId by viewModel.defaultIdentityId.collectAsState()
    val identities by viewModel.identities.collectAsState()
    val currentDefault = identities.find { it.id == defaultId }

    SettingsDetailScreen(
        title = "身份设置",
        onBack = onBack,
        embedded = embedded
    ) {
        SettingsCard {
            Text("启动时优先加载")
            Text(
                "决定应用启动后优先尝试进入哪个身份。若只有一个启用身份，会直接进入该身份。",
                style = MaterialTheme.typography.bodyMedium
            )
            val modeOptions = listOf(
                "last_used" to "上一次使用的身份",
                "configured_default" to "设置的默认身份"
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                modeOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = mode == value,
                        onClick = { viewModel.setStartupMode(value) },
                        label = { Text(label) }
                    )
                }
            }
            SettingsExampleBlock {
                SettingsExampleLine("上一次使用的身份", "如果你昨晚最后查看的是“研究生校园卡”，下次启动会优先回到它。")
                SettingsExampleLine("设置的默认身份", "即使你上次临时切到别的身份，下次启动仍固定进入你指定的默认身份。")
            }
        }

        SettingsCard(emphasized = mode == "configured_default") {
            Text("默认身份")
            Text(
                "选择后，每次启动（且启动模式为「设置的默认身份」）会直接进入该身份。",
                style = MaterialTheme.typography.bodyMedium
            )

            if (identities.isEmpty()) {
                Text(
                    "尚未创建任何身份。请先到「我」页面添加身份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var expanded by remember { mutableStateOf(false) }
                val selectedLabel = currentDefault
                    ?.let { "${displayName(it)}（ID #${it.id}）" }
                    ?: "请选择默认身份"

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("默认身份") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        identities.forEach { identity ->
                            DropdownMenuItem(
                                text = {
                                    Text("${displayName(identity)}（ID #${identity.id}）")
                                },
                                onClick = {
                                    viewModel.setDefaultIdentityId(identity.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        SettingsCard {
            Text("当前默认身份")
            Text(
                if (currentDefault != null) {
                    "${displayName(currentDefault)}（ID #${currentDefault.id}）"
                } else {
                    "尚未设置。可在「我 → 身份管理」中先添加身份，再回到此处指定默认。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingsExampleBlock {
                SettingsExampleLine("示例", "你可以把最常用的身份固定为默认，避免每次启动后都重新切换。")
            }
        }
    }
}

private fun displayName(identity: Identity): String =
    identity.remark.ifBlank { identity.username }
