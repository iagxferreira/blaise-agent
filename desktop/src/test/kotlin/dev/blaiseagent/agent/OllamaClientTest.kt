package dev.blaiseagent.agent

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OllamaClientTest {
    @Test
    fun healthyEndpointReportsOllamaAndDiscoversModels() = runTest {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            exchange.respond(200, "Ollama is running")
        }
        server.createContext("/api/tags") { exchange ->
            exchange.respond(
                200,
                """{"models":[{"name":"qwen2.5-coder:3b","size":1900000000,"details":{"family":"qwen2"}}]}""",
            )
        }
        server.start()
        try {
            val client = OllamaClient("http://127.0.0.1:${server.address.port}")
            assertEquals(OllamaConnection.Ready, client.checkConnection())
            val models = client.listModels()

            assertEquals(1, models.size)
            assertEquals("qwen2.5-coder:3b", models.single().name)
            assertEquals(1900000000L, models.single().sizeBytes)
            assertEquals("qwen2", models.single().family)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun unavailableEndpointDoesNotExposeTransportDetails() = runTest {
        val client = OllamaClient("http://127.0.0.1:1", timeoutMillis = 100)

        val result = client.checkConnection()

        assertIs<OllamaConnection.Unavailable>(result)
        assertTrue(result.message.contains("Ollama"))
        assertTrue(!result.message.contains("java.net"))
    }
}

private fun com.sun.net.httpserver.HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray()
    responseHeaders.add("Content-Type", "application/json")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
