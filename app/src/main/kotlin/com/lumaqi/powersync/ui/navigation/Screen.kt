package com.lumaqi.powersync.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ConnectDrive : Screen("connect_drive")
    object SyncSelection : Screen("sync_selection")
}
