package com.slam.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.slam.app.feature.auth.AuthFieldErrors
import com.slam.app.feature.auth.AuthValidation
import com.slam.app.feature.auth.AuthViewModel
import com.slam.app.ui.components.SlamCard
import com.slam.app.ui.components.SlamField
import com.slam.app.ui.components.SlamModal
import com.slam.app.ui.components.SlamPrimaryButton
import com.slam.app.ui.components.SlamTextButton

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
    var agreed by rememberSaveable { mutableStateOf(false) }
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
        Text("Create account", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Your phone stays trackable over SMS. The account syncs plans and limits.",
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
            label = "Phone (11–12 digits)",
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
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = fieldErrors.confirmPassword != null,
            supportingText = fieldErrors.confirmPassword,
        )

        Spacer(Modifier.height(24.dp))
        SlamCard {
            Column(Modifier.padding(20.dp)) {
                Text("Consent and disclosure", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Please read this carefully before creating an account.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ConsentBullet(
                    "You confirm you own this phone, or you have the owner’s clear permission to install and run SLAM on it.",
                )
                ConsentBullet(
                    "When Listening is on, trusted numbers you add can send an SMS with your PIN to request this phone’s location.",
                )
                ConsentBullet(
                    "SLAM may use SMS, location (including background), and notifications so tracking can work when the app is not open.",
                )
                ConsentBullet(
                    "Phone makers (including Oppo) may pause background apps. You may need to allow unrestricted battery use for reliable listening.",
                )
                ConsentBullet(
                    "Signing out stops tracking and clears this account’s PIN, trusted numbers, and local activity from this phone.",
                )
                ConsentBullet(
                    "Do not use SLAM to monitor anyone without their knowledge and consent.",
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = agreed,
                            role = Role.Checkbox,
                            onValueChange = { agreed = it },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(checked = agreed, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "I have read and agree to the consent and disclosure above.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SlamPrimaryButton(
            text = "Create account",
            loading = authState.loading,
            enabled = agreed && !authState.loading,
            onClick = {
                val validation = AuthValidation.register(name, email, phone, password, confirmPassword)
                fieldErrors = validation
                if (validation.hasErrors) return@SlamPrimaryButton
                viewModel.register(name, email, phone, password)
            },
        )
        Spacer(Modifier.height(8.dp))
        SlamTextButton(text = "Back to sign in", onClick = onBackToLogin)
        Spacer(Modifier.height(24.dp))
    }

    authState.error?.let { message ->
        SlamModal(
            title = "Could not create account",
            message = message,
            confirmLabel = "OK",
            cancelLabel = "",
            onConfirm = viewModel::clearError,
            onDismiss = viewModel::clearError,
        )
    }
}

@Composable
private fun ConsentBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
