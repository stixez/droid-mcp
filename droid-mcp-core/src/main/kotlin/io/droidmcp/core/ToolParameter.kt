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
 * @property itemsType Element type for [ParameterType.ARRAY] parameters, emitted as the schema's
 *   `items`. When null an array still gets an unconstrained `"items": {}` — some clients
 *   (Gemini/OpenAI-compatible bridges) reject array schemas with no `items` at all.
 * @property enumValues The only accepted values, emitted as the schema's `enum` (for arrays,
 *   on `items`). Advisory — tools still validate their own input.
 * @property minimum Inclusive lower bound for [ParameterType.INTEGER] / [ParameterType.NUMBER],
 *   emitted as `minimum`. Use it to advertise the range the tool clamps to.
 * @property maximum Inclusive upper bound, emitted as `maximum`.
 */
@Serializable
data class ToolParameter(
    val name: String,
    val description: String,
    val type: ParameterType,
    val required: Boolean = false,
    val itemsType: ParameterType? = null,
    val enumValues: List<String>? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
) {
    init {
        require(enumValues == null || enumValues.isNotEmpty()) { "enumValues must not be empty for '$name'" }
        require((minimum == null && maximum == null) || type == ParameterType.INTEGER || type == ParameterType.NUMBER) {
            "minimum/maximum only apply to numeric parameters ('$name' is $type)"
        }
        require(minimum == null || maximum == null || minimum <= maximum) { "minimum > maximum for '$name'" }
    }

    /**
     * The JSON Schema fragment for this one parameter — `{ "type": ..., "description": ... }`,
     * plus `items` for arrays, `enum`, and `minimum`/`maximum` when declared. Required-ness is
     * intentionally omitted here; the protocol layer aggregates it into the parent object
     * schema's `required` list.
     */
    fun toJsonSchema(): Map<String, Any> = buildMap {
        put("type", type.jsonType)
        put("description", description)
        if (type == ParameterType.ARRAY) {
            val items = buildMap<String, Any> {
                itemsType?.let { put("type", it.jsonType) }
                enumValues?.let { put("enum", it) }
            }
            put("items", items)
        } else {
            enumValues?.let { put("enum", it) }
        }
        minimum?.let { put("minimum", bound(it)) }
        maximum?.let { put("maximum", bound(it)) }
    }

    /** Integer schemas get integral bounds (`1`, not `1.0`). */
    private fun bound(value: Double): Number =
        if (type == ParameterType.INTEGER) value.toLong() else value
}
