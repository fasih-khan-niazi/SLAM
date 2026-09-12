package com.slam.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slam.app.R
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.security.DeviceUnlock
import com.slam.app.security.PinCloudSync
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import com.slam.app.sms.EmergencyScheduler
import com.slam.app.sms.PhoneNumbers
import com.slam.app.ui.components.LocalSlamToastHostState
import com.slam.app.ui.components.SlamBanner
import com.slam.app.ui.components.SlamButtonStyle
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamLottie
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSkeleton
import com.slam.app.ui.components.SlamStatusTone
import com.slam.app.ui.components.SlamToastTone
import kotlinx.coroutines.launch
import kotlin.math.ceil

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
    val pinStore = remember { PinStore.get(context) }
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
    var openSettingsForBackground by rememberSaveable { mutableStateOf(false) }

    val prerequisites = state.prerequisites ?: CorePrerequisites.status(context)
    val commandTemplate = "${state.smsPrefix} <PIN> LOCATE"
    val recoverablePin = remember(state.pinReady) { pinStore.recoverablePin() }
    val commandReady = recoverablePin?.let { "${state.smsPrefix} $it LOCATE" } ?: commandTemplate

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

    fun openAppPermissionSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun copyLocateCommand() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SLAM command", commandReady))
        scope.launch {
            toast.show(
                if (recoverablePin != null) "Copied locate command." else "Copied template. Replace <PIN> with your PIN.",
                SlamToastTone.SUCCESS,
            )
        }
    }

    fun requestChangePin() {
        val activity = context as? FragmentActivity
        if (activity == null) {
            changePinOpen = true
            return
        }
        DeviceUnlock.authenticate(
            activity = activity,
            title = "Unlock to change PIN",
            subtitle = "Confirm it is you before changing the tracking PIN.",
            onSuccess = { changePinOpen = true },
            onFailed = { message ->
                scope.launch { toast.show(message, SlamToastTone.WARNING) }
            },
        )
    }

    suspend fun addTrustedNumber(label: String, number: String): Boolean {
        if (!PhoneNumbers.isValid(number)) {
            formError = "Phone must be 11 or 12 digits."
            return false
        }
        if (db.trustedNumbers().count() >= state.maxContacts) {
            formError = "This plan allows ${state.maxContacts} trusted number${if (state.maxContacts == 1) "" else "s"}."
            return false
        }
        val normalized = PhoneNumbers.last10(number)
        if (db.trustedNumbers().findByNormalized(normalized) != null) {
            formError = "That number is already on the list."
            return false
        }
        db.trustedNumbers().insert(
            TrustedNumberEntity(
                label = label.trim(),
                number = number.trim(),
                normalized = normalized,
            ),
        )
        viewModel.reloadLocal()
        toast.show("Trusted number added", SlamToastTone.SUCCESS)
        return true
    }

    val contactsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val picked = readPhoneContact(context, uri) ?: run {
            scope.launch { toast.show("Could not read that contact.", SlamToastTone.WARNING) }
            return@rememberLauncherForActivityResult
        }
        if (!PhoneNumbers.isValid(picked.number)) {
            newLabel = picked.label
            newNumber = picked.number
            contactSheetOpen = true
            formError = "Phone must be 11 or 12 digits."
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            if (addTrustedNumber(picked.label, picked.number)) {
                newLabel = ""
                newNumber = ""
            } else {
                newLabel = picked.label
                newNumber = picked.number
                contactSheetOpen = true
            }
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshDeviceState()
        val status = CorePrerequisites.status(context)
        if (!status.backgroundLocationGranted) {
            openSettingsForBackground = true
            scope.launch {
                toast.show(
                    "Background location still needed. Tap the button again to open app settings, then choose Allow all the time.",
                    SlamToastTone.WARNING,
                )
            }
        } else if (status.listenerReady && pinStore.hasPin()) {
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
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAll()
                if (CorePrerequisites.status(context).backgroundLocationGranted) {
                    openSettingsForBackground = false
                }
            }
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
        missingPermissions.isNotEmpty() -> "Needed: ${missingPermissions.joinToString()}"
        !prerequisites.locationServicesEnabled -> "Turn on Location in system settings."
        else -> "Ready"
    }

    PullToRefreshBox(
        isRefreshing = state.bootstrapped && state.syncing,
        onRefresh = { viewModel.refreshAll() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("Tracking", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "PIN, trusted numbers, listening",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            if (!state.bootstrapped) {
                SlamSkeleton(height = 96)
                Spacer(Modifier.height(12.dp))
                SlamSkeleton(height = 140)
            } else {
                if (state.pendingOutbox > 0) {
                    SlamBanner(
                        title = "Waiting to sync",
                        message = "${state.pendingOutbox} locate(s) will upload when online.",
                        tone = SlamStatusTone.WARNING,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (state.pinLocks.isNotEmpty()) {
                    val lockMessage = state.pinLocks.joinToString(", ") { lock ->
                        val minutes = ceil(lock.remainingMs / 60_000.0).toInt().coerceAtLeast(1)
                        "${lock.sender}, $minutes min left"
                    }
                    SlamBanner(
                        title = "PIN attempts blocked",
                        message = lockMessage,
                        tone = SlamStatusTone.DANGER,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                SlamCard {
                    Column(Modifier.padding(20.dp)) {
                        Text("Current plan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(state.planName, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (state.unlimited) {
                                "Unlimited location requests this period"
                            } else {
                                "${state.remaining ?: "-"} of ${state.limit ?: 5} requests remaining"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.syncing) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Updating in the background...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                                commandTemplate,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            if (state.listening) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SlamLottie(
                                        resId = R.raw.lottie_pulse,
                                        size = 36.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Listening on",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            SlamPrimaryButton(
                                text = if (state.listening) "Listening" else "Start listening",
                                enabled = !state.listening,
                                onClick = { confirmListen = true },
                            )
                            Spacer(Modifier.height(8.dp))
                            SlamPrimaryButton(
                                text = "Stop listening",
                                style = SlamButtonStyle.DESTRUCTIVE,
                                enabled = state.listening,
                                onClick = {
                                    SlamListenerService.stop(context)
                                    EmergencyPrefs(context).setOn(false)
                                    EmergencyScheduler.stop(context)
                                    viewModel.refreshDeviceState()
                                    scope.launch { toast.show("Listening stopped") }
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                            SlamPrimaryButton(
                                text = "Change PIN",
                                style = SlamButtonStyle.SECONDARY,
                                onClick = { requestChangePin() },
                            )
                            Spacer(Modifier.height(8.dp))
                            SlamPrimaryButton(
                                text = "Copy command",
                                style = SlamButtonStyle.SECONDARY,
                                onClick = { copyLocateCommand() },
                            )
                        } else {
                            Text(
                                "${state.pinMinLength}-${state.pinMaxLength} digits, saved to your account",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            SlamField(
                                value = pinInput,
                                onValueChange = { value ->
                                    if (value.length <= state.pinMaxLength && value.all { it.isDigit() }) {
                                        pinInput = value
                                    }
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
                                        scope.launch {
                                            PinCloudSync.pushCurrent(context)
                                            toast.show("PIN saved", SlamToastTone.SUCCESS)
                                        }
                                    } else {
                                        pinError =
                                            "PIN must be ${state.pinMinLength} to ${state.pinMaxLength} digits"
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
                        if (state.contacts.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                SlamLottie(resId = R.raw.lottie_empty, size = 100.dp)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Add at least one trusted number to start.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(16.dp))
                                SlamPrimaryButton(
                                    text = "Add number",
                                    onClick = { contactSheetOpen = true },
                                )
                                Spacer(Modifier.height(8.dp))
                                SlamPrimaryButton(
                                    text = "Pick from contacts",
                                    style = SlamButtonStyle.SECONDARY,
                                    onClick = {
                                        contactsPicker.launch(
                                            Intent(
                                                Intent.ACTION_PICK,
                                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                            ),
                                        )
                                    },
                                )
                            }
                        } else {
                            Text(
                                "${state.contacts.size}/${state.maxContacts} on this plan. Only these can locate.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            state.contacts.forEach { contact ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text(
                                        contact.label.ifBlank { contact.number },
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(contact.number, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    SlamPrimaryButton(
                                        text = "Remove",
                                        style = SlamButtonStyle.SECONDARY,
                                        onClick = { pendingDelete = contact },
                                    )
                                }
                            }
                            if (state.contacts.size < state.maxContacts) {
                                Spacer(Modifier.height(8.dp))
                                SlamPrimaryButton(
                                    text = "Add number",
                                    onClick = { contactSheetOpen = true },
                                )
                                Spacer(Modifier.height(8.dp))
                                SlamPrimaryButton(
                                    text = "Pick from contacts",
                                    style = SlamButtonStyle.SECONDARY,
                                    onClick = {
                                        contactsPicker.launch(
                                            Intent(
                                                Intent.ACTION_PICK,
                                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                            ),
                                        )
                                    },
                                )
                            } else {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Trusted-number limit is full for this plan.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
                                            state.contacts.isEmpty() ->
                                                emergencyNote = "Add at least one trusted number."
                                            !store.canLocate() ->
                                                emergencyNote = "No locates left on this plan."
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
                            SlamPrimaryButton(
                                text = "Stop emergency",
                                style = if (state.emergencyOn) {
                                    SlamButtonStyle.DESTRUCTIVE
                                } else {
                                    SlamButtonStyle.SECONDARY
                                },
                                enabled = state.emergencyOn,
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
                                missingPermissions.any { it != "background location" } ->
                                    "Allow required permissions"
                                !prerequisites.backgroundLocationGranted && openSettingsForBackground ->
                                    "Open app settings for location"
                                !prerequisites.backgroundLocationGranted ->
                                    "Allow background location"
                                !prerequisites.locationServicesEnabled ->
                                    "Open Location settings"
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
                                    !prerequisites.backgroundLocationGranted && openSettingsForBackground ->
                                        openAppPermissionSettings()
                                    !prerequisites.backgroundLocationGranted ->
                                        backgroundPermissionLauncher.launch(
                                            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                        )
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
            SlamPrimaryButton(
                text = "How tracking works",
                style = SlamButtonStyle.SECONDARY,
                onClick = {
                    helpPage = 0
                    helpOpen = true
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmListen) {
        SlamModal(
            title = "Start listening?",
            message = "While listening is on, numbers on your trusted list can request this phone's location by SMS if they use your PIN. You can stop listening anytime.",
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
                    onValueChange = {
                        if (it.length <= state.pinMaxLength && it.all(Char::isDigit)) currentPin = it
                    },
                    label = "Current PIN",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                Spacer(Modifier.height(8.dp))
                SlamField(
                    value = newPin,
                    onValueChange = {
                        if (it.length <= state.pinMaxLength && it.all(Char::isDigit)) newPin = it
                    },
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
                                    PinCloudSync.pushCurrent(context)
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
                    label = "Phone (11-12 digits)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(16.dp))
                SlamPrimaryButton(
                    text = "Save number",
                    onClick = {
                        scope.launch {
                            if (addTrustedNumber(newLabel, newNumber)) {
                                newLabel = ""
                                newNumber = ""
                                contactSheetOpen = false
                            }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                SlamPrimaryButton(
                    text = "Pick from contacts",
                    style = SlamButtonStyle.SECONDARY,
                    onClick = {
                        contactsPicker.launch(
                            Intent(
                                Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            ),
                        )
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
            "Only numbers on your Trusted list can request a location. Without trusted numbers, nobody can locate this phone - listening will not start until you add at least one.",
            "Allow SMS, location (including Allow all the time), and notifications. Then tap Start listening. A confirmation appears before listening begins.",
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

private data class PickedPhoneContact(
    val label: String,
    val number: String,
)

private fun readPhoneContact(context: Context, uri: Uri): PickedPhoneContact? {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
    )
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        if (numberIndex < 0) return@use null
        val number = cursor.getString(numberIndex)?.trim().orEmpty()
        if (number.isBlank()) return@use null
        val label = if (nameIndex >= 0) cursor.getString(nameIndex)?.trim().orEmpty() else ""
        PickedPhoneContact(label = label, number = number)
    }
}
