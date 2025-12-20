package com.lumaqi.powersync.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumaqi.powersync.services.GoogleAuthService
import com.lumaqi.powersync.ui.theme.*
import kotlinx.coroutines.launch

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
                if (result.resultCode == Activity.RESULT_OK) {
                    scope.launch {
                        isLoading = true
                        try {
                            val success = authService.handleSignInResult(result.data)
                            if (success) {
                                onLoginSuccess()
                            } else {
                                Toast.makeText(context, "Sign in failed", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            val message = "Sign in failed: ${e.message}"
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    isLoading = false
                }
            }

    // Check if already signed in
    LaunchedEffect(Unit) {
        if (authService.isSignedIn()) {
            onLoginSuccess()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DriveSyncDarkBackground) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // Top Bar
            Row(
                    modifier =
                            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "DriveSync",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveSyncGreenAccent
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { /* TODO: Help */}) {
                        Icon(
                                imageVector =
                                        Icons.Outlined
                                                .HelpOutline, // Need to make sure this exists or
                                // use QuestionMark
                                contentDescription = "Help",
                                tint = Color.White
                        )
                    }
                    IconButton(onClick = { /* TODO: Menu */}) {
                        Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = Color.White
                        )
                    }
                }
            }

            // Center Content
            Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
            ) {
                Text(
                        text = "Keep Your Data in Sync",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(64.dp))

                // Connect to Google Drive Button
                Button(
                        onClick = {
                            if (!isLoading) {
                                isLoading = true
                                val signInIntent = authService.getSignInIntent()
                                launcher.launch(signInIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = DriveSyncGreenAccent,
                                        contentColor = Color.Black
                                ),
                        shape = RoundedCornerShape(28.dp) // Capsule shape
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.Black,
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

                // Choose what to sync Button

                // Choose what to sync Button - Removed for linear flow
                /*
                Button(
                    onClick = {
                        Toast.makeText(context, "Please sign in first", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DriveSyncButtonGrey,
                        contentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        text = "Choose what to sync",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                */
            }

            // Bottom Link
            Row(
                    modifier =
                            Modifier.align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { /* TODO: Open User Guide */}
                                    .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector =
                                Icons.Outlined.Help, // Using Help instead of QuestionMark if needed
                        contentDescription = null,
                        tint = DriveSyncGreenAccent,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = "Online User's Guide",
                        fontSize = 14.sp,
                        color = DriveSyncGreenAccent,
                        style =
                                MaterialTheme.typography.bodyMedium.copy(
                                        textDecoration =
                                                androidx.compose.ui.text.style.TextDecoration
                                                        .Underline
                                )
                )
            }
        }
    }
}
