package com.slam.app.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MessageIdsTest {
    @Test
    fun repeatedDeliveryHasSameId() {
        assertEquals(
            MessageIds.stable("+15551234567", 1234L, "SLAM 1234 LOCATE"),
            MessageIds.stable("+15551234567", 1234L, "SLAM 1234 LOCATE"),
        )
    }

    @Test
    fun distinctSmsHasDistinctId() {
        assertNotEquals(
            MessageIds.stable("+15551234567", 1234L, "SLAM 1234 LOCATE"),
            MessageIds.stable("+15551234567", 1235L, "SLAM 1234 LOCATE"),
        )
    }
}
