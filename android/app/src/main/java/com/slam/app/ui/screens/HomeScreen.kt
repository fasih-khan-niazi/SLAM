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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import com.slam.app.sms.EmergencyScheduler
import com.slam.app.sms.PhoneNumbers
import com.slam.app.ui.components.LocalSlamToastHostState
import com.slam.app.ui.components.SlamButtonStyle
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSkeleton
import com.slam.app.ui.components.SlamTextButton
import com.slam.app.ui.components.SlamToastTone
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: TrackingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val toast = LocalSlamToastHostState.current
    val pinStore = remember { PinStore(context) }
    val db = remember { SlamDatabase.get(context) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var helpOpen by remember { mutableStateOf(false) }
    var helpPage by remember { mutableIntStateOf(0) }
    var contactSheetOpen by remember { mutableStateOf(false) }
    var confirmListen by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var changePinOpen by remember { mutableStateOf(false) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var emergencyNote by remember { mutableStateOf<String?>(null) }
    var newLabel by remember { mutableStateOf("") }
    var newNumber by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TrustedNumberEntity?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val prerequisites = state.prerequisites ?: CorePrerequisites.status(context)

    suspend fun startListenerWhenReady(): Boolean {
        if (db.trustedNumbers().count() < 1) {
            toast.show("Add at least one trusted number first.", SlamToastTone.WARNING)
            return false
        }
        val started = SlamListenerService.start(context)
        viewModel.refreshDeviceState()
        toast.show(
            if (started) "Listening for SLAM commands." else "Allow the required permissions first.",
            if (started) SlamToastTone.SUCCESS else SlamToastTone.WARNING,
        )
        return started
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshDeviceState()
        if (CorePrerequisites.status(context).listenerReady && pinStore.hasPin()) {
            scope.launch { startListenerWhenReady() }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshDeviceState()
        val status = CorePrerequisites.status(context)
        if (status.locationGranted && !status.backgroundLocationGranted) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else if (status.listenerReady && pinStore.hasPin()) {
            scope.launch { startListenerWhenReady() }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDeviceState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val missingPermissions = buildList {
        if (!prerequisites.receiveSmsGranted) add("receive SMS")
        if (!prerequisites.sendSmsGranted) add("send SMS")
        if (!prerequisites.locationGranted) add("location")
        if (!prerequisites.backgroundLocationGranted) add("background location")
        if (!prerequisites.notificationsGranted) add("notifications")
    }
    val permissionNote = when {
        missingPermissions.isNotEmpty() -> "Still needed: ${missingPermissions.joinToString()}."
        !prerequisites.locationServicesEnabled ->
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

        if (!state.bootstrapped) {
            SlamSkeleton(height = 96)
            Spacer(Modifier.height(12.dp))
            SlamSkeleton(height = 140)
        } else {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Current plan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(state.planName, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.unlimited) "Unlimited location requests this period"
                        else "${state.remaining ?: "—"} of ${state.limit ?: 5} requests remaining",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.syncing) {
                        Spacer(Modifier.height(6.dp))
                        Text("Updating in the background…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Tracking PIN", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    if (state.pinReady) {
                        Text(
                            "PIN saved. Command: ${state.smsPrefix} <PIN> LOCATE",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SlamPrimaryButton(
                            text = if (state.listening) "Listening" else "Start listening",
                            enabled = !state.listening,
                            onClick = { confirmListen = true },
                        )
                        Spacer(Modifier.height(8.dp))
                        SlamTextButton(
                            text = "Stop listening",
                            onClick = {
                                SlamListenerService.stop(context)
                                EmergencyPrefs(context).setOn(false)
                                EmergencyScheduler.stop(context)
                                viewModel.refreshDeviceState()
                                scope.launch { toast.show("Listening stopped") }
                            },
                        )
                        SlamTextButton(text = "Change PIN", onClick = { changePinOpen = true })
                    } else {
                        Text(
                            "Set a ${state.pinMinLength}–${state.pinMaxLength} digit PIN used in SMS requests.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        SlamField(
                            value = pinInput,
                            onValueChange = { value ->
                                if (value.length <= state.pinMaxLength && value.all { it.isDigit() }) pinInput = value
                            },
                            label = "PIN",
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Spacer(Modifier.height(16.dp))
                        SlamPrimaryButton(
                            text = "Save PIN",
                            onClick = {
                                if (pinStore.setPin(pinInput, state.pinMinLength, state.pinMaxLength)) {
                                    pinInput = ""
                                    viewModel.reloadLocal()
                                    scope.launch { toast.show("PIN saved", SlamToastTone.SUCCESS) }
                                } else {
                                    pinError = "PIN must be ${state.pinMinLength} to ${state.pinMaxLength} digits"
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
                        if (state.contacts.isEmpty()) {
                            "Add a trusted number before listening can start. Plan allows ${state.maxContacts}."
                        } else {
                            "Only these numbers can request location. ${state.contacts.size} of ${state.maxContacts} used."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    state.contacts.forEach { contact ->
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
                    if (state.contacts.size < state.maxContacts) {
                        SlamPrimaryButton(text = "Add number", onClick = { contactSheetOpen = true })
                    } else {
                        Text(
                            "Trusted-number limit is full for this plan.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.pinReady && state.emergencyAllowed) {
                Spacer(Modifier.height(12.dp))
                SlamCard {
                    Column(Modifier.padding(20.dp)) {
                        Text("Emergency", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.emergencyOn) {
                                "Sending to trusted numbers every ${state.emergencyHours} hour${if (state.emergencyHours == 1) "" else "s"}."
                            } else {
                                "Timed updates to trusted numbers every ${state.emergencyHours} hour${if (state.emergencyHours == 1) "" else "s"}."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.emergencyLastResult.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Last result: ${state.emergencyLastResult}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (state.emergencyOn && state.emergencyNextRun > 0L) {
                            Text(
                                "Next run: ${
                                    java.text.DateFormat.getDateTimeInstance(
                                        java.text.DateFormat.SHORT,
                                        java.text.DateFormat.SHORT,
                                    ).format(java.util.Date(state.emergencyNextRun))
                                }",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        SlamPrimaryButton(
                            text = if (state.emergencyOn) "Emergency on" else "Start emergency",
                            enabled = !state.emergencyOn,
                            onClick = {
                                scope.launch {
                                    when {
                                        !state.listening -> emergencyNote = "Start listening first."
                                        state.contacts.isEmpty() -> emergencyNote = "Add at least one trusted number."
                                        !store.canLocate() -> emergencyNote = "No locates left on this plan."
                                        else -> {
                                            EmergencyPrefs(context).setOn(true)
                                            EmergencyScheduler.pingNow(context)
                                            EmergencyScheduler.start(context, store.cachedEmergencyHours())
                                            emergencyNote = null
                                            toast.show("Emergency started", SlamToastTone.SUCCESS)
                                            viewModel.refreshDeviceState()
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
                                viewModel.refreshDeviceState()
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
                            !prerequisites.backgroundLocationGranted -> "Allow background location"
                            !prerequisites.locationServicesEnabled -> "Open Location settings"
                            else -> "Permissions granted"
                        },
                        enabled = !prerequisites.listenerReady || !prerequisites.locationServicesEnabled,
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
                                !prerequisites.backgroundLocationGranted ->
                                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                !prerequisites.locationServicesEnabled ->
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SlamTextButton(
            text = "How tracking works",
            onClick = {
                helpPage = 0
                helpOpen = true
            },
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmListen) {
        SlamModal(
            title = "Start listening?",
            message = "While listening is on, numbers on your trusted list can request this phone’s location by SMS if they use your PIN. You can stop listening anytime.",
            confirmLabel = "Start listening",
            onConfirm = {
                confirmListen = false
                scope.launch { startListenerWhenReady() }
            },
            onDismiss = { confirmListen = false },
        )
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
                    onValueChange = { if (it.length <= state.pinMaxLength && it.all(Char::isDigit)) currentPin = it },
                    label = "Current PIN",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(8.dp))
                SlamField(
                    value = newPin,
                    onValueChange = { if (it.length <= state.pinMaxLength && it.all(Char::isDigit)) newPin = it },
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
                            !pinStore.setPin(newPin, state.pinMinLength, state.pinMaxLength) ->
                                scope.launch {
                                    toast.show(
                                        "New PIN must be ${state.pinMinLength} to ${state.pinMaxLength} digits.",
                                        SlamToastTone.WARNING,
                                    )
                                }
                            else -> {
                                currentPin = ""
                                newPin = ""
                                changePinOpen = false
                                scope.launch {
                                    db.failedPins().clear()
                                    viewModel.reloadLocal()
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
                            if (db.trustedNumbers().count() >= state.maxContacts) {
                                formError = "This plan allows ${state.maxContacts} trusted number${if (state.maxContacts == 1) "" else "s"}."
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
                            viewModel.reloadLocal()
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
                    viewModel.reloadLocal()
                    viewModel.refreshDeviceState()
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
        val pages = listOf(
            "Send an SMS like this:\n\n${state.smsPrefix} 1234 LOCATE\n\nUse your real PIN. The tracked phone replies with a clear location message and map link.",
            "Only numbers on your Trusted list can request a location. Add them on this Tracking tab before you start listening.",
            "Allow SMS, location (including background), and notifications. Then tap Start listening. A confirmation appears before listening begins.",
            "Emergency sends timed updates to every trusted number and uses one locate per run, not one per recipient. Stop it anytime from this tab.",
        )
        ModalBottomSheet(
            onDismissRequest = { helpOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    "How tracking works · ${helpPage + 1}/${pages.size}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    pages[helpPage],
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (helpPage > 0) {
                        SlamPrimaryButton(
                            text = "Back",
                            style = SlamButtonStyle.SECONDARY,
                            onClick = { helpPage -= 1 },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    SlamPrimaryButton(
                        text = if (helpPage == pages.lastIndex) "Finish" else "Next",
                        onClick = {
                            if (helpPage == pages.lastIndex) {
                                helpOpen = false
                            } else {
                                helpPage += 1
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
