package com.slam.app.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.slam.app.ui.components.SlamBanner
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamStatusChip
import com.slam.app.ui.components.SlamStatusTone
import com.slam.app.ui.components.SlamTextButton
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val cachedName = session.displayName.first()
            val cachedRemaining = session.cachedRemaining.first()
            val lastLocation = SlamDatabase.get(getApplication()).lastLocations()
                .get(AccountIdentity.current(getApplication()))
            _state.value = _state.value.copy(
                name = cachedName,
                remaining = cachedRemaining.takeUnless { it == Int.MAX_VALUE },
                listening = ListenerPrefs(getApplication()).run {
                    isListening() && isServiceActive()
                },
                emergency = EmergencyPrefs(getApplication()).isOn(),
                lastLocationSummary = lastLocation?.let {
                    val ageMinutes = ((System.currentTimeMillis() - it.locationTimestamp)
                        .coerceAtLeast(0) / 60_000)
                    "Captured ${ageMinutes}m ago" +
                        it.accuracyMeters?.let { meters -> " · +/-${meters.toInt()}m" }.orEmpty()
                },
            )
            val token = session.token.first()
            if (token.isBlank()) {
                _state.value = _state.value.copy(loading = false)
                return@launch
            }
            try {
                val configCall = async { api.config() }
                val profileCall = async { api.me("Bearer $token") }
                val config = configCall.await().body()?.data
                session.cacheProductConfig(
                    config?.pinAttemptCap,
                    config?.pinWindowMinutes,
                    config?.emergencyEnabled,
                    config?.emergencyIntervalHours,
                    config?.smsPrefix,
                    config?.pinMinLength,
                    config?.pinMaxLength,
                )
                val profileResponse = profileCall.await()
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
                val contactCap = (profile?.subscription?.maxContacts ?: 1).coerceAtLeast(1)
                val contactDao = SlamDatabase.get(getApplication()).trustedNumbers()
                contactDao.all(AccountIdentity.current(getApplication()))
                    .drop(contactCap)
                    .forEach { contactDao.delete(it) }
                _state.value = _state.value.copy(
                    loading = false,
                    name = profile?.user?.name ?: cachedName,
                    plan = profile?.subscription?.planName ?: "Free",
                    remaining = profile?.subscription?.requestsRemaining,
                    limit = profile?.subscription?.monthlyLimit,
                    offline = false,
                    maintenance = config?.maintenance == true,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Hello${state.name.substringBefore(' ').takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty()}",
                    style = MaterialTheme.typography.headlineMedium,
                )
                SlamTextButton(text = "Refresh", onClick = viewModel::refresh)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Your protection status at a glance.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.offline) {
            item {
                SlamBanner(
                    title = "Offline",
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
                        Text("Protection", style = MaterialTheme.typography.titleLarge)
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
                    Text("Last successful location", style = MaterialTheme.typography.titleLarge)
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
                    Text(state.plan, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.limit == null) {
                            "Unlimited location events"
                        } else {
                            "${state.remaining ?: "—"} of ${state.limit} locates remaining"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SlamCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Emergency mode", style = MaterialTheme.typography.titleLarge)
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
