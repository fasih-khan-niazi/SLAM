package com.slam.app.data

object BillingPeriod {
    const val LENGTH_MS = 30L * 24L * 60L * 60L * 1_000L

    fun containing(anchorMs: Long, nowMs: Long): Long {
        if (nowMs <= anchorMs) return anchorMs
        return anchorMs + ((nowMs - anchorMs) / LENGTH_MS) * LENGTH_MS
    }
}
