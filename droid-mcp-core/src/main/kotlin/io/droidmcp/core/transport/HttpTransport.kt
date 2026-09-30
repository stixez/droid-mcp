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
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Collections
import java.util.UUID

/**
 * Ktor/Netty MCP server for desktop clients, speaking the Streamable HTTP transport. Exposes the
 * JSON-RPC surface at `POST /mcp` (plus `DELETE /mcp` to end a session and a `/health` probe),
 * enforces bearer auth via a [TokenStore] when [requireAuth] is set, optionally terminates TLS,
 * and advertises itself over mDNS (`_mcp._tcp`). Prefer building one through
 * [DroidMcp.Builder.enableHttpServer][io.droidmcp.core.DroidMcp.Builder.enableHttpServer]
 * rather than directly.
 *
 * Request hardening, applied before any tool runs:
 * - an `Origin` header outside [allowedOrigins] is rejected with 403 (DNS-rebinding / drive-by
 *   browser protection — native MCP clients send no `Origin`);
 * - `POST` bodies must be `application/json` (415 otherwise) and at most [MAX_BODY_BYTES] (413);
 * - an `MCP-Protocol-Version` header outside
 *   [McpProtocolImpl.SUPPORTED_PROTOCOL_VERSIONS] is rejected with 400;
 * - every request after `initialize` must carry the `Mcp-Session-Id` issued to the same client
 *   (400 when missing, 404 when unknown or owned by another client).
 *
 * The server never pushes server-initiated messages, so `GET /mcp` answers 405 as the spec allows.
 *
 * @param registry Tools to serve.
 * @param port Plaintext port; overridden by [TlsConfig.httpsPort] when [tls] is set.
 * @param bearerToken Fixed primary token (at least [MIN_TOKEN_LENGTH] characters); a random one is
 *   generated when null and [requireAuth] is true.
 * @throws IllegalArgumentException if [bearerToken] is shorter than [MIN_TOKEN_LENGTH].
 * @param requireAuth Require a `Bearer` token on every request. When false the server is open.
 * @param readOnly Serve only read-only tools.
 * @param context Android context for mDNS registration; mDNS is skipped when null.
 * @param serverVersion Version reported in `initialize` and the mDNS TXT record.
 * @param auditSink Optional per-call audit hook.
 * @param tls TLS material; when set the server binds HTTPS and exposes [tlsFingerprint].
 * @param allowedOrigins Exact `Origin` values (scheme://host[:port]) permitted to call the server.
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
    private val allowedOrigins: Set<String> = emptySet(),
) {
    init {
        require(bearerToken == null || bearerToken.length >= MIN_TOKEN_LENGTH) {
            "bearerToken must be at least $MIN_TOKEN_LENGTH characters"
        }
    }

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
        // Build.MODEL is null in JVM unit tests (android.jar stubs) and theoretically on odd ROMs.
        val sanitized = (Build.MODEL ?: "device").replace(Regex("[^A-Za-z0-9-]"), "-")
        "droid-mcp-$sanitized".take(MAX_NSD_NAME_LENGTH)
    }

    @Volatile private var server: EmbeddedServer<*, *>? = null
    @Volatile private var nsdRegistration: NsdManager.RegistrationListener? = null
    private val protocol = McpProtocolImpl(registry, readOnly = readOnly, auditSink = auditSink)

    /** Session id -> label of the client that created it (LRU-bounded). */
    private val sessions: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, String>?): Boolean =
                size > MAX_SESSIONS
        }
    )

    /**
     * Start the embedded server and register mDNS. No-op if already running; rethrows bind
     * failures. Synchronized with [stop] so concurrent start/stop can't orphan a bound server.
     */
    @Synchronized
    fun start() {
        if (server != null) return
        val tlsConfig = tls
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
    }

    /** Installs the `/mcp` and `/health` routes; `internal` so tests can mount it in `testApplication`. */
    internal fun Application.installMcpModule() {
        install(ContentNegotiation) { json() }
        routing {
            route("/mcp") {
                post {
                    if (!checkOrigin(call) || !checkProtocolVersion(call)) return@post
                    val clientLabel = authenticate(call) ?: return@post

                    if (call.request.contentType().withoutParameters() != ContentType.Application.Json) {
                        call.respond(HttpStatusCode.UnsupportedMediaType, """{"error":"Content-Type must be application/json"}""")
                        return@post
                    }
                    val body = readBoundedBody(call) ?: return@post

                    val method = runCatching {
                        (Json.parseToJsonElement(body) as? JsonObject)?.get("method")?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    val isInitialize = method == "initialize"
                    val sessionId = call.request.header(SESSION_HEADER)

                    if (!isInitialize) {
                        if (sessionId == null) {
                            call.respond(HttpStatusCode.BadRequest, """{"error":"Missing Mcp-Session-Id header"}""")
                            return@post
                        }
                        if (sessions[sessionId] != clientLabel) {
                            call.respond(HttpStatusCode.NotFound, """{"error":"Unknown session"}""")
                            return@post
                        }
                    }

                    val response = protocol.handleMessage(body, clientLabel)

                    if (isInitialize && response.isNotEmpty() && isSuccessResponse(response)) {
                        val newSessionId = UUID.randomUUID().toString()
                        sessions[newSessionId] = clientLabel
                        call.response.header(SESSION_HEADER, newSessionId)
                    }

                    if (response.isNotEmpty()) {
                        call.respondText(response, ContentType.Application.Json)
                    } else {
                        call.respond(HttpStatusCode.Accepted)
                    }
                }

                // No server-initiated messages are ever sent, so there is no SSE stream to open.
                get {
                    if (!checkOrigin(call)) return@get
                    call.response.header(HttpHeaders.Allow, "POST, DELETE")
                    call.respond(HttpStatusCode.MethodNotAllowed)
                }

                delete {
                    if (!checkOrigin(call) || !checkProtocolVersion(call)) return@delete
                    val clientLabel = authenticate(call) ?: return@delete

                    val sessionId = call.request.header(SESSION_HEADER)
                    val removed = sessionId != null && synchronized(sessions) {
                        if (sessions[sessionId] == clientLabel) sessions.remove(sessionId) != null else false
                    }
                    call.respond(if (removed) HttpStatusCode.OK else HttpStatusCode.NotFound)
                }
            }

            get("/health") {
                if (!checkOrigin(call)) return@get
                if (authenticate(call) == null) return@get

                call.respondText(
                    """{"status":"ok","tools":${registry.listEnabledTools().size},"readonly":$readOnly}""",
                    ContentType.Application.Json
                )
            }
        }
    }

    /** 403 unless the request has no `Origin` or one listed in [allowedOrigins]. */
    private suspend fun checkOrigin(call: ApplicationCall): Boolean {
        val origin = call.request.header(HttpHeaders.Origin) ?: return true
        if (origin in allowedOrigins) return true
        call.respond(HttpStatusCode.Forbidden, """{"error":"Origin not allowed"}""")
        return false
    }

    /** 400 when an `MCP-Protocol-Version` header names a revision this server doesn't speak. */
    private suspend fun checkProtocolVersion(call: ApplicationCall): Boolean {
        val version = call.request.header(PROTOCOL_VERSION_HEADER) ?: return true
        if (version in McpProtocolImpl.SUPPORTED_PROTOCOL_VERSIONS) return true
        call.respond(HttpStatusCode.BadRequest, """{"error":"Unsupported MCP-Protocol-Version"}""")
        return false
    }

    /** Reads the body up to [MAX_BODY_BYTES]; responds 413 and returns null when it is larger. */
    private suspend fun readBoundedBody(call: ApplicationCall): String? {
        val declared = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
        if (declared != null && declared > MAX_BODY_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            return null
        }
        val bytes = call.receiveChannel().readBuffer(MAX_BODY_BYTES + 1L).readByteArray()
        if (bytes.size > MAX_BODY_BYTES) {
            call.respond(HttpStatusCode.PayloadTooLarge)
            return null
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun isSuccessResponse(response: String): Boolean = runCatching {
        (Json.parseToJsonElement(response) as? JsonObject)?.containsKey("result") == true
    }.getOrDefault(false)

    /** Stop the server and unregister mDNS. No-op if not running. */
    @Synchronized
    fun stop() {
        unregisterNsd()
        server?.stop(1000, 2000)
        server = null
        sessions.clear()
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
        // RFC 7235: the auth scheme is case-insensitive, but it must be present.
        val token = call.request.header(HttpHeaders.Authorization)
            ?.takeIf { it.length > BEARER_PREFIX.length && it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)
            ?.trim()
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
        private const val BEARER_PREFIX = "Bearer "
        private const val SESSION_HEADER = "Mcp-Session-Id"
        private const val PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version"
        /** Largest accepted `POST /mcp` body, in bytes. */
        const val MAX_BODY_BYTES: Int = 4 * 1024 * 1024
        /** Minimum length for a caller-supplied bearer token. */
        const val MIN_TOKEN_LENGTH: Int = 16
        /** Label attributed to requests on an open (no-auth) server. */
        const val ANONYMOUS_LABEL: String = "anonymous"
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
