package com.slam.app.sms

data class SlamCommand(
    val pin: String,
    val action: String,
)

object SmsCommandParser {
    fun parse(
        body: String?,
        prefix: String = "SLAM",
        minPinLength: Int = 4,
        maxPinLength: Int = 6,
    ): SlamCommand? {
        val parts = body?.trim()?.split(Regex("\\s+")) ?: return null
        if (parts.size != 3) return null
        if (!parts[0].equals(prefix, ignoreCase = true)) return null
        val pin = parts[1]
        val action = parts[2].uppercase()
        if (pin.length !in minPinLength..maxPinLength || !pin.all { it.isDigit() }) return null
        if (action != "LOCATE") return null
        return SlamCommand(pin = pin, action = action)
    }
}
