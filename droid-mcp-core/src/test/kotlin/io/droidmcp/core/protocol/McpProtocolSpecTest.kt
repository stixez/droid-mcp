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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/** JSON-RPC 2.0 / MCP spec-compliance cases for [McpProtocolImpl]. */
class McpProtocolSpecTest {

    private val echoTool = object : McpTool {
        override val name = "echo"
        override val description = "Echoes input"
        override val parameters = listOf(
            ToolParameter("message", "The message", ParameterType.STRING, required = true),
            ToolParameter("tags", "Tags", ParameterType.ARRAY, itemsType = ParameterType.STRING),
        )
        override suspend fun execute(params: Map<String, Any>): ToolResult =
            ToolResult.success(mapOf("echo" to params["message"]))
    }

    private val writeTool = object : McpTool {
        override val name = "write_thing"
        override val description = "mutates"
        override val parameters = emptyList<ToolParameter>()
        override suspend fun execute(params: Map<String, Any>): ToolResult = ToolResult.success(mapOf())
    }

    private val registry = ToolRegistry().apply {
        register(echoTool)
        register(writeTool)
    }
    private val protocol = McpProtocolImpl(registry)

    private suspend fun call(raw: String): JsonObject =
        Json.parseToJsonElement(protocol.handleMessage(raw)).jsonObject

    private fun JsonObject.errorCode(): Int? = this["error"]?.jsonObject?.get("code")?.jsonPrimitive?.int

    @Test
    fun `initialize echoes a supported client protocol version`() = runTest {
        val r = call("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""")
        assertThat(r["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content).isEqualTo("2025-06-18")
    }

    @Test
    fun `initialize offers the newest version for an unknown client version`() = runTest {
        val r = call("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"1999-01-01"}}""")
        assertThat(r["result"]!!.jsonObject["protocolVersion"]!!.jsonPrimitive.content)
            .isEqualTo(McpProtocolImpl.SUPPORTED_PROTOCOL_VERSIONS.first())
    }

    @Test
    fun `unknown notifications get no response`() = runTest {
        assertThat(protocol.handleMessage("""{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":3}}""")).isEmpty()
        assertThat(protocol.handleMessage("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")).isEmpty()
    }

    @Test
    fun `a tools-call without id is treated as a notification and not executed`() = runTest {
        val recorded = mutableListOf<ToolCallAudit>()
        val audited = McpProtocolImpl(registry, auditSink = { recorded.add(it) })
        val response = audited.handleMessage("""{"jsonrpc":"2.0","method":"tools/call","params":{"name":"echo","arguments":{"message":"x"}}}""")
        assertThat(response).isEmpty()
        assertThat(recorded).isEmpty()
    }

    @Test
    fun `client responses are ignored`() = runTest {
        assertThat(protocol.handleMessage("""{"jsonrpc":"2.0","id":9,"result":{}}""")).isEmpty()
    }

    @Test
    fun `parse error has code -32700 and null id`() = runTest {
        val r = call("""{not json""")
        assertThat(r.errorCode()).isEqualTo(-32700)
        assertThat(r["id"]).isEqualTo(JsonNull)
    }

    @Test
    fun `batch arrays are rejected as invalid requests`() = runTest {
        val r = call("""[{"jsonrpc":"2.0","id":1,"method":"ping"}]""")
        assertThat(r.errorCode()).isEqualTo(-32600)
    }

    @Test
    fun `non-object json is an invalid request`() = runTest {
        assertThat(call("42").errorCode()).isEqualTo(-32600)
    }

    @Test
    fun `wrong jsonrpc version is an invalid request keeping the id`() = runTest {
        val r = call("""{"jsonrpc":"1.0","id":7,"method":"ping"}""")
        assertThat(r.errorCode()).isEqualTo(-32600)
        assertThat(r["id"]!!.jsonPrimitive.content).isEqualTo("7")
    }

    @Test
    fun `non-object params is -32602 keeping the id`() = runTest {
        val r = call("""{"jsonrpc":"2.0","id":"abc","method":"tools/call","params":[]}""")
        assertThat(r.errorCode()).isEqualTo(-32602)
        assertThat(r["id"]!!.jsonPrimitive.content).isEqualTo("abc")
    }

    @Test
    fun `non-object arguments is -32602`() = runTest {
        val r = call("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":"x"}}""")
        assertThat(r.errorCode()).isEqualTo(-32602)
    }

    @Test
    fun `non-string tool name is -32602`() = runTest {
        val r = call("""{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":{}}}""")
        assertThat(r.errorCode()).isEqualTo(-32602)
    }

    @Test
    fun `ping returns an empty result`() = runTest {
        val r = call("""{"jsonrpc":"2.0","id":5,"method":"ping"}""")
        assertThat(r["result"]!!.jsonObject).isEmpty()
    }

    @Test
    fun `successful tool call carries structuredContent`() = runTest {
        val r = call("""{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}""")
        val structured = r["result"]!!.jsonObject["structuredContent"]!!.jsonObject
        assertThat(structured["echo"]!!.jsonPrimitive.content).isEqualTo("hi")
    }

    @Test
    fun `array parameters advertise an items schema`() = runTest {
        val r = call("""{"jsonrpc":"2.0","id":8,"method":"tools/list"}""")
        val echo = r["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content == "echo" }
        val tags = echo["inputSchema"]!!.jsonObject["properties"]!!.jsonObject["tags"]!!.jsonObject
        assertThat(tags["items"]!!.jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("string")
    }

    @Test
    fun `read-only rejections are audited`() = runTest {
        val recorded = mutableListOf<ToolCallAudit>()
        val readOnly = McpProtocolImpl(registry, readOnly = true, auditSink = { recorded.add(it) })
        readOnly.handleMessage("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"write_thing"}}""", "laptop")
        assertThat(recorded.single().success).isFalse()
        assertThat(recorded.single().clientLabel).isEqualTo("laptop")
    }

    @Test
    fun `annotation title is surfaced as the top-level tool title`() = runTest {
        val titled = object : McpTool {
            override val name = "titled"
            override val description = "d"
            override val parameters = emptyList<ToolParameter>()
            override val annotations = ToolAnnotations(title = "Nice Title")
            override suspend fun execute(params: Map<String, Any>) = ToolResult.success(mapOf())
        }
        registry.register(titled)
        val r = call("""{"jsonrpc":"2.0","id":8,"method":"tools/list"}""")
        val tool = r["result"]!!.jsonObject["tools"]!!.jsonArray.map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content == "titled" }
        assertThat(tool["title"]!!.jsonPrimitive.content).isEqualTo("Nice Title")
    }
}
