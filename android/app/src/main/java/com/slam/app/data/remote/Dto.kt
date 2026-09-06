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

data class LocationLogResult(
    @SerializedName("requests_used") val requestsUsed: Int?,
    @SerializedName("requests_remaining") val requestsRemaining: Int?,
    @SerializedName("limit_reached") val limitReached: Boolean?,
)
