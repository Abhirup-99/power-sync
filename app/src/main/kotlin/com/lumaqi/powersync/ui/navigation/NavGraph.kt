package com.lumaqi.powersync.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.lumaqi.powersync.ui.screens.LoginScreen
import com.lumaqi.powersync.ui.screens.ConnectDriveScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    isSignedIn: Boolean
) {
    val startDestination =
        remember { if (isSignedIn) RootRoute.Main.route else RootRoute.Auth.route }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        authGraph(navController)
        mainGraph(navController)
    }

    LaunchedEffect(isSignedIn) {
        val targetRoute = if (isSignedIn) RootRoute.Main.route else RootRoute.Auth.route
        val isOnTargetGraph =
            navController.currentBackStackEntry?.destination
                ?.hierarchy
                ?.any { it.route == targetRoute } == true
        if (!isOnTargetGraph) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

private fun NavGraphBuilder.authGraph(navController: NavHostController) {
    navigation(
        startDestination = Screen.Login.route,
        route = RootRoute.Auth.route
    ) {
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

private fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    navigation(
        startDestination = Screen.ConnectDrive.route,
        route = RootRoute.Main.route
    ) {
        composable(Screen.ConnectDrive.route) {
            ConnectDriveScreen(
                onFolderSelected = {
                    navController.navigate(Screen.SyncSelection.route)
                }
            )
        }

        composable(Screen.SyncSelection.route) {
            // Placeholder for Step 3
            Text("Sync Selection Screen (Coming Soon)")
        }
    }
}
