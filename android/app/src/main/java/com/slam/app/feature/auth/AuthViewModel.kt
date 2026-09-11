package com.slam.app.feature.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slam.app.BuildConfig
import com.slam.app.data.SessionStore
import com.slam.app.data.AccountLifecycleManager
import com.slam.app.data.remote.LoginBody
import com.slam.app.data.remote.RegisterBody
import com.slam.app.data.remote.SlamApi
import com.slam.app.data.remote.SlamApiFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val authenticated: Boolean = false,
    val error: String? = null,
)

class AuthRepository(
    private val context: android.content.Context,
    private val session: SessionStore,
    private val api: SlamApi,
) {
    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        session.setApiBaseUrl(BuildConfig.API_BASE_URL)
        val response = api.login(LoginBody(email.trim().lowercase(), password))
        val body = response.body()
        if (!response.isSuccessful || body?.success != true || body.data == null) {
            error(body?.message ?: "Could not sign in")
        }
        AccountLifecycleManager(context).establishSession(
            body.data.token,
            body.data.user.name,
            body.data.user.id,
        )
        session.cacheUsage(body.data.subscription)
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
        AccountLifecycleManager(context).establishSession(
            body.data.token,
            body.data.user.name,
            body.data.user.id,
        )
        session.cacheUsage(body.data.subscription)
    }
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(
        context = application,
        session = SessionStore(application),
        api = SlamApiFactory.create(BuildConfig.API_BASE_URL),
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

    private fun submit(block: suspend () -> Result<Unit>) {
        if (_state.value.loading) return
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            val result = block()
            _state.value = if (result.isSuccess) {
                AuthUiState(authenticated = true)
            } else {
                val failure = result.exceptionOrNull()
                AuthUiState(
                    error = if (failure is IllegalStateException) {
                        failure.message ?: "Authentication failed."
                    } else {
                        "Cannot reach the server. Check your internet connection."
                    },
                )
            }
        }
    }
}
