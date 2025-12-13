package com.even.chord

import android.content.Context
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
import com.even.chord.ui.navigation.NavGraph
import com.even.chord.ui.navigation.Screen
import com.even.chord.ui.theme.ChordTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize debug logger
        DebugLogger.initialize(applicationContext)
        DebugLogger.i("MainActivity", "onCreate called")
        
        enableEdgeToEdge()
        
        setContent {
            ChordTheme(darkTheme = false) {
                val navController = rememberNavController()
                var startDestination by remember { mutableStateOf<String?>(null) }
                
                // Determine start destination based on auth state
                LaunchedEffect(Unit) {
                    val user = FirebaseAuth.getInstance().currentUser
                    startDestination = if (user != null) {
                        // Check if permissions are granted
                        if (hasRequiredPermissions()) {
                            Screen.Onboarding.route
                        } else {
                            Screen.Permissions.route
                        }
                    } else {
                        Screen.Login.route
                    }
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    startDestination?.let { destination ->
                        NavGraph(
                            navController = navController,
                            startDestination = destination
                        )
                    }
                }
            }
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        val hasPhone = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == 
            android.content.pm.PackageManager.PERMISSION_GRANTED
        
        val hasStorage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        
        return hasPhone && hasStorage
    }
    
    override fun onDestroy() {
        DebugLogger.i("MainActivity", "onDestroy called")
        super.onDestroy()
    }
}
