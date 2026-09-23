package dev.blaiseagent.payments

import com.sun.net.httpserver.HttpServer
import dev.blaiseagent.config.WooviEnvironment
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WooviClientTest {
    @Test
    fun connectionTestUsesCompanyEndpointAndRawAuthorizationKey() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        var authorization: String? = null
        var path: String? = null
        server.createContext("/api/v1/company") { exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            path = exchange.requestURI.path
            exchange.respond(200, "{}")
        }
        server.start()
        try {
            val client = WooviClient(
                WooviEnvironment.Sandbox,
                baseUrlOverride = "http://127.0.0.1:${server.address.port}/api",
            )

            val result = runBlocking { client.testConnection("sandbox-app-id") }

            assertIs<WooviConnection.Ready>(result)
            assertEquals("sandbox-app-id", authorization)
            assertEquals("/api/v1/company", path)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun failedConnectionDoesNotExposeApiKeyOrTransportDetails() {
        val client = WooviClient(
            WooviEnvironment.Sandbox,
            baseUrlOverride = "http://127.0.0.1:1/api",
            timeoutMillis = 100,
        )

        val result = runBlocking { client.testConnection("secret-key") }

        assertIs<WooviConnection.Unavailable>(result)
        assertTrue(!result.message.contains("secret-key"))
        assertTrue(!result.message.contains("java.net"))
    }
}

private fun com.sun.net.httpserver.HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray()
    responseHeaders.add("Content-Type", "application/json")
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
