package com.slam.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slam.app.BuildConfig
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.AccountIdentity
import com.slam.app.data.AccountLifecycleManager
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.ui.components.LocalSlamHapticsEnabled
import com.slam.app.ui.components.SlamBanner
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamStatusChip
import com.slam.app.ui.components.SlamStatusTone
import com.slam.app.ui.components.slamHaptic
import com.slam.app.data.OutboxDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.app.Application

data class DashboardUiState(
    val loading: Boolean = true,
    val name: String = "",
    val plan: String = "Free",
    val remaining: Int? = null,
    val limit: Int? = 5,
    val listening: Boolean = false,
    val emergency: Boolean = false,
    val offline: Boolean = false,
    val sessionExpired: Boolean = false,
    val lastLocationSummary: String? = null,
    val maintenance: Boolean = false,
    val pendingOutbox: Int = 0,
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionStore(application)
    private val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.cachedRemaining.collectLatest { remaining ->
                _state.value = _state.value.copy(
                    remaining = remaining.takeUnless { it == Int.MAX_VALUE },
                )
            }
        }
        viewModelScope.launch {
            ListenerPrefs(getApplication()).listeningActiveFlow().collectLatest { listening ->
                _state.value = _state.value.copy(listening = listening)
            }
        }
        viewModelScope.launch {
            EmergencyPrefs(getApplication()).isOnFlow().collectLatest { emergency ->
                _state.value = _state.value.copy(emergency = emergency)
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val cachedName = runCatching { session.displayName.first() }.getOrDefault("")
                val cachedRemaining = runCatching { session.cachedRemaining.first() }.getOrDefault(5)
                val accountId = AccountIdentity.current(getApplication())
                val lastLocation = runCatching {
                    SlamDatabase.get(getApplication()).lastLocations().get(accountId)
                }.getOrNull()
                val pending = runCatching {
                    SlamDatabase.get(getApplication()).eventLedger().pending(accountId).size
                }.getOrDefault(0)
                _state.value = _state.value.copy(
                    name = cachedName,
                    remaining = cachedRemaining.takeUnless { it == Int.MAX_VALUE },
                    listening = runCatching {
                        ListenerPrefs(getApplication()).run { isListening() && isServiceActive() }
                    }.getOrDefault(false),
                    emergency = runCatching {
                        EmergencyPrefs(getApplication()).isOn()
                    }.getOrDefault(false),
                    pendingOutbox = pending,
                    lastLocationSummary = lastLocation?.let {
                        val ageMinutes = ((System.currentTimeMillis() - it.locationTimestamp)
                            .coerceAtLeast(0) / 60_000)
                        "Captured ${ageMinutes}m ago" +
                            it.accuracyMeters?.let { meters -> " · +/-${meters.toInt()}m" }.orEmpty()
                    },
                )
                val token = runCatching { session.token.first() }.getOrDefault("")
                if (token.isBlank()) {
                    _state.value = _state.value.copy(loading = false)
                    return@launch
                }

                runCatching { OutboxDispatcher(getApplication()).flush() }
                val pendingAfterFlush = runCatching {
                    SlamDatabase.get(getApplication()).eventLedger().pending(accountId).size
                }.getOrDefault(0)

                val config = runCatching { api.config().body()?.data }.getOrNull()
                if (config != null) {
                    session.cacheProductConfig(
                        config.pinAttemptCap,
                        config.pinWindowMinutes,
                        config.emergencyEnabled,
                        config.resolvedEmergencyMinutes(),
                        config.smsPrefix,
                        config.pinMinLength,
                        config.pinMaxLength,
                        config.maintenance,
                        config.paymentsEnabled,
                    )
                }

                val profileResponse = runCatching { api.me("Bearer $token") }.getOrNull()
                if (profileResponse == null) {
                    _state.value = _state.value.copy(
                        loading = false,
                        offline = true,
                        pendingOutbox = pendingAfterFlush,
                        maintenance = config?.maintenance == true || session.cachedMaintenance(),
                    )
                    return@launch
                }
                if (profileResponse.code() == 401) {
                    _state.value = _state.value.copy(loading = false, sessionExpired = true)
                    return@launch
                }
                val profile = profileResponse.body()?.data
                if (profile != null && AccountIdentity.current(getApplication()) ==
                    AccountIdentity.LEGACY_ACCOUNT_ID
                ) {
                    AccountLifecycleManager(getApplication()).establishSession(
                        token,
                        profile.user.name,
                        profile.user.id,
                    )
                }
                profile?.subscription?.let { session.cacheUsage(it) }
                val live = profile?.subscription?.forLiveUsage()
                val contactCap = (live?.maxContacts ?: 1).coerceAtLeast(1)
                runCatching {
                    val contactDao = SlamDatabase.get(getApplication()).trustedNumbers()
                    contactDao.all(AccountIdentity.current(getApplication()))
                        .drop(contactCap)
                        .forEach { contactDao.delete(it) }
                }
                val remainingAfter = session.cachedRemaining.first()
                _state.value = _state.value.copy(
                    loading = false,
                    name = profile?.user?.name ?: cachedName,
                    plan = live?.planName ?: "Free",
                    remaining = remainingAfter.takeUnless { it == Int.MAX_VALUE },
                    limit = live?.monthlyLimit,
                    offline = false,
                    maintenance = config?.maintenance == true ||
                        (config == null && session.cachedMaintenance()),
                    pendingOutbox = pendingAfterFlush,
                )
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, offline = true)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    onOpenTracking: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            val view = LocalView.current
            val haptics = LocalSlamHapticsEnabled.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Hello${state.name.substringBefore(' ').takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = {
                        view.slamHaptic(haptics)
                        viewModel.refresh()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Protection status at a glance.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.pendingOutbox > 0) {
            item {
                SlamBanner(
                    title = "Waiting to sync",
                    message = "${state.pendingOutbox} locate(s) will upload when online.",
                    tone = SlamStatusTone.WARNING,
                )
            }
        }
        if (state.offline) {
            item {
                SlamBanner(
                    title = "Sync issue",
                    message = "Showing saved device status. SMS tracking can still work.",
                    tone = SlamStatusTone.WARNING,
                )
            }
        }
        if (state.maintenance) {
            item {
                SlamBanner(
                    title = "Service maintenance",
                    message = "Account sync may be limited. Offline SMS tracking remains available.",
                    tone = SlamStatusTone.WARNING,
                )
            }
        }
        item {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Protection",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        SlamStatusChip(
                            text = if (state.listening) "Protected" else "Attention needed",
                            tone = if (state.listening) SlamStatusTone.SUCCESS else SlamStatusTone.WARNING,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (state.listening) {
                            "SLAM is listening for approved SMS location requests."
                        } else {
                            "Tracking is stopped. Open Tracking to review requirements."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SlamPrimaryButton(text = "Open tracking", onClick = onOpenTracking)
                }
            }
        }
        item {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Last successful location",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.lastLocationSummary
                            ?: "No location has been captured on this phone yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Current plan", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        state.plan,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.limit == null) {
                            "Unlimited location events"
                        } else {
                            "${state.remaining ?: "-"} of ${state.limit} locates remaining"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Emergency mode",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    SlamStatusChip(
                        text = if (state.emergency) "Active" else "Off",
                        tone = if (state.emergency) SlamStatusTone.DANGER else SlamStatusTone.NEUTRAL,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Each scheduled location event uses one locate, regardless of recipient count.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
