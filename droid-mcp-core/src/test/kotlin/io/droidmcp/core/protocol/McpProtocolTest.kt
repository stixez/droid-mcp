package io.droidmcp.core.protocol

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class McpProtocolTest {

    private lateinit var registry: ToolRegistry
    private lateinit var protocol: McpProtocol

    private val echoTool = object : McpTool {
        override val name = "echo"
        override val description = "Echoes input"
        override val parameters = listOf(
            ToolParameter("message", "The message", ParameterType.STRING, required = true)
        )
        override suspend fun execute(params: Map<String, Any>): ToolResult {
            val msg = params["message"]?.toString() ?: return ToolResult.error("missing message")
            return ToolResult.success(mapOf("echo" to msg))
        }
    }

    @BeforeEach
    fun setup() {
        registry = ToolRegistry()
        registry.register(echoTool)
        protocol = McpProtocolImpl(registry)
    }

    @Test
    fun `handleInitialize returns server info`() = runTest {
        val request = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        assertThat(json["id"]?.jsonPrimitive?.content).isEqualTo("1")
        val result = json["result"]?.jsonObject
        assertThat(result).isNotNull()
        assertThat(result!!["serverInfo"]?.jsonObject?.get("name")?.jsonPrimitive?.content).isEqualTo("droid-mcp")
    }

    @Test
    fun `handleToolsList returns registered tools`() = runTest {
        val request = """{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        val result = json["result"]?.jsonObject
        assertThat(result).isNotNull()
        assertThat(result.toString()).contains("echo")
    }

    @Test
    fun `handleToolsCall executes tool and returns result`() = runTest {
        val request = """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello"}}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        val result = json["result"]?.jsonObject
        assertThat(result).isNotNull()
        assertThat(result.toString()).contains("hello")
    }

    @Test
    fun `handleToolsCall returns error for unknown tool`() = runTest {
        val request = """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"nonexistent","arguments":{}}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        assertThat(json.toString()).contains("error")
    }

    @Test
    fun `handleUnknownMethod returns method not found error`() = runTest {
        val request = """{"jsonrpc":"2.0","id":5,"method":"unknown/method","params":{}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        assertThat(json["error"]).isNotNull()
    }
}
