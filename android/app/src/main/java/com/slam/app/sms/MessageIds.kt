package com.slam.app.sms

import java.security.MessageDigest

object MessageIds {
    fun stable(sender: String, timestampMs: Long, body: String): String {
        val raw = "$sender\u0000$timestampMs\u0000$body"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
