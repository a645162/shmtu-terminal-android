package cn.edu.shmtu.terminal.android.ui.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.Identity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    onManageIdentities: () -> Unit,
    onIdentityDetail: (Long) -> Unit,
    viewModel: MeViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()
    val currentIdentity by viewModel.currentIdentity.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("我") }) }
    ) { innerPadding ->
        if (identities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有身份", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(onClick = onManageIdentities) {
                        Text("去创建身份")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CurrentIdentityCard(
                        identity = currentIdentity,
                        totalIdentities = identities.size,
                        onManageIdentities = onManageIdentities,
                        onIdentityDetail = { currentIdentity?.let { onIdentityDetail(it.id) } }
                    )
                }

                item {
                    Text(
                        "切换身份",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(identities, key = { it.id }) { identity ->
                    SwitchIdentityCard(
                        identity = identity,
                        isCurrent = currentIdentity?.id == identity.id,
                        onSwitch = { viewModel.switchIdentity(identity.id) },
                        onOpenDetail = { onIdentityDetail(identity.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentIdentityCard(
    identity: Identity?,
    totalIdentities: Int,
    onManageIdentities: () -> Unit,
    onIdentityDetail: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IdentityAvatar(identity?.remark?.ifBlank { identity.username } ?: "?")
                Column(modifier = Modifier.weight(1f)) {
                    Text("当前身份", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        identity?.remark?.ifBlank { identity.username } ?: "未选择",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "${identity?.accountCount ?: 0} 个账号 · 共 $totalIdentities 个身份",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onIdentityDetail, enabled = identity != null) {
                    Text("进入详情")
                }
                OutlinedButton(onClick = onManageIdentities) {
                    Text("管理身份")
                }
            }
        }
    }
}

@Composable
private fun SwitchIdentityCard(
    identity: Identity,
    isCurrent: Boolean,
    onSwitch: () -> Unit,
    onOpenDetail: () -> Unit
) {
    ElevatedCard(
        onClick = if (isCurrent) onOpenDetail else onSwitch,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        ListItem(
            leadingContent = { IdentityAvatar(identity.remark.ifBlank { identity.username }) },
            headlineContent = { Text(identity.remark.ifBlank { identity.username }) },
            supportingContent = { Text("${identity.accountCount} 个账号") },
            trailingContent = {
                Text(
                    if (isCurrent) "当前" else "切换",
                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                )
            }
        )
    }
}

@Composable
private fun IdentityAvatar(label: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
