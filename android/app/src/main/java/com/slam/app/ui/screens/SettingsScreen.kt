package com.slam.app.ui.screens

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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.slam.app.data.SessionStore
import com.slam.app.data.local.LocationHistoryEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.security.PinStore
import com.slam.app.sms.PhoneNumbers
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSkeleton
import com.slam.app.ui.components.SlamTextButton
import com.slam.app.ui.components.slamHaptic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val db = remember { SlamDatabase.get(context) }
    val pinStore = remember { PinStore(context) }
    val session = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()
    val historyFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    var loading by remember { mutableStateOf(true) }
    var contacts by remember { mutableStateOf(listOf<TrustedNumberEntity>()) }
    var history by remember { mutableStateOf(listOf<LocationHistoryEntity>()) }
    var failedCount by remember { mutableStateOf(0) }
    var preferBattery by remember { mutableStateOf(false) }

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
            failedCount = db.failedPins().count()
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
        SlamTextButton(text = "Back", onClick = onBack)
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
                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) currentPin = it },
                        label = "Current PIN",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Spacer(Modifier.height(8.dp))
                    SlamField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) newPin = it },
                        label = "New PIN (4–6 digits)",
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    Spacer(Modifier.height(12.dp))
                    SlamPrimaryButton(
                        text = "Update PIN",
                        onClick = {
                            when {
                                !pinStore.verify(currentPin) -> pinMessage = "Current PIN is incorrect."
                                !pinStore.setPin(newPin) -> pinMessage = "New PIN must be 4 to 6 digits."
                                else -> {
                                    currentPin = ""
                                    newPin = ""
                                    pinMessage = "PIN updated."
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
                            "List is empty: any sender with the correct PIN can request location. Add a number to restrict access."
                        } else {
                            "Only these numbers can request location, even if they know the PIN."
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
                    SlamPrimaryButton(text = "Add number", onClick = { sheetOpen = true })
                }
            }

            Spacer(Modifier.height(12.dp))
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Location style", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Prefer battery")
                            Text(
                                "Skip GPS first. Also used automatically below 15% battery.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = preferBattery,
                            onCheckedChange = {
                                view.slamHaptic()
                                preferBattery = it
                                scope.launch { session.setPreferBattery(it) }
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Failed PIN attempts logged: $failedCount",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    pendingDelete = null
                    reload()
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}
