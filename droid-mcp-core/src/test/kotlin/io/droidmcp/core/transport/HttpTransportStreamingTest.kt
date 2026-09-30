package io.droidmcp.core.transport

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import io.droidmcp.core.elicit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

/**
 * End-to-end streaming over a real Netty server. Ktor's test engine buffers a streamed body until
 * it completes, so a round trip that has to happen *mid*-stream (elicitation) can't be tested there.
 */
class HttpTransportStreamingTest {

    private val token = "test-token-0123456789"
    private val port = ServerSocket(0).use { it.localPort }

    private val askTool = object : McpTool {
        override val name = "ask"
        override val description = "asks the user"
        override val parameters = emptyList<ToolParameter>()
        override suspend fun execute(params: Map<String, Any>): ToolResult {
            val answer = elicit(
                "Pick a colour",
                mapOf("type" to "object", "properties" to mapOf("colour" to mapOf("type" to "string"))),
            ) ?: return ToolResult.success(mapOf("action" to "unsupported"))
            return ToolResult.success(mapOf("action" to answer.action.name, "colour" to answer.content["colour"]))
        }
    }

    private val transport = HttpTransport(
        registry = ToolRegistry().apply { register(askTool) },
        port = port,
        bearerToken = token,
    ).also { it.start() }

    private val http = HttpClient.newHttpClient()

    @AfterEach
    fun tearDown() = transport.stop()

    private fun post(body: String, session: String? = null, sse: Boolean = false): HttpRequest =
        HttpRequest.newBuilder(URI("http://127.0.0.1:$port/mcp"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .apply { if (sse) header("Accept", "application/json, text/event-stream") }
            .apply { session?.let { header("Mcp-Session-Id", it) } }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

    @Test
    @Timeout(20, unit = TimeUnit.SECONDS)
    fun `elicitation round trip over the call's SSE stream`() {
        val init = http.send(
            post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{"elicitation":{}}}}"""),
            HttpResponse.BodyHandlers.ofString(),
        )
        val session = init.headers().firstValue("Mcp-Session-Id").get()

        val call = http.send(
            post("""{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"ask"}}""", session, sse = true),
            HttpResponse.BodyHandlers.ofLines(),
        )
        assertThat(call.headers().firstValue("Content-Type").get()).startsWith("text/event-stream")
        val data = call.body().iterator().asSequence().filter { it.startsWith("data: ") }.map { it.removePrefix("data: ") }.iterator()

        val request = Json.parseToJsonElement(data.next()).jsonObject
        assertThat(request["method"]!!.jsonPrimitive.content).isEqualTo("elicitation/create")
        val requestId = request["id"]!!.jsonPrimitive.content

        val answered = http.send(
            post("""{"jsonrpc":"2.0","id":"$requestId","result":{"action":"accept","content":{"colour":"teal"}}}""", session),
            HttpResponse.BodyHandlers.discarding(),
        )
        assertThat(answered.statusCode()).isEqualTo(202)

        val result = data.next()
        assertThat(result).contains("\"id\":10")
        assertThat(result).contains("ACCEPT")
        assertThat(result).contains("teal")
    }

    @Test
    @Timeout(20, unit = TimeUnit.SECONDS)
    fun `another client's session can't answer the elicitation`() {
        val initA = http.send(
            post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{"elicitation":{}}}}"""),
            HttpResponse.BodyHandlers.ofString(),
        )
        val sessionA = initA.headers().firstValue("Mcp-Session-Id").get()
        val otherToken = transport.pairClient("intruder")
        val call = http.send(
            post("""{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"ask"}}""", sessionA, sse = true),
            HttpResponse.BodyHandlers.ofLines(),
        )
        val data = call.body().iterator().asSequence().filter { it.startsWith("data: ") }.map { it.removePrefix("data: ") }.iterator()
        val requestId = Json.parseToJsonElement(data.next()).jsonObject["id"]!!.jsonPrimitive.content

        // The intruder opens its own session and posts an answer to A's request id.
        val initB = http.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/mcp"))
                .header("Authorization", "Bearer $otherToken").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val sessionB = initB.headers().firstValue("Mcp-Session-Id").get()
        http.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/mcp"))
                .header("Authorization", "Bearer $otherToken").header("Content-Type", "application/json")
                .header("Mcp-Session-Id", sessionB)
                .POST(HttpRequest.BodyPublishers.ofString("""{"jsonrpc":"2.0","id":"$requestId","result":{"action":"accept","content":{"colour":"hacked"}}}""")).build(),
            HttpResponse.BodyHandlers.discarding(),
        )
        // The real client then declines; the tool must see A's answer, not the intruder's.
        http.send(
            post("""{"jsonrpc":"2.0","id":"$requestId","result":{"action":"decline"}}""", sessionA),
            HttpResponse.BodyHandlers.discarding(),
        )
        val result = data.next()
        assertThat(result).contains("DECLINE")
        assertThat(result).doesNotContain("hacked")
    }

    @Test
    @Timeout(20, unit = TimeUnit.SECONDS)
    fun `a second session on the same token can't answer the elicitation`() {
        val initBody = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{"elicitation":{}}}}"""
        val sessionA = http.send(post(initBody), HttpResponse.BodyHandlers.ofString()).headers().firstValue("Mcp-Session-Id").get()
        val sessionB = http.send(post(initBody), HttpResponse.BodyHandlers.ofString()).headers().firstValue("Mcp-Session-Id").get()
        assertThat(sessionB).isNotEqualTo(sessionA)

        val call = http.send(
            post("""{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"ask"}}""", sessionA, sse = true),
            HttpResponse.BodyHandlers.ofLines(),
        )
        val data = call.body().iterator().asSequence().filter { it.startsWith("data: ") }.map { it.removePrefix("data: ") }.iterator()
        val requestId = Json.parseToJsonElement(data.next()).jsonObject["id"]!!.jsonPrimitive.content
        assertThat(requestId).doesNotMatch("droidmcp-[0-9]+")

        // Same token (label "primary"), different session: must be ignored.
        http.send(
            post("""{"jsonrpc":"2.0","id":"$requestId","result":{"action":"accept","content":{"colour":"hacked"}}}""", sessionB),
            HttpResponse.BodyHandlers.discarding(),
        )
        http.send(
            post("""{"jsonrpc":"2.0","id":"$requestId","result":{"action":"decline"}}""", sessionA),
            HttpResponse.BodyHandlers.discarding(),
        )
        val result = data.next()
        assertThat(result).contains("DECLINE")
        assertThat(result).doesNotContain("hacked")
    }
}
