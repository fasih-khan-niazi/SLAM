package com.slam.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slam.app.BuildConfig
import com.slam.app.data.SessionStore
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.data.remote.SubscriptionInfo
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSkeleton
import com.slam.app.ui.components.SlamTextButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var subscription by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var permissionNote by remember { mutableStateOf("SMS and location permissions are required for tracking.") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.count { it }
        permissionNote = if (granted == result.size) {
            "Permissions granted. SMS tracking will be enabled in the next update."
        } else {
            "Some permissions were denied. Tracking needs SMS and location access."
        }
    }

    LaunchedEffect(Unit) {
        name = store.displayName.first()
        val token = store.token.first()
        val base = store.apiBaseUrl.first().ifBlank { BuildConfig.API_BASE_URL }
        try {
            if (token.isNotBlank()) {
                val api = SlamApiFactory.create(base)
                val response = api.me("Bearer $token")
                val data = response.body()?.data
                if (response.isSuccessful && data != null) {
                    name = data.user.name
                    subscription = data.subscription
                }
            }
        } catch (_: Exception) {
            // Offline home still renders with cached name.
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Hello${if (name.isNotBlank()) ", ${name.split(" ").first()}" else ""}", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Your phone is ready to be found over SMS.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        if (loading) {
            SlamSkeleton(height = 96)
            Spacer(Modifier.height(12.dp))
            SlamSkeleton(height = 72)
        } else {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Current plan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(subscription?.planName ?: "Free", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    val remaining = subscription?.requestsRemaining
                    val limit = subscription?.monthlyLimit
                    Text(
                        if (limit == null && subscription?.planName == "Premium") {
                            "Unlimited location requests this month"
                        } else {
                            "${remaining ?: "—"} of ${limit ?: 5} requests remaining"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Permissions", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(permissionNote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    SlamPrimaryButton(
                        text = "Allow SMS and location",
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_SMS,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            )
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SlamTextButton(text = "How tracking works", onClick = { sheetOpen = true })
        SlamTextButton(text = "Sign out", onClick = { confirmSignOut = true })
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("How tracking works", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "A trusted contact texts SLAM followed by your PIN and LOCATE. " +
                        "This phone replies with coordinates and a map link. Internet is not required for that loop.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmSignOut) {
        SlamModal(
            title = "Sign out?",
            message = "You can still receive SMS location requests after signing out, once tracking is enabled.",
            confirmLabel = "Sign out",
            onConfirm = {
                scope.launch {
                    store.clearSession()
                    confirmSignOut = false
                    onSignedOut()
                }
            },
            onDismiss = { confirmSignOut = false },
            destructive = true,
        )
    }
}
