package io.droidmcp.core.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import io.droidmcp.core.AuditSink
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
import java.util.Collections
import java.util.UUID

/**
 * Ktor/Netty MCP server for desktop clients. Exposes the JSON-RPC surface at `POST /mcp`
 * (plus an `SSE` channel and a `/health` probe), enforces bearer auth via a [TokenStore]
 * when [requireAuth] is set, optionally terminates TLS, and advertises itself over mDNS
 * (`_mcp._tcp`). Prefer building one through
 * [DroidMcp.Builder.enableHttpServer][io.droidmcp.core.DroidMcp.Builder.enableHttpServer]
 * rather than directly.
 *
 * @param registry Tools to serve.
 * @param port Plaintext port; overridden by [TlsConfig.httpsPort] when [tls] is set.
 * @param bearerToken Fixed primary token; a random one is generated when null and [requireAuth] is true.
 * @param requireAuth Require a `Bearer` token on every request. When false the server is open.
 * @param readOnly Serve only read-only tools.
 * @param context Android context for mDNS registration; mDNS is skipped when null.
 * @param serverVersion Version reported in `initialize` and the mDNS TXT record.
 * @param auditSink Optional per-call audit hook.
 * @param tls TLS material; when set the server binds HTTPS and exposes [tlsFingerprint].
 */
