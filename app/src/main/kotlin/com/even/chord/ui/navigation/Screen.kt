package com.even.chord.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Permissions : Screen("permissions")
    object Onboarding : Screen("onboarding")
}
