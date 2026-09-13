package com.slam.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlamFixSmsTest {
    @Test
    fun lastKnownSmsCallsOutAgeAndUnavailableLiveFix() {
        val fix = SlamFix(
            latitude = 31.5204,
            longitude = 74.3587,
            accuracy = "LAST_KNOWN",
            accuracyMeters = 25f,
            provider = "fused",
            timestamp = System.currentTimeMillis() - 2L * 24L * 60L * 60L * 1_000L,
            isLastKnownFallback = true,
        )
        val body = fix.smsBody()
        assertTrue(body.contains("LAST KNOWN"))
        assertTrue(body.contains("Live fix: unavailable"))
        assertTrue(body.contains("Age:"))
        assertTrue(body.contains("Map:"))
    }

    @Test
    fun formatAgeUsesDaysAndHours() {
        assertEquals("2d 5h", formatAge((2L * 24L + 5L) * 60L * 60L * 1_000L))
        assertEquals("45m", formatAge(45L * 60L * 1_000L))
    }
}
