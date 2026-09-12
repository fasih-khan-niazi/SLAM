package com.slam.app.ui.screens

import android.content.Intent
import android.net.Uri
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
    var confirmSignOut by remember { mutableStateOf(false) }
    val appearance by uiPreferences.appearance.collectAsStateWithLifecycle(AppearanceMode.LIGHT)
    val hapticsEnabled by uiPreferences.hapticsEnabled.collectAsStateWithLifecycle(true)
    val lastKnownEnabled by uiPreferences.lastKnownFallbackEnabled.collectAsStateWithLifecycle(true)
    val darkThemeOn = appearance != AppearanceMode.LIGHT

    LaunchedEffect(Unit) {
        preferBattery = session.preferBattery.first()
        displayName = session.displayName.first()
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
                Text("Appearance", style = MaterialTheme.typography.titleLarge)
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
                Text("Location preferences", style = MaterialTheme.typography.titleLarge)
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
            }
        }

        Spacer(Modifier.height(12.dp))
        SlamCard {
            Column(Modifier.padding(20.dp)) {
                Text("Device reliability", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Phone battery savers can stop background listening. Allow unrestricted use for SLAM.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SlamPrimaryButton(
                    text = "Open battery settings",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
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
                Text("Help", style = MaterialTheme.typography.titleLarge)
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
                Text("Account", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                if (displayName.isNotBlank()) {
                    Text(displayName, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    "Signing out stops tracking and clears local contacts and activity.",
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
