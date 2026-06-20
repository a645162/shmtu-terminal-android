package cn.edu.shmtu.terminal.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.edu.shmtu.terminal.android.data.dedupe.BillDedupeRepository
import cn.edu.shmtu.terminal.android.data.webserver.SettingsDataStoreWebExt
import cn.edu.shmtu.terminal.android.data.local.datastore.SettingsDataStore
import cn.edu.shmtu.terminal.android.data.sync.BillRulesManager
import cn.edu.shmtu.terminal.android.ui.cloud.CloudBackupScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    featureStore: FeatureSettingsStore,
    rulesManager: BillRulesManager,
    dedupeRepository: BillDedupeRepository,
    settingsDataStore: SettingsDataStore,
    webServerSettings: SettingsDataStoreWebExt,
    onBack: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToOcrSettings: () -> Unit,
    onNavigateToNotificationSettings: () -> Unit,
    initialTab: String? = null
) {
    CompositionLocalProvider(LocalFeatureStore provides featureStore) {
        val groups = remember { settingsGroups() }
        val twoPane = isSettingsTwoPane()
        var selectedKey by rememberSaveable { mutableStateOf(initialTab ?: groups.firstOrNull()?.key) }
        var phoneDetailKey by rememberSaveable { mutableStateOf<String?>(if (!twoPane && initialTab != null) initialTab else null) }
        val phoneListState = rememberLazyListState()

        BackHandler(enabled = !twoPane && phoneDetailKey != null) {
            phoneDetailKey = null
        }

        if (twoPane) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("设置") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
            ) { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SettingsSidebar(
                        groups = groups,
                        selectedKey = selectedKey,
                        onSelect = { selectedKey = it },
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                    )
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 1.dp,
                        shadowElevation = 0.dp
                    ) {
                        AnimatedContent(
                            targetState = selectedKey,
                            transitionSpec = {
                                settingsTabletTransition()
                            },
                            label = "settings_tablet_detail"
                        ) { detailKey ->
                            DetailFor(
                                key = detailKey,
                                embedded = true,
                                onBack = onBack,
                                rulesManager = rulesManager,
                                dedupeRepository = dedupeRepository,
                                settingsDataStore = settingsDataStore,
                                webServerSettings = webServerSettings,
                                onNavigateToAbout = onNavigateToAbout,
                                onNavigateToOcrSettings = onNavigateToOcrSettings,
                                onNavigateToNotificationSettings = onNavigateToNotificationSettings
                            )
                        }
                    }
                }
            }
        } else {
            AnimatedContent(
                targetState = phoneDetailKey,
                transitionSpec = {
                    settingsPhoneTransition(targetState != null)
                },
                label = "settings_phone_page"
            ) { detailKey ->
                if (detailKey != null) {
                    DetailFor(
                        key = detailKey,
                        embedded = false,
                        onBack = { phoneDetailKey = null },
                        rulesManager = rulesManager,
                        dedupeRepository = dedupeRepository,
                        settingsDataStore = settingsDataStore,
                                webServerSettings = webServerSettings,
                        onNavigateToAbout = onNavigateToAbout,
                        onNavigateToOcrSettings = onNavigateToOcrSettings,
                        onNavigateToNotificationSettings = onNavigateToNotificationSettings
                    )
                } else {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("设置") },
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                    }
                                }
                            )
                        }
                    ) { inner ->
                        LazyColumn(
                            state = phoneListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                SettingsHero()
                            }
                            items(groups) { group ->
                                SettingsGroupCard(
                                    group = group,
                                    selected = false,
                                    onClick = { phoneDetailKey = group.key }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun settingsPhoneTransition(enteringDetail: Boolean): ContentTransform {
    val duration = 320
    val enterOffset: (Int) -> Int = { fullWidth -> if (enteringDetail) fullWidth / 5 else -fullWidth / 5 }
    val exitOffset: (Int) -> Int = { fullWidth -> if (enteringDetail) -fullWidth / 8 else fullWidth / 8 }
    return (slideInHorizontally(
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        initialOffsetX = enterOffset
    ) + fadeIn(animationSpec = tween(durationMillis = duration)))
        .togetherWith(
            slideOutHorizontally(
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                targetOffsetX = exitOffset
            ) + fadeOut(animationSpec = tween(durationMillis = duration - 40))
        )
}

private fun settingsTabletTransition(): ContentTransform {
    val duration = 240
    return (slideInHorizontally(
        animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
        initialOffsetX = { it / 18 }
    ) + fadeIn(animationSpec = tween(durationMillis = duration)))
        .togetherWith(
            slideOutHorizontally(
                animationSpec = tween(durationMillis = duration - 20, easing = FastOutSlowInEasing),
                targetOffsetX = { -it / 18 }
            ) + fadeOut(animationSpec = tween(durationMillis = duration - 40))
        )
}

private data class SettingsGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val accent: List<Color>
)

@Composable
private fun SettingsHero() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraLarge)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "设置工作台",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "按模块集中管理界面、同步、OCR、安全和数据行为。手机以单列流畅浏览，平板可在同屏直接调整。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSidebar(
    groups: List<SettingsGroup>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "选择左侧模块后，右侧立即显示可编辑内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(groups) { group ->
                SettingsGroupCard(
                    group = group,
                    selected = group.key == selectedKey,
                    onClick = { onSelect(group.key) }
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    group: SettingsGroup,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderStripeAlpha = if (selected) 1f else 0.72f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = if (selected) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(Brush.linearGradient(group.accent.map { it.copy(alpha = borderStripeAlpha) })),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = group.icon,
                    contentDescription = group.title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    group.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    group.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailFor(
    key: String?,
    embedded: Boolean,
    onBack: () -> Unit,
    rulesManager: BillRulesManager,
    dedupeRepository: BillDedupeRepository,
    settingsDataStore: SettingsDataStore,
    webServerSettings: SettingsDataStoreWebExt,
    onNavigateToAbout: () -> Unit,
    onNavigateToOcrSettings: () -> Unit,
    onNavigateToNotificationSettings: () -> Unit
) {
    when (key) {
        "appearance" -> AppearanceSettingsScreen(onBack = onBack, embedded = embedded)
        "bill_display" -> BillDisplaySettingsScreen(onBack = onBack, embedded = embedded)
        "home_chart" -> HomeChartSettingsScreen(onBack = onBack, embedded = embedded)
        "identity" -> IdentitySettingsScreen(onBack = onBack, embedded = embedded)
        "sync" -> SyncSettingsScreen(onBack = onBack, embedded = embedded)
        "security" -> SecuritySettingsScreen(onBack = onBack, embedded = embedded)
        "data" -> DataSettingsScreen(onBack = onBack, dedupeRepository = dedupeRepository, embedded = embedded)
        "cloud_backup" -> CloudBackupScreen(onBack = onBack, embedded = embedded)
        "classification" -> ClassificationSettingsScreen(onBack = onBack, rulesManager = rulesManager, embedded = embedded)
        "update" -> UpdateSettingsScreen(onBack = onBack, embedded = embedded)
        "debug" -> DebugSettingsScreen(onBack = onBack, embedded = embedded)
        "ocr" -> OcrSettingsScreen(onBack = onBack, embedded = embedded)
        "p2p" -> P2PSettingsScreen(onBack = onBack, embedded = embedded, settingsDataStore = settingsDataStore)
        "webserver" -> WebServerSettingsScreen(onBack = onBack, embedded = embedded, webServerSettings = webServerSettings)
        "notification" -> NotificationSettingsScreen(onBack = onBack, embedded = embedded)
        "about" -> AboutScreen(onBack = onBack, embedded = embedded)
        else -> SettingsDetailBody {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("选择一个设置分组", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "左侧选择后即可在这里直接查看和编辑对应选项。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun settingsGroups(): List<SettingsGroup> {
    return listOf(
        SettingsGroup(
            key = "appearance",
            title = "界面",
            subtitle = "主题、数字显示与整体观感",
            description = "调整应用的外观基调和统计数字精度。",
            icon = Icons.Filled.Brush,
            accent = listOf(Color(0xFF3E7BFA), Color(0xFF6DB9FF))
        ),
        SettingsGroup(
            key = "bill_display",
            title = "消费展示",
            subtitle = "原始类型与解析位置",
            description = "控制账单标题优先显示原始消费类型还是解析出的楼栋/窗口。",
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            accent = listOf(Color(0xFF7B5E57), Color(0xFFD7CCC8))
        ),
        SettingsGroup(
            key = "home_chart",
            title = "首页图表",
            subtitle = "趋势范围、分类区间",
            description = "控制首页默认展示的时间窗口和图表口径。",
            icon = Icons.Filled.Home,
            accent = listOf(Color(0xFF0B8F6A), Color(0xFF6FD3A6))
        ),
        SettingsGroup(
            key = "ocr",
            title = "验证码",
            subtitle = "识别模式、模型与服务地址",
            description = "管理本地 OCR 与远程识别的工作方式。",
            icon = Icons.Filled.Refresh,
            accent = listOf(Color(0xFFB5660B), Color(0xFFFFC56A))
        ),
        SettingsGroup(
            key = "sync",
            title = "同步",
            subtitle = "页数上限、自动同步与范围",
            description = "配置账单同步策略和自动化行为。",
            icon = Icons.Filled.Sync,
            accent = listOf(Color(0xFF2F6FCE), Color(0xFF87AFFF))
        ),
        SettingsGroup(
            key = "notification",
            title = "通知",
            subtitle = "通知类型、样式与 Webhook 转发",
            description = "管理应用通知、阈值和飞书/企业微信机器人转发。",
            icon = Icons.Filled.Notifications,
            accent = listOf(Color(0xFF6750A4), Color(0xFFB69DF8))
        ),
        SettingsGroup(
            key = "p2p",
            title = "点对点互传",
            subtitle = "设备名称、端口与自动启动",
            description = "配置局域网点对点传输参数。",
            icon = Icons.Filled.SwapHoriz,
            accent = listOf(Color(0xFFE65100), Color(0xFFFF9E40))
        ),
        SettingsGroup(
            key = "webserver",
            title = "远程访问",
            subtitle = "Web 服务与端口配置",
            description = "在局域网内通过浏览器访问账单数据。",
            icon = Icons.Filled.Public,
            accent = listOf(Color(0xFF1565C0), Color(0xFF5BA1FF))
        ),
        SettingsGroup(
            key = "data",
            title = "数据",
            subtitle = "去重与维护操作",
            description = "对身份级和账号级账单进行维护处理。",
            icon = Icons.Filled.Storage,
            accent = listOf(Color(0xFF6A55E6), Color(0xFFB49CFF))
        ),
        SettingsGroup(
            key = "cloud_backup",
            title = "云备份",
            subtitle = "WebDAV / Google Drive / OneDrive",
            description = "备份数据到云端，支持自动定时备份、加密和远程恢复。",
            icon = Icons.Filled.CloudDownload,
            accent = listOf(Color(0xFF6750A4), Color(0xFFB69DF8))
        ),
        SettingsGroup(
            key = "classification",
            title = "分类规则",
            subtitle = "规则来源与同步状态",
            description = "从 GitHub 拉取账单分类规则并写入本地。",
            icon = Icons.Filled.CloudDownload,
            accent = listOf(Color(0xFF0081A7), Color(0xFF76D4F2))
        ),
        SettingsGroup(
            key = "security",
            title = "安全",
            subtitle = "启动保护与密码状态",
            description = "控制应用启动时的密码校验行为。",
            icon = Icons.Filled.Security,
            accent = listOf(Color(0xFFAA334B), Color(0xFFFF98A9))
        ),
        SettingsGroup(
            key = "identity",
            title = "身份",
            subtitle = "启动默认身份",
            description = "配置应用启动时优先进入哪个身份。",
            icon = Icons.Filled.Person,
            accent = listOf(Color(0xFF6B5CA5), Color(0xFFA7A0E8))
        ),
        SettingsGroup(
            key = "update",
            title = "更新",
            subtitle = "自动检查与手动查看版本",
            description = "决定是否自动检查版本，以及检查频率。",
            icon = Icons.Filled.SystemUpdate,
            accent = listOf(Color(0xFF3B7C48), Color(0xFF8FD48B))
        ),
        SettingsGroup(
            key = "debug",
            title = "调试",
            subtitle = "错误日志与问题记录",
            description = "手动写入调试日志，便于排查问题。",
            icon = Icons.Filled.BugReport,
            accent = listOf(Color(0xFF6C5B36), Color(0xFFE6C57C))
        ),
        SettingsGroup(
            key = "about",
            title = "关于",
            subtitle = "版本、作者与开源信息",
            description = "查看当前应用的基础元信息。",
            icon = Icons.Filled.Info,
            accent = listOf(Color(0xFF6B5CA5), Color(0xFFA7A0E8))
        )
    )
}
