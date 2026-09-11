package com.slam.app.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slam.app.BuildConfig
import com.slam.app.data.AccountLifecycleManager
import com.slam.app.data.LoginLockoutStore
import com.slam.app.data.SessionStore
import com.slam.app.data.remote.LoginBody
import com.slam.app.data.remote.RegisterBody
import com.slam.app.data.remote.SlamApi
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.security.PinCloudSync
import com.slam.app.security.PinStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val authenticated: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val toastDanger: Boolean = false,
)

class AuthRepository(
    private val context: android.content.Context,
    private val session: SessionStore,
    private val api: SlamApi,
    private val lockout: LoginLockoutStore,
) {
    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        if (lockout.isLocked()) {
            val minutes = kotlin.math.ceil(lockout.remainingLockSeconds() / 60.0).toInt().coerceAtLeast(1)
            error("You've been locked for $minutes minutes.")
        }
        session.setApiBaseUrl(BuildConfig.API_BASE_URL)
        val response = api.login(LoginBody(email.trim().lowercase(), password))
        val body = response.body()
        if (!response.isSuccessful || body?.success != true || body.data == null) {
            when (val status = lockout.recordFailure()) {
                is LoginLockoutStore.LockoutStatus.Failed ->
                    error(
                        "Incorrect email or password. " +
                            if (status.triesLeft == 1) "You have 1 try left."
                            else "You have ${status.triesLeft} tries left.",
                    )
                is LoginLockoutStore.LockoutStatus.Locked ->
                    error("You've been locked for ${status.minutesRemaining} minutes.")
            }
        }
        val data = body!!.data!!
        lockout.clear()
        AccountLifecycleManager(context).establishSession(
            data.token,
            data.user.name,
            data.user.id,
        )
        session.setConsent(true)
        session.cacheUsage(data.subscription)
        PinCloudSync.restoreLocal(context, data.trackingPin)
        if (PinStore(context).hasPin()) {
            PinCloudSync.pushCurrent(context)
        } else {
            PinCloudSync.pullIfNeeded(context)
        }
    }

    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
    ): Result<Unit> = runCatching {
        session.setApiBaseUrl(BuildConfig.API_BASE_URL)
        val response = api.register(
            RegisterBody(
                name = name.trim(),
                email = email.trim().lowercase(),
                password = password,
                phone = AuthValidation.normalizePhone(phone),
            ),
        )
        val body = response.body()
        if (!response.isSuccessful || body?.success != true || body.data == null) {
            error(body?.message ?: "Could not create account")
        }
        val data = body!!.data!!
        AccountLifecycleManager(context).establishSession(
            data.token,
            data.user.name,
            data.user.id,
        )
        session.setConsent(true)
        session.cacheUsage(data.subscription)
        PinCloudSync.restoreLocal(context, data.trackingPin)
    }
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val lockout = LoginLockoutStore(application)
    private val repository = AuthRepository(
        context = application,
        session = SessionStore(application),
        api = SlamApiFactory.create(BuildConfig.API_BASE_URL),
        lockout = lockout,
    )
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun login(email: String, password: String) = submit {
        repository.login(email, password)
    }

    fun register(name: String, email: String, phone: String, password: String) = submit {
        repository.register(name, email, phone, password)
    }

    fun consumeAuthentication() {
        _state.value = _state.value.copy(authenticated = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearToast() {
        _state.value = _state.value.copy(toast = null)
    }

    private fun submit(block: suspend () -> Result<Unit>) {
        if (_state.value.loading) return
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            val result = block()
            _state.value = if (result.isSuccess) {
                AuthUiState(authenticated = true)
            } else {
                val message = result.exceptionOrNull()?.message
                    ?: "Cannot reach the server. Check your internet connection."
                val isCredential = message.contains("Incorrect") || message.contains("locked", ignoreCase = true)
                AuthUiState(
                    error = if (!isCredential) message else null,
                    toast = if (isCredential) message else null,
                    toastDanger = isCredential,
                )
            }
        }
    }
}
