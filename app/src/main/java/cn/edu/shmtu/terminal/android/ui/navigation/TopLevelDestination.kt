package cn.edu.shmtu.terminal.android.ui.navigation

import cn.edu.shmtu.terminal.android.R

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: Int
) {
    HOME("home", "首页", R.drawable.ic_home),
    BILL("bill", "账单", R.drawable.ic_bill),
    ACCOUNT("account", "账号", R.drawable.ic_person_add),
    SETTINGS("settings", "设置", R.drawable.ic_settings)
}
