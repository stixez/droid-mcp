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
import org.junit.jupiter.api.Test

/** Route-level behaviour of [HttpTransport]: auth, origin, sessions, limits. */
class HttpTransportTest {

    private val token = "test-token-0123456789"

    private val echoTool = object : McpTool {
        override val name = "echo"
        override val description = "Echoes input"
        override val parameters = listOf(ToolParameter("message", "m", ParameterType.STRING, required = true))
        override suspend fun execute(params: Map<String, Any>) = ToolResult.success(mapOf("echo" to params["message"]))
    }

    private fun transport(allowedOrigins: Set<String> = emptySet()) = HttpTransport(
        registry = ToolRegistry().apply { register(echoTool) },
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
}
