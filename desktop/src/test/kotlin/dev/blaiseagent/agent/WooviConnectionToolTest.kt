package dev.blaiseagent.agent

import dev.blaiseagent.config.CredentialKey
import dev.blaiseagent.config.CredentialStore
import dev.blaiseagent.config.WooviEnvironment
import dev.blaiseagent.payments.WooviConnection
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WooviConnectionToolTest {
    @Test
    fun toolTestsTheSelectedEnvironmentWithoutExposingTheApiKey() = runBlocking {
        val store = FakeCredentialStore("secret-app-id")
        var testedKey: String? = null
        val tool = WooviConnectionTool(
            credentialStore = store,
            environment = { WooviEnvironment.Production },
            connectionTester = { _, key ->
                testedKey = key
                WooviConnection.Ready
            },
        )

        val result = tool.execute()

        assertEquals(
            "Environment: Live Woovi\n" +
                "Endpoint: https://api.woovi.com/api\n" +
                "Authorization: AppID loaded from OS credential store (hidden)\n" +
                "Connection successful.",
            result,
        )
        assertEquals("secret-app-id", testedKey)
        assertTrue("secret-app-id" !in result)
    }

    @Test
    fun toolRequestsSetupWhenTheSelectedEnvironmentHasNoKey() = runBlocking {
        val tool = WooviConnectionTool(
            credentialStore = FakeCredentialStore(null),
            environment = { WooviEnvironment.Sandbox },
            connectionTester = { _, _ -> error("should not call gateway") },
        )

        assertEquals("Save a Sandbox Woovi API key first.", tool.execute())
    }
}

private class FakeCredentialStore(private val value: String?) : CredentialStore {
    override suspend fun contains(key: CredentialKey) = value != null
    override suspend fun read(key: CredentialKey) = value
    override suspend fun write(key: CredentialKey, value: String) = Unit
    override suspend fun delete(key: CredentialKey) = Unit
}
