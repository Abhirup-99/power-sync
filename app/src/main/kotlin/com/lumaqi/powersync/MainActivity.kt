package com.lumaqi.powersync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.lumaqi.powersync.ui.navigation.NavGraph
import com.lumaqi.powersync.ui.navigation.Screen
import com.lumaqi.powersync.ui.theme.PowerSyncTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize debug logger
        DebugLogger.initialize(applicationContext)
        DebugLogger.i("MainActivity", "onCreate called")

        enableEdgeToEdge()

        setContent {
            PowerSyncTheme(darkTheme = false) {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }

                // Determine start destination based on auth state
                LaunchedEffect(Unit) {
                    val user = FirebaseAuth.getInstance().currentUser
                    startDestination =
                            if (user != null) {
                                Screen.ConnectDrive.route
                            } else {
                                Screen.Login.route
                            }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    startDestination?.let { destination ->
                        NavGraph(navController = navController, startDestination = destination)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        DebugLogger.i("MainActivity", "onDestroy called")
        super.onDestroy()
    }
}
