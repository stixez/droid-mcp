package io.droidmcp.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ToolRegistryTest {

    private fun fakeTool(toolName: String): McpTool = object : McpTool {
        override val name = toolName
        override val description = "A fake tool"
        override val parameters = emptyList<ToolParameter>()
        override suspend fun execute(params: Map<String, Any>) = ToolResult.success(mapOf("ok" to true))
    }

    @Test
    fun `register and retrieve tool by name`() {
        val registry = ToolRegistry()
        val tool = fakeTool("test_tool")
        registry.register(tool)
        assertThat(registry.getTool("test_tool")).isEqualTo(tool)
    }

    @Test
    fun `getTool returns null for unknown name`() {
        val registry = ToolRegistry()
        assertThat(registry.getTool("nonexistent")).isNull()
    }

    @Test
    fun `listTools returns all registered tools`() {
        val registry = ToolRegistry()
        registry.register(fakeTool("tool_a"))
        registry.register(fakeTool("tool_b"))
        assertThat(registry.listTools().map { it.name }).containsExactly("tool_a", "tool_b")
    }

    @Test
    fun `executeTool calls correct tool`() = runTest {
        val registry = ToolRegistry()
        registry.register(fakeTool("my_tool"))
        val result = registry.executeTool("my_tool", emptyMap())
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `executeTool returns error for unknown tool`() = runTest {
        val registry = ToolRegistry()
        val result = registry.executeTool("missing", emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("missing")
    }
}
