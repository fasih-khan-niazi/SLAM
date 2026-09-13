package com.slam.app.data.remote

import com.google.gson.annotations.SerializedName

data class ApiEnvelope<T>(
    val success: Boolean,
    val message: String?,
    val data: T?,
)

data class AuthData(
    val token: String,
    val user: PublicUser,
    val subscription: SubscriptionInfo?,
    @SerializedName("tracking_pin") val trackingPin: TrackingPinPayload? = null,
)

data class PublicUser(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
)

data class TrackingPinPayload(
    val salt: String?,
    val verifier: String?,
)

data class PinBody(
    @SerializedName("pin_salt") val pinSalt: String,
    @SerializedName("pin_verifier") val pinVerifier: String,
)

data class SubscriptionInfo(
    @SerializedName("plan_name") val planName: String?,
    val status: String?,
    @SerializedName("requests_remaining") val requestsRemaining: Int?,
    @SerializedName("monthly_limit") val monthlyLimit: Int?,
    @SerializedName("max_contacts") val maxContacts: Int? = null,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
    /** Present on older API responses when top-level was a pending upgrade. */
    @SerializedName("active_plan") val activePlan: SubscriptionInfo? = null,
    @SerializedName("pending_upgrade") val pendingUpgrade: SubscriptionInfo? = null,
) {
    /**
     * Live entitlements only. Pending upgrades never contribute quota or plan name.
     */
    fun forLiveUsage(): SubscriptionInfo {
        if (status == "pending_payment" || status == "pending_approval") {
            return activePlan?.forLiveUsage()
                ?: SubscriptionInfo(
                    planName = "Free",
                    status = "active",
                    requestsRemaining = 5,
                    monthlyLimit = 5,
                    maxContacts = 1,
                )
        }
        return this
    }
}

data class RegisterBody(
    val name: String,
    val email: String,
    val password: String,
    val phone: String,
)

data class LoginBody(
    val email: String,
    val password: String,
)

data class MeData(
    val user: PublicUser,
    val subscription: SubscriptionInfo?,
    @SerializedName("tracking_pin") val trackingPin: TrackingPinPayload? = null,
)

data class PinSaveResult(
    @SerializedName("tracking_pin") val trackingPin: TrackingPinPayload?,
)

data class LocationLogBody(
    val latitude: Double,
    val longitude: Double,
    val accuracy: String,
    @SerializedName("requested_by") val requestedBy: String,
    @SerializedName("event_id") val eventId: String,
    @SerializedName("accuracy_meters") val accuracyMeters: Float? = null,
    val provider: String? = null,
    val source: String,
    @SerializedName("captured_at") val capturedAt: String,
)

data class PublicConfig(
    @SerializedName("sms_prefix") val smsPrefix: String? = null,
    @SerializedName("pin_min_length") val pinMinLength: Int? = null,
    @SerializedName("pin_max_length") val pinMaxLength: Int? = null,
    @SerializedName("pin_attempt_cap") val pinAttemptCap: Int? = null,
    @SerializedName("pin_window_minutes") val pinWindowMinutes: Int? = null,
    @SerializedName("login_attempt_cap") val loginAttemptCap: Int? = null,
    @SerializedName("login_window_minutes") val loginWindowMinutes: Int? = null,
    val maintenance: Boolean? = null,
    @SerializedName("payments_enabled") val paymentsEnabled: Boolean? = null,
    @SerializedName("emergency_enabled") val emergencyEnabled: Boolean? = null,
    @SerializedName("emergency_interval_minutes") val emergencyIntervalMinutes: Int? = null,
    /** Legacy field; prefer [emergencyIntervalMinutes]. */
    @SerializedName("emergency_interval_hours") val emergencyIntervalHours: Int? = null,
) {
    fun resolvedEmergencyMinutes(): Int {
        emergencyIntervalMinutes?.takeIf { it in 15..1440 }?.let { return it }
        emergencyIntervalHours?.takeIf { it in 1..24 }?.let { return (it * 60).coerceIn(15, 1440) }
        return 60
    }
}

data class LocationLogResult(
    @SerializedName("requests_used") val requestsUsed: Int?,
    @SerializedName("requests_remaining") val requestsRemaining: Int?,
    @SerializedName("limit_reached") val limitReached: Boolean?,
    @SerializedName("plan_name") val planName: String? = null,
    @SerializedName("monthly_limit") val monthlyLimit: Int? = null,
    val status: String? = null,
)

data class LocationActivityList(
    val logs: List<RemoteLocationLog> = emptyList(),
)

data class RemoteLocationLog(
    val id: Int,
    val latitude: Double,
    val longitude: Double,
    val accuracy: String? = null,
    @SerializedName("accuracy_meters") val accuracyMeters: Float? = null,
    val source: String? = null,
    @SerializedName("requested_by") val requestedBy: String? = null,
    @SerializedName("event_id") val eventId: String? = null,
    @SerializedName("captured_at") val capturedAt: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
)

data class NotificationList(
    val notifications: List<NotificationItem> = emptyList(),
)

data class NotificationItem(
    val id: Int,
    val title: String,
    val body: String,
    val kind: String,
    val read: Boolean,
    @SerializedName("created_at") val createdAt: String,
)

data class NotificationReadResult(
    val id: Int,
    val read: Boolean,
)
