package cn.edu.shmtu.terminal.android.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.domain.model.Identity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToBill: () -> Unit,
    onNavigateToAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val identities by viewModel.identities.collectAsState()

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("海事终端") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "身份总览",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${identities.size} 个身份",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToBill,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看账单")
                }
                OutlinedButton(
                    onClick = onNavigateToAccount,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("管理账号")
                }
            }
        }
    }
}
