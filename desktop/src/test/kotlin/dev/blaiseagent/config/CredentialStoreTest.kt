package dev.blaiseagent.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CredentialStoreTest {
    @Test
    fun savingPassesSecretThroughStdinAndNeverAsAnArgument() {
        var capturedArguments: List<String>? = null
        var capturedStdin: String? = null
        val store = SecretToolCredentialStore(processRunner = SecretToolProcessRunner { _, arguments, stdin ->
            capturedArguments = arguments
            capturedStdin = stdin
            SecretToolResult(0, "")
        })

        kotlinx.coroutines.runBlocking {
            store.write(CredentialKey.WooviSandboxApiKey, "sandbox-secret")
        }

        assertEquals("sandbox-secret", capturedStdin)
        assertEquals(listOf("store", "--label=Blaise Agent credential", "app", "dev.blaiseagent", "credential", "WooviSandboxApiKey"), capturedArguments)
    }

    @Test
    fun aFailedSecretServiceOperationIsSanitized() {
        val store = SecretToolCredentialStore(processRunner = SecretToolProcessRunner { _, _, _ ->
            SecretToolResult(2, "secret-tool: private session error")
        })

        assertFailsWith<CredentialStoreUnavailable> {
            kotlinx.coroutines.runBlocking { store.read(CredentialKey.WooviSandboxApiKey) }
        }
    }
}
