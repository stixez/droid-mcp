package io.droidmcp.core

import kotlinx.serialization.Serializable

enum class ParameterType(val jsonType: String) {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object"),
}

@Serializable
data class ToolParameter(
    val name: String,
    val description: String,
    val type: ParameterType,
    val required: Boolean = false,
) {
    fun toJsonSchema(): Map<String, Any> = buildMap {
        put("type", type.jsonType)
        put("description", description)
    }
}
