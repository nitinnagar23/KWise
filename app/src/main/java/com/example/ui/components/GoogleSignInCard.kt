package com.example.ui.components

import com.example.ui.theme.CGreenPrimary
import com.example.ui.theme.FlatDarkBg
import com.example.ui.theme.kw
import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.PashuAaharApp
import com.example.auth.GoogleAuthManager
import com.example.sync.FirestoreSyncState
import com.example.ui.theme.CGreen
import com.example.ui.theme.CRedUdhari
import com.example.ui.theme.FlatDarkCard
import com.example.ui.theme.FlatDarkBorder
import com.example.ui.theme.FlatTextMuted
import com.example.ui.theme.FlatTextPrimary
import com.example.util.findActivity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoogleSignInCard(
    modifier: Modifier = Modifier,
    onAuthSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val currentUser by GoogleAuthManager.currentUser.collectAsState()
    val syncEngine = remember { PashuAaharApp.instance.firestoreSyncEngine }
    val syncState by syncEngine.syncState.collectAsState()

    var isAuthenticating by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FlatDarkCard)
            .border(1.dp, FlatDarkBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (currentUser != null) CGreen.copy(alpha = 0.15f) else Color(0xFF4285F4).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = if (currentUser != null) CGreen else Color(0xFF4285F4),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "KhataWise Cloud Sync",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = if (currentUser != null) "Real-time Firebase Firestore" else "Sign in to backup ledger",
                            style = MaterialTheme.typography.bodySmall,
                            color = FlatTextMuted
                        )
                    }
                }

                // Sync status badge
                if (currentUser != null) {
                    when (val state = syncState) {
                        is FirestoreSyncState.Syncing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = CGreen)
                                Text("Syncing", style = MaterialTheme.typography.labelSmall, color = CGreen)
                            }
                        }
                        is FirestoreSyncState.Success -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CGreen, modifier = Modifier.size(14.dp))
                                Text("Synced", style = MaterialTheme.typography.labelSmall, color = CGreen)
                            }
                        }
                        is FirestoreSyncState.Error -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Error, contentDescription = null, tint = CRedUdhari, modifier = Modifier.size(14.dp))
                                Text("Offline", style = MaterialTheme.typography.labelSmall, color = CRedUdhari)
                            }
                        }
                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = CGreen, modifier = Modifier.size(14.dp))
                                Text("Active", style = MaterialTheme.typography.labelSmall, color = CGreen)
                            }
                        }
                    }
                }
            }

            if (currentUser != null) {
                // Signed-in profile view
                val user = currentUser!!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(FlatDarkBg)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.displayName ?: "Store Owner",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = FlatTextPrimary
                        )
                        Text(
                            text = user.email ?: (user.phoneNumber ?: "Authenticated"),
                            style = MaterialTheme.typography.bodySmall,
                            color = FlatTextMuted
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = syncEngine.syncNow()
                                    when (result) {
                                        is FirestoreSyncState.Success -> {
                                            Toast.makeText(context, "Cloud sync complete (${result.count} records)", Toast.LENGTH_SHORT).show()
                                        }
                                        is FirestoreSyncState.Error -> {
                                            Toast.makeText(context, "Sync error: ${result.message}", Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {}
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CGreenPrimary),
                            modifier = Modifier.testTag("sync_now_btn")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Sync", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = {
                                GoogleAuthManager.signOut(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onSignOutComplete = {
                                        Toast.makeText(context, "Signed out of Google", Toast.LENGTH_SHORT).show()
                                    },
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("google_sign_out_btn")
                        ) {
                            Text("Sign out", style = MaterialTheme.typography.labelSmall, color = FlatTextMuted)
                        }
                    }
                }
            } else {
                // Unauthenticated view with Google Sign-In button
                Text(
                    text = "Sign in with Google to enable real-time cloud sync, automatic multi-device backup, and secure Firestore database storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FlatTextMuted
                )

                if (authError != null) {
                    Text(
                        text = authError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = CRedUdhari
                    )
                }

                Button(
                    onClick = {
                        if (activity != null) {
                            isAuthenticating = true
                            authError = null
                            GoogleAuthManager.onGoogleSignInClicked(
                                activity = activity,
                                credentialManager = credentialManager,
                                onAuthSuccess = {
                                    isAuthenticating = false
                                    Toast.makeText(context, "Connected to Google Cloud", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                },
                                onAuthError = { errorMsg ->
                                    isAuthenticating = false
                                    authError = errorMsg
                                },
                                scope = scope,
                                onAuthCancelled = {
                                    isAuthenticating = false
                                }
                            )
                        }
                    },
                    enabled = !isAuthenticating && activity != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4285F4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_in_with_google_button")
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Sign in with Google",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
