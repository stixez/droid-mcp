package io.droidmcp.core.protocol

import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Shared JSON builders for the tool catalogue, used by both [McpProtocolImpl] (`tools/list`)
 * and [InProcessTransport][io.droidmcp.core.transport.InProcessTransport] so the two
 * transports can't drift apart.
 */
internal object ToolSchemas {

    /** The tool's JSON Schema: `type: object` with `properties` and a `required` list. */
    fun inputSchema(tool: McpTool): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            tool.parameters.forEach { param -> put(param.name, toJsonElement(param.toJsonSchema())) }
        }
        putJsonArray("required") {
            tool.parameters.filter { it.required }.forEach { add(JsonPrimitive(it.name)) }
        }
    }

    /** MCP `annotations` object with only non-default hints, or null when all are defaults. */
    fun annotations(a: ToolAnnotations): JsonObject? {
        val default = ToolAnnotations()
        if (a == default) return null
        return buildJsonObject {
            if (a.readOnlyHint != default.readOnlyHint) put("readOnlyHint", a.readOnlyHint)
            if (a.destructiveHint != default.destructiveHint) put("destructiveHint", a.destructiveHint)
            if (a.idempotentHint != default.idempotentHint) put("idempotentHint", a.idempotentHint)
            if (a.openWorldHint != default.openWorldHint) put("openWorldHint", a.openWorldHint)
            a.title?.let { put("title", it) }
        }
    }

    /** Converts plain Kotlin values (maps, lists, primitives) into a [JsonElement] tree. */
    fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) -> if (k is String) put(k, toJsonElement(v)) }
        }
        is Iterable<*> -> buildJsonArray { value.forEach { add(toJsonElement(it)) } }
        is Array<*> -> buildJsonArray { value.forEach { add(toJsonElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }
}
