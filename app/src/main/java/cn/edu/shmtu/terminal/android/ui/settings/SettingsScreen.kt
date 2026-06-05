package cn.edu.shmtu.terminal.android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    featureStore: FeatureSettingsStore,
    rulesManager: BillRulesManager,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToOcrSettings: () -> Unit,
    onDedupeIdentity: suspend () -> Pair<Int, Int>,
    onDedupeAccount: suspend (Long) -> Pair<Int, Int>
) {
    CompositionLocalProvider(LocalFeatureStore provides featureStore) {
        val configuration = LocalConfiguration.current
        val isWide = configuration.screenWidthDp >= 600
        val groups = remember {
            listOf(
                SettingsGroup("界面", "主题/小数位", Icons.Filled.Brush, "appearance"),
                SettingsGroup("首页图表", "趋势/分类范围", Icons.Filled.Home, "home_chart"),
                SettingsGroup("验证码", "识别模式/重试", Icons.Filled.Refresh, "ocr"),
                SettingsGroup("同步", "页数/定时", Icons.Filled.Sync, "sync"),
                SettingsGroup("数据", "去重/快照", Icons.Filled.Storage, "data"),
                SettingsGroup("分类规则", "GitHub 同步", Icons.Filled.CloudDownload, "classification"),
                SettingsGroup("安全", "启动保护", Icons.Filled.Security, "security"),
                SettingsGroup("更新", "检查更新", Icons.Filled.SystemUpdate, "update"),
                SettingsGroup("调试", "错误日志", Icons.Filled.BugReport, "debug"),
                SettingsGroup("关于", "版本/致谢", Icons.Filled.Person, "about"),
            )
        }
        var selectedKey by remember { mutableStateOf<String?>(null) }
        var showPhoneModal by remember { mutableStateOf(false) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } }
                )
            }
        ) { inner ->
            Row(modifier = Modifier.fillMaxSize().padding(inner)) {
                if (isWide) {
                    NavigationRail(modifier = Modifier.width(180.dp)) {
                        groups.forEach { g ->
                            NavigationRailItem(
                                selected = selectedKey == g.key,
                                onClick = { selectedKey = g.key },
                                icon = { Icon(g.icon, contentDescription = g.title) },
                                label = { Text(g.title) }
                            )
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        DetailFor(key = selectedKey, onBack = onBack, rulesManager = rulesManager, onNavigateToAbout = onNavigateToAbout, onNavigateToOcrSettings = onNavigateToOcrSettings, onDedupeIdentity = onDedupeIdentity, onDedupeAccount = onDedupeAccount)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(groups) { g ->
                            SettingsGroupRow(g) { selectedKey = g.key; showPhoneModal = true }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
        if (!isWide && showPhoneModal && selectedKey != null) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showPhoneModal = false; selectedKey = null }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    DetailFor(
                        key = selectedKey,
                        onBack = { showPhoneModal = false; selectedKey = null },
                        rulesManager = rulesManager,
                        onNavigateToAbout = { showPhoneModal = false; selectedKey = null; onNavigateToAbout() },
                        onNavigateToOcrSettings = { showPhoneModal = false; selectedKey = null; onNavigateToOcrSettings() },
                        onDedupeIdentity = onDedupeIdentity,
                        onDedupeAccount = onDedupeAccount
                    )
                }
            }
        }
    }
}

private data class SettingsGroup(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val key: String
)

@Composable
private fun SettingsGroupRow(g: SettingsGroup, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(g.icon, contentDescription = g.title, modifier = Modifier.size(28.dp))
            Box(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium)
                Text(g.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ArrowForwardIos, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DetailFor(
    key: String?,
    onBack: () -> Unit,
    rulesManager: BillRulesManager,
    onNavigateToAbout: () -> Unit,
    onNavigateToOcrSettings: () -> Unit,
    onDedupeIdentity: suspend () -> Pair<Int, Int>,
    onDedupeAccount: suspend (Long) -> Pair<Int, Int>
) {
    when (key) {
        "appearance" -> AppearanceSettingsScreen(onBack = onBack)
        "home_chart" -> HomeChartSettingsScreen(onBack = onBack)
        "sync" -> SyncSettingsScreen(onBack = onBack)
        "security" -> SecuritySettingsScreen(onBack = onBack)
        "data" -> DataSettingsScreen(onBack = onBack, onDedupeIdentity = onDedupeIdentity, onDedupeAccount = onDedupeAccount)
        "classification" -> ClassificationSettingsScreen(onBack = onBack, rulesManager = rulesManager)
        "update" -> UpdateSettingsScreen(onBack = onBack)
        "debug" -> DebugSettingsScreen(onBack = onBack)
        "ocr" -> onNavigateToOcrSettings()
        "about" -> onNavigateToAbout()
        else -> Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("请从左侧选择一个分组", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
