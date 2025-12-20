package com.lumaqi.powersync.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.lumaqi.powersync.ui.screens.LoginScreen
import com.lumaqi.powersync.ui.screens.ConnectDriveScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.ConnectDrive.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.ConnectDrive.route) {
            ConnectDriveScreen(
                onFolderSelected = {
                    navController.navigate(Screen.SyncSelection.route)
                }
            )
        }

        composable(Screen.SyncSelection.route) {
            // Placeholder for Step 3
            androidx.compose.material3.Text("Sync Selection Screen (Coming Soon)")
        }
    }
}
