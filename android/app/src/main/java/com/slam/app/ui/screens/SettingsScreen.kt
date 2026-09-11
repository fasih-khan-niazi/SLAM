package com.slam.app.ui.screens

import android.content.Intent
import android.provider.Settings
import android.net.Uri
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
import com.slam.app.data.SessionStore
import com.slam.app.data.AppearanceMode
import com.slam.app.data.UiPreferences
import com.slam.app.data.local.LocationHistoryEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.security.PinStore
import com.slam.app.service.SlamListenerService
import com.slam.app.sms.PhoneNumbers
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSkeleton
import com.slam.app.ui.components.SlamTextButton
import com.slam.app.ui.components.LocalSlamSnackbarHostState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onSignOut: () -> Unit = {},
) {
    val context = LocalContext.current
    val db = remember { SlamDatabase.get(context) }
    val pinStore = remember { PinStore(context) }
    val session = remember { SessionStore(context) }
    val uiPreferences = remember { UiPreferences(context) }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSlamSnackbarHostState.current
    val historyFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    var loading by remember { mutableStateOf(true) }
    var contacts by remember { mutableStateOf(listOf<TrustedNumberEntity>()) }
    var history by remember { mutableStateOf(listOf<LocationHistoryEntity>()) }
    var failedCount by remember { mutableStateOf(0) }
    var pinCap by remember { mutableStateOf(3) }
    var pinWindowMin by remember { mutableStateOf(15) }
    var pinMinLength by remember { mutableStateOf(4) }
    var pinMaxLength by remember { mutableStateOf(6) }
    var maxContacts by remember { mutableStateOf(1) }
    var preferBattery by remember { mutableStateOf(false) }
    val appearance by uiPreferences.appearance.collectAsStateWithLifecycle(AppearanceMode.DARK)
    val hapticsEnabled by uiPreferences.hapticsEnabled.collectAsStateWithLifecycle(true)
    val lastKnownEnabled by uiPreferences.lastKnownFallbackEnabled.collectAsStateWithLifecycle(true)
    var confirmSignOut by remember { mutableStateOf(false) }

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var pinMessage by remember { mutableStateOf<String?>(null) }

    var sheetOpen by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newNumber by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<TrustedNumberEntity?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            contacts = db.trustedNumbers().all()
            history = db.locationHistory().latest()
            pinCap = session.cachedPinAttemptCap()
            pinWindowMin = session.cachedPinWindowMinutes()
            pinMinLength = session.cachedPinMinLength()
            pinMaxLength = session.cachedPinMaxLength()
            maxContacts = session.cachedMaxContacts()
            val windowStart = System.currentTimeMillis() - session.cachedPinWindowMs()
            db.failedPins().deleteOlderThan(windowStart)
            failedCount = db.failedPins().countSince(windowStart)
            preferBattery = session.preferBattery.first()
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        onBack?.let { SlamTextButton(text = "Back", onClick = it) }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Control who can request location and how this phone replies.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        if (loading) {
            SlamSkeleton(height = 88)
            Spacer(Modifier.height(12.dp))
            SlamSkeleton(height = 120)
        } else {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Change PIN", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    SlamField(
                        value = currentPin,
                        onValueChange = { if (it.length <= pinMaxLength && it.all { ch -> ch.isDigit() }) currentPin = it },
                        label = "Current PIN",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Spacer(Modifier.height(8.dp))
                    SlamField(
                        value = newPin,
                        onValueChange = { if (it.length <= pinMaxLength && it.all { ch -> ch.isDigit() }) newPin = it },
                        label = "New PIN ($pinMinLength–$pinMaxLength digits)",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Spacer(Modifier.height(12.dp))
                    SlamPrimaryButton(
                        text = "Update PIN",
                        onClick = {
                            when {
                                !pinStore.verify(currentPin) -> pinMessage = "Current PIN is incorrect."
                                !pinStore.setPin(newPin, pinMinLength, pinMaxLength) ->
                                    pinMessage = "New PIN must be $pinMinLength to $pinMaxLength digits."
                                else -> {
                                    currentPin = ""
                                    newPin = ""
                                    pinMessage = "PIN updated."
                                    scope.launch {
                                        db.failedPins().clear()
                                        reload()
                                        snackbar.showSnackbar("PIN updated securely.")
                                    }
                                }
                            }
                        },
                    )
                    pinMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            "Add a trusted number before tracking can start. Your plan allows $maxContacts trusted number${if (maxContacts == 1) "" else "s"}."
                        } else {
                            "Only these numbers can request location. ${contacts.size} of $maxContacts used on this plan."
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
                                Text(contact.label.ifBlank { contact.number }, style = MaterialTheme.typography.bodyLarge)
                                Text(contact.number, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            SlamTextButton(text = "Remove", onClick = { pendingDelete = contact })
                        }
                    }
                    if (contacts.size >= maxContacts) {
                        Text(
                            "This plan’s trusted-number limit is full. Remove a number or upgrade.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        SlamPrimaryButton(text = "Add number", onClick = { sheetOpen = true })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Location style", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    com.slam.app.ui.components.SlamSwitchRow(
                        title = "Prefer battery",
                        subtitle = "Skip GPS first. Also used automatically below 15% battery.",
                        checked = preferBattery,
                        onCheckedChange = {
                            preferBattery = it
                            scope.launch { session.setPreferBattery(it) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Wrong PIN texts in the last $pinWindowMin minutes: $failedCount of $pinCap. " +
                            "After the cap, locates stay silent until the window resets. Updating the PIN clears the count.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Device reliability", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android and Oppo battery controls can stop background tracking. Review both after installing an update.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    SlamPrimaryButton(
                        text = "Open SLAM app settings",
                        style = com.slam.app.ui.components.SlamButtonStyle.SECONDARY,
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    SlamTextButton(
                        text = "Review battery optimization",
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Recent locations", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    if (history.isEmpty()) {
                        Text("No replies stored yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        history.forEach { item ->
                            Text(
                                "${historyFormat.format(Date(item.createdAt))}  ${"%.4f".format(item.latitude)}, ${"%.4f".format(item.longitude)}  ${item.accuracy}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("App preferences", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Appearance · ${appearance.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth()) {
                        AppearanceMode.entries.forEach { mode ->
                            SlamTextButton(
                                text = mode.name.lowercase().replaceFirstChar { it.uppercase() } +
                                    if (mode == appearance) " (on)" else "",
                                onClick = { scope.launch { uiPreferences.setAppearance(mode) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    com.slam.app.ui.components.SlamSwitchRow(
                        title = "Haptic feedback",
                        subtitle = "Vibrate gently for controls and confirmations.",
                        checked = hapticsEnabled,
                        onCheckedChange = { scope.launch { uiPreferences.setHapticsEnabled(it) } },
                    )
                    com.slam.app.ui.components.SlamSwitchRow(
                        title = "Use last known location",
                        subtitle = "Clearly label and send SLAM's saved fix if a live fix is unavailable.",
                        checked = lastKnownEnabled,
                        onCheckedChange = { scope.launch { uiPreferences.setLastKnownFallbackEnabled(it) } },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Account", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Signing out stops tracking and clears this account's PIN, contacts and local activity.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    SlamPrimaryButton(
                        text = "Sign out",
                        style = com.slam.app.ui.components.SlamButtonStyle.DESTRUCTIVE,
                        onClick = { confirmSignOut = true },
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
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
                    label = "Phone number",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(16.dp))
                SlamPrimaryButton(
                    text = "Save number",
                    onClick = {
                        if (!PhoneNumbers.isValid(newNumber)) {
                            formError = "Enter a valid phone number (10–15 digits)."
                            return@SlamPrimaryButton
                        }
                        scope.launch {
                            val cap = session.cachedMaxContacts()
                            if (db.trustedNumbers().count() >= cap) {
                                formError = "This plan allows $cap trusted number${if (cap == 1) "" else "s"}. Remove one or upgrade."
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
                                )
                            )
                            newLabel = ""
                            newNumber = ""
                            sheetOpen = false
                            reload()
                            snackbar.showSnackbar("Trusted number added.")
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
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
            message = "${contact.label.ifBlank { contact.number }} will no longer be able to request location if the list stays non-empty.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                scope.launch {
                    db.trustedNumbers().delete(contact)
                    if (db.trustedNumbers().count() == 0) {
                        SlamListenerService.stop(context)
                    }
                    pendingDelete = null
                    reload()
                    snackbar.showSnackbar(
                        if (db.trustedNumbers().count() == 0) {
                            "Trusted number removed. Tracking stopped for safety."
                        } else {
                            "Trusted number removed."
                        },
                    )
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (confirmSignOut) {
        SlamModal(
            title = "Sign out and stop tracking?",
            message = "SLAM will stop listening and emergency updates. This account's PIN, trusted contacts, activity and pending location data will be removed from this phone.",
            confirmLabel = "Sign out and clear",
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}
