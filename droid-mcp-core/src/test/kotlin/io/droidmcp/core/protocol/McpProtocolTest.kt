package io.droidmcp.core.protocol

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolCallAudit
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

    /** Reports the runtime Kotlin type each argument actually arrives as in execute(). */
    private val typesTool = object : McpTool {
        override val name = "types"
        override val description = "Reports the runtime type of each received argument"
        override val parameters = emptyList<ToolParameter>()
        override suspend fun execute(params: Map<String, Any>): ToolResult =
            ToolResult.success(mapOf("types" to params.mapValues { (_, v) -> v::class.simpleName }))
    }

    @BeforeEach
    fun setup() {
        registry = ToolRegistry()
        registry.register(echoTool)
        registry.register(typesTool)
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
    fun `handleToolsCall preserves non-string argument types across the JSON boundary`() = runTest {
        // Regression test: the arguments-conversion path used to route every non-string JSON
        // primitive (and every array/object) through JsonPrimitive.content — the RAW STRING
        // form of the value regardless of its actual JSON type — so a tool's own
        // `params["x"] as? Number`/`as? Boolean`/`as? List<*>`/`as? Map<*, *>` checks always
        // failed for arguments sent over the HTTP transport, silently falling back to defaults.
        val request = """{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"types","arguments":{
            "int_arg":5,"double_arg":2.5,"true_arg":true,"false_arg":false,
            "string_arg":"hello","array_arg":[1,2,3],"object_arg":{"nested":1},"null_arg":null
        }}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        val text = json["result"]?.jsonObject
            ?.get("content")?.jsonArray?.get(0)?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
        assertThat(text).isNotNull()
        val types = Json.parseToJsonElement(text!!).jsonObject["types"]?.jsonObject
        assertThat(types).isNotNull()
        assertThat(types!!["int_arg"]?.jsonPrimitive?.content).isEqualTo("Long")
        assertThat(types["double_arg"]?.jsonPrimitive?.content).isEqualTo("Double")
        assertThat(types["true_arg"]?.jsonPrimitive?.content).isEqualTo("Boolean")
        assertThat(types["false_arg"]?.jsonPrimitive?.content).isEqualTo("Boolean")
        assertThat(types["string_arg"]?.jsonPrimitive?.content).isEqualTo("String")
        assertThat(types["array_arg"]?.jsonPrimitive?.content).isEqualTo("ArrayList")
        assertThat(types["object_arg"]?.jsonPrimitive?.content).isEqualTo("LinkedHashMap")
        // A JSON null argument is dropped rather than surfacing as a null-valued map entry.
        assertThat(types.containsKey("null_arg")).isFalse()
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
    fun `handleToolsCall serializes nested maps and lists as JSON not toString`() = runTest {
        val nestedRegistry = ToolRegistry()
        nestedRegistry.register(object : McpTool {
            override val name = "nested"
            override val description = "returns nested data"
            override val parameters = emptyList<ToolParameter>()
            override suspend fun execute(params: Map<String, Any>): ToolResult = ToolResult.success(mapOf(
                "items" to listOf(
                    mapOf("a" to 1, "b" to "two"),
                    mapOf("a" to 3, "b" to "four"),
                ),
                "single" to mapOf("k" to "v"),
                "count" to 2,
            ))
        })
        val nestedProtocol = McpProtocolImpl(nestedRegistry)

        val request = """{"jsonrpc":"2.0","id":30,"method":"tools/call","params":{"name":"nested","arguments":{}}}"""
        val response = nestedProtocol.handleMessage(request)
        val text = Json.parseToJsonElement(response).jsonObject["result"]
            ?.jsonObject?.get("content")?.jsonArray?.first()?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
            ?: error("missing content text")

        // text should be parseable JSON, not Kotlin toString
        assertThat(text).doesNotContain("=")
        val parsed = Json.parseToJsonElement(text).jsonObject
        val items = parsed["items"]?.jsonArray ?: error("items missing")
        assertThat(items).hasSize(2)
        assertThat(items[0].jsonObject["a"]?.jsonPrimitive?.content).isEqualTo("1")
        assertThat(items[0].jsonObject["b"]?.jsonPrimitive?.content).isEqualTo("two")
        assertThat(parsed["single"]?.jsonObject?.get("k")?.jsonPrimitive?.content).isEqualTo("v")
        assertThat(parsed["count"]?.jsonPrimitive?.content).isEqualTo("2")
    }

    @Test
    fun `handleUnknownMethod returns method not found error`() = runTest {
        val request = """{"jsonrpc":"2.0","id":5,"method":"unknown/method","params":{}}"""
        val response = protocol.handleMessage(request)
        val json = Json.parseToJsonElement(response).jsonObject
        assertThat(json["error"]).isNotNull()
    }

    @Test
    fun `tools-call records an audit entry with client label and arguments`() = runTest {
        val recorded = mutableListOf<ToolCallAudit>()
        val auditedProtocol = McpProtocolImpl(registry, auditSink = { recorded.add(it) })

        val request = """{"jsonrpc":"2.0","id":40,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}"""
        auditedProtocol.handleMessage(request, clientLabel = "laptop")

        assertThat(recorded).hasSize(1)
        val entry = recorded.single()
        assertThat(entry.toolName).isEqualTo("echo")
        assertThat(entry.clientLabel).isEqualTo("laptop")
        assertThat(entry.success).isTrue()
        assertThat(entry.argumentsJson).contains("hi")
        assertThat(entry.durationMs).isAtLeast(0L)
    }

    @Test
    fun `audit records failures with the error envelope`() = runTest {
        val recorded = mutableListOf<ToolCallAudit>()
        val auditedProtocol = McpProtocolImpl(registry, auditSink = { recorded.add(it) })

        val request = """{"jsonrpc":"2.0","id":41,"method":"tools/call","params":{"name":"missing_tool","arguments":{}}}"""
        auditedProtocol.handleMessage(request, clientLabel = "primary")

        val entry = recorded.single()
        assertThat(entry.toolName).isEqualTo("missing_tool")
        assertThat(entry.success).isFalse()
        assertThat(entry.errorMessage).contains("Unknown tool")
    }

    @Test
    fun `a throwing audit sink never fails the call`() = runTest {
        val auditedProtocol = McpProtocolImpl(registry, auditSink = { error("audit backend down") })
        val request = """{"jsonrpc":"2.0","id":42,"method":"tools/call","params":{"name":"echo","arguments":{"message":"ok"}}}"""
        val response = auditedProtocol.handleMessage(request, clientLabel = "primary")
        val result = Json.parseToJsonElement(response).jsonObject["result"]?.jsonObject
        assertThat(result!!["isError"].toString()).isEqualTo("false")
    }

    @Test
    fun `tools-list and tools-call are not recorded twice`() = runTest {
        val recorded = mutableListOf<ToolCallAudit>()
        val auditedProtocol = McpProtocolImpl(registry, auditSink = { recorded.add(it) })
        auditedProtocol.handleMessage("""{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""", null)
        assertThat(recorded).isEmpty()
    }

    @Test
    fun `tools-list hides a disabled tool at the protocol layer`() = runTest {
        registry.setToolEnabled("echo", false)
        val response = protocol.handleMessage("""{"jsonrpc":"2.0","id":50,"method":"tools/list","params":{}}""")
        val names = Json.parseToJsonElement(response).jsonObject["result"]?.jsonObject
            ?.get("tools")?.jsonArray?.map { it.jsonObject["name"]?.jsonPrimitive?.content } ?: emptyList()
        assertThat(names).doesNotContain("echo")
    }

    @Test
    fun `tools-call rejects a disabled tool at the protocol layer`() = runTest {
        registry.setToolEnabled("echo", false)
        val request = """{"jsonrpc":"2.0","id":51,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}"""
        val result = Json.parseToJsonElement(protocol.handleMessage(request)).jsonObject["result"]?.jsonObject
        assertThat(result!!["isError"].toString()).isEqualTo("true")
        assertThat(result.toString()).contains("tool_disabled")
    }

    @Test
    fun `disabled readonly tool is rejected in read-only mode`() = runTest {
        val mixedRegistry = ToolRegistry()
        mixedRegistry.register(readTool("read_thing"))
        mixedRegistry.setToolEnabled("read_thing", false)
        val readOnlyProtocol = McpProtocolImpl(mixedRegistry, readOnly = true)

        val request = """{"jsonrpc":"2.0","id":52,"method":"tools/call","params":{"name":"read_thing","arguments":{}}}"""
        val result = Json.parseToJsonElement(readOnlyProtocol.handleMessage(request)).jsonObject["result"]?.jsonObject
        assertThat(result!!["isError"].toString()).isEqualTo("true")
        assertThat(result.toString()).contains("tool_disabled")
    }
}
