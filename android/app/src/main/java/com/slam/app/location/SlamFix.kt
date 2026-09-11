package com.slam.app.location

data class SlamFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: String,
    val accuracyMeters: Float? = null,
    val provider: String = "unknown",
    val timestamp: Long = System.currentTimeMillis(),
    val isLastKnownFallback: Boolean = false,
)

private fun formatCoord(value: Double): String = String.format(java.util.Locale.US, "%.5f", value)

fun SlamFix.mapLink(): String =
    "https://maps.google.com/?q=${formatCoord(latitude)},${formatCoord(longitude)}"

fun SlamFix.smsBody(): String {
    val lat = formatCoord(latitude)
    val lng = formatCoord(longitude)
    val source = if (isLastKnownFallback) "LAST KNOWN (live location unavailable)" else "CURRENT"
    val captured = java.time.Instant.ofEpochMilli(timestamp).toString()
    val age = if (isLastKnownFallback) {
        " age=${((System.currentTimeMillis() - timestamp).coerceAtLeast(0) / 60_000)}m"
    } else {
        ""
    }
    val accuracyText = accuracyMeters?.let { " +/-${it.toInt()}m" }.orEmpty()
    return "SLAM $source captured=$captured$age $lat,$lng$accuracyText ${mapLink()}"
}
