package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onBack: () -> Unit
) {
    val store = LocalFeatureStore.current
    val enabled = store.enableStartupProtection.value
    val hash = store.startupPasswordHash.value
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("安全设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(if (isWide) 32.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("启动保护", style = MaterialTheme.typography.titleMedium)
                    Text("启用后, 每次打开 App 都需要输入密码", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = enabled, onCheckedChange = { store.setEnableStartupProtection(it) })
                }
            }
            if (enabled) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("启动密码哈希", style = MaterialTheme.typography.titleMedium)
                        Text(if (hash.isNullOrBlank()) "未设置" else "已设置 (${hash?.take(8) ?: ""}...)", style = MaterialTheme.typography.bodyMedium)
                        Text("提示: StartupLockActivity 拦截在主流程, 此处仅展示状态。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
