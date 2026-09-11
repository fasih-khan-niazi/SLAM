package com.slam.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slam.app.feature.auth.AuthFieldErrors
import com.slam.app.feature.auth.AuthValidation
import com.slam.app.feature.auth.AuthViewModel
import com.slam.app.ui.components.AuthScreenScaffold
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamTextButton
import com.slam.app.ui.components.SlamToastHostState
import com.slam.app.ui.components.SlamToastTone

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val authState by viewModel.state.collectAsStateWithLifecycle()
    val toastHost = remember { SlamToastHostState() }

    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var fieldErrors by remember { mutableStateOf(AuthFieldErrors()) }

    LaunchedEffect(authState.authenticated) {
        if (authState.authenticated) {
            viewModel.consumeAuthentication()
            onLoggedIn()
        }
    }

    LaunchedEffect(authState.toast) {
        val message = authState.toast ?: return@LaunchedEffect
        toastHost.show(
            message,
            if (authState.toastDanger) SlamToastTone.DANGER else SlamToastTone.WARNING,
        )
        viewModel.clearToast()
    }

    AuthScreenScaffold(
        title = "Welcome back",
        subtitle = "Sign in to sync your plan. SMS tracking still works without internet.",
        toastHostState = toastHost,
    ) {
        Text(
            "Email",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        SlamField(
            value = email,
            onValueChange = {
                email = it
                fieldErrors = fieldErrors.copy(email = null)
            },
            label = "Email",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = fieldErrors.email != null,
            supportingText = fieldErrors.email,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Password",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        SlamField(
            value = password,
            onValueChange = {
                password = it
                fieldErrors = fieldErrors.copy(password = null)
            },
            label = "Password",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = fieldErrors.password != null,
            supportingText = fieldErrors.password,
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                    )
                }
            },
        )
        Spacer(Modifier.height(24.dp))
        SlamPrimaryButton(
            text = "Sign in",
            loading = authState.loading,
            onClick = {
                val validation = AuthValidation.login(email, password)
                fieldErrors = validation
                if (validation.hasErrors) return@SlamPrimaryButton
                viewModel.login(email, password)
            },
        )
        Spacer(Modifier.height(8.dp))
        SlamTextButton(text = "Create an account", onClick = onCreateAccount)
    }

    authState.error?.let { message ->
        SlamModal(
            title = "Sign in failed",
            message = message,
            confirmLabel = "OK",
            cancelLabel = "",
            onConfirm = viewModel::clearError,
            onDismiss = viewModel::clearError,
        )
    }
}
