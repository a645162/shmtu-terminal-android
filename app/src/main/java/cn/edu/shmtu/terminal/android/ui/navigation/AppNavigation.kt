package cn.edu.shmtu.terminal.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cn.edu.shmtu.terminal.android.ui.account.AddAccountScreen
import cn.edu.shmtu.terminal.android.ui.account.IdentityDetailScreen
import cn.edu.shmtu.terminal.android.ui.account.IdentityListScreen
import cn.edu.shmtu.terminal.android.ui.account.LoginScreen
import cn.edu.shmtu.terminal.android.ui.bill.BillDetailScreen
import cn.edu.shmtu.terminal.android.ui.bill.BillListScreen
import cn.edu.shmtu.terminal.android.ui.home.HomeScreen
import cn.edu.shmtu.terminal.android.ui.settings.AboutScreen
import cn.edu.shmtu.terminal.android.ui.settings.SettingsScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(
                onNavigateToBill = { navController.navigate(TopLevelDestination.BILL.route) },
                onNavigateToAccount = { navController.navigate(TopLevelDestination.ACCOUNT.route) }
            )
        }
        composable(TopLevelDestination.BILL.route) {
            BillListScreen(
                onBillClick = { billId ->
                    navController.navigate("bill_detail/$billId")
                }
            )
        }
        composable(TopLevelDestination.ACCOUNT.route) {
            IdentityListScreen(
                onIdentityClick = { identityId ->
                    navController.navigate("identity_detail/$identityId")
                },
                onAddAccount = { identityId ->
                    navController.navigate("add_account/$identityId")
                }
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(
                onNavigateToAbout = {
                    navController.navigate("about")
                }
            )
        }
        composable("about") {
            AboutScreen(
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
                onLoginAccount = { accountId ->
                    navController.navigate("login/$accountId")
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
                    navController.popBackStack(TopLevelDestination.ACCOUNT.route, inclusive = false)
                }
            )
        }
    }
}
