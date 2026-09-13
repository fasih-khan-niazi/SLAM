package com.slam.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class SmsReplySender(private val context: Context) {
    fun send(to: String, body: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return try {
            val manager = context.getSystemService(SmsManager::class.java) ?: return false
            val parts = manager.divideMessage(body)
            if (parts.size == 1) {
                manager.sendTextMessage(to, null, body, null, null)
            } else {
                manager.sendMultipartTextMessage(to, null, parts, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
