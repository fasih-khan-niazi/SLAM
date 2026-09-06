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
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.data.remote.SubscriptionInfo
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSkeleton
import com.slam.app.ui.components.SlamTextButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()

    val pinStore = remember { PinStore(context) }
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var subscription by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var pinReady by remember { mutableStateOf(pinStore.hasPin()) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var listenerNote by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(ListenerPrefs(context).isListening()) }
    var cachedRemaining by remember { mutableStateOf<Int?>(null) }
    var cachedUnlimited by remember { mutableStateOf(false) }
    var permissionNote by remember { mutableStateOf("SMS and location permissions are required for tracking.") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.count { it }
        permissionNote = if (granted == result.size) {
            "Permissions granted. Tracking is listening for SLAM commands."
        } else {
            "Some permissions were denied. Tracking needs SMS and location access."
        }
        if (granted == result.size && pinStore.hasPin()) {
            SlamListenerService.start(context)
        }
    }

    LaunchedEffect(Unit) {
        name = store.displayName.first()
        val token = store.token.first()
        val base = BuildConfig.API_BASE_URL
        try {
            val api = SlamApiFactory.create(base)
            runCatching {
                val config = api.config().body()?.data
                store.cacheProductConfig(config?.pinAttemptCap, config?.pinWindowMinutes)
            }
            if (token.isNotBlank()) {
                val response = api.me("Bearer $token")
                val data = response.body()?.data
                if (response.isSuccessful && data != null) {
                    name = data.user.name
                    subscription = data.subscription
                    store.cacheUsage(data.subscription)
                }
            }
        } catch (_: Exception) {
            // Offline home still renders with cached name and usage.
        } finally {
            cachedUnlimited = store.cachedUnlimited.first()
            cachedRemaining = store.cachedRemaining.first().let { if (it == Int.MAX_VALUE) null else it }
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
                    val remaining = subscription?.requestsRemaining ?: cachedRemaining
                    val limit = subscription?.monthlyLimit
                    val unlimited = cachedUnlimited || (limit == null && subscription?.planName == "Premium")
                    Text(
                        if (unlimited) {
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
                    Text("Tracking PIN", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    if (pinReady) {
                        Text(
                            "PIN is saved on this device. Send: SLAM <PIN> LOCATE",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SlamPrimaryButton(
                            text = if (listening) "Listening" else "Start listening",
                            onClick = {
                                listenerNote = if (SlamListenerService.start(context)) {
                                    listening = true
                                    "Listening. Pull down the notification shade — you should see SLAM."
                                } else {
                                    "Could not start the listener. Allow notifications for SLAM in system settings."
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        SlamTextButton(
                            text = "Stop listening",
                            onClick = {
                                SlamListenerService.stop(context)
                                listening = false
                                listenerNote = "Stopped. Incoming SLAM texts will be ignored until you start again."
                            },
                        )
                        listenerNote?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            "Set a 4–6 digit PIN. Only this PIN can request location by SMS.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SlamField(
                            value = pinInput,
                            onValueChange = { value ->
                                if (value.length <= 6 && value.all { it.isDigit() }) pinInput = value
                            },
                            label = "PIN",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Spacer(Modifier.height(16.dp))
                        SlamPrimaryButton(
                            text = "Save PIN",
                            onClick = {
                                if (pinStore.setPin(pinInput)) {
                                    pinReady = true
                                    pinError = null
                                    SlamListenerService.start(context)
                                } else {
                                    pinError = "PIN must be 4 to 6 digits"
                                }
                            },
                        )
                    }
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
        SlamTextButton(text = "Settings", onClick = onOpenSettings)
        SlamTextButton(text = "How tracking works", onClick = { sheetOpen = true })
        SlamTextButton(text = "Sign out", onClick = { confirmSignOut = true })
    }

    pinError?.let { message ->
        SlamModal(
            title = "Invalid PIN",
            message = message,
            confirmLabel = "OK",
            cancelLabel = "",
            onConfirm = { pinError = null },
            onDismiss = { pinError = null },
        )
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
                    "Text SLAM, then your PIN, then LOCATE. Example: SLAM 1234 LOCATE. " +
                        "This phone replies with coordinates and a map link. Internet is not required for that loop. " +
                        "In Settings you can restrict requests to trusted numbers.",
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
