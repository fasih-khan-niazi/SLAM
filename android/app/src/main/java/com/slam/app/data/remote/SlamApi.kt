package com.slam.app.data.remote

import com.slam.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface SlamApi {
    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterBody): Response<ApiEnvelope<AuthData>>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginBody): Response<ApiEnvelope<AuthData>>

    @GET("api/auth/me")
    suspend fun me(@Header("Authorization") bearer: String): Response<ApiEnvelope<MeData>>

    @POST("api/location/log")
    suspend fun logLocation(
        @Header("Authorization") bearer: String,
        @Body body: LocationLogBody,
    ): Response<ApiEnvelope<LocationLogResult>>

    @GET("api/config")
    suspend fun config(): Response<ApiEnvelope<PublicConfig>>

    @GET("api/notifications")
    suspend fun notifications(
        @Header("Authorization") bearer: String,
    ): Response<ApiEnvelope<NotificationList>>

    @PATCH("api/notifications/{id}/read")
    suspend fun markNotificationRead(
        @Header("Authorization") bearer: String,
        @Path("id") id: Int,
    ): Response<ApiEnvelope<NotificationReadResult>>

    @GET("health")
    suspend fun health(): Response<Map<String, Any>>
}

object SlamApiFactory {
    @Volatile
    private var cached: Pair<String, SlamApi>? = null

    fun create(baseUrl: String): SlamApi {
        val root = (baseUrl.ifBlank { BuildConfig.API_BASE_URL }).trimEnd('/') + "/"
        cached?.takeIf { it.first == root }?.let { return it.second }
        return synchronized(this) {
            cached?.takeIf { it.first == root }?.second ?: build(root).also {
                cached = root to it
            }
        }
    }

    private fun build(root: String): SlamApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
                )
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(root)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SlamApi::class.java)
    }
}
