package dev.blaiseagent.payments

import dev.blaiseagent.config.WooviEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

sealed interface WooviConnection {
    data object Ready : WooviConnection
    data class Unavailable(val message: String) : WooviConnection
}

interface WooviService {
    @GET("v1/company")
    suspend fun company(@Header("Authorization") authorization: String): Response<JsonObject>
}

/** Non-mutating Woovi API boundary used to verify a saved AppID. */
class WooviClient(
    private val environment: WooviEnvironment,
    baseUrlOverride: String? = null,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    service: WooviService? = null,
) {
    private val service = service ?: createService(
        (baseUrlOverride ?: environment.baseUrl).trimEnd('/'),
        timeoutMillis,
    )

    suspend fun testConnection(apiKey: String): WooviConnection {
        if (apiKey.isBlank()) return WooviConnection.Unavailable("Woovi API key is not configured")
        return try {
            val response = service.company(apiKey)
            if (response.isSuccessful) WooviConnection.Ready
            else WooviConnection.Unavailable("Woovi ${environment.label.lowercase()} credentials were rejected")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WooviConnection.Unavailable("Woovi ${environment.label.lowercase()} API is unreachable")
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        val json = Json { ignoreUnknownKeys = true }

        fun createService(baseUrl: String, timeoutMillis: Long): WooviService {
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("$baseUrl/")
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(WooviService::class.java)
        }
    }
}
