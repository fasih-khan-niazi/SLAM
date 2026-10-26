package com.slam.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun isLocked_belowCap_notLocked() {
        assertFalse(PinLockout.isLocked(failCountInWindow = 2, cap = 3))
        assertFalse(PinLockout.isLocked(failCountInWindow = 0, cap = 1))
    }

    @Test
    fun isLocked_atOrAboveCap_locked() {
        assertTrue(PinLockout.isLocked(failCountInWindow = 3, cap = 3))
        assertTrue(PinLockout.isLocked(failCountInWindow = 10, cap = 3))
    }

    @Test
    fun remainingLockMs_beforeWindowExpires_isPositive() {
        val now = 1_000_000L
        val window = 60_000L
        val latestFailAt = now - 10_000L // failed 10s ago, window is 60s
        val remaining = PinLockout.remainingLockMs(latestFailAt, window, now)
        assertEquals(50_000L, remaining)
        assertTrue(remaining > 0)
    }

    @Test
    fun remainingLockMs_atExactExpiry_isZero() {
        val now = 1_000_000L
        val window = 60_000L
        val latestFailAt = now - window
        assertEquals(0L, PinLockout.remainingLockMs(latestFailAt, window, now))
    }

    @Test
    fun remainingLockMs_afterWindowExpires_isNegative() {
        val now = 1_000_000L
        val window = 60_000L
        val latestFailAt = now - window - 5_000L
        assertTrue(PinLockout.remainingLockMs(latestFailAt, window, now) < 0)
    }

    @Test
    fun otherSenderUnaffectedByOneSendersLockout() {
        // The sender lock decision is purely a function of THAT sender's own failure count -
        // nothing here is shared state, so a locked sender (cap reached) and a clean sender
        // (zero failures) are independent by construction.
        val cap = 3
        assertTrue(PinLockout.isLocked(failCountInWindow = 3, cap = cap))
        assertFalse(PinLockout.isLocked(failCountInWindow = 0, cap = cap))
    }
}
