package cn.edu.shmtu.terminal.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cn.edu.shmtu.terminal.android.ui.navigation.AppNavigation
import cn.edu.shmtu.terminal.android.ui.navigation.AppShellViewModel
import cn.edu.shmtu.terminal.android.ui.navigation.TopLevelDestination
import cn.edu.shmtu.terminal.android.ui.p2p.P2PViewModel
import cn.edu.shmtu.terminal.android.ui.p2p.PairRequestDialog
import cn.edu.shmtu.terminal.android.ui.settings.SettingsViewModelWrapper
import cn.edu.shmtu.terminal.android.ui.theme.ShmtuterminalandroidTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShmtuterminalandroidApp()
        }
    }
}

@Composable
fun ShmtuterminalandroidApp() {
    val context = LocalContext.current
    val settingsWrapper: SettingsViewModelWrapper = hiltViewModel()
    val themeMode by settingsWrapper.featureStore.themeMode.collectAsState()
    val p2pAutoAccept by settingsWrapper.settingsDataStore.p2pAutoAccept.collectAsState(initial = false)
    val p2pAutoStart by settingsWrapper.settingsDataStore.p2pAutoStart.collectAsState(initial = false)
    val shellViewModel: AppShellViewModel = hiltViewModel()
    val p2pViewModel: P2PViewModel = hiltViewModel()
    val currentIdentity by shellViewModel.currentIdentity.collectAsState()
    val p2pUiState by p2pViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val topLevelDestinations = TopLevelDestination.entries
    val currentDestination = topLevelDestinations.find { it.route == currentRoute }
    val configuration = LocalConfiguration.current
    val wideLayout = configuration.screenWidthDp >= 900
    val meLabel = currentIdentity?.remark?.ifBlank { currentIdentity?.username ?: "当前身份" }
        ?: "当前身份"
    val currentPairRequest = p2pUiState.pairRequests.firstOrNull()

    val permissionPrefs = remember {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionPrefs.edit().putBoolean("notification_permission_requested", true).apply()
        permissionPrefs.edit().putBoolean("notification_permission_granted", granted).apply()
    }

    ShmtuterminalandroidTheme(themeMode = themeMode) {
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alreadyGranted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                val requested = permissionPrefs.getBoolean("notification_permission_requested", false)
                if (!alreadyGranted && !requested) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        LaunchedEffect(p2pAutoStart) {
            if (p2pAutoStart) {
                p2pViewModel.startServer()
            }
        }

        LaunchedEffect(p2pAutoAccept, currentPairRequest?.remoteAddr, currentPairRequest?.timestamp) {
            if (p2pAutoAccept && currentPairRequest != null) {
                p2pViewModel.acceptPairing(currentPairRequest.remoteAddr)
            }
        }

        if (!p2pAutoAccept && currentPairRequest != null) {
            PairRequestDialog(
                deviceName = currentPairRequest.remoteDevice,
                pairCode = currentPairRequest.pairCode,
                remoteAddr = currentPairRequest.remoteAddr,
                onAccept = {
                    p2pViewModel.acceptPairing(currentPairRequest.remoteAddr)
                },
                onReject = {
                    p2pViewModel.rejectPairing(currentPairRequest.remoteAddr)
                }
            )
        }

        if (wideLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                ) {
                    topLevelDestinations.forEach { destination ->
                        val label = if (destination == TopLevelDestination.ME) meLabel else destination.label
                        NavigationRailItem(
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            selected = destination == currentDestination,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    androidx.compose.material3.MaterialTheme.colorScheme.surface,
                                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest
                                )
                            )
                        )
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { innerPadding ->
                        AppNavigation(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    androidx.compose.material3.MaterialTheme.colorScheme.surface,
                                    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest
                                )
                            )
                        )
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets(0, 0, 0, 0)
                    ) { innerPadding ->
                        AppNavigation(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }

                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val label = if (destination == TopLevelDestination.ME) meLabel else destination.label
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = label
                                )
                            },
                            label = { Text(label) },
                            selected = destination == currentDestination,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
