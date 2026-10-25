package com.slam.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slam.app.BuildConfig
import com.slam.app.data.AccountIdentity
import com.slam.app.data.EmergencyPrefs
import com.slam.app.data.ListenerPrefs
import com.slam.app.data.OutboxDispatcher
import com.slam.app.data.SessionStore
import com.slam.app.data.local.SlamDatabase
import com.slam.app.data.local.TrustedNumberEntity
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.permissions.CorePrerequisites
import com.slam.app.permissions.CoreStatus
import com.slam.app.security.PinLockout
import com.slam.app.security.PinSenderLock
import com.slam.app.security.PinStore
import com.slam.app.sms.EmergencyScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    val emergencyMinutes: Int = 60,
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
    private val pinStore = PinStore.get(application)
    private val _state = MutableStateFlow(TrackingUiState())
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                withTimeoutOrNull(8_000) {
                    withContext(Dispatchers.IO) { loadFromCache() }
                }
            } catch (_: Exception) {
                // Keep defaults; still leave the skeleton.
            } finally {
                _state.value = _state.value.copy(bootstrapped = true)
            }
            syncInBackground()
            observeLiveState()
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

    /**
     * Keeps the screen in step with the device without a manual refresh. Preference and database
     * changes trigger a reload at once; the ticker covers things Android does not notify us about
     * (location services toggled from the shade, PIN lock countdowns, emergency next-run).
     */
    private suspend fun observeLiveState() {
        val app = getApplication<Application>()
        val db = SlamDatabase.get(app)
        val ticker = flow {
            while (true) {
                emit(Unit)
                delay(LIVE_TICK_MS)
            }
        }
        merge(
            ListenerPrefs(app).listeningActiveFlow().map { },
            EmergencyPrefs(app).changes(),
            db.trustedNumbers().observeCount().map { },
            db.eventLedger().observePendingCount().map { },
            ticker,
        ).conflate().collect {
            runCatching { withContext(Dispatchers.IO) { loadFromCache() } }
        }
    }

    fun refreshDeviceState() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadFromCache() } }
        }
    }

    fun reloadLocal() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadFromCache() } }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { loadFromCache() } }
            syncInBackground()
        }
    }

    fun syncInBackground() {
        viewModelScope.launch {
            _state.value = _state.value.copy(syncing = true)
            try {
                runCatching { OutboxDispatcher(getApplication()).flush() }
                val token = session.token.first()
                val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
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
                        config.quietLocationIntervalMinutes,
                    )
                    runCatching { EmergencyScheduler.syncWithConfig(getApplication(), session.cachedEmergencyMinutes()) }
                    runCatching {
                        com.slam.app.location.QuietLocationWorker.syncWithConfig(
                            getApplication(),
                            session.cachedQuietLocationMinutes(),
                        )
                    }
                }
                var plan = _state.value.planName
                var limit = _state.value.limit
                if (token.isNotBlank()) {
                    val response = runCatching { api.me("Bearer $token") }.getOrNull()
                    val data = response?.body()?.data
                    if (response?.isSuccessful == true && data != null) {
                        session.cacheUsage(data.subscription)
                        val live = data.subscription?.forLiveUsage()
                        plan = live?.planName ?: plan
                        limit = live?.monthlyLimit ?: limit
                    }
                }
                runCatching { withContext(Dispatchers.IO) { loadFromCache() } }
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
        val minPin = session.cachedPinMinLength().coerceIn(4, 8)
        val maxPin = session.cachedPinMaxLength().coerceIn(minPin, 8)
        val limit = session.quotaLimit()
        val accountId = AccountIdentity.current(app)
        val pending = SlamDatabase.get(app).eventLedger().pending(accountId).size
        // Read everything first, then apply in one atomic update. Several callers run this
        // concurrently now, and a copy taken before the reads would overwrite newer state.
        val planName = session.cachedPlanName()
        val smsPrefix = session.cachedSmsPrefix()
        val emergencyAllowed = session.cachedEmergencyEnabled()
        val emergencyMinutes = session.cachedEmergencyMinutes()
        val contacts = SlamDatabase.get(app).trustedNumbers().all()
        val maxContacts = session.cachedMaxContacts()
        val pinLocks = PinLockout.lockedSenders(app)
        val listenerPrefs = ListenerPrefs(app)
        val emergencyPrefs = EmergencyPrefs(app)
        _state.update {
            it.copy(
                planName = planName,
                remaining = remaining.takeUnless { value -> value == Int.MAX_VALUE },
                unlimited = unlimited || remaining == Int.MAX_VALUE || limit < 0,
                limit = limit.takeUnless { value -> value < 0 },
                pinReady = pinStore.hasPin(),
                pinMinLength = minPin,
                pinMaxLength = maxPin,
                smsPrefix = smsPrefix,
                emergencyAllowed = emergencyAllowed,
                emergencyMinutes = emergencyMinutes,
                contacts = contacts,
                maxContacts = maxContacts,
                prerequisites = CorePrerequisites.status(app),
                listening = listenerPrefs.isListening() && listenerPrefs.isServiceActive(),
                emergencyOn = emergencyPrefs.isOn(),
                emergencyLastResult = emergencyPrefs.lastResult(),
                emergencyNextRun = emergencyPrefs.nextRun(),
                pendingOutbox = pending,
                pinLocks = pinLocks,
            )
        }
    }

    private companion object {
        const val LIVE_TICK_MS = 5_000L
    }
}
