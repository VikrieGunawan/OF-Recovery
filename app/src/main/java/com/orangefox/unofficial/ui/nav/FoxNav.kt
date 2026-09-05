package com.orangefox.unofficial.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.orangefox.unofficial.ui.screens.about.AboutScreen
import com.orangefox.unofficial.ui.screens.bridge.BridgeScreen
import com.orangefox.unofficial.ui.screens.checker.CheckerScreen
import com.orangefox.unofficial.ui.screens.device.DeviceDetailScreen
import com.orangefox.unofficial.ui.screens.devices.DevicesScreen
import com.orangefox.unofficial.ui.screens.downloads.DownloadsScreen
import com.orangefox.unofficial.ui.screens.home.HomeScreen
import com.orangefox.unofficial.ui.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val DEVICES = "devices"
    const val DEVICE_DETAIL = "device/{codename}"
    const val CHECKER = "checker"
    const val DOWNLOADS = "downloads"
    const val BRIDGE = "bridge"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun deviceDetail(codename: String) = "device/$codename"
}

private data class BottomItem(val route: String, val icon: ImageVector, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val topLevels = listOf(
        BottomItem(Routes.HOME, Icons.Rounded.Home, "Home"),
        BottomItem(Routes.DEVICES, Icons.Rounded.Smartphone, "Devices"),
        BottomItem(Routes.CHECKER, Icons.Rounded.HealthAndSafety, "Checker"),
        BottomItem(Routes.DOWNLOADS, Icons.Rounded.Download, "Downloads")
    )

    var showMoreSheet by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            if (currentRoute in topLevels.map { it.route }) {
                NavigationBar {
                    topLevels.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                    NavigationBarItem(
                        selected = false,
                        onClick = { showMoreSheet = true },
                        icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More") },
                        label = { Text("More") }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = {
                fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 10 }
            },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = {
                fadeIn(tween(260)) + slideInHorizontally(tween(260)) { -it / 10 }
            },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 10 }
            }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenDevices = { navController.navigate(Routes.DEVICES) },
                    onOpenChecker = { navController.navigate(Routes.CHECKER) },
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onOpenBridge = { navController.navigate(Routes.BRIDGE) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onDeviceClick = { navController.navigate(Routes.deviceDetail(it)) }
                )
            }
            composable(Routes.DEVICES) {
                DevicesScreen(onDeviceClick = { navController.navigate(Routes.deviceDetail(it)) })
            }
            composable(
                route = Routes.DEVICE_DETAIL,
                arguments = listOf(navArgument("codename") { type = NavType.StringType })
            ) { entry ->
                val codename = entry.arguments?.getString("codename").orEmpty()
                DeviceDetailScreen(codename = codename, onBack = { navController.popBackStack() })
            }
            composable(Routes.CHECKER) { CheckerScreen() }
            composable(Routes.DOWNLOADS) { DownloadsScreen() }
            composable(Routes.BRIDGE) { BridgeScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) }
                )
            }
            composable(Routes.ABOUT) { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(onDismissRequest = { showMoreSheet = false }) {
            MoreSheetContent(
                onBridge = { showMoreSheet = false; navController.navigate(Routes.BRIDGE) },
                onSettings = { showMoreSheet = false; navController.navigate(Routes.SETTINGS) },
                onAbout = { showMoreSheet = false; navController.navigate(Routes.ABOUT) }
            )
        }
    }
}

@Composable
private fun MoreSheetContent(onBridge: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            "More",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        ListItem(
            headlineContent = { Text("Bridge Health") },
            supportingContent = { Text("Check connectivity to every OrangeFox server") },
            leadingContent = { Icon(Icons.Rounded.NetworkCheck, contentDescription = null) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onBridge)
        )
        ListItem(
            headlineContent = { Text("Settings") },
            supportingContent = { Text("Theme, API base URL, storage") },
            leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onSettings)
        )
        ListItem(
            headlineContent = { Text("About") },
            supportingContent = { Text("Version, credits & libraries") },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onAbout)
        )
    }
}
