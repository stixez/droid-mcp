package io.droidmcp.core.transport

import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.protocol.McpProtocolImpl
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HttpTransport(
    private val registry: ToolRegistry,
    private val port: Int = 8080,
    private val authToken: String? = null,
) {
    @Volatile private var server: EmbeddedServer<*, *>? = null
    private val protocol = McpProtocolImpl(registry)
    private val sseEvents = MutableSharedFlow<String>()
    private val sessions = ConcurrentHashMap<String, Boolean>()

    fun start() {
        if (server != null) return
        try {
            server = embeddedServer(Netty, port = port) {
                install(ContentNegotiation) { json() }
                install(SSE)
                routing {
                    route("/mcp") {
                        post {
                            if (!authenticate(call)) return@post

                            val body = call.receiveText()
                            val sessionId = call.request.header("Mcp-Session-Id")

                            // Check if this is an initialize request
                            val isInitialize = body.contains("\"method\"") &&
                                body.contains("\"initialize\"")

                            if (!isInitialize && sessionId != null && !sessions.containsKey(sessionId)) {
                                call.respond(HttpStatusCode.NotFound, """{"error":"Unknown session"}""")
                                return@post
                            }

                            val response = protocol.handleMessage(body)

                            if (isInitialize && response.isNotEmpty()) {
                                val newSessionId = UUID.randomUUID().toString()
                                sessions[newSessionId] = true
                                call.response.header("Mcp-Session-Id", newSessionId)
                            }

                            if (response.isNotEmpty()) {
                                call.respondText(response, ContentType.Application.Json)
                            } else {
                                call.respond(HttpStatusCode.Accepted)
                            }
                        }

                        sse {
                            if (authToken != null) {
                                val token = call.request.queryParameters["token"]
                                if (token != authToken) {
                                    call.respond(HttpStatusCode.Unauthorized)
                                    return@sse
                                }
                            }
                            sseEvents.collect { event ->
                                send(event)
                            }
                        }

                        delete {
                            val sessionId = call.request.header("Mcp-Session-Id")
                            if (sessionId != null) {
                                sessions.remove(sessionId)
                            }
                            call.respond(HttpStatusCode.OK)
                        }
                    }

                    get("/health") {
                        call.respondText(
                            """{"status":"ok","tools":${registry.listTools().size}}""",
                            ContentType.Application.Json
                        )
                    }
                }
            }.start(wait = false)
        } catch (e: Exception) {
            server = null
            throw e
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    fun isRunning(): Boolean = server != null

    private suspend fun authenticate(call: ApplicationCall): Boolean {
        if (authToken == null) return true
        val token = call.request.header("Authorization")?.removePrefix("Bearer ")
        if (token != authToken) {
            call.respond(HttpStatusCode.Unauthorized, """{"error":"Invalid token"}""")
            return false
        }
        return true
    }
}
