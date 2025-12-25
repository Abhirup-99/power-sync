package com.lumaqi.powersync.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Onboarding : Screen("onboarding")
    object ConnectDrive : Screen("connect_drive")
    object SyncSelection : Screen("sync_selection")
    object SynchronizationSettings : Screen("synchronization_settings")
}

sealed class RootRoute(val route: String) {
    object Auth : RootRoute("auth")
    object Main : RootRoute("main")
}
