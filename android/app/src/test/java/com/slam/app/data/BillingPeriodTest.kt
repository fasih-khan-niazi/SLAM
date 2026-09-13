package com.slam.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BillingPeriodTest {
    @Test
    fun periodIsThirtyDaysFromAnchorNotCalendarMonth() {
        val anchor = 1_700_000_000_000L

        assertEquals(anchor, BillingPeriod.containing(anchor, anchor + BillingPeriod.LENGTH_MS - 1))
        assertEquals(
            anchor + BillingPeriod.LENGTH_MS,
            BillingPeriod.containing(anchor, anchor + BillingPeriod.LENGTH_MS),
        )
    }

    @Test
    fun futureAnchorRemainsThePeriodStart() {
        assertEquals(2_000L, BillingPeriod.containing(2_000L, 1_000L))
    }
}
