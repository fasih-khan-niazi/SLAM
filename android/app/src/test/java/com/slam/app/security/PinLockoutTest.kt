package com.slam.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinLockoutTest {
    @Test
    fun windowIsFifteenMinutes() {
        assertEquals(15L * 60L * 1000L, PinLockout.WINDOW_MS)
    }

    @Test
    fun attemptCapIsThree() {
        assertEquals(3, PinLockout.ATTEMPT_CAP)
    }
}
