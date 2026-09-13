package com.slam.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinLockoutTest {
    @Test
    fun defaultWindowIsFifteenMinutes() {
        assertEquals(15L * 60L * 1000L, PinLockout.DEFAULT_WINDOW_MS)
    }

    @Test
    fun defaultAttemptCapIsThree() {
        assertEquals(3, PinLockout.DEFAULT_ATTEMPT_CAP)
    }
}
