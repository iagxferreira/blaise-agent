package dev.blaiseagent.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ParsedTextToolCall(val name: String, val arguments: String)

private val toolCallJson = Json { ignoreUnknownKeys = true }

fun parseTextToolCall(text: String): ParsedTextToolCall? {
    val objectValue = runCatching { toolCallJson.parseToJsonElement(text.trim()) as? JsonObject }.getOrNull()
        ?: return null
    val name = objectValue["name"]?.jsonPrimitive?.contentOrNull ?: return null
    val arguments = objectValue["arguments"] as? JsonObject ?: return null
    return ParsedTextToolCall(name, arguments.toString())
}