class HttpTransport(
    private val registry: ToolRegistry,
    private val port: Int = 8080,
    bearerToken: String? = null,
    private val requireAuth: Boolean = true,
    private val readOnly: Boolean = false,
    private val context: Context? = null,
    private val serverVersion: String = DROID_MCP_VERSION,
    auditSink: AuditSink? = null,
    private val tls: TlsConfig? = null,
) {
    /**
     * Bearer-token authority. `null` when [requireAuth] is false (open server).
     * Seeded with the caller-supplied token if any, otherwise a random primary.
     */
    private val tokenStore: TokenStore? = if (requireAuth) TokenStore(bearerToken) else null

    /** Current primary token, or `null` on an open server. Tracks [rotateToken]. */
    val effectiveToken: String? get() = tokenStore?.primaryToken

    /** Whether the server terminates TLS. */
    val isTlsEnabled: Boolean get() = tls != null

    /**
     * SHA-256 fingerprint of the server certificate to pin in the pairing QR,
     * or `null` when TLS is disabled. See [TlsConfig.certFingerprintSha256].
     */
    val tlsFingerprint: String? get() = tls?.certFingerprintSha256

    /** Port the server actually binds — the HTTPS port when TLS is on. */
    private val activePort: Int get() = tls?.httpsPort ?: port

    /** mDNS service name advertised on the network, derived from the device model (sanitised, ≤63 chars). */
    val mdnsServiceName: String = run {
        val sanitized = Build.MODEL.replace(Regex("[^A-Za-z0-9-]"), "-")
        "droid-mcp-$sanitized".take(MAX_NSD_NAME_LENGTH)
    }

    @Volatile private var server: EmbeddedServer<*, *>? = null
    @Volatile private var nsdRegistration: NsdManager.RegistrationListener? = null
    private val protocol = McpProtocolImpl(registry, readOnly = readOnly, auditSink = auditSink)
    private val sseEvents = MutableSharedFlow<String>()
    private val sessions: MutableMap<String, Boolean> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Boolean>?): Boolean =
                size > MAX_SESSIONS
        }
    )

    /** Start the embedded server and register mDNS. No-op if already running; rethrows bind failures. */
    fun start() {
        if (server != null) return
        val tlsConfig = tls
        try {
            server = embeddedServer(
                factory = Netty,
                configure = {
                    if (tlsConfig == null) {
                        connector { port = this@HttpTransport.port }
                    } else {
                        sslConnector(
                            keyStore = tlsConfig.keyStore,
                            keyAlias = tlsConfig.keyAlias,
                            keyStorePassword = { tlsConfig.keyStorePassword },
                            privateKeyPassword = { tlsConfig.privateKeyPassword },
                        ) {
                            port = tlsConfig.httpsPort
                        }
                    }
                },
                module = { installMcpModule() },
            ).start(wait = false)
            registerNsd()
        } catch (e: Exception) {
            server = null
            throw e
        }
    }

    private fun Application.installMcpModule() {
        install(ContentNegotiation) { json() }
        install(SSE)
        routing {
            route("/mcp") {
                post {
                    val clientLabel = authenticate(call) ?: return@post

                    val body = call.receiveText()
                    val sessionId = call.request.header("Mcp-Session-Id")

                    val isInitialize = INITIALIZE_METHOD_REGEX.containsMatchIn(body)

                    if (!isInitialize && sessionId != null && !sessions.containsKey(sessionId)) {
                        call.respond(HttpStatusCode.NotFound, """{"error":"Unknown session"}""")
                        return@post
                    }

                    val response = protocol.handleMessage(body, clientLabel)

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
                    val store = tokenStore
                    if (store != null) {
                        val token = call.request.queryParameters["token"]
                        if (store.verify(token) == null) {
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
                    if (authenticate(call) == null) return@delete

                    val sessionId = call.request.header("Mcp-Session-Id")
                    if (sessionId != null) {
                        sessions.remove(sessionId)
                    }
                    call.respond(HttpStatusCode.OK)
                }
            }

            get("/health") {
                if (authenticate(call) == null) return@get

                call.respondText(
                    """{"status":"ok","tools":${registry.listEnabledTools().size},"readonly":$readOnly}""",
                    ContentType.Application.Json
                )
            }
        }
    }

    /** Stop the server and unregister mDNS. No-op if not running. */
    fun stop() {
        unregisterNsd()
        server?.stop(1000, 2000)
        server = null
    }

    /** Whether the embedded server is currently running. */
    fun isRunning(): Boolean = server != null

    /**
     * Rotate the primary bearer token, invalidating the old one. Paired-client
     * tokens are unaffected. Returns the new token, or `null` on an open server.
     * Surface the new value in a fresh pairing QR.
     */
    fun rotateToken(): String? = tokenStore?.rotatePrimary()

    /**
     * Mint a revocable token for a named client. Re-pairing an existing label
     * replaces its token. Returns the new token, or `null` on an open server.
     */
    fun pairClient(label: String): String? = tokenStore?.pair(label)

    /** Revoke a paired client's token. @return true if a client with that label existed. */
    fun revokeClient(label: String): Boolean = tokenStore?.revoke(label) ?: false

    /** Snapshot of paired clients (excludes the primary). Empty on an open server. */
    fun pairedClients(): List<TokenStore.PairedClient> = tokenStore?.pairedClients() ?: emptyList()

    /**
     * Authenticate a request against the token store.
     * @return the owning client label ([TokenStore.PRIMARY_LABEL] for the primary
     *   token, [ANONYMOUS_LABEL] on an open server), or `null` if the request was
     *   rejected — in which case a 401 has already been written.
     */
    private suspend fun authenticate(call: ApplicationCall): String? {
        val store = tokenStore ?: return ANONYMOUS_LABEL
        val token = call.request.header("Authorization")?.removePrefix("Bearer ")
        val label = store.verify(token)
        if (label == null) {
            call.response.header("WWW-Authenticate", "Bearer realm=\"droid-mcp\"")
            call.respond(HttpStatusCode.Unauthorized, """{"error":"Invalid or missing token"}""")
        }
        return label
    }

    companion object {
        private const val MAX_SESSIONS = 1024
        private const val MAX_NSD_NAME_LENGTH = 63
        /** Label attributed to requests on an open (no-auth) server. */
        const val ANONYMOUS_LABEL: String = "anonymous"
        private val INITIALIZE_METHOD_REGEX = Regex("\"method\"\\s*:\\s*\"initialize\"")
    }

    private fun registerNsd() {
        val ctx = context ?: return
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = mdnsServiceName
            serviceType = "_mcp._tcp."
            port = activePort
            setAttribute("version", serverVersion)
            setAttribute("auth", if (requireAuth) "bearer" else "none")
            setAttribute("readonly", readOnly.toString())
            setAttribute("tls", isTlsEnabled.toString())
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
