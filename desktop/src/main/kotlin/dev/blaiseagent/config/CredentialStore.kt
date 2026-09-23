package dev.blaiseagent.config

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface CredentialStore {
    suspend fun contains(key: CredentialKey): Boolean
    suspend fun read(key: CredentialKey): String?
    suspend fun write(key: CredentialKey, value: String)
    suspend fun delete(key: CredentialKey)
}

enum class CredentialKey(val label: String) {
    WooviSandboxApiKey("Woovi Sandbox API Key"),
    WooviProductionApiKey("Woovi Production API Key"),
}

class CredentialStoreUnavailable : IOException("Secure credential storage is unavailable")

/** Linux Secret Service adapter. It never writes credentials to ordinary files or preferences. */
class SecretToolCredentialStore(
    private val command: String = "secret-tool",
    private val processRunner: SecretToolProcessRunner = SecretToolProcessRunner.Default,
) : CredentialStore {
    override suspend fun contains(key: CredentialKey): Boolean = read(key) != null

    override suspend fun read(key: CredentialKey): String? = withContext(Dispatchers.IO) {
        val result = processRunner.run(
            command,
            listOf("lookup", "app", APP_ID, "credential", key.name),
            stdin = null,
        )
        when {
            result.exitCode == 0 && result.stdout.isNotBlank() -> result.stdout.trimEnd('\n')
            result.exitCode == 0 -> null
            result.exitCode == 1 -> null
            else -> throw CredentialStoreUnavailable()
        }
    }

    override suspend fun write(key: CredentialKey, value: String) = withContext(Dispatchers.IO) {
        require(value.isNotBlank()) { "Credential cannot be blank" }
        val result = processRunner.run(
            command,
            listOf("store", "--label=$APP_LABEL", "app", APP_ID, "credential", key.name),
            stdin = value,
        )
        if (result.exitCode != 0) throw CredentialStoreUnavailable()
    }

    override suspend fun delete(key: CredentialKey) = withContext(Dispatchers.IO) {
        val result = processRunner.run(
            command,
            listOf("clear", "app", APP_ID, "credential", key.name),
            stdin = null,
        )
        if (result.exitCode != 0 && result.exitCode != 1) throw CredentialStoreUnavailable()
    }

    private companion object {
        const val APP_ID = "dev.blaiseagent"
        const val APP_LABEL = "Blaise Agent credential"
    }
}

object CredentialStoreFactory {
    fun create(): CredentialStore? = if (System.getProperty("os.name").lowercase().contains("linux")) {
        SecretToolCredentialStore()
    } else {
        null
    }
}

data class SecretToolResult(val exitCode: Int, val stdout: String)

fun interface SecretToolProcessRunner {
    fun run(command: String, arguments: List<String>, stdin: String?): SecretToolResult

    companion object {
        val Default = SecretToolProcessRunner { command, arguments, stdin ->
            val process = try {
                ProcessBuilder(listOf(command) + arguments).redirectErrorStream(false).start()
            } catch (_: IOException) {
                throw CredentialStoreUnavailable()
            }
            process.outputStream.bufferedWriter().use { writer ->
                if (stdin != null) writer.write(stdin)
            }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            process.errorStream.bufferedReader().use { it.readText() }
            SecretToolResult(process.waitFor(), stdout)
        }
    }
}
