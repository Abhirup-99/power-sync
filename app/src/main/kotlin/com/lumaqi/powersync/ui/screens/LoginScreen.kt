package com.lumaqi.powersync.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Help
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumaqi.powersync.R
import com.lumaqi.powersync.services.GoogleAuthService
import com.lumaqi.powersync.ui.theme.*
import kotlinx.coroutines.launch

import com.lumaqi.powersync.DebugLogger

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { GoogleAuthService(context) }

    var isLoading by remember { mutableStateOf(false) }

    val launcher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                DebugLogger.i("LoginScreen", "Activity result received: ${result.resultCode}")
                if (result.resultCode == Activity.RESULT_OK) {
                    scope.launch {
                        isLoading = true
                        try {
                            DebugLogger.i("LoginScreen", "Processing sign-in result")
                            val success = authService.handleSignInResult(result.data)
                            if (success) {
                                DebugLogger.i("LoginScreen", "Sign-in successful, navigating")
                                onLoginSuccess()
                            } else {
                                DebugLogger.w("LoginScreen", "Sign-in returned false")
                                Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
                                isLoading = false // Reset loading on failure
                            }
                        } catch (e: Exception) {
                            DebugLogger.e("LoginScreen", "Sign-in exception", e)
                            val message = "Sign in failed: ${e.message}"
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            isLoading = false // Reset loading on error
                        }
                    }
                } else {
                    DebugLogger.w("LoginScreen", "Activity result not OK: ${result.resultCode}")
                    isLoading = false
                }
            }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(
                                    Brush.verticalGradient(
                                            colors =
                                                    listOf(
                                                            MaterialTheme.colorScheme
                                                                    .primaryContainer,
                                                            MaterialTheme.colorScheme.background
                                                    )
                                    )
                            )
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {


            // Center Content
            Card(
                    modifier = Modifier.align(Alignment.Center),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                            )
            ) {
                Column(
                        modifier = Modifier.padding(vertical = 48.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                    Image(
                            painter = painterResource(id = R.drawable.logo),
                            contentDescription = "App Logo",
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.Center,
                            modifier =
                                    Modifier.size(120.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .padding(2.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                            text = "AutoSync",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                            text = "Keep Your Data in Sync",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                            textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Connect to Google Drive Button
                    Button(
                            onClick = {
                                if (!isLoading) {
                                    DebugLogger.i("LoginScreen", "Connect button clicked")
                                    isLoading = true
                                    try {
                                        val signInIntent = authService.getSignInIntent()
                                        launcher.launch(signInIntent)
                                    } catch (e: Exception) {
                                        DebugLogger.e("LoginScreen", "Failed to launch sign-in", e)
                                        isLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors =
                                    ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                            shape = RoundedCornerShape(28.dp) // Capsule shape
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                    text = "Connect to Google Drive",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }


        }
    }
}
