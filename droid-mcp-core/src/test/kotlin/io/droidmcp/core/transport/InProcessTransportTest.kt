package io.droidmcp.core.transport

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class InProcessTransportTest {

    private val echoTool = object : McpTool {
        override val name = "echo"
        override val description = "Echoes input"
        override val parameters = listOf(
            ToolParameter("message", "The message", ParameterType.STRING, required = true)
        )
        override suspend fun execute(params: Map<String, Any>): ToolResult =
            ToolResult.success(mapOf("echo" to params["message"]))
    }

    private fun createTransport(): InProcessTransport {
        val registry = ToolRegistry()
        registry.register(echoTool)
        return InProcessTransport(registry)
    }

    @Test
    fun `listTools returns tool definitions`() {
        val transport = createTransport()
        val tools = transport.listTools()
        assertThat(tools).hasSize(1)
        assertThat(tools[0].name).isEqualTo("echo")
    }

    @Test
    fun `listToolsJson returns valid JSON array`() {
        val transport = createTransport()
        val json = transport.listToolsJson()
        assertThat(json).contains("echo")
        assertThat(json).contains("Echoes input")
    }

    @Test
    fun `callTool executes tool and returns result`() = runTest {
        val transport = createTransport()
        val result = transport.callTool("echo", mapOf("message" to "hi"))
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("echo")).isEqualTo("hi")
    }

    @Test
    fun `callTool returns error for missing tool`() = runTest {
        val transport = createTransport()
        val result = transport.callTool("missing", emptyMap())
        assertThat(result.isSuccess).isFalse()
    }
}
