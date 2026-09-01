package com.slam.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.slam.app.BuildConfig
import com.slam.app.data.SessionStore
import com.slam.app.data.remote.RegisterBody
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamTextButton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Create account", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your phone stays trackable over SMS. The account is for plans and limits.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        SlamField(value = name, onValueChange = { name = it }, label = "Full name")
        Spacer(Modifier.height(12.dp))
        SlamField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        SlamField(
            value = phone,
            onValueChange = { phone = it },
            label = "Phone",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Spacer(Modifier.height(12.dp))
        SlamField(
            value = password,
            onValueChange = { password = it },
            label = "Password (8+ characters)",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Spacer(Modifier.height(24.dp))
        SlamPrimaryButton(
            text = "Create account",
            loading = loading,
            onClick = {
                loading = true
                scope.launch {
                    try {
                        val base = store.apiBaseUrl.first().ifBlank { BuildConfig.API_BASE_URL }
                        val api = SlamApiFactory.create(base)
                        val response = api.register(
                            RegisterBody(
                                name = name.trim(),
                                email = email.trim(),
                                password = password,
                                phone = phone.trim(),
                            )
                        )
                        val body = response.body()
                        if (response.isSuccessful && body?.success == true && body.data != null) {
                            store.setSession(body.data.token, body.data.user.name)
                            onRegistered()
                        } else {
                            error = body?.message ?: "Could not create account"
                        }
                    } catch (e: Exception) {
                        error = "Cannot reach the API. Sign in first and set the API URL, or start the server."
                    } finally {
                        loading = false
                    }
                }
            },
        )
        SlamTextButton(text = "Already have an account", onClick = onBackToLogin)
    }

    error?.let { message ->
        SlamModal(
            title = "Could not register",
            message = message,
            confirmLabel = "OK",
            cancelLabel = "",
            onConfirm = { error = null },
            onDismiss = { error = null },
        )
    }
}
