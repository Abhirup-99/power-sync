package com.lumaqi.powersync.services

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.lumaqi.powersync.DebugLogger
import com.lumaqi.powersync.NativeSyncConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class GoogleAuthService(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val googleSignInClient: GoogleSignInClient

    init {
        DebugLogger.i("GoogleAuthService", "Initializing GoogleAuthService")
        val clientId = getWebClientId()
        DebugLogger.i("GoogleAuthService", "Web Client ID found: ${clientId.isNotEmpty()}")
        
        val gso =
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(clientId)
                        .requestEmail()
                        .requestScopes(
                                com.google.android.gms.common.api.Scope(
                                        "https://www.googleapis.com/auth/drive"
                                )
                        )
                        .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    private fun getWebClientId(): String {
        val resId =
                context.resources.getIdentifier(
                        "default_web_client_id",
                        "string",
                        context.packageName
                )
        if (resId == 0) {
            DebugLogger.e(
                    "GoogleAuthService",
                    "Could not find default_web_client_id resource. Google Sign-In will not work."
            )
            return ""
        }
        return context.getString(resId)
    }

    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getSignInIntent(): Intent {
        DebugLogger.i("GoogleAuthService", "Getting sign-in intent")
        // Sign out first to ensure account picker shows
        googleSignInClient.signOut()
        return googleSignInClient.signInIntent
    }

    suspend fun handleSignInResult(data: Intent?): Boolean {
        DebugLogger.i("GoogleAuthService", "Handling sign-in result")
        return withContext(Dispatchers.IO) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                DebugLogger.i("GoogleAuthService", "Google Account retrieved: ${account.email}")

                // Get Firebase credential
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                DebugLogger.i("GoogleAuthService", "Firebase credential created")
                
                val authResult = auth.signInWithCredential(credential).await()
                DebugLogger.i("GoogleAuthService", "Firebase sign-in complete. User: ${authResult.user?.uid}")

                // Upsert user to API (Removed)
                // authResult.user?.let { user -> upsertUser(user) }

                DebugLogger.i("GoogleAuthService", "Sign in successful for ${account.email}")
                true
            } catch (e: ApiException) {
                DebugLogger.e("GoogleAuthService", "Google sign in failed code=${e.statusCode}", e)
                throw Exception("Google sign in failed: ${e.statusCode}")
            } catch (e: Exception) {
                DebugLogger.e("GoogleAuthService", "Sign in error", e)
                throw e
            }
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            try {
                auth.signOut()
                googleSignInClient.signOut().await()
                DebugLogger.i("GoogleAuthService", "Sign out successful")
            } catch (e: Exception) {
                DebugLogger.e("GoogleAuthService", "Sign out error", e)
            }
        }
    }
}
