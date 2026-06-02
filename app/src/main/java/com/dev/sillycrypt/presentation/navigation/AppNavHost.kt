package com.dev.sillycrypt.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dev.exfat.exfat.VeracryptVolumeData
import com.dev.sillycrypt.presentation.screens.CreateVolumeScreen
import com.dev.sillycrypt.presentation.screens.OpenVolumeScreen
import com.dev.sillycrypt.settings.presentation.screens.SettingsScreen
import com.libsillycrypt.workprofile.presentation.screens.WorkProfileSelectionScreen
import com.sillycrypt.exfat_browser.presentation.screens.ExFatBrowserRoute

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

    val goBack = remember { { navController.popBackStack() } }

    val items = listOf(
        BottomItem(AppDestination.PickFileNav, Icons.Default.Add),
        BottomItem(AppDestination.CreateFile, Icons.Default.Create),
        BottomItem(AppDestination.ManageWorkProfile, Icons.Default.Work),
        BottomItem(AppDestination.Settings, Icons.Default.Settings)
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
                                contentDescription = stringResource(item.destination.label)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(item.destination.label),
                                textAlign = TextAlign.Center
                            )
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
            navigation(
                startDestination = AppDestination.PickFile.route,
                route = AppDestination.PickFileNav.route
            ) {
                composable(AppDestination.PickFile.route) {
                    OpenVolumeScreen(
                        innerPadding = innerPadding,
                        closeActivity = closeActivity,
                        onVolumeClick = { volumeData ->
                            navController.navigate(
                                AppDestination.ManageFiles.createRoute(volumeData)
                            )
                        }
                    )
                }

                composable(
                    route = AppDestination.ManageFiles.route,
                    arguments = listOf(
                        navArgument(AppDestination.ManageFiles.ARG_VOLUME_UUID) {
                            type = NavType.StringType
                        },
                        navArgument(AppDestination.ManageFiles.ARG_VOLUME_NAME) {
                            type = NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    val volumeUuid = requireNotNull(
                        backStackEntry.arguments?.getString(
                            AppDestination.ManageFiles.ARG_VOLUME_UUID
                        )
                    )

                    val volumeName = requireNotNull(
                        backStackEntry.arguments?.getString(
                            AppDestination.ManageFiles.ARG_VOLUME_NAME
                        )
                    )

                    ExFatBrowserRoute(
                        innerPadding = innerPadding,
                        volumeData = VeracryptVolumeData(
                            name = volumeName,
                            uuid = volumeUuid
                        )
                    )
                }
            }
            composable(AppDestination.CreateFile.route) {
                CreateVolumeScreen(innerPadding = innerPadding, goBack = goBack)
            }
            composable(AppDestination.ManageWorkProfile.route) {
                WorkProfileSelectionScreen(
                    isWorkProfileAvailable = isWorkProfileAvailable,
                    createProfile = createProfile, innerPadding = innerPadding)
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(innerPadding)
            }
        }
    }
}