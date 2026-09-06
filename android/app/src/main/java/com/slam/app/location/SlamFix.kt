package com.slam.app.location

data class SlamFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: String,
)

private fun formatCoord(value: Double): String = "%.5f".format(value)

fun SlamFix.mapLink(): String =
    "https://maps.google.com/?q=${formatCoord(latitude)},${formatCoord(longitude)}"

fun SlamFix.smsBody(): String {
    val lat = formatCoord(latitude)
    val lng = formatCoord(longitude)
    return "SLAM $lat,$lng $accuracy ${mapLink()}"
}
