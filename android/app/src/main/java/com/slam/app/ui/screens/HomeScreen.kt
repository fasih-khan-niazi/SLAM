package com.slam.app.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.slam.app.BuildConfig
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.local.SlamDatabase
import com.slam.app.sms.EmergencyScheduler
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.data.remote.SubscriptionInfo
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSkeleton
import com.slam.app.ui.components.SlamTextButton
import com.slam.app.ui.components.LocalSlamSnackbarHostState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSlamSnackbarHostState.current

    val pinStore = remember { PinStore(context) }
    var loading by remember { mutableStateOf(true) }
    var subscription by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var pinReady by remember { mutableStateOf(pinStore.hasPin()) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var listenerNote by remember { mutableStateOf<String?>(null) }
    var listening by remember {
        mutableStateOf(
            ListenerPrefs(context).isListening() && ListenerPrefs(context).isServiceActive(),
        )
    }
    var emergencyOn by remember { mutableStateOf(EmergencyPrefs(context).isOn()) }
    var emergencyAllowed by remember { mutableStateOf(true) }
    var emergencyHours by remember { mutableStateOf(1) }
    var pinMinLength by remember { mutableStateOf(4) }
    var pinMaxLength by remember { mutableStateOf(6) }
    var smsPrefix by remember { mutableStateOf("SLAM") }
    var emergencyNote by remember { mutableStateOf<String?>(null) }
    var emergencyLastResult by remember { mutableStateOf("") }
    var emergencyNextRun by remember { mutableStateOf(0L) }
    val liveRemainingValue by store.cachedRemaining.collectAsStateWithLifecycle(initialValue = 5)
    val cachedRemaining = liveRemainingValue.takeUnless { it == Int.MAX_VALUE }
    val cachedUnlimited by store.cachedUnlimited.collectAsStateWithLifecycle(initialValue = false)
    var prerequisiteStatus by remember { mutableStateOf(CorePrerequisites.status(context)) }

    fun refreshDeviceState() {
        prerequisiteStatus = CorePrerequisites.status(context)
        listening = ListenerPrefs(context).isListening() && ListenerPrefs(context).isServiceActive()
        emergencyOn = EmergencyPrefs(context).isOn()
        emergencyLastResult = EmergencyPrefs(context).lastResult()
        emergencyNextRun = EmergencyPrefs(context).nextRun()
    }

    suspend fun startListenerWhenReady(): Boolean {
        if (SlamDatabase.get(context).trustedNumbers().count() < 1) {
            listenerNote = "Add at least one trusted number before starting tracking."
            return false
        }
        val started = SlamListenerService.start(context)
        listening = started
        listenerNote = if (started) {
            "Listening. SLAM is ready for approved SMS commands."
        } else {
            "Review the required permissions before starting tracking."
        }
        snackbar.showSnackbar(listenerNote.orEmpty())
        return started
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshDeviceState()
        if (CorePrerequisites.status(context).listenerReady && pinStore.hasPin()) {
            scope.launch {
                startListenerWhenReady()
                refreshDeviceState()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshDeviceState()
        val status = CorePrerequisites.status(context)
        if (status.locationGranted && !status.backgroundLocationGranted) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else if (status.listenerReady && pinStore.hasPin()) {
            scope.launch {
                startListenerWhenReady()
                refreshDeviceState()
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshDeviceState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val token = store.token.first()
        val base = BuildConfig.API_BASE_URL
        try {
            val api = SlamApiFactory.create(base)
            coroutineScope {
                val configRequest = async { runCatching { api.config().body()?.data }.getOrNull() }
                val profileRequest = async {
                    if (token.isBlank()) null else runCatching {
                        api.me("Bearer $token")
                    }.getOrNull()
                }
                val config = configRequest.await()
                store.cacheProductConfig(
                    config?.pinAttemptCap,
                    config?.pinWindowMinutes,
                    config?.emergencyEnabled,
                    config?.emergencyIntervalHours,
                    config?.smsPrefix,
                    config?.pinMinLength,
                    config?.pinMaxLength,
                )
                val response = profileRequest.await()
                val data = response?.body()?.data
                if (response?.isSuccessful == true && data != null) {
                    subscription = data.subscription
                    store.cacheUsage(data.subscription)
                }
            }
        } catch (_: Exception) {
            // Offline home still renders with cached name and usage.
        } finally {
            emergencyAllowed = store.cachedEmergencyEnabled()
            emergencyHours = store.cachedEmergencyHours()
            pinMinLength = store.cachedPinMinLength()
            pinMaxLength = store.cachedPinMaxLength()
            smsPrefix = store.cachedSmsPrefix()
            emergencyOn = EmergencyPrefs(context).isOn()
            refreshDeviceState()
            loading = false
        }
    }

    val missingPermissions = buildList {
        if (!prerequisiteStatus.receiveSmsGranted) add("receive SMS")
        if (!prerequisiteStatus.sendSmsGranted) add("send SMS")
        if (!prerequisiteStatus.locationGranted) add("location")
        if (!prerequisiteStatus.backgroundLocationGranted) add("background location")
        if (!prerequisiteStatus.notificationsGranted) add("notifications")
    }
    val permissionNote = when {
        missingPermissions.isNotEmpty() -> "Still needed: ${missingPermissions.joinToString()}."
        !prerequisiteStatus.locationServicesEnabled ->
            "Permissions are granted. Device Location is off; SLAM will clearly send its last saved fix when available."
        else -> "All tracking permissions are granted and Location is on."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Tracking", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Control how this phone responds to location requests.",
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
                    val remaining = cachedRemaining
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
                            "PIN is saved on this device. Send: $smsPrefix <PIN> LOCATE",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (emergencyLastResult.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Last result: $emergencyLastResult",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (emergencyOn && emergencyNextRun > 0L) {
                            Text(
                                "Next run: ${
                                    java.text.DateFormat.getDateTimeInstance(
                                        java.text.DateFormat.SHORT,
                                        java.text.DateFormat.SHORT,
                                    ).format(java.util.Date(emergencyNextRun))
                                }",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        SlamPrimaryButton(
                            text = if (listening) "Listening" else "Start listening",
                            enabled = !listening,
                            onClick = {
                                scope.launch { startListenerWhenReady() }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        SlamTextButton(
                            text = "Stop listening",
                            onClick = {
                                SlamListenerService.stop(context)
                                EmergencyPrefs(context).setOn(false)
                                EmergencyScheduler.stop(context)
                                listening = false
                                emergencyOn = false
                                listenerNote = "Stopped. Incoming SLAM texts will be ignored until you start again."
                            },
                        )
                        listenerNote?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(
                            "Set a $pinMinLength–$pinMaxLength digit PIN. Only this PIN can request location by SMS.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SlamField(
                            value = pinInput,
                            onValueChange = { value ->
                                if (value.length <= pinMaxLength && value.all { it.isDigit() }) pinInput = value
                            },
                            label = "PIN",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Spacer(Modifier.height(16.dp))
                        SlamPrimaryButton(
                            text = "Save PIN",
                            onClick = {
                                if (pinStore.setPin(pinInput, pinMinLength, pinMaxLength)) {
                                    pinReady = true
                                    pinError = null
                                    listenerNote = "PIN saved. Add a trusted number, review permissions, then start listening."
                                    scope.launch { snackbar.showSnackbar("PIN saved securely.") }
                                } else {
                                    pinError = "PIN must be $pinMinLength to $pinMaxLength digits"
                                }
                            },
                        )
                    }
                }
            }
            if (pinReady && emergencyAllowed) {
                Spacer(Modifier.height(12.dp))
                SlamCard {
                    Column(Modifier.padding(20.dp)) {
                        Text("Emergency", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (emergencyOn) {
                                "Sending location to your trusted numbers every $emergencyHours hour${if (emergencyHours == 1) "" else "s"}."
                            } else {
                                "Texts your trusted numbers automatically. Interval is set by the operator (every $emergencyHours hour${if (emergencyHours == 1) "" else "s"})."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        SlamPrimaryButton(
                            text = if (emergencyOn) "Emergency on" else "Start emergency",
                            onClick = {
                                if (emergencyOn) return@SlamPrimaryButton
                                scope.launch {
                                    val trusted = SlamDatabase.get(context).trustedNumbers().count()
                                    when {
                                        !listening -> emergencyNote = "Start listening first."
                                        trusted < 1 -> emergencyNote = "Add at least one trusted number in Settings."
                                        !store.canLocate() -> emergencyNote = "No locates left on this plan this period."
                                        else -> {
                                            EmergencyPrefs(context).setOn(true)
                                            EmergencyScheduler.pingNow(context)
                                            EmergencyScheduler.start(context, store.cachedEmergencyHours())
                                            emergencyOn = true
                                            emergencyNote = "First location is sending now. The next one is in $emergencyHours hour${if (emergencyHours == 1) "" else "s"}."
                                        }
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        SlamTextButton(
                            text = "Stop emergency",
                            onClick = {
                                EmergencyPrefs(context).setOn(false)
                                EmergencyScheduler.stop(context)
                                emergencyOn = false
                                emergencyNote = "Emergency stopped. Trusted numbers will not get timed updates."
                            },
                        )
                        emergencyNote?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                        text = when {
                            !prerequisiteStatus.locationGranted ||
                                !prerequisiteStatus.receiveSmsGranted ||
                                !prerequisiteStatus.sendSmsGranted ||
                                !prerequisiteStatus.notificationsGranted -> "Allow required permissions"
                            !prerequisiteStatus.backgroundLocationGranted -> "Allow background location"
                            !prerequisiteStatus.locationServicesEnabled -> "Open Location settings"
                            else -> "Permissions granted"
                        },
                        enabled = !prerequisiteStatus.listenerReady ||
                            !prerequisiteStatus.locationServicesEnabled,
                        onClick = {
                            when {
                                !prerequisiteStatus.locationGranted ||
                                    !prerequisiteStatus.receiveSmsGranted ||
                                    !prerequisiteStatus.sendSmsGranted ||
                                    !prerequisiteStatus.notificationsGranted -> {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.SEND_SMS,
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ),
                                    )
                                }
                                !prerequisiteStatus.backgroundLocationGranted ->
                                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                !prerequisiteStatus.locationServicesEnabled ->
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SlamTextButton(text = "Manage trusted numbers", onClick = onOpenSettings)
        SlamTextButton(text = "How tracking works", onClick = { sheetOpen = true })
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
                    "Text $smsPrefix, then your PIN, then LOCATE. Example: $smsPrefix 1234 LOCATE. " +
                        "This phone replies with coordinates and a map link. Internet is not required for that loop. " +
                        "Only numbers in your trusted list can request a location.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

}
