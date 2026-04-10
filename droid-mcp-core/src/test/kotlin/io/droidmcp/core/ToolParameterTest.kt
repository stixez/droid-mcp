package io.droidmcp.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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
}
