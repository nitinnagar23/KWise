package com.example.ui.auth

import androidx.compose.runtime.Composable
import com.example.data.repository.CattleFeedRepository

/**
 * SetupScreen delegates to the modern unified LoginScreen.
 */
@Composable
fun SetupScreen(
    repository: CattleFeedRepository,
    onSetupComplete: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    LoginScreen(
        repository = repository,
        onLoginSuccess = onSetupComplete,
        onBack = onBack
    )
}
