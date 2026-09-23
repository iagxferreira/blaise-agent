package dev.blaiseagent.agent

import dev.blaiseagent.config.CredentialStore
import dev.blaiseagent.config.WooviEnvironment
import dev.blaiseagent.payments.WooviClient
import dev.blaiseagent.payments.WooviConnection
import dev.langchain4j.agent.tool.ToolSpecification

class WooviConnectionTool(
    private val credentialStore: CredentialStore,
    private val environment: () -> WooviEnvironment,
    private val connectionTester: suspend (WooviEnvironment, String) -> WooviConnection = { selected, key ->
        WooviClient(selected).testConnection(key)
    },
) {
    val specification: ToolSpecification = ToolSpecification.builder()
        .name("test_woovi_connection")
        .description("Test the saved Woovi API key for the currently selected Woovi environment.")
        .build()

    suspend fun execute(): String {
        val selected = environment()
        val key = credentialStore.read(selected.credentialKey)
            ?: return "Save a ${selected.label} Woovi API key first."
        val result = when (connectionTester(selected, key)) {
            WooviConnection.Ready -> "Connection successful."
            is WooviConnection.Unavailable -> "Connection failed."
        }
        return "Environment: ${selected.label} Woovi\n" +
            "Endpoint: ${selected.baseUrl}\n" +
            "Authorization: AppID loaded from OS credential store (hidden)\n" +
            result
    }
}
