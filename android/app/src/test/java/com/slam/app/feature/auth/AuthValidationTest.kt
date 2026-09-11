package com.slam.app.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun loginRejectsMalformedEmailAndBlankPassword() {
        val errors = AuthValidation.login("not-an-email", "")
        assertTrue(errors.hasErrors)
        assertTrue(errors.email != null)
        assertTrue(errors.password != null)
    }

    @Test
    fun registrationAcceptsStrongPasswordAndNormalizedPhone() {
        val errors = AuthValidation.register(
            name = "Client User",
            email = "client@example.com",
            phone = "+92 300-1234567",
            password = "SecurePass1!",
            confirmPassword = "SecurePass1!",
        )
        assertFalse(errors.hasErrors)
        assertNull(errors.phone)
        assertNull(errors.password)
    }

    @Test
    fun registrationRejectsWeakPassword() {
        val errors = AuthValidation.register(
            name = "Client User",
            email = "client@example.com",
            phone = "03001234567",
            password = "password123",
            confirmPassword = "password123",
        )
        assertTrue(errors.password != null)
    }

    @Test
    fun registrationRejectsPasswordMismatch() {
        val errors = AuthValidation.register(
            name = "Client User",
            email = "client@example.com",
            phone = "03001234567",
            password = "SecurePass1!",
            confirmPassword = "SecurePass2!",
        )
        assertTrue(errors.confirmPassword != null)
    }

    @Test
    fun passwordStrengthRequiresDigitAndSpecial() {
        assertEquals(PasswordStrength.WEAK, PasswordRules.evaluate("OnlyLetters").strength)
        assertEquals(PasswordStrength.WEAK, PasswordRules.evaluate("letters1").strength)
        assertTrue(PasswordRules.evaluate("SecurePass1!").isAcceptable)
    }
}
