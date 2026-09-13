package com.slam.app.feature.auth

enum class PasswordStrength { WEAK, MEDIUM, STRONG }

data class PasswordStrengthResult(
    val strength: PasswordStrength,
    val hasMinLength: Boolean,
    val hasLetter: Boolean,
    val hasDigit: Boolean,
    val hasSpecial: Boolean,
    val hasUpper: Boolean,
    val hasLower: Boolean,
) {
    val isAcceptable: Boolean get() = strength == PasswordStrength.STRONG
}

object PasswordRules {
    private val specialPattern = Regex("[^A-Za-z0-9]")

    fun evaluate(password: String): PasswordStrengthResult {
        val hasMinLength = password.length >= 8
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = specialPattern.containsMatchIn(password)
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }

        var score = 0
        if (hasMinLength) score += 1
        if (password.length >= 12) score += 1
        if (hasLower) score += 1
        if (hasUpper) score += 1
        if (hasDigit) score += 1
        if (hasSpecial) score += 1

        // Industry-style gate for this product: Strong requires length, letter, digit, and special.
        val strength = when {
            !hasMinLength || !hasLetter || !hasDigit || !hasSpecial -> PasswordStrength.WEAK
            score >= 5 && hasUpper && hasLower -> PasswordStrength.STRONG
            score >= 4 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }

        return PasswordStrengthResult(
            strength = strength,
            hasMinLength = hasMinLength,
            hasLetter = hasLetter,
            hasDigit = hasDigit,
            hasSpecial = hasSpecial,
            hasUpper = hasUpper,
            hasLower = hasLower,
        )
    }

    fun rejectionMessage(result: PasswordStrengthResult): String? {
        if (result.isAcceptable) return null
        return when {
            !result.hasMinLength -> "Use at least 8 characters."
            !result.hasLetter -> "Add a letter."
            !result.hasDigit -> "Add a number."
            !result.hasSpecial -> "Add a special character (!@#\$%…)."
            result.strength != PasswordStrength.STRONG ->
                "Use upper and lower case letters to make it Strong."
            else -> "Choose a Strong password."
        }
    }
}

private val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
private val phonePattern = Regex("^\\+?\\d{11,12}$")

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
        val strength = PasswordRules.evaluate(password)
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
                !phonePattern.matches(normalizedPhone) -> "Phone must be 11 or 12 digits (optional +)."
                else -> null
            },
            password = when {
                password.isBlank() -> "Password is required."
                else -> PasswordRules.rejectionMessage(strength)
            },
            confirmPassword = when {
                confirmPassword.isBlank() -> "Confirm your password."
                confirmPassword != password -> "Passwords do not match."
                else -> null
            },
        )
    }
}
