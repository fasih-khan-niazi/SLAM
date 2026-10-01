package com.slam.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slam.app.data.AppearanceMode
import com.slam.app.data.SessionStore
import com.slam.app.data.UiPreferences
import com.slam.app.ui.components.LocalSlamToastHostState
import com.slam.app.ui.components.SlamButtonStyle
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamSwitchRow
import com.slam.app.ui.components.SlamToastTone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.slam.app.data.AccountIdentity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.location.QuietLocationPrefs
import com.slam.app.location.formatAge

@Composable
fun SettingsScreen(
    onSignOut: () -> Unit = {},
    onShowOnboarding: () -> Unit = {},
) {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val uiPreferences = remember { UiPreferences(context) }
    val scope = rememberCoroutineScope()
    val toast = LocalSlamToastHostState.current

    var preferBattery by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var planName by remember { mutableStateOf("Free") }
    var confirmSignOut by remember { mutableStateOf(false) }
    val appearance by uiPreferences.appearance.collectAsStateWithLifecycle(AppearanceMode.LIGHT)
    val hapticsEnabled by uiPreferences.hapticsEnabled.collectAsStateWithLifecycle(true)
    val lastKnownEnabled by uiPreferences.lastKnownFallbackEnabled.collectAsStateWithLifecycle(true)
    val darkThemeOn = appearance != AppearanceMode.LIGHT
    val lifecycleOwner = LocalLifecycleOwner.current
    val accountId = remember { AccountIdentity.current(context) }
    val quietPrefs = remember { QuietLocationPrefs(context) }
    val savedLocation by remember {
        SlamDatabase.get(context).lastLocations().observe(accountId)
    }.collectAsStateWithLifecycle(null)
    var quietRun by remember { mutableStateOf(0L) }
    var quietResult by remember { mutableStateOf("") }
    var batteryUnrestricted by remember { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(Unit) {
        quietPrefs.changes().collect {
            quietRun = quietPrefs.lastRun(accountId)
            quietResult = quietPrefs.lastResult(accountId)
        }
    }
    DisposableEffect(lifecycleOwner) {
        fun check() {
            val power = context.getSystemService(PowerManager::class.java)
            batteryUnrestricted = power?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        check()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) check()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        preferBattery = session.preferBattery.first()
        displayName = session.displayName.first()
        planName = session.cachedPlanName()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Appearance, reliability, and account.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        SlamCard {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                SlamSwitchRow(
                    title = "Dark theme",
                    subtitle = "Off is Light. On is Dark.",
                    checked = darkThemeOn,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            uiPreferences.setDarkTheme(enabled)
                            toast.show(if (enabled) "Dark theme on" else "Light theme on", SlamToastTone.SUCCESS)
                        }
                    },
                )
                SlamSwitchRow(
                    title = "Haptic feedback",
                    subtitle = "Vibration on taps and confirmations.",
                    checked = hapticsEnabled,
                    onCheckedChange = {
                        scope.launch {
                            uiPreferences.setHapticsEnabled(it)
                            toast.show(if (it) "Haptics on" else "Haptics off")
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SlamCard {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Location preferences",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                SlamSwitchRow(
                    title = "Prefer battery",
                    subtitle = "Try a lower-power fix first. Also used under about 15% battery.",
                    checked = preferBattery,
                    onCheckedChange = {
                        preferBattery = it
                        scope.launch { session.setPreferBattery(it) }
                    },
                )
                SlamSwitchRow(
                    title = "Use last known location",
                    subtitle = "If live location fails, send the saved fix with an age warning.",
                    checked = lastKnownEnabled,
                    onCheckedChange = {
                        scope.launch {
                            uiPreferences.setLastKnownFallbackEnabled(it)
                            toast.show(if (it) "Last-known enabled" else "Last-known disabled")
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                val saved = savedLocation
                Text(
                    if (saved == null) {
                        "No saved location yet. SLAM saves one quietly about every 15 minutes while listening is on."
                    } else {
                        val age = formatAge((nowMs - saved.locationTimestamp).coerceAtLeast(0))
                        val accuracy = saved.accuracyMeters?.let { " (±${it.toInt()} m)" }.orEmpty()
                        "Saved location: ${if (age == "just now") age else "$age ago"}$accuracy"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (quietRun > 0L) {
                    val runAge = formatAge((nowMs - quietRun).coerceAtLeast(0))
                    Text(
                        "Last background check: ${if (runAge == "just now") runAge else "$runAge ago"}. $quietResult.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SlamCard {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Device reliability",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Phone battery savers can stop background listening. Allow unrestricted use for SLAM.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (batteryUnrestricted) {
                        "Battery: unrestricted for SLAM."
                    } else {
                        "Battery: may be restricted. Background refresh and listening can be delayed or stopped."
                    },
                    color = if (batteryUnrestricted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.height(12.dp))
                SlamPrimaryButton(
                    text = if (batteryUnrestricted) "Open battery settings" else "Allow unrestricted battery",
                    onClick = {
                        val direct = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        if (batteryUnrestricted) {
                            context.startActivity(list)
                        } else {
                            runCatching { context.startActivity(direct) }
                                .onFailure { context.startActivity(list) }
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choose unrestricted or no restrictions for SLAM if asked.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                SlamPrimaryButton(
                    text = "Open app settings",
                    style = SlamButtonStyle.SECONDARY,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SlamCard {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Help",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Replay the short setup guide anytime.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SlamPrimaryButton(
                    text = "Show onboarding",
                    style = SlamButtonStyle.SECONDARY,
                    onClick = onShowOnboarding,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SlamCard {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Current plan",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    planName.ifBlank { "Free" },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "To change your plan, open the SLAM web portal on a computer or phone browser.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SlamCard {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                if (displayName.isNotBlank()) {
                    Text(displayName, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "Signing out stops tracking. Your setup stays saved on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SlamPrimaryButton(
                    text = "Sign out",
                    style = SlamButtonStyle.DESTRUCTIVE,
                    onClick = { confirmSignOut = true },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmSignOut) {
        SlamModal(
            title = "Sign out and stop tracking?",
            message = "SLAM will stop listening and emergency updates. Your PIN, trusted contacts, activity and settings stay saved on this phone.",
            confirmLabel = "Sign out",
            destructive = true,
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}
