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
import androidx.compose.material3.Slider
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
fun SyncSettingsScreen(
    onBack: () -> Unit
) {
    val store = LocalFeatureStore.current
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(if (isWide) 32.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingCard("默认同步页数上限: ${store.syncMaxPages.value}") { Slider(value = store.syncMaxPages.value.toFloat(), onValueChange = { store.setSyncMaxPages(it.toInt()) }, valueRange = 10f..500f) }
            SettingCard("提前停止阈值: ${store.syncEarlyStop.value}") { Slider(value = store.syncEarlyStop.value.toFloat(), onValueChange = { store.setSyncEarlyStop(it.toInt()) }, valueRange = 1f..20f) }
            SettingSwitch("跳过已毕业账号同步", store.syncSkipGraduated.value) { store.setSyncSkipGraduated(it) }
            SettingSwitch("同步后自动合并", store.syncAutoMerge.value) { store.setSyncAutoMerge(it) }
            SettingSwitch("启用定时账单同步", store.autoSyncEnabled.value) { store.setAutoSyncEnabled(it) }
            if (store.autoSyncEnabled.value) {
                SettingCard("定时同步间隔(分钟): ${store.autoSyncInterval.value}") { Slider(value = store.autoSyncInterval.value.toFloat(), onValueChange = { store.setAutoSyncInterval(it.toInt()) }, valueRange = 5f..1440f) }
                SettingCard("定时同步范围: ${store.autoSyncRange.value}") { Text("可选: week / half_month / month / half_year / year / all", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
