package dev.blaiseagent.config

enum class WooviEnvironment(
    val label: String,
    val baseUrl: String,
    val credentialKey: CredentialKey,
) {
    Sandbox("Sandbox", "https://api.woovi-sandbox.com/api", CredentialKey.WooviSandboxApiKey),
    Production("Live", "https://api.woovi.com/api", CredentialKey.WooviProductionApiKey),
}
