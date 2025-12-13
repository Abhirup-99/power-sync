package com.even.chord.services

import android.content.Context
import android.content.Intent
import com.even.chord.DebugLogger
import com.even.chord.NativeSyncConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GoogleAuthService(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val googleSignInClient: GoogleSignInClient
    private val httpClient =
            OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

    init {
        val gso =
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getWebClientId())
                        .requestEmail()
                        .requestScopes(
                                com.google.android.gms.common.api.Scope(
                                        "https://www.googleapis.com/auth/drive.file"
                                )
                        )
                        .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    private fun getWebClientId(): String {
        // This should match your Firebase project's web client ID
        // Get it from google-services.json or Firebase console
        return context.getString(
                context.resources.getIdentifier(
                        "default_web_client_id",
                        "string",
                        context.packageName
                )
        )
    }

    fun isSignedIn(): Boolean {
        return auth.currentUser != null
    }

    fun getSignInIntent(): Intent {
        // Sign out first to ensure account picker shows
        googleSignInClient.signOut()
        return googleSignInClient.signInIntent
    }

    suspend fun handleSignInResult(data: Intent?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)

                // Get Firebase credential
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val authResult = auth.signInWithCredential(credential).await()

                // Upsert user to API
                authResult.user?.let { user -> upsertUser(user) }

                DebugLogger.i("GoogleAuthService", "Sign in successful for ${account.email}")
                true
            } catch (e: ApiException) {
                DebugLogger.e("GoogleAuthService", "Google sign in failed", e)
                throw Exception("Google sign in failed: ${e.statusCode}")
            } catch (e: Exception) {
                DebugLogger.e("GoogleAuthService", "Sign in error", e)
                throw e
            }
        }
    }

    private suspend fun upsertUser(user: com.google.firebase.auth.FirebaseUser) {
        try {
            val idToken = user.getIdToken(false).await().token ?: return

            val request =
                    Request.Builder()
                            .url("${NativeSyncConfig.API_BASE_URL}/app-internal/upsert-user")
                            .addHeader("Authorization", "Bearer $idToken")
                            .post(ByteArray(0).toRequestBody())
                            .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    DebugLogger.i("GoogleAuthService", "User upserted successfully")
                }
            }
        } catch (e: Exception) {
            // Silent failure - upsert is best-effort
            DebugLogger.w("GoogleAuthService", "User upsert failed: ${e.message}")
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
