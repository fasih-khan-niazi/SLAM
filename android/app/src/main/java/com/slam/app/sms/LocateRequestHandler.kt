package com.slam.app.sms

import android.content.Context
import com.slam.app.data.local.LocationHistoryEntity
import com.slam.app.data.local.SlamDatabase
import com.slam.app.location.LocationClient
import com.slam.app.location.smsBody
import com.slam.app.security.PinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocateRequestHandler(private val context: Context) {
    suspend fun handle(from: String, body: String) = withContext(Dispatchers.IO) {
        val command = SmsCommandParser.parse(body) ?: return@withContext
        val pinStore = PinStore(context)
        if (!pinStore.hasPin() || !pinStore.verify(command.pin)) {
            return@withContext
        }

        val sender = SmsReplySender(context)
        val fix = LocationClient(context).acquire()
        if (fix == null) {
            sender.send(from, "SLAM location unavailable")
            return@withContext
        }

        sender.send(from, fix.smsBody())
        val dao = SlamDatabase.get(context).locationHistory()
        dao.insert(
            LocationHistoryEntity(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracy = fix.accuracy,
                requestedBy = from,
            )
        )
        dao.trim()
    }
}
