package com.slam.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.slam.app.R
import com.slam.app.data.local.LocationHistoryEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.BuildConfig
import com.slam.app.data.AccountIdentity
import com.slam.app.data.SessionStore
import com.slam.app.data.remote.NotificationItem
import com.slam.app.data.remote.RemoteLocationLog
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.ui.components.LocalSlamHapticsEnabled
import com.slam.app.ui.components.SlamBanner
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamLottie
import com.slam.app.ui.components.SlamStatusTone
import com.slam.app.ui.components.slamHaptic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import java.time.Instant
import java.io.IOException

data class ActivityEvent(
    val key: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestampMs: Long,
    val isLastKnown: Boolean,
)

data class ActivityUiState(
    val events: List<ActivityEvent> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val syncWarning: String? = null,
    val sessionExpired: Boolean = false,
)

class ActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
            val accountId = AccountIdentity.current(getApplication())
            val local = SlamDatabase.get(getApplication()).locationHistory().latest(accountId)
            _state.value = ActivityUiState(events = local.map { it.toEvent() })
            val token = SessionStore(getApplication()).token.first()
            if (token.isBlank()) {
                return@launch
            }

            val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
            var syncWarning: String? = null
            var sessionExpired = false
            var notifications = emptyList<NotificationItem>()
            var remote = emptyList<RemoteLocationLog>()

            try {
                val activity = api.locationActivity("Bearer $token")
                when {
                    activity.isSuccessful -> {
                        remote = activity.body()?.data?.logs.orEmpty()
                        upsertRemote(accountId, remote)
                    }
                    activity.code() == 401 -> sessionExpired = true
                    activity.code() == 404 || activity.code() == 501 -> Unit
                    activity.code() >= 500 ->
                        syncWarning = "Server activity is temporarily unavailable."
                }
            } catch (_: IOException) {
                syncWarning = "Could not reach the server. Showing saved activity."
            } catch (_: Exception) {
                syncWarning = "Could not refresh activity. Showing saved activity."
            }

            try {
                val response = api.notifications("Bearer $token")
                when {
                    response.isSuccessful -> notifications = response.body()?.data?.notifications.orEmpty()
                    response.code() == 401 -> sessionExpired = true
                }
            } catch (_: Exception) {
                // Notifications are secondary. Keep local activity without a misleading warning.
            }

            val refreshedLocal = SlamDatabase.get(getApplication()).locationHistory().latest(accountId)
            val merged = mergeEvents(refreshedLocal, remote)
            _state.value = ActivityUiState(
                events = merged,
                notifications = notifications,
                syncWarning = syncWarning,
                sessionExpired = sessionExpired,
            )
    }

    private suspend fun upsertRemote(accountId: String, remote: List<RemoteLocationLog>) {
        if (remote.isEmpty()) return
        val dao = SlamDatabase.get(getApplication()).locationHistory()
        for (log in remote) {
            val ts = parseTime(log.capturedAt) ?: parseTime(log.createdAt) ?: continue
            val existing = dao.findNear(accountId, log.latitude, log.longitude, ts)
            if (existing != null) continue
            dao.insert(
                LocationHistoryEntity(
                    accountId = accountId,
                    latitude = log.latitude,
                    longitude = log.longitude,
                    accuracy = log.accuracy ?: "LOW",
                    accuracyMeters = log.accuracyMeters,
                    locationTimestamp = ts,
                    isLastKnownFallback = log.source.equals("LAST_KNOWN", ignoreCase = true),
                    requestedBy = log.requestedBy ?: "remote",
                    createdAt = ts,
                ),
            )
        }
        dao.trim(accountId)
    }

    private fun mergeEvents(
        local: List<LocationHistoryEntity>,
        remote: List<RemoteLocationLog>,
    ): List<ActivityEvent> {
        val byKey = LinkedHashMap<String, ActivityEvent>()
        remote.map { it.toEvent() }.forEach { byKey[it.key] = it }
        local.map { it.toEvent() }.forEach { event ->
            byKey.putIfAbsent(event.key, event)
        }
        return byKey.values.sortedByDescending { it.timestampMs }
    }

    private fun LocationHistoryEntity.toEvent() = ActivityEvent(
        key = "local-$id",
        title = when {
            isLastKnownFallback -> "Last-known fallback"
            requestedBy == "emergency" -> "Emergency update"
            else -> "SMS location request"
        },
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        timestampMs = locationTimestamp ?: createdAt,
        isLastKnown = isLastKnownFallback,
    )

    private fun RemoteLocationLog.toEvent(): ActivityEvent {
        val ts = parseTime(capturedAt) ?: parseTime(createdAt) ?: 0L
        return ActivityEvent(
            key = "remote-$id",
            title = when {
                source.equals("LAST_KNOWN", ignoreCase = true) -> "Last-known fallback"
                requestedBy.equals("emergency", ignoreCase = true) -> "Emergency update"
                else -> "SMS location request"
            },
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracyMeters,
            timestampMs = ts,
            isLastKnown = source.equals("LAST_KNOWN", ignoreCase = true),
        )
    }

    private fun parseTime(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }
}

@Composable
fun ActivityScreen(
    onSessionExpired: () -> Unit = {},
    viewModel: ActivityViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val view = LocalView.current
    val haptics = LocalSlamHapticsEnabled.current

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Activity",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Location replies for this account.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        }
        state.syncWarning?.let { warning ->
            item {
                SlamBanner(
                    title = "Sync issue",
                    message = warning,
                    tone = SlamStatusTone.WARNING,
                )
            }
        }
        if (state.notifications.isNotEmpty()) {
            item {
                Text(
                    "Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            items(state.notifications, key = { "notification-${it.id}" }) { notification ->
                SlamCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            notification.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(notification.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Text(
                "Location history",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (state.events.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SlamLottie(resId = R.raw.lottie_empty, size = 120.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No activity yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Successful SMS or emergency locates will show here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.events, key = { it.key }) { event ->
                SlamCard(
                    modifier = Modifier.clickable {
                        val uri = Uri.parse(
                            "https://maps.google.com/?q=${event.latitude},${event.longitude}",
                        )
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            event.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "Captured ${formatter.format(Date(event.timestampMs))}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${"%.5f".format(event.latitude)}, ${"%.5f".format(event.longitude)}" +
                                event.accuracyMeters?.let { " · +/-${it.toInt()}m" }.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
