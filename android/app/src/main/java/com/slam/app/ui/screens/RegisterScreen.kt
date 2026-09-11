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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
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
import com.slam.app.feature.auth.AuthFieldErrors
import com.slam.app.feature.auth.AuthValidation
import com.slam.app.feature.auth.AuthViewModel
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamTextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onBackToLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val authState by viewModel.state.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var fieldErrors by remember { mutableStateOf(AuthFieldErrors()) }

    LaunchedEffect(authState.authenticated) {
        if (authState.authenticated) {
            viewModel.consumeAuthentication()
            onRegistered()
        }
    }

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
        SlamField(
            value = name,
            onValueChange = {
                name = it
                fieldErrors = fieldErrors.copy(name = null)
            },
            label = "Full name",
            isError = fieldErrors.name != null,
            supportingText = fieldErrors.name,
        )
        Spacer(Modifier.height(12.dp))
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
        Spacer(Modifier.height(12.dp))
        SlamField(
            value = phone,
            onValueChange = {
                phone = it
                fieldErrors = fieldErrors.copy(phone = null)
            },
            label = "Phone",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = fieldErrors.phone != null,
            supportingText = fieldErrors.phone,
        )
        Spacer(Modifier.height(12.dp))
        SlamField(
            value = password,
            onValueChange = {
                password = it
                fieldErrors = fieldErrors.copy(password = null)
            },
            label = "Password (8+ characters)",
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
        Spacer(Modifier.height(12.dp))
        SlamField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                fieldErrors = fieldErrors.copy(confirmPassword = null)
            },
            label = "Confirm password",
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = fieldErrors.confirmPassword != null,
            supportingText = fieldErrors.confirmPassword,
        )
        Spacer(Modifier.height(24.dp))
        SlamPrimaryButton(
            text = "Create account",
            loading = authState.loading,
            onClick = {
                val validation = AuthValidation.register(name, email, phone, password, confirmPassword)
                fieldErrors = validation
                if (validation.hasErrors) return@SlamPrimaryButton
                viewModel.register(name, email, phone, password)
            },
        )
        SlamTextButton(text = "Already have an account", onClick = onBackToLogin)
    }

    authState.error?.let { message ->
        SlamModal(
            title = "Could not register",
            message = message,
            confirmLabel = "OK",
            cancelLabel = "",
            onConfirm = viewModel::clearError,
            onDismiss = viewModel::clearError,
        )
    }
}
