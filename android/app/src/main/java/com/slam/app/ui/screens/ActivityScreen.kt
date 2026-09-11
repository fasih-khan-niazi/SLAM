package com.slam.app.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.slam.app.data.local.LocationHistoryEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.BuildConfig
import com.slam.app.data.AccountIdentity
import com.slam.app.data.SessionStore
import com.slam.app.data.remote.NotificationItem
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.ui.components.SlamBanner
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamStatusTone
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

data class ActivityUiState(
    val locations: List<LocationHistoryEntity> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val offline: Boolean = false,
)

class ActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val accountId = AccountIdentity.current(getApplication())
            val locations = SlamDatabase.get(getApplication()).locationHistory().latest(accountId)
            val token = SessionStore(getApplication()).token.first()
            if (token.isBlank()) {
                _state.value = ActivityUiState(locations = locations)
                return@launch
            }
            try {
                val response = SlamApiFactory.create(BuildConfig.API_BASE_URL)
                    .notifications("Bearer $token")
                _state.value = ActivityUiState(
                    locations = locations,
                    notifications = response.body()?.data?.notifications.orEmpty(),
                    offline = !response.isSuccessful,
                )
            } catch (_: Exception) {
                _state.value = ActivityUiState(locations = locations, offline = true)
            }
        }
    }
}

@Composable
fun ActivityScreen(viewModel: ActivityViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Activity", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Recent location events stored on this phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.offline) {
            item {
                SlamBanner(
                    title = "Offline",
                    message = "Showing activity stored on this phone.",
                    tone = SlamStatusTone.WARNING,
                )
            }
        }
        if (state.notifications.isNotEmpty()) {
            item { Text("Notifications", style = MaterialTheme.typography.titleLarge) }
            items(state.notifications, key = { "notification-${it.id}" }) { notification ->
                SlamCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(notification.title, style = MaterialTheme.typography.titleMedium)
                        Text(notification.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text("Location history", style = MaterialTheme.typography.titleLarge) }
        if (state.locations.isEmpty()) {
            item {
                SlamBanner(
                    title = "No activity yet",
                    message = "Successful manual and emergency locations will appear here.",
                    tone = SlamStatusTone.NEUTRAL,
                )
            }
        } else {
            items(state.locations, key = { "location-${it.id}" }) { event ->
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
                            when {
                                event.isLastKnownFallback -> "Last-known fallback"
                                event.requestedBy == "emergency" -> "Emergency update"
                                else -> "SMS location request"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Captured ${formatter.format(Date(event.locationTimestamp ?: event.createdAt))}",
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
