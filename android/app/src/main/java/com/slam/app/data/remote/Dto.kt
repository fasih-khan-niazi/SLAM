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
)

data class PublicUser(
    val id: Int,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
)

data class SubscriptionInfo(
    @SerializedName("plan_name") val planName: String?,
    val status: String?,
    @SerializedName("requests_remaining") val requestsRemaining: Int?,
    @SerializedName("monthly_limit") val monthlyLimit: Int?,
    @SerializedName("start_date") val startDate: String? = null,
    @SerializedName("end_date") val endDate: String? = null,
)

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
)

data class LocationLogBody(
    val latitude: Double,
    val longitude: Double,
    val accuracy: String,
    @SerializedName("requested_by") val requestedBy: String,
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
)

data class LocationLogResult(
    @SerializedName("requests_used") val requestsUsed: Int?,
    @SerializedName("requests_remaining") val requestsRemaining: Int?,
    @SerializedName("limit_reached") val limitReached: Boolean?,
)
