package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object GoogleAuthManager {
    private const val TAG = "KhataWiseAuth"
    private val auth: FirebaseAuth?
        get() = try {
            Firebase.auth
        } catch (e: Throwable) {
            null
        }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        try {
            auth?.addAuthStateListener { firebaseAuth ->
                _currentUser.value = firebaseAuth.currentUser
            }
        } catch (_: Throwable) {}
    }

    fun isUserSignedIn(): Boolean = auth?.currentUser != null

    fun getUserId(): String? = auth?.currentUser?.uid

    fun requireUserId(): String = auth?.currentUser?.uid
        ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        val current = auth?.currentUser
        if (current != null) {
            _currentUser.value = current
            onAuthSuccess(current)
            return
        }

        val clientId = getWebClientId(context)
        if (clientId.isNullOrEmpty()) {
            Log.w(TAG, "default_web_client_id not found in resources; silent sign-in skipped")
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val firebaseAuth = auth
                    if (firebaseAuth == null) {
                        onUnauthenticated()
                        return@launch
                    }
                    val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        _currentUser.value = user
                        onAuthSuccess(user)
                    } else {
                        onUnauthenticated()
                    }
                } else {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent auto-sign-in skipped or unavailable: ${e.message}")
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        activity: Activity,
        credentialManager: CredentialManager,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        // If user is already authenticated in Firebase, reuse immediately
        val existing = auth?.currentUser
        if (existing != null) {
            _currentUser.value = existing
            onAuthSuccess(existing)
            return
        }

        val clientId = getWebClientId(activity)
        if (clientId.isNullOrEmpty()) {
            onAuthError(
                "Google Sign-In requires OAuth client IDs in google-services.json.\n\n" +
                "To generate them:\n" +
                "1. Firebase Console -> Authentication -> Sign-in method -> Enable 'Google'\n" +
                "2. Project Settings -> Your Android Apps -> Add SHA-1: 6B:B0:60:1E:58:8B:48:2A:A6:9E:A7:FC:67:35:71:69:34:85:BA:32\n" +
                "3. Re-download google-services.json and upload it here."
            )
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val firebaseAuth = auth ?: throw IllegalStateException("FirebaseAuth is not initialized")
                    val authResult = firebaseAuth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        _currentUser.value = user
                        onAuthSuccess(user)
                    } else {
                        onAuthError("Failed to authenticate user profile")
                    }
                } else {
                    onAuthError("Unexpected credential type returned")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                val fullError = "${e.javaClass.simpleName}: ${e.message ?: "No error message"} | Cause: ${e.cause?.message}"
                val userFriendlyMessage = when {
                    fullError.contains("10") || fullError.contains("DEVELOPER_ERROR") ->
                        "Developer Error (Code 10): Keystore SHA-1 mismatch.\nThe APK's signing certificate SHA-1 does not match the OAuth client in Firebase Console.\nEnsure SHA-1 '6B:B0:60:1E:58:8B:48:2A:A6:9E:A7:FC:67:35:71:69:34:85:BA:32' is added in Firebase Console (Project Settings -> Android Apps)."
                    fullError.contains("12500") || fullError.contains("SIGN_IN_FAILED") ->
                        "Configuration Error (Code 12500): Google Sign-In is disabled or Support Email is missing.\nGo to Firebase Console -> Authentication -> Sign-in method -> Enable Google, and ensure a Support Email is selected in Project Settings."
                    fullError.contains("NoCredential") || fullError.contains("No credentials") ->
                        "No Google Account found on this device/emulator.\nPlease add a Google Account in Android Settings -> Accounts to continue."
                    fullError.contains("7") || fullError.contains("NETWORK") ->
                        "Network Error: Unable to reach Google servers. Please check your internet connection."
                    else ->
                        "Google Sign-In Error: ${e.message ?: e.javaClass.simpleName}"
                }
                onAuthError(userFriendlyMessage)
            }
        }
    }

    fun quickSignOut() {
        try {
            auth?.signOut()
            _currentUser.value = null
        } catch (e: Throwable) {
            Log.e(TAG, "Error in quickSignOut", e)
        }
    }

    fun signOut(
        context: Context,
        credentialManager: CredentialManager,
        onSignOutComplete: () -> Unit,
        scope: CoroutineScope
    ) {
        try {
            auth?.signOut()
        } catch (_: Throwable) {}
        _currentUser.value = null
        scope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear credential state", e)
            } finally {
                onSignOutComplete()
            }
        }
    }

    private fun getWebClientId(context: Context): String? {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId).takeIf { it.isNotBlank() } else null
        } catch (e: Exception) {
            null
        }
    }
}
