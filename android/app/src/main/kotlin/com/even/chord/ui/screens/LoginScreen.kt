package com.even.chord.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.even.chord.services.GoogleAuthService
import com.even.chord.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { GoogleAuthService(context) }
    
    var isLoading by remember { mutableStateOf(false) }
    
    val launcher = rememberLauncherForActivityResult(
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
                    val message = if (e.message?.contains("@even.in") == true) {
                        "⚠️ Access Restricted: Only @even.in email addresses are allowed"
                    } else {
                        "Sign in failed: ${e.message}"
                    }
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
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo container
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .clip(CircleShape)
                    .background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudSync,
                    contentDescription = "Chord Logo",
                    modifier = Modifier.size(80.dp),
                    tint = Primary
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App name
            Text(
                text = "Chord",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Grey900,
                letterSpacing = (-1).sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "by Even.in",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Grey500
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Info box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Blue50)
                    .border(1.dp, Blue200, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Blue700,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Only @even.in email addresses can sign in",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Blue900
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Sign in button or loading
            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Signing in...",
                        color = Grey600
                    )
                }
            } else {
                Button(
                    onClick = {
                        isLoading = true
                        val signInIntent = authService.getSignInIntent()
                        launcher.launch(signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Login,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sign in with Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Grey800
                    )
                }
            }
        }
    }
}
