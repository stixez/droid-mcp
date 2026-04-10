package io.droidmcp.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DroidMcpTest {

    private val fakeTool = object : McpTool {
        override val name = "fake"
        override val description = "Fake tool"
        override val parameters = emptyList<ToolParameter>()
        override suspend fun execute(params: Map<String, Any>) = ToolResult.success(mapOf("ok" to true))
    }

    @Test
    fun `builder registers tools`() {
        val mcp = DroidMcp.builder()
            .addTool(fakeTool)
            .build()
        assertThat(mcp.listTools()).hasSize(1)
        assertThat(mcp.listTools()[0].name).isEqualTo("fake")
    }

    @Test
    fun `builder registers multiple tools at once`() {
        val tool2 = object : McpTool {
            override val name = "fake2"
            override val description = "Fake 2"
            override val parameters = emptyList<ToolParameter>()
            override suspend fun execute(params: Map<String, Any>) = ToolResult.success(mapOf("ok" to true))
        }
        val mcp = DroidMcp.builder()
            .addTools(listOf(fakeTool, tool2))
            .build()
        assertThat(mcp.listTools()).hasSize(2)
    }

    @Test
    fun `callTool delegates to registry`() = runTest {
        val mcp = DroidMcp.builder()
            .addTool(fakeTool)
            .build()
        val result = mcp.callTool("fake", emptyMap())
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `listToolsJson returns valid JSON`() {
        val mcp = DroidMcp.builder()
            .addTool(fakeTool)
            .build()
        val json = mcp.listToolsJson()
        assertThat(json).contains("fake")
    }
}
