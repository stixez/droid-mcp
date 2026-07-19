package io.droidmcp.core

import kotlinx.serialization.Serializable

/**
 * The JSON Schema primitive type of a [ToolParameter]. [jsonType] is the literal string
 * emitted into the tool's `inputSchema` (`"string"`, `"integer"`, …).
 */
enum class ParameterType(val jsonType: String) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object"),
}

/**
 * A single declared input to an [McpTool]. The list of these on a tool is compiled into the
 * `inputSchema` advertised by MCP `tools/list`, so [name] and [type] are part of the wire
 * contract.
 *
 * @property name Argument key the caller supplies and [McpTool.execute] reads from its params map.
 * @property description Human/LLM-readable explanation of the argument.
 * @property type JSON Schema type of the value.
 * @property required Whether the argument must be present. Surfaced in the enclosing schema's
 *   `required` array by the protocol layer (not by [toJsonSchema], which describes one property).
 */
@Serializable
data class ToolParameter(
    val name: String,
    val description: String,
    val type: ParameterType,
    val required: Boolean = false,
) {
    /**
     * The JSON Schema fragment for this one parameter — `{ "type": ..., "description": ... }`.
     * Required-ness is intentionally omitted here; the protocol layer aggregates it into the
     * parent object schema's `required` list.
     */
    fun toJsonSchema(): Map<String, Any> = buildMap {
        put("type", type.jsonType)
        put("description", description)
    }
}
