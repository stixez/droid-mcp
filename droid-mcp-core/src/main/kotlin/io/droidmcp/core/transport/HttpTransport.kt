package io.droidmcp.core.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.droidmcp.core.DROID_MCP_VERSION
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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections
import java.util.UUID

class HttpTransport(
    private val registry: ToolRegistry,
    private val port: Int = 8080,
    bearerToken: String? = null,
    private val requireAuth: Boolean = true,
    private val readOnly: Boolean = false,
    private val context: Context? = null,
    private val serverVersion: String = DROID_MCP_VERSION,
) {
    val effectiveToken: String? = when {
        !requireAuth -> null
        bearerToken != null -> bearerToken
        else -> generateToken()
    }

    private val effectiveTokenBytes: ByteArray? = effectiveToken?.toByteArray(Charsets.UTF_8)

    val mdnsServiceName: String = run {
        val sanitized = Build.MODEL.replace(Regex("[^A-Za-z0-9-]"), "-")
        "droid-mcp-$sanitized".take(MAX_NSD_NAME_LENGTH)
    }

    @Volatile private var server: EmbeddedServer<*, *>? = null
    @Volatile private var nsdRegistration: NsdManager.RegistrationListener? = null
    private val protocol = McpProtocolImpl(registry, readOnly = readOnly)
    private val sseEvents = MutableSharedFlow<String>()
    private val sessions: MutableMap<String, Boolean> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>?): Boolean =
                size > MAX_SESSIONS
        }
    )

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

                            val isInitialize = INITIALIZE_METHOD_REGEX.containsMatchIn(body)

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
                            if (effectiveTokenBytes != null) {
                                val token = call.request.queryParameters["token"]
                                if (!tokensMatch(token)) {
                                    call.response.header("WWW-Authenticate", "Bearer realm=\"droid-mcp\"")
                                    call.respond(HttpStatusCode.Unauthorized)
                                    return@sse
                                }
                            }
                            sseEvents.collect { event ->
                                send(event)
                            }
                        }

                        delete {
                            if (!authenticate(call)) return@delete

                            val sessionId = call.request.header("Mcp-Session-Id")
                            if (sessionId != null) {
                                sessions.remove(sessionId)
                            }
                            call.respond(HttpStatusCode.OK)
                        }
                    }

                    get("/health") {
                        if (!authenticate(call)) return@get

                        call.respondText(
                            """{"status":"ok","tools":${registry.listTools().size},"readonly":$readOnly}""",
                            ContentType.Application.Json
                        )
                    }
                }
            }.start(wait = false)
            registerNsd()
        } catch (e: Exception) {
            server = null
            throw e
        }
    }

    fun stop() {
        unregisterNsd()
        server?.stop(1000, 2000)
        server = null
    }

    fun isRunning(): Boolean = server != null

    private suspend fun authenticate(call: ApplicationCall): Boolean {
        if (effectiveTokenBytes == null) return true
        val token = call.request.header("Authorization")?.removePrefix("Bearer ")
        if (!tokensMatch(token)) {
            call.response.header("WWW-Authenticate", "Bearer realm=\"droid-mcp\"")
            call.respond(HttpStatusCode.Unauthorized, """{"error":"Invalid or missing token"}""")
            return false
        }
        return true
    }

    private fun tokensMatch(provided: String?): Boolean {
        val expected = effectiveTokenBytes ?: return true
        val providedBytes = provided?.toByteArray(Charsets.UTF_8) ?: return false
        return MessageDigest.isEqual(expected, providedBytes)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private const val MAX_SESSIONS = 1024
        private const val MAX_NSD_NAME_LENGTH = 63
        private val INITIALIZE_METHOD_REGEX = Regex("\"method\"\\s*:\\s*\"initialize\"")
    }

    private fun registerNsd() {
        val ctx = context ?: return
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = mdnsServiceName
            serviceType = "_mcp._tcp."
            port = this@HttpTransport.port
            setAttribute("version", serverVersion)
            setAttribute("auth", if (requireAuth) "bearer" else "none")
            setAttribute("readonly", readOnly.toString())
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                nsdRegistration = null
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                nsdRegistration = null
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                nsdRegistration = null
            }
        }
        nsdRegistration = listener
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {
            nsdRegistration = null
        }
    }

    private fun unregisterNsd() {
        val ctx = context ?: return
        val listener = nsdRegistration ?: return
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        try {
            nsd.unregisterService(listener)
        } catch (_: Exception) {
        }
        nsdRegistration = null
    }
}
