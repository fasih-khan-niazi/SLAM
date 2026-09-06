package com.slam.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.slam.app.BuildConfig
import com.slam.app.R
import com.slam.app.data.SessionStore
import com.slam.app.data.remote.LoginBody
import com.slam.app.data.remote.SlamApiFactory
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamTextButton
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SessionStore(context) }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Image(
            painter = painterResource(R.drawable.ic_slam_mark),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sign in to sync your plan. Tracking still works over SMS without internet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        SlamField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        SlamField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
        )
        Spacer(Modifier.height(24.dp))
        SlamPrimaryButton(
            text = "Sign in",
            loading = loading,
            onClick = {
                loading = true
                scope.launch {
                    try {
                        store.setApiBaseUrl(BuildConfig.API_BASE_URL)
                        val api = SlamApiFactory.create(BuildConfig.API_BASE_URL)
                        val response = api.login(LoginBody(email.trim(), password))
                        val body = response.body()
                        if (response.isSuccessful && body?.success == true && body.data != null) {
                            store.setSession(body.data.token, body.data.user.name)
                            onLoggedIn()
                        } else {
                            error = body?.message ?: "Could not sign in"
                        }
                    } catch (e: Exception) {
                        error = "Cannot reach the server. Check your internet connection."
                    } finally {
                        loading = false
                    }
                }
            },
        )
        SlamTextButton(
            text = "Create an account",
            onClick = onCreateAccount,
        )
    }

    error?.let { message ->
        SlamModal(
            title = "Sign in failed",
            message = message,
            confirmLabel = "OK",
            cancelLabel = "",
            onConfirm = { error = null },
            onDismiss = { error = null },
        )
    }
}
