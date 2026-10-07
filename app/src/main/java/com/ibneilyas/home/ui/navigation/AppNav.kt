package com.ibneilyas.home.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ibneilyas.home.ui.HomeViewModel
import com.ibneilyas.home.ui.components.VoiceFab
import com.ibneilyas.home.ui.screens.HomeScreen
import com.ibneilyas.home.ui.screens.ActivityScreen
import com.ibneilyas.home.ui.screens.NodeOptionsScreen
import com.ibneilyas.home.ui.screens.SchedulesScreen
import com.ibneilyas.home.ui.screens.ScenesScreen
import com.ibneilyas.home.ui.screens.DevicesScreen
import com.ibneilyas.home.ui.screens.PlaceholderScreen
import com.ibneilyas.home.ui.screens.RoomDetailScreen
import com.ibneilyas.home.ui.screens.RoomsScreen
import com.ibneilyas.home.ui.screens.SettingsScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Rooms("rooms", "Rooms", Icons.Filled.MeetingRoom),
    Scenes("scenes", "Scenes", Icons.Filled.AutoAwesome),
    Devices("devices", "Devices", Icons.Filled.Memory),
    Settings("settings", "Settings", Icons.Filled.Settings)
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: HomeViewModel = viewModel()
    val backEntry by nav.currentBackStackEntryAsState()
    val current = backEntry?.destination?.route
    val openRoom: (String) -> Unit = { id -> nav.navigate("room/$id") }

    Scaffold(
        floatingActionButton = { if (current in listOf("home", "rooms", "scenes", "devices") || current?.startsWith("room/") == true) VoiceFab(vm) },
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { tab ->
                    val selected = current == tab.route ||
                        (tab == Tab.Rooms && current?.startsWith("room/") == true)
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Tab.Home.route, modifier = Modifier.padding(pad)) {
            composable(Tab.Home.route) { HomeScreen(vm, openRoom) }
            composable(Tab.Rooms.route) { RoomsScreen(vm, openRoom) }
            composable(
                "room/{roomId}",
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) { entry ->
                RoomDetailScreen(entry.arguments?.getString("roomId").orEmpty(), vm) {
                    nav.popBackStack()
                }
            }
            composable("schedules/{nodeId}", arguments = listOf(navArgument("nodeId") { type = NavType.StringType })) { e ->
                SchedulesScreen(e.arguments?.getString("nodeId").orEmpty(), vm) { nav.popBackStack() }
            }
            composable("node/{nodeId}", arguments = listOf(navArgument("nodeId") { type = NavType.StringType })) { e ->
                NodeOptionsScreen(e.arguments?.getString("nodeId").orEmpty(), vm) { nav.popBackStack() }
            }
            composable("activity") { ActivityScreen(vm) { nav.popBackStack() } }
            composable(Tab.Scenes.route) { ScenesScreen(vm) }
            composable(Tab.Devices.route) { DevicesScreen(vm) { id -> if (id.startsWith("opt:")) nav.navigate("node/" + id.removePrefix("opt:")) else nav.navigate("schedules/$id") } }
            composable(Tab.Settings.route) { SettingsScreen(vm) { nav.navigate("activity") } }
        }
    }
}
