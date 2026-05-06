package com.dev.sillycrypt.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.dev.sillycrypt.presentation.screens.CreateVolumeScreen
import com.dev.sillycrypt.presentation.screens.OpenVolumeScreen
import com.libsillycrypt.workprofile.presentation.screens.WorkProfileSelectionScreen

private data class BottomItem(
    val destination: AppDestination,
    val icon: ImageVector
)

@Composable
fun AppNavHost(
    isWorkProfileAvailable: State<Boolean>,
    closeActivity: () -> Unit,
    createProfile: () -> Unit,
) {
    val navController = rememberNavController()

    val items = listOf(
        BottomItem(AppDestination.PickFile, Icons.Default.Add),
        BottomItem(AppDestination.CreateFile, Icons.Default.Create),
        BottomItem(AppDestination.ManageWorkProfile, Icons.Default.Work)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == item.destination.route
                    } == true

                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.destination.label
                            )
                        },
                        label = {
                            Text(item.destination.label)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.PickFileNav.route
        ) {
            navigation(AppDestination.PickFile.route, AppDestination.PickFileNav.route) {
                composable(AppDestination.PickFile.route) {
                    OpenVolumeScreen(innerPadding = innerPadding, closeActivity = closeActivity)
                }
            }
            composable(AppDestination.CreateFile.route) {
                CreateVolumeScreen(innerPadding = innerPadding, closeActivity = closeActivity)
            }
            composable(AppDestination.ManageWorkProfile.route) {
                WorkProfileSelectionScreen(
                    isWorkProfileAvailable = isWorkProfileAvailable,
                    createProfile = createProfile, innerPadding = innerPadding)
            }
        }
    }
}