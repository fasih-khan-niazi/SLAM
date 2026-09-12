package com.slam.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slam.app.BuildConfig
import com.slam.app.data.AccountIdentity
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.SessionStore
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.permissions.CoreStatus
import com.slam.app.security.PinLockout
import com.slam.app.security.PinSenderLock
import com.slam.app.security.PinStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TrackingUiState(
    val bootstrapped: Boolean = false,
    val syncing: Boolean = false,
    val planName: String = "Free",
    val remaining: Int? = 5,
    val unlimited: Boolean = false,
    val limit: Int? = 5,
    val pinReady: Boolean = false,
    val pinMinLength: Int = 4,
    val pinMaxLength: Int = 6,
    val smsPrefix: String = "SLAM",
    val listening: Boolean = false,
    val emergencyOn: Boolean = false,
    val emergencyAllowed: Boolean = true,
    val emergencyHours: Int = 1,
    val emergencyLastResult: String = "",
    val emergencyNextRun: Long = 0L,
    val contacts: List<TrustedNumberEntity> = emptyList(),
    val maxContacts: Int = 1,
    val prerequisites: CoreStatus? = null,
    val pendingOutbox: Int = 0,
    val pinLocks: List<PinSenderLock> = emptyList(),
)

class TrackingViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SessionStore(application)
    private val pinStore = PinStore(application)
    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            loadFromCache()
            _state.value = _state.value.copy(bootstrapped = true)
            syncInBackground()
        }
        viewModelScope.launch {
            session.cachedRemaining.collect { remaining ->
                _state.value = _state.value.copy(
                    remaining = remaining.takeUnless { it == Int.MAX_VALUE },
                    unlimited = remaining == Int.MAX_VALUE,
                )
            }
        }
    }

    fun refreshDeviceState() {
        viewModelScope.launch { loadFromCache() }
    }

    fun reloadLocal() {
        viewModelScope.launch { loadFromCache() }
    }

    fun refreshAll() {
        viewModelScope.launch {
            loadFromCache()
            syncInBackground()
        }
    }

    fun syncInBackground() {
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true)
            try {
                val token = session.token.first()
                val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
                val config = runCatching { api.config().body()?.data }.getOrNull()
                session.cacheProductConfig(
                    config?.pinAttemptCap,
                    config?.pinWindowMinutes,
                    config?.emergencyEnabled,
                    config?.emergencyIntervalHours,
                    config?.smsPrefix,
                    config?.pinMinLength,
                    config?.pinMaxLength,
                )
                var plan = _state.value.planName
                var limit = _state.value.limit
                if (token.isNotBlank()) {
                    val response = runCatching { api.me("Bearer $token") }.getOrNull()
                    val data = response?.body()?.data
                    if (response?.isSuccessful == true && data != null) {
                        session.cacheUsage(data.subscription)
                        plan = data.subscription?.planName ?: plan
                        limit = data.subscription?.monthlyLimit ?: limit
                    }
                }
                loadFromCache()
                _state.value = _state.value.copy(planName = plan, limit = limit)
            } catch (_: Exception) {
                // Keep cached UI.
            } finally {
                _state.value = _state.value.copy(syncing = false)
            }
        }
    }

    private suspend fun loadFromCache() {
        val app = getApplication<Application>()
        val remaining = session.cachedRemaining.first()
        val unlimited = session.cachedUnlimited.first()
        val minPin = session.cachedPinMinLength().coerceIn(4, 6)
        val maxPin = session.cachedPinMaxLength().coerceIn(minPin, 6)
        val limit = session.quotaLimit()
        val accountId = AccountIdentity.current(app)
        val pending = SlamDatabase.get(app).eventLedger().pending(accountId).size
        _state.value = _state.value.copy(
            planName = session.cachedPlanName(),
            remaining = remaining.takeUnless { it == Int.MAX_VALUE },
            unlimited = unlimited || remaining == Int.MAX_VALUE || limit < 0,
            limit = limit.takeUnless { it < 0 },
            pinReady = pinStore.hasPin(),
            pinMinLength = minPin,
            pinMaxLength = maxPin,
            smsPrefix = session.cachedSmsPrefix(),
            emergencyAllowed = session.cachedEmergencyEnabled(),
            emergencyHours = session.cachedEmergencyHours(),
            contacts = SlamDatabase.get(app).trustedNumbers().all(),
            maxContacts = session.cachedMaxContacts(),
            prerequisites = CorePrerequisites.status(app),
            listening = ListenerPrefs(app).isListening() && ListenerPrefs(app).isServiceActive(),
            emergencyOn = EmergencyPrefs(app).isOn(),
            emergencyLastResult = EmergencyPrefs(app).lastResult(),
            emergencyNextRun = EmergencyPrefs(app).nextRun(),
            pendingOutbox = pending,
            pinLocks = PinLockout.lockedSenders(app),
        )
    }
}
