package dev.blaiseagent.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

sealed interface OllamaConnection {
    data object Ready : OllamaConnection
    data class Unavailable(val message: String) : OllamaConnection
}

data class OllamaModel(
    val name: String,
    val sizeBytes: Long,
    val family: String?,
)

/** Typed API boundary. The same pattern will be used for Woovi and other gateways. */
interface OllamaService {
    @GET("/")
    suspend fun health(): Response<Unit>

    @GET("api/tags")
    suspend fun listModels(): Response<ModelListResponse>
}

/** Retrofit-backed Ollama client with sanitized diagnostics for the UI. */
class OllamaClient(
    baseUrl: String = DEFAULT_BASE_URL,
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    service: OllamaService? = null,
) {
    val endpoint: String = baseUrl.trimEnd('/')
    private val service = service ?: createService(endpoint, timeoutMillis)

    suspend fun checkConnection(): OllamaConnection = try {
        val response = service.health()
        if (response.isSuccessful) OllamaConnection.Ready
        else OllamaConnection.Unavailable("Ollama is not reachable at $endpoint")
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        OllamaConnection.Unavailable("Ollama is not reachable at $endpoint")
    }

    suspend fun listModels(): List<OllamaModel> {
        val response = service.listModels()
        if (!response.isSuccessful) throw OllamaException("Ollama model discovery failed")
        return response.body()?.models.orEmpty().map {
            OllamaModel(it.name, it.size, it.details?.family)
        }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "http://127.0.0.1:11434"
        const val DEFAULT_TIMEOUT_MILLIS = 2_000L

        fun createService(endpoint: String, timeoutMillis: Long): OllamaService {
            val json = Json { ignoreUnknownKeys = true }
            val httpClient = OkHttpClient.Builder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("$endpoint/")
                .client(httpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OllamaService::class.java)
        }
    }
}

class OllamaException(message: String) : RuntimeException(message)

@Serializable
data class ModelListResponse(val models: List<ModelSummary> = emptyList())

@Serializable
data class ModelSummary(
    val name: String,
    val size: Long = 0,
    val details: ModelDetails? = null,
)

@Serializable
data class ModelDetails(val family: String? = null)
