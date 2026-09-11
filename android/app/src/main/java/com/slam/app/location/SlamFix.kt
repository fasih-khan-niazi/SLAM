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

private fun formatCoord(value: Double): String =
    String.format(java.util.Locale.US, "%.5f", value)

fun SlamFix.mapLink(): String =
    "https://maps.google.com/?q=${formatCoord(latitude)},${formatCoord(longitude)}"

fun SlamFix.smsBody(): String {
    val lat = formatCoord(latitude)
    val lng = formatCoord(longitude)
    val captured = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
    val accuracyText = accuracyMeters?.let { " (±${it.toInt()}m)" }.orEmpty()
    val ageMs = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
    val ageText = formatAge(ageMs)

    return if (isLastKnownFallback) {
        """
        SLAM LOCATION
        Status: LAST KNOWN
        Live fix: unavailable
        Captured: $captured
        Age: $ageText
        Coords: $lat, $lng$accuracyText
        Map: ${mapLink()}
        """.trimIndent().replace("\n", "\n")
    } else {
        """
        SLAM LOCATION
        Status: CURRENT
        Captured: $captured
        Coords: $lat, $lng$accuracyText
        Map: ${mapLink()}
        """.trimIndent()
    }
}

internal fun formatAge(ageMs: Long): String {
    val minutes = ageMs / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> {
            val hours = minutes / 60
            val rem = minutes % 60
            if (rem == 0L) "${hours}h" else "${hours}h ${rem}m"
        }
        else -> {
            val days = minutes / (24 * 60)
            val hours = (minutes % (24 * 60)) / 60
            if (hours == 0L) "${days}d" else "${days}d ${hours}h"
        }
    }
}
