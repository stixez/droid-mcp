package io.droidmcp.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ToolParameterTest {

    @Test
    fun `required parameter has correct properties`() {
        val param = ToolParameter(
            name = "date",
            description = "The date to query",
            type = ParameterType.STRING,
            required = true,
        )
        assertThat(param.name).isEqualTo("date")
        assertThat(param.required).isTrue()
    }

    @Test
    fun `optional parameter defaults required to false`() {
        val param = ToolParameter(
            name = "limit",
            description = "Max results",
            type = ParameterType.INTEGER,
        )
        assertThat(param.required).isFalse()
    }

    @Test
    fun `toJsonSchema produces valid MCP parameter schema`() {
        val param = ToolParameter(
            name = "query",
            description = "Search query",
            type = ParameterType.STRING,
            required = true,
        )
        val schema = param.toJsonSchema()
        assertThat(schema).containsKey("type")
        assertThat(schema["type"]).isEqualTo("string")
        assertThat(schema["description"]).isEqualTo("Search query")
    }

    @Test
    fun `enum and bounds are emitted in the schema`() {
        val mode = ToolParameter("mode", "m", ParameterType.STRING, enumValues = listOf("a", "b"))
        assertThat(mode.toJsonSchema()["enum"]).isEqualTo(listOf("a", "b"))

        val limit = ToolParameter("limit", "l", ParameterType.INTEGER, minimum = 1.0, maximum = 100.0)
        assertThat(limit.toJsonSchema()["minimum"]).isEqualTo(1L)
        assertThat(limit.toJsonSchema()["maximum"]).isEqualTo(100L)

        val pitch = ToolParameter("pitch", "p", ParameterType.NUMBER, minimum = 0.5, maximum = 2.0)
        assertThat(pitch.toJsonSchema()["minimum"]).isEqualTo(0.5)
    }

    @Test
    fun `array enum goes on items`() {
        val tags = ToolParameter("tags", "t", ParameterType.ARRAY, itemsType = ParameterType.STRING, enumValues = listOf("x"))
        val schema = tags.toJsonSchema()
        assertThat(schema).doesNotContainKey("enum")
        assertThat(schema["items"]).isEqualTo(mapOf("type" to "string", "enum" to listOf("x")))
    }

    @Test
    fun `invalid declarations are rejected`() {
        assertThrows<IllegalArgumentException> { ToolParameter("x", "x", ParameterType.STRING, minimum = 1.0) }
        assertThrows<IllegalArgumentException> { ToolParameter("x", "x", ParameterType.INTEGER, minimum = 5.0, maximum = 1.0) }
        assertThrows<IllegalArgumentException> { ToolParameter("x", "x", ParameterType.STRING, enumValues = emptyList()) }
    }
}
