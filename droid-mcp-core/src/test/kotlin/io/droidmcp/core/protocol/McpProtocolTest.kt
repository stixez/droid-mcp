package io.droidmcp.core.protocol

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
        // Unknown tool errors flow as MCP content errors (isError: true), not JSON-RPC level errors
        val result = json["result"]?.jsonObject
        assertThat(result).isNotNull()
        assertThat(result!!["isError"].toString()).isEqualTo("true")
        assertThat(result.toString()).contains("Unknown tool")
    }

    @Test
    fun `handleToolsList omits annotations when tool uses defaults`() = runTest {
        val request = """{"jsonrpc":"2.0","id":10,"method":"tools/list","params":{}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        val toolEntry = json["result"]?.jsonObject?.get("tools")?.jsonArray?.first()?.jsonObject
        assertThat(toolEntry).isNotNull()
        assertThat(toolEntry!!.containsKey("annotations")).isFalse()
    }

    @Test
    fun `handleToolsList emits annotations for annotated tool`() = runTest {
        val annotatedRegistry = ToolRegistry()
        annotatedRegistry.register(object : McpTool {
            override val name = "read_thing"
            override val description = "reads"
            override val parameters = emptyList<ToolParameter>()
            override val annotations = ToolAnnotations(
                readOnlyHint = true,
                idempotentHint = true,
                title = "Read Thing",
            )
            override suspend fun execute(params: Map<String, Any>): ToolResult =
                ToolResult.success(emptyMap())
        })
        val annotatedProtocol = McpProtocolImpl(annotatedRegistry)

        val request = """{"jsonrpc":"2.0","id":11,"method":"tools/list","params":{}}"""
        val response = annotatedProtocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        val toolEntry = json["result"]?.jsonObject?.get("tools")?.jsonArray?.first()?.jsonObject
        val annotations = toolEntry?.get("annotations")?.jsonObject
        assertThat(annotations).isNotNull()
        assertThat(annotations!!["readOnlyHint"]?.jsonPrimitive?.content).isEqualTo("true")
        assertThat(annotations["idempotentHint"]?.jsonPrimitive?.content).isEqualTo("true")
        assertThat(annotations["title"]?.jsonPrimitive?.content).isEqualTo("Read Thing")
        assertThat(annotations.containsKey("destructiveHint")).isFalse()
        assertThat(annotations.containsKey("openWorldHint")).isFalse()
    }

    @Test
    fun `handleToolsList in read-only mode hides destructive tools`() = runTest {
        val mixedRegistry = ToolRegistry()
        mixedRegistry.register(readTool("read_thing"))
        mixedRegistry.register(destructiveTool("send_thing"))
        val readOnlyProtocol = McpProtocolImpl(mixedRegistry, readOnly = true)

        val request = """{"jsonrpc":"2.0","id":20,"method":"tools/list","params":{}}"""
        val response = readOnlyProtocol.handleMessage(request)
        val tools = Json.parseToJsonElement(response).jsonObject["result"]?.jsonObject
            ?.get("tools")?.jsonArray ?: error("missing tools array")
        val names = tools.map { it.jsonObject["name"]?.jsonPrimitive?.content }
        assertThat(names).contains("read_thing")
        assertThat(names).doesNotContain("send_thing")
    }

    @Test
    fun `handleToolsCall in read-only mode rejects non-readonly tools`() = runTest {
        val mixedRegistry = ToolRegistry()
        mixedRegistry.register(readTool("read_thing"))
        mixedRegistry.register(destructiveTool("send_thing"))
        val readOnlyProtocol = McpProtocolImpl(mixedRegistry, readOnly = true)

        val request = """{"jsonrpc":"2.0","id":21,"method":"tools/call","params":{"name":"send_thing","arguments":{}}}"""
        val response = readOnlyProtocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        val result = json["result"]?.jsonObject
        assertThat(result).isNotNull()
        assertThat(result!!["isError"].toString()).isEqualTo("true")
        assertThat(result.toString()).contains("read-only")
    }

    @Test
    fun `handleToolsCall in read-only mode permits readonly tools`() = runTest {
        val mixedRegistry = ToolRegistry()
        mixedRegistry.register(readTool("read_thing"))
        val readOnlyProtocol = McpProtocolImpl(mixedRegistry, readOnly = true)

        val request = """{"jsonrpc":"2.0","id":22,"method":"tools/call","params":{"name":"read_thing","arguments":{}}}"""
        val response = readOnlyProtocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        val result = json["result"]?.jsonObject
        assertThat(result).isNotNull()
        assertThat(result!!["isError"].toString()).isEqualTo("false")
    }

    private fun readTool(toolName: String) = object : McpTool {
        override val name = toolName
        override val description = "read-only tool"
        override val parameters = emptyList<ToolParameter>()
        override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
        override suspend fun execute(params: Map<String, Any>): ToolResult =
            ToolResult.success(mapOf("ok" to true))
    }

    private fun destructiveTool(toolName: String) = object : McpTool {
        override val name = toolName
        override val description = "destructive tool"
        override val parameters = emptyList<ToolParameter>()
        override val annotations = ToolAnnotations(destructiveHint = true)
        override suspend fun execute(params: Map<String, Any>): ToolResult =
            ToolResult.success(mapOf("ok" to true))
    }

    @Test
    fun `handleUnknownMethod returns method not found error`() = runTest {
        val request = """{"jsonrpc":"2.0","id":5,"method":"unknown/method","params":{}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        assertThat(json["error"]).isNotNull()
    }
}
