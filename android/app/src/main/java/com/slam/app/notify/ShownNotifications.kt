package com.slam.app.notify

import android.content.Context

/**
 * Remembers which server notifications were already surfaced as system notifications,
 * per account, so a poll never re-alerts for the same item.
 */
class ShownNotifications(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("slam_shown_notifications", Context.MODE_PRIVATE)

    fun hasShown(accountId: String, notificationId: Int): Boolean =
        ids(accountId).contains(notificationId.toString())

    fun markShown(accountId: String, notificationId: Int) {
        val next = (ids(accountId) + notificationId.toString())
            .sortedBy { it.toIntOrNull() ?: 0 }
            .takeLast(MAX_REMEMBERED)
            .toSet()
        prefs.edit().putStringSet(accountId, next).apply()
    }

    private fun ids(accountId: String): Set<String> =
        prefs.getStringSet(accountId, emptySet()).orEmpty()

    private companion object {
        const val MAX_REMEMBERED = 100
    }
}
