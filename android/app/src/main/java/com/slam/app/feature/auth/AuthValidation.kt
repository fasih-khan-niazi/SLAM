package com.slam.app.feature.auth

private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val phonePattern = Regex("^\\+?\\d{10,15}$")

data class AuthFieldErrors(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null,
) {
    val hasErrors: Boolean
        get() = listOf(name, email, phone, password, confirmPassword).any { it != null }
}

object AuthValidation {
    fun normalizePhone(value: String): String =
        value.replace(Regex("[\\s\\-()]"), "").trim()

    fun login(email: String, password: String): AuthFieldErrors {
        val normalizedEmail = email.trim().lowercase()
        return AuthFieldErrors(
            email = when {
                normalizedEmail.isBlank() -> "Email is required."
                !emailPattern.matches(normalizedEmail) -> "Enter a valid email address."
                else -> null
            },
            password = if (password.isBlank()) "Password is required." else null,
        )
    }

    fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
        confirmPassword: String,
    ): AuthFieldErrors {
        val normalizedName = name.trim()
        val normalizedEmail = email.trim().lowercase()
        val normalizedPhone = normalizePhone(phone)
        return AuthFieldErrors(
            name = when {
                normalizedName.isBlank() -> "Name is required."
                normalizedName.length < 2 -> "Name must be at least 2 characters."
                else -> null
            },
            email = when {
                normalizedEmail.isBlank() -> "Email is required."
                !emailPattern.matches(normalizedEmail) -> "Enter a valid email address."
                else -> null
            },
            phone = when {
                normalizedPhone.isBlank() -> "Phone number is required."
                !phonePattern.matches(normalizedPhone) -> "Use 10–15 digits, optionally starting with +."
                else -> null
            },
            password = when {
                password.isBlank() -> "Password is required."
                password.length < 8 -> "Password must be at least 8 characters."
                else -> null
            },
            confirmPassword = when {
                confirmPassword.isBlank() -> "Confirm your password."
                confirmPassword != password -> "Passwords do not match."
                else -> null
            },
        )
    }
}
