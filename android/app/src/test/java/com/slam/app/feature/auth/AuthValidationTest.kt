package com.slam.app.feature.auth

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
    fun registrationAcceptsNormalizedInternationalPhone() {
        val errors = AuthValidation.register(
            name = "Client User",
            email = "client@example.com",
            phone = "+92 300-1234567",
            password = "password123",
            confirmPassword = "password123",
        )
        assertFalse(errors.hasErrors)
        assertNull(errors.phone)
    }

    @Test
    fun registrationRejectsPasswordMismatch() {
        val errors = AuthValidation.register(
            name = "Client User",
            email = "client@example.com",
            phone = "03001234567",
            password = "password123",
            confirmPassword = "password456",
        )
        assertTrue(errors.confirmPassword != null)
    }
}
