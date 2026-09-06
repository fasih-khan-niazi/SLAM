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

    @GET("health")
    suspend fun health(): Response<Map<String, Any>>
}

object SlamApiFactory {
    fun create(baseUrl: String): SlamApi {
        val root = (baseUrl.ifBlank { BuildConfig.API_BASE_URL }).trimEnd('/') + "/"
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()

        return Retrofit.Builder()
            .baseUrl(root)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SlamApi::class.java)
    }
}
