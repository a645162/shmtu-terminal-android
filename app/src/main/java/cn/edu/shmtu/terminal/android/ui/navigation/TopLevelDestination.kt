package cn.edu.shmtu.terminal.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME("home", "首页", Icons.Outlined.Home),
    BILL("bill", "账单", Icons.AutoMirrored.Outlined.ReceiptLong),
    FEATURES("features", "功能大全", Icons.Outlined.Widgets),
    ACCOUNT("account", "账号", Icons.Outlined.AccountCircle),
    SETTINGS("settings", "设置", Icons.Outlined.Settings)
}
