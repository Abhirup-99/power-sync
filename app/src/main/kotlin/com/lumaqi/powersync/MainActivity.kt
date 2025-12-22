package com.lumaqi.powersync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.lumaqi.powersync.ui.navigation.AppNavHost
import com.lumaqi.powersync.ui.navigation.rememberAuthState
import com.lumaqi.powersync.ui.theme.PowerSyncTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize debug logger
        DebugLogger.initialize(applicationContext)
        DebugLogger.i("MainActivity", "onCreate called")

        enableEdgeToEdge()

        setContent { PowerSyncTheme(darkTheme = false) { PowerSyncApp() } }
    }

    override fun onDestroy() {
        DebugLogger.i("MainActivity", "onDestroy called")
        super.onDestroy()
    }
}

@Composable
private fun PowerSyncApp() {
    val navController = rememberNavController()
    val isSignedIn by rememberAuthState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        AppNavHost(navController = navController, isSignedIn = isSignedIn)
    }
}
