package com.slam.app.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slam.app.BuildConfig
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.data.remote.SubscriptionInfo
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import com.slam.app.sms.EmergencyScheduler
import com.slam.app.sms.PhoneNumbers
import com.slam.app.ui.components.LocalSlamToastHostState
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSkeleton
import com.slam.app.ui.components.SlamTextButton
import com.slam.app.ui.components.SlamToastTone
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val toast = LocalSlamToastHostState.current
    val pinStore = remember { PinStore(context) }
    val db = remember { SlamDatabase.get(context) }

    var loading by remember { mutableStateOf(true) }
    var subscription by remember { mutableStateOf<SubscriptionInfo?>(null) }
    var helpOpen by remember { mutableStateOf(false) }
    var contactSheetOpen by remember { mutableStateOf(false) }
    var pinReady by remember { mutableStateOf(pinStore.hasPin()) }
    var pinInput by remember { mutableStateOf("") }
    var changePinOpen by remember { mutableStateOf(false) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var listening by remember {
        mutableStateOf(ListenerPrefs(context).isListening() && ListenerPrefs(context).isServiceActive())
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
    var contacts by remember { mutableStateOf(listOf<TrustedNumberEntity>()) }
    var maxContacts by remember { mutableStateOf(1) }
    var newLabel by remember { mutableStateOf("") }
    var newNumber by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TrustedNumberEntity?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
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

    suspend fun reloadContacts() {
        contacts = db.trustedNumbers().all()
        maxContacts = store.cachedMaxContacts()
    }

    suspend fun startListenerWhenReady(): Boolean {
        if (db.trustedNumbers().count() < 1) {
            toast.show("Add at least one trusted number first.", SlamToastTone.WARNING)
            return false
        }
        val started = SlamListenerService.start(context)
        listening = started
        toast.show(
            if (started) "Listening for SLAM commands." else "Allow the required permissions first.",
            if (started) SlamToastTone.SUCCESS else SlamToastTone.WARNING,
        )
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
        ActivityResultContracts.RequestMultiplePermissions(),
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
        try {
            val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
            coroutineScope {
                val config = async { runCatching { api.config().body()?.data }.getOrNull() }.await()
                store.cacheProductConfig(
                    config?.pinAttemptCap,
                    config?.pinWindowMinutes,
                    config?.emergencyEnabled,
                    config?.emergencyIntervalHours,
                    config?.smsPrefix,
                    config?.pinMinLength,
                    config?.pinMaxLength,
                )
                if (token.isNotBlank()) {
                    val response = runCatching { api.me("Bearer $token") }.getOrNull()
                    val data = response?.body()?.data
                    if (response?.isSuccessful == true && data != null) {
                        subscription = data.subscription
                        store.cacheUsage(data.subscription)
                    }
                }
            }
        } catch (_: Exception) {
            // Offline tracking still works from cache.
        } finally {
            emergencyAllowed = store.cachedEmergencyEnabled()
            emergencyHours = store.cachedEmergencyHours()
            pinMinLength = store.cachedPinMinLength().coerceIn(4, 6)
            pinMaxLength = store.cachedPinMaxLength().coerceIn(pinMinLength, 6)
            smsPrefix = store.cachedSmsPrefix()
            reloadContacts()
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
            "Permissions granted. Location is off — SLAM will send last known with a clear age warning when available."
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
            "PIN, trusted numbers, listening, and emergency live here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        if (loading) {
            SlamSkeleton(height = 96)
            Spacer(Modifier.height(12.dp))
            SlamSkeleton(height = 140)
            Spacer(Modifier.height(12.dp))
            SlamSkeleton(height = 120)
        } else {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Current plan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(subscription?.planName ?: "Free", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    val limit = subscription?.monthlyLimit
                    val unlimited = cachedUnlimited || (limit == null && subscription?.planName == "Premium")
                    Text(
                        if (unlimited) "Unlimited location requests this period"
                        else "${cachedRemaining ?: "—"} of ${limit ?: 5} requests remaining",
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
                            "PIN saved. Command: $smsPrefix <PIN> LOCATE",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SlamPrimaryButton(
                            text = if (listening) "Listening" else "Start listening",
                            enabled = !listening,
                            onClick = { scope.launch { startListenerWhenReady() } },
                        )
                        Spacer(Modifier.height(8.dp))
                        SlamTextButton(
                            text = "Stop listening",
                            onClick = {
                                SlamListenerService.stop(context)
                                EmergencyPrefs(context).setOn(false)
                                EmergencyScheduler.stop(context)
                                listening = false
                                emergencyOn = false
                                scope.launch { toast.show("Listening stopped") }
                            },
                        )
                        SlamTextButton(text = "Change PIN", onClick = { changePinOpen = true })
                    } else {
                        Text(
                            "Set a $pinMinLength–$pinMaxLength digit PIN used in SMS requests.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SlamField(
                            value = pinInput,
                            onValueChange = { value ->
                                if (value.length <= pinMaxLength && value.all { it.isDigit() }) pinInput = value
                            },
                            label = "PIN",
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Spacer(Modifier.height(16.dp))
                        SlamPrimaryButton(
                            text = "Save PIN",
                            onClick = {
                                if (pinStore.setPin(pinInput, pinMinLength, pinMaxLength)) {
                                    pinReady = true
                                    pinInput = ""
                                    scope.launch { toast.show("PIN saved", SlamToastTone.SUCCESS) }
                                } else {
                                    pinError = "PIN must be $pinMinLength to $pinMaxLength digits"
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Trusted numbers", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (contacts.isEmpty()) {
                            "Add a trusted number before listening can start. Plan allows $maxContacts."
                        } else {
                            "Only these numbers can request location. ${contacts.size} of $maxContacts used."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    contacts.forEach { contact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    contact.label.ifBlank { contact.number },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(contact.number, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SlamTextButton(text = "Remove", onClick = { pendingDelete = contact })
                        }
                    }
                    if (contacts.size < maxContacts) {
                        SlamPrimaryButton(text = "Add number", onClick = { contactSheetOpen = true })
                    } else {
                        Text(
                            "Trusted-number limit is full for this plan.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                "Sending to trusted numbers every $emergencyHours hour${if (emergencyHours == 1) "" else "s"}."
                            } else {
                                "Timed updates to trusted numbers every $emergencyHours hour${if (emergencyHours == 1) "" else "s"}."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (emergencyLastResult.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("Last result: $emergencyLastResult", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            text = if (emergencyOn) "Emergency on" else "Start emergency",
                            enabled = !emergencyOn,
                            onClick = {
                                scope.launch {
                                    when {
                                        !listening -> emergencyNote = "Start listening first."
                                        contacts.isEmpty() -> emergencyNote = "Add at least one trusted number."
                                        !store.canLocate() -> emergencyNote = "No locates left on this plan."
                                        else -> {
                                            EmergencyPrefs(context).setOn(true)
                                            EmergencyScheduler.pingNow(context)
                                            EmergencyScheduler.start(context, store.cachedEmergencyHours())
                                            emergencyOn = true
                                            emergencyNote = null
                                            toast.show("Emergency started", SlamToastTone.SUCCESS)
                                            refreshDeviceState()
                                        }
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        SlamTextButton(
                            text = "Stop emergency",
                            onClick = {
                                EmergencyPrefs(context).setOn(false)
                                EmergencyScheduler.stop(context)
                                emergencyOn = false
                                scope.launch { toast.show("Emergency stopped") }
                            },
                        )
                        emergencyNote?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
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
                            missingPermissions.any { it != "background location" } -> "Allow required permissions"
                            !prerequisiteStatus.backgroundLocationGranted -> "Allow background location"
                            !prerequisiteStatus.locationServicesEnabled -> "Open Location settings"
                            else -> "Permissions granted"
                        },
                        enabled = !prerequisiteStatus.listenerReady || !prerequisiteStatus.locationServicesEnabled,
                        onClick = {
                            when {
                                missingPermissions.any { it != "background location" } ->
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.RECEIVE_SMS,
                                            Manifest.permission.SEND_SMS,
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ),
                                    )
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
        SlamTextButton(text = "How tracking works", onClick = { helpOpen = true })
        Spacer(Modifier.height(24.dp))
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

    if (changePinOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                changePinOpen = false
                currentPin = ""
                newPin = ""
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Change PIN", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                SlamField(
                    value = currentPin,
                    onValueChange = { if (it.length <= pinMaxLength && it.all(Char::isDigit)) currentPin = it },
                    label = "Current PIN",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(8.dp))
                SlamField(
                    value = newPin,
                    onValueChange = { if (it.length <= pinMaxLength && it.all(Char::isDigit)) newPin = it },
                    label = "New PIN",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(16.dp))
                SlamPrimaryButton(
                    text = "Update PIN",
                    onClick = {
                        when {
                            !pinStore.verify(currentPin) ->
                                scope.launch { toast.show("Current PIN is incorrect.", SlamToastTone.DANGER) }
                            !pinStore.setPin(newPin, pinMinLength, pinMaxLength) ->
                                scope.launch {
                                    toast.show(
                                        "New PIN must be $pinMinLength to $pinMaxLength digits.",
                                        SlamToastTone.WARNING,
                                    )
                                }
                            else -> {
                                currentPin = ""
                                newPin = ""
                                changePinOpen = false
                                scope.launch {
                                    db.failedPins().clear()
                                    toast.show("PIN updated", SlamToastTone.SUCCESS)
                                }
                            }
                        }
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (contactSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { contactSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Add trusted number", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                SlamField(value = newLabel, onValueChange = { newLabel = it }, label = "Name (optional)")
                Spacer(Modifier.height(8.dp))
                SlamField(
                    value = newNumber,
                    onValueChange = { newNumber = it },
                    label = "Phone (11–12 digits)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(16.dp))
                SlamPrimaryButton(
                    text = "Save number",
                    onClick = {
                        if (!PhoneNumbers.isValid(newNumber)) {
                            formError = "Phone must be 11 or 12 digits."
                            return@SlamPrimaryButton
                        }
                        scope.launch {
                            if (db.trustedNumbers().count() >= maxContacts) {
                                formError = "This plan allows $maxContacts trusted number${if (maxContacts == 1) "" else "s"}."
                                return@launch
                            }
                            val normalized = PhoneNumbers.last10(newNumber)
                            if (db.trustedNumbers().findByNormalized(normalized) != null) {
                                formError = "That number is already on the list."
                                return@launch
                            }
                            db.trustedNumbers().insert(
                                TrustedNumberEntity(
                                    label = newLabel.trim(),
                                    number = newNumber.trim(),
                                    normalized = normalized,
                                ),
                            )
                            newLabel = ""
                            newNumber = ""
                            contactSheetOpen = false
                            reloadContacts()
                            toast.show("Trusted number added", SlamToastTone.SUCCESS)
                        }
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    formError?.let {
        SlamModal(
            title = "Cannot add number",
            message = it,
            confirmLabel = "OK",
            cancelLabel = "",
            onConfirm = { formError = null },
            onDismiss = { formError = null },
        )
    }

    pendingDelete?.let { contact ->
        SlamModal(
            title = "Remove this number?",
            message = "${contact.label.ifBlank { contact.number }} will lose locate access.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                scope.launch {
                    db.trustedNumbers().delete(contact)
                    val empty = db.trustedNumbers().count() == 0
                    if (empty) SlamListenerService.stop(context)
                    pendingDelete = null
                    reloadContacts()
                    refreshDeviceState()
                    toast.show(
                        if (empty) "Number removed. Tracking stopped." else "Number removed.",
                        SlamToastTone.SUCCESS,
                    )
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (helpOpen) {
        ModalBottomSheet(
            onDismissRequest = { helpOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("How tracking works", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Text $smsPrefix, then your PIN, then LOCATE. Example: $smsPrefix 1234 LOCATE. " +
                        "This phone replies with a clear location SMS. Only trusted numbers can request a location.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
