package com.lumaqi.powersync.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.lumaqi.powersync.NativeSyncConfig
import com.lumaqi.powersync.data.SyncSettingsRepository
import com.lumaqi.powersync.ui.screens.ConnectDriveScreen
import com.lumaqi.powersync.ui.screens.LoginScreen
import com.lumaqi.powersync.ui.screens.OnboardingScreen
import com.lumaqi.powersync.ui.screens.SyncStatusScreen
import com.lumaqi.powersync.ui.screens.SynchronizationSettingsScreen

@Composable
fun AppNavHost(navController: NavHostController, isSignedIn: Boolean) {
    val context = LocalContext.current
    val hasFolder =
            remember(isSignedIn) {
                val repository = SyncSettingsRepository.getInstance(context)
                repository.getFolders().isNotEmpty()
            }

    val startDestination = remember {
        if (isSignedIn) RootRoute.Main.route else RootRoute.Auth.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        authGraph(navController)
        mainGraph(navController, hasFolder)
    }

    LaunchedEffect(isSignedIn) {
        val targetRoute = if (isSignedIn) RootRoute.Main.route else RootRoute.Auth.route
        val isOnTargetGraph =
                navController.currentBackStackEntry?.destination?.hierarchy?.any {
                    it.route == targetRoute
                } == true
        if (!isOnTargetGraph) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

private fun NavGraphBuilder.authGraph(navController: NavHostController) {
    navigation(startDestination = Screen.Login.route, route = RootRoute.Auth.route) {
        composable(Screen.Login.route) {
            LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(RootRoute.Main.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
            )
        }
    }
}

private fun NavGraphBuilder.mainGraph(navController: NavHostController, hasFolder: Boolean) {
    navigation(
            startDestination =
                    if (hasFolder) Screen.SyncSelection.route else Screen.Onboarding.route,
            route = RootRoute.Main.route
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                    onFolderSelected = {
                        navController.navigate(Screen.SyncSelection.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                    onSignOut = {
                        // Sign out is handled by the AuthListener in MainActivity/AppNavHost
                    }
            )
        }

        composable(Screen.ConnectDrive.route) {
            ConnectDriveScreen(onFolderSelected = { navController.popBackStack() })
        }

        composable(Screen.SyncSelection.route) {
            SyncStatusScreen(
                    onConfigureFolders = { navController.navigate(Screen.ConnectDrive.route) },
                    onNavigateToSynchronizationSettings = { navController.navigate(Screen.SynchronizationSettings.route) }
            )
        }

        composable(Screen.SynchronizationSettings.route) {
            SynchronizationSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
