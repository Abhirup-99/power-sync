package com.lumaqi.powersync.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ConnectDrive : Screen("connect_drive")
    object SyncSelection : Screen("sync_selection")
}

sealed class RootRoute(val route: String) {
    object Auth : RootRoute("auth")
    object Main : RootRoute("main")
}
