package io.droidmcp.core

import android.content.Context
import io.droidmcp.core.transport.HttpTransport
import io.droidmcp.core.transport.InProcessTransport
import io.droidmcp.core.transport.TlsConfig
import io.droidmcp.core.transport.TokenStore

class DroidMcp private constructor(
    private val registry: ToolRegistry,
    private val httpTransport: HttpTransport?,
    private val inProcessTransport: InProcessTransport,
) {
    val serverToken: String? get() = httpTransport?.effectiveToken

    fun listTools(): List<McpTool> = inProcessTransport.listTools()

    fun listToolsJson(): String = inProcessTransport.listToolsJson()

    suspend fun callTool(name: String, params: Map<String, Any>): ToolResult =
        inProcessTransport.callTool(name, params)

    fun startServer() {
        httpTransport?.start() ?: error("HTTP server not enabled. Use enableHttpServer() in builder.")
    }

    fun stopServer() {
        httpTransport?.stop()
    }

    fun isServerRunning(): Boolean = httpTransport?.isRunning() ?: false

    /**
     * Gate a single tool on or off at runtime. A disabled tool disappears from
     * `tools/list` and is rejected by `tools/call` (both transports) without
     * being unregistered, so the host can drive a per-tool checkbox grid without
     * rebuilding the server. No-op for unknown names.
     */
    fun setToolEnabled(name: String, enabled: Boolean) = registry.setToolEnabled(name, enabled)

    /** Replace the full set of gated-off tools in one call. */
    fun setDisabledTools(names: Set<String>) = registry.setDisabledTools(names)

    /** Names currently gated off. */
    fun disabledTools(): Set<String> = registry.disabledTools()

    /**
     * Rotate the primary bearer token, invalidating the old one (paired clients
     * keep theirs). Returns the new token, or `null` if the HTTP server is
     * disabled or running without auth. Re-issue the pairing QR after calling.
     */
    fun rotateToken(): String? = httpTransport?.rotateToken()

    /**
     * Mint a revocable token for a named client. Re-pairing an existing label
     * replaces its token. Returns the new token, or `null` when there's no
     * authenticated HTTP server.
     */
    fun pairClient(label: String): String? = httpTransport?.pairClient(label)

    /** Revoke a paired client's token. @return true if a client with that label existed. */
    fun revokeClient(label: String): Boolean = httpTransport?.revokeClient(label) ?: false

    /** Snapshot of paired clients (excludes the primary). */
    fun pairedClients(): List<TokenStore.PairedClient> = httpTransport?.pairedClients() ?: emptyList()

    /**
     * SHA-256 fingerprint of the server's TLS certificate to pin in the pairing
     * QR, or `null` if the server is plaintext. See [TlsConfig.certFingerprintSha256].
     */
    val tlsFingerprint: String? get() = httpTransport?.tlsFingerprint

    class Builder {
        private val tools = mutableListOf<McpTool>()
        private var httpPort: Int? = null
        private var authToken: String? = null
        private var requireAuth: Boolean = true
        private var readOnly: Boolean = false
        private var androidContext: Context? = null
        private var auditSink: AuditSink? = null
        private var tls: TlsConfig? = null

        fun addTool(tool: McpTool) = apply { tools.add(tool) }

        fun addTools(toolList: List<McpTool>) = apply { tools.addAll(toolList) }

        /**
         * Record every HTTP `tools/call` to [sink] (e.g. the Room-backed sink
         * from `droid-mcp-audit`). Has no effect unless the HTTP server is
         * enabled. In-process calls are not audited — the host owns that path.
         */
        fun withAuditSink(sink: AuditSink) = apply { this.auditSink = sink }

        /**
         * Serve the HTTP transport over TLS using [config]'s keystore. Build a
         * config with `SelfSignedCert.loadOrCreate(...)` from the opt-in
         * `droid-mcp-tls` module, or supply your own keystore. The server binds
         * [TlsConfig.httpsPort] instead of the plaintext port; clients pin
         * [DroidMcp.tlsFingerprint]. No effect unless the HTTP server is enabled.
         */
        fun enableTls(config: TlsConfig) = apply { this.tls = config }

        fun enableHttpServer(
            port: Int = 8080,
            token: String? = null,
            requireAuth: Boolean = true,
            readOnly: Boolean = false,
            context: Context? = null,
        ) = apply {
            this.httpPort = port
            this.authToken = token
            this.requireAuth = requireAuth
            this.readOnly = readOnly
            this.androidContext = context ?: this.androidContext
        }

        fun build(): DroidMcp {
            val registry = ToolRegistry()
            registry.registerAll(tools)
            val inProcess = InProcessTransport(registry)
            val http = httpPort?.let {
                HttpTransport(
                    registry = registry,
                    port = it,
                    bearerToken = authToken,
                    requireAuth = requireAuth,
                    readOnly = readOnly,
                    context = androidContext,
                    auditSink = auditSink,
                    tls = tls,
                )
            }
            return DroidMcp(registry, http, inProcess)
        }
    }

    companion object {
        fun builder() = Builder()
    }
}
