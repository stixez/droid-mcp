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

    @Test
    fun `disabled tool is hidden from listEnabledTools but stays registered`() {
        val registry = ToolRegistry()
        registry.register(fakeTool("tool_a"))
        registry.register(fakeTool("tool_b"))
        registry.setToolEnabled("tool_a", false)
        assertThat(registry.listEnabledTools().map { it.name }).containsExactly("tool_b")
        assertThat(registry.listTools().map { it.name }).containsExactly("tool_a", "tool_b")
        assertThat(registry.getTool("tool_a")).isNotNull()
        assertThat(registry.isEnabled("tool_a")).isFalse()
    }

    @Test
    fun `executeTool rejects disabled tool with tool_disabled code`() = runTest {
        val registry = ToolRegistry()
        registry.register(fakeTool("gated"))
        registry.setToolEnabled("gated", false)
        val result = registry.executeTool("gated", emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).startsWith("tool_disabled")
    }

    @Test
    fun `re-enabling a tool restores it`() = runTest {
        val registry = ToolRegistry()
        registry.register(fakeTool("gated"))
        registry.setToolEnabled("gated", false)
        registry.setToolEnabled("gated", true)
        assertThat(registry.isEnabled("gated")).isTrue()
        assertThat(registry.executeTool("gated", emptyMap()).isSuccess).isTrue()
    }

    @Test
    fun `setDisabledTools replaces the full gate set`() {
        val registry = ToolRegistry()
        registry.register(fakeTool("a"))
        registry.register(fakeTool("b"))
        registry.register(fakeTool("c"))
        registry.setDisabledTools(setOf("a", "b"))
        assertThat(registry.disabledTools()).containsExactly("a", "b")
        registry.setDisabledTools(setOf("c"))
        assertThat(registry.disabledTools()).containsExactly("c")
        assertThat(registry.isEnabled("a")).isTrue()
    }
}
