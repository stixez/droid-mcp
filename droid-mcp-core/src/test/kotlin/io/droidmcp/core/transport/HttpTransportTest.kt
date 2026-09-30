package io.droidmcp.core.transport

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.droidmcp.core.reportProgress
import io.droidmcp.core.elicit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Route-level behaviour of [HttpTransport]: auth, origin, sessions, limits. */
class HttpTransportTest {

    private val token = "test-token-0123456789"

    private val echoTool = object : McpTool {
        override val name = "echo"
        override val description = "Echoes input"
        override val parameters = listOf(ToolParameter("message", "m", ParameterType.STRING, required = true))
        override suspend fun execute(params: Map<String, Any>) = ToolResult.success(mapOf("echo" to params["message"]))
    }

    private val progressTool = object : McpTool {
        override val name = "slow"
        override val description = "reports progress"
        override val parameters = emptyList<ToolParameter>()
        override suspend fun execute(params: Map<String, Any>): ToolResult {
            reportProgress(1.0, 2.0, "half")
            reportProgress(2.0, 2.0)
            return ToolResult.success(mapOf("done" to true))
        }
    }

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

    private fun transport(allowedOrigins: Set<String> = emptySet()) = HttpTransport(
        registry = ToolRegistry().apply { register(echoTool); register(progressTool); register(askTool) },
        bearerToken = token,
        allowedOrigins = allowedOrigins,
    )

    private fun mcpTest(
        transport: HttpTransport = transport(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { with(transport) { installMcpModule() } }
        block()
    }

    private suspend fun HttpClient.rpc(
        body: String,
        session: String? = null,
        auth: String? = "Bearer $token",
        configure: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse = post("/mcp") {
        auth?.let { header(HttpHeaders.Authorization, it) }
        session?.let { header("Mcp-Session-Id", it) }
        contentType(ContentType.Application.Json)
        setBody(body)
        configure()
    }

    private suspend fun HttpClient.initialize(): String {
        val response = rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18"}}""")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        return response.headers["Mcp-Session-Id"]!!
    }

    @Test
    fun `missing token is 401 with WWW-Authenticate`() = mcpTest {
        val response = client.rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""", auth = null)
        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(response.headers[HttpHeaders.WWWAuthenticate]).contains("Bearer")
    }

    @Test
    fun `bearer scheme is case-insensitive but required`() = mcpTest {
        val lower = client.rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""", auth = "bearer $token")
        assertThat(lower.status).isEqualTo(HttpStatusCode.OK)
        val bare = client.rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""", auth = token)
        assertThat(bare.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `foreign origin is rejected before auth`() = mcpTest {
        val response = client.rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""") {
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    fun `allowlisted origin is accepted`() = mcpTest(transport(setOf("http://localhost:6274"))) {
        val response = client.rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""") {
            header(HttpHeaders.Origin, "http://localhost:6274")
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `text-plain body is 415`() = mcpTest {
        val response = client.post("/mcp") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Text.Plain)
            setBody("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""")
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.UnsupportedMediaType)
    }

    @Test
    fun `oversized body is 413`() = mcpTest {
        val huge = "x".repeat(HttpTransport.MAX_BODY_BYTES + 10)
        val response = client.rpc(huge)
        assertThat(response.status).isEqualTo(HttpStatusCode.PayloadTooLarge)
    }

    @Test
    fun `unsupported protocol version header is 400`() = mcpTest {
        val response = client.rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize"}""") {
            header("MCP-Protocol-Version", "1999-01-01")
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `DELETE with an unsupported protocol version header is 400`() = mcpTest {
        val response = client.delete("/mcp") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("Mcp-Session-Id", "whatever")
            header("MCP-Protocol-Version", "1999-01-01")
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `requests after initialize require a session`() = mcpTest {
        val response = client.rpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `unknown session is 404`() = mcpTest {
        val response = client.rpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", session = "nope")
        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `a tool call smuggling an initialize string does not mint a session`() = mcpTest {
        val response = client.rpc(
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"echo","arguments":{"method":"initialize"}}}"""
        )
        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.headers["Mcp-Session-Id"]).isNull()
    }

    @Test
    fun `full session round trip`() = mcpTest {
        val session = client.initialize()
        val notified = client.rpc("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", session = session)
        assertThat(notified.status).isEqualTo(HttpStatusCode.Accepted)

        val called = client.rpc(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hi"}}}""",
            session = session,
        )
        assertThat(called.status).isEqualTo(HttpStatusCode.OK)
        assertThat(called.bodyAsText()).contains("hi")

        val deleted = client.delete("/mcp") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("Mcp-Session-Id", session)
        }
        assertThat(deleted.status).isEqualTo(HttpStatusCode.OK)
        val after = client.rpc("""{"jsonrpc":"2.0","id":4,"method":"tools/list"}""", session = session)
        assertThat(after.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `a session cannot be used by another client`() {
        val t = transport()
        val laptopToken = t.pairClient("laptop")!!
        mcpTest(t) {
            val session = client.initialize()
            val response = client.rpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", session = session, auth = "Bearer $laptopToken")
            assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `GET mcp is 405`() = mcpTest {
        val response = client.get("/mcp") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertThat(response.status).isEqualTo(HttpStatusCode.MethodNotAllowed)
    }

    @Test
    fun `constructor rejects a short bearer token`() {
        assertThrows<IllegalArgumentException> {
            HttpTransport(registry = ToolRegistry(), bearerToken = "short")
        }
    }

    private val sseAccept: io.ktor.client.request.HttpRequestBuilder.() -> Unit =
        { header(HttpHeaders.Accept, "application/json, text/event-stream") }

    @Test
    fun `progress upgrades a tools-call reply to an SSE stream ending with the result`() = mcpTest {
        val session = client.initialize()
        val response = client.rpc(
            """{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"slow","_meta":{"progressToken":"p1"}}}""",
            session = session,
            configure = sseAccept,
        )
        assertThat(response.contentType()?.withoutParameters()).isEqualTo(ContentType.Text.EventStream)
        val events = response.bodyAsText().lines().filter { it.startsWith("data: ") }.map { it.removePrefix("data: ") }
        assertThat(events).hasSize(3)
        assertThat(events[0]).contains("notifications/progress")
        assertThat(events[0]).contains("\"progressToken\":\"p1\"")
        assertThat(events[0]).contains("\"message\":\"half\"")
        assertThat(events[2]).contains("\"id\":7")
        assertThat(events[2]).contains("done")
    }

    @Test
    fun `without SSE in Accept or without a progress token the reply stays plain JSON`() = mcpTest {
        val session = client.initialize()
        val noAccept = client.rpc(
            """{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"slow","_meta":{"progressToken":"p1"}}}""",
            session = session,
        )
        assertThat(noAccept.contentType()?.withoutParameters()).isEqualTo(ContentType.Application.Json)
        assertThat(noAccept.bodyAsText()).doesNotContain("notifications/progress")

        val noToken = client.rpc(
            """{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"slow"}}""",
            session = session,
            configure = sseAccept,
        )
        assertThat(noToken.contentType()?.withoutParameters()).isEqualTo(ContentType.Application.Json)
    }

    @Test
    fun `elicit returns null for a client without the capability`() = mcpTest {
        val session = client.initialize()
        val response = client.rpc(
            """{"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"ask"}}""",
            session = session,
            configure = sseAccept,
        )
        assertThat(response.bodyAsText()).contains("unsupported")
    }
}
