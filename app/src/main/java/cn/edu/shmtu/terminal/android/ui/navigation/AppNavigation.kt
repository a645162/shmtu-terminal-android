package cn.edu.shmtu.terminal.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.ui.account.AddAccountScreen
import cn.edu.shmtu.terminal.android.ui.account.IdentityDetailScreen
import cn.edu.shmtu.terminal.android.ui.account.IdentityListScreen
import cn.edu.shmtu.terminal.android.ui.account.LoginScreen
import cn.edu.shmtu.terminal.android.ui.bill.BillDetailScreen
import cn.edu.shmtu.terminal.android.ui.datatransfer.DataTransferScreen
import cn.edu.shmtu.terminal.android.ui.hotwater.HotWaterScreen
import cn.edu.shmtu.terminal.android.ui.bill.BillListScreen
import cn.edu.shmtu.terminal.android.ui.features.FeatureHubScreen
import cn.edu.shmtu.terminal.android.ui.home.HomeScreen
import cn.edu.shmtu.terminal.android.ui.me.MeScreen
import cn.edu.shmtu.terminal.android.ui.p2p.P2PScreen
import cn.edu.shmtu.terminal.android.ui.p2p.QRScanScreen
import cn.edu.shmtu.terminal.android.ui.statistics.BillStatisticsScreen
import cn.edu.shmtu.terminal.android.ui.settings.AboutScreen
import cn.edu.shmtu.terminal.android.ui.settings.AppearanceSettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.ClassificationSettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.DataSettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.DebugSettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.HomeChartSettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.LocalFeatureStore
import cn.edu.shmtu.terminal.android.ui.settings.OcrSettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.SecuritySettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.SettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.SettingsViewModelWrapper
import cn.edu.shmtu.terminal.android.ui.settings.SyncSettingsScreen
import cn.edu.shmtu.terminal.android.ui.settings.UpdateSettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val wrapper: SettingsViewModelWrapper = hiltViewModel()
    CompositionLocalProvider(LocalFeatureStore provides wrapper.featureStore) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = modifier
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onNavigateToBill = { navController.navigate(TopLevelDestination.BILL.route) },
                    onNavigateToMe = { navController.navigate(TopLevelDestination.ME.route) },
                    onNavigateToStatistics = { navController.navigate("bill_statistics") },
                    onBillClick = { billId -> navController.navigate("bill_detail/$billId") }
                )
            }
            composable(TopLevelDestination.BILL.route) {
                BillListScreen(
                    onBillClick = { billId ->
                        navController.navigate("bill_detail/$billId")
                    }
                )
            }
            composable(TopLevelDestination.FEATURES.route) {
                FeatureHubScreen(
                    onNavigateToBillStatistics = { navController.navigate("bill_statistics") },
                    onNavigateToDataTransfer = { navController.navigate("data_transfer") },
                    onNavigateToP2P = { navController.navigate("p2p") }
                )
            }
            composable(TopLevelDestination.ME.route) {
                MeScreen(
                    onManageIdentities = { navController.navigate("identity_manager") },
                    onIdentityDetail = { identityId ->
                        navController.navigate("identity_detail/$identityId")
                    }
                )
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    featureStore = wrapper.featureStore,
                    rulesManager = wrapper.rulesManager,
                    dedupeRepository = wrapper.dedupeRepository,
                    settingsDataStore = wrapper.settingsDataStore,
                    onBack = { navController.popBackStack() },
                    onNavigateToAbout = { navController.navigate("about") },
                    onNavigateToOcrSettings = { navController.navigate("ocr_settings") }
                )
            }
            composable("settings/appearance") { AppearanceSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/home_chart") { HomeChartSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/security") { SecuritySettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/sync") { SyncSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/update") { UpdateSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("settings/debug") { DebugSettingsScreen(onBack = { navController.popBackStack() }) }
            composable("bill_statistics") {
                BillStatisticsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "hot_water/{accountId}",
                arguments = listOf(navArgument("accountId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getString("accountId")?.toLongOrNull() ?: return@composable
                HotWaterScreen(
                    accountId = accountId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("about") {
                AboutScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("ocr_settings") {
                OcrSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("data_transfer") {
                DataTransferScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("p2p") {
                P2PScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToQRScan = { navController.navigate("p2p_qr_scan") },
                    navController = navController
                )
            }
            composable("p2p_qr_scan") {
                QRScanScreen(
                    onQRScanned = { payload ->
                        // Store result as JSON string to avoid Parcelable/Serializable requirement
                        val payloadJson = cn.edu.shmtu.terminal.android.data.p2p.p2pJson
                            .encodeToString(
                                cn.edu.shmtu.terminal.android.data.p2p.QRPayload.serializer(),
                                payload
                            )
                        android.util.Log.d("P2PNav", "QRScan result stored, length=${payloadJson.length}")
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("p2p_qr_scan_result", payloadJson)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("identity_manager") {
                IdentityListScreen(
                    onIdentityClick = { identityId ->
                        navController.navigate("identity_detail/$identityId")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "identity_detail/{identityId}",
                arguments = listOf(navArgument("identityId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val identityId = backStackEntry.arguments?.getString("identityId")?.toLongOrNull() ?: return@composable
                IdentityDetailScreen(
                    identityId = identityId,
                    onAddAccount = { navController.navigate("add_account/$identityId") },
                    onHotWater = { accountId ->
                        navController.navigate("hot_water/$accountId")
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "add_account/{identityId}",
                arguments = listOf(navArgument("identityId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val identityId = backStackEntry.arguments?.getString("identityId")?.toLongOrNull() ?: return@composable
                AddAccountScreen(
                    identityId = identityId,
                    onAddSuccess = { message ->
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("account_add_message", message)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "bill_detail/{billId}",
                arguments = listOf(navArgument("billId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val billId = backStackEntry.arguments?.getString("billId")?.toLongOrNull() ?: return@composable
                BillDetailScreen(
                    billId = billId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                "login/{accountId}",
                arguments = listOf(navArgument("accountId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val accountId = backStackEntry.arguments?.getString("accountId")?.toLongOrNull() ?: return@composable
                LoginScreen(
                    accountId = accountId,
                    onBack = { navController.popBackStack() },
                    onLoginSuccess = {
                        navController.popBackStack(TopLevelDestination.ME.route, inclusive = false)
                    }
                )
            }
        }
    }
}
