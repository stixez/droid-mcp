package io.droidmcp.core

import android.content.Context
import io.droidmcp.core.transport.HttpTransport
import io.droidmcp.core.transport.InProcessTransport
import io.droidmcp.core.transport.TlsConfig
import io.droidmcp.core.transport.TokenStore

/**
 * The SDK entry point. Built via [DroidMcp.builder] / [Builder], it owns a [ToolRegistry] and
 * both transports: an always-on [InProcessTransport] for on-device LLMs ([listTools] / [callTool])
 * and an optional [HttpTransport] for desktop MCP clients ([startServer]).
 *
 * Also surfaces the 1.0 hardening controls: runtime per-tool gating ([setToolEnabled] /
 * [setDisabledTools]), token rotation ([rotateToken]), per-client pairing ([pairClient] /
 * [revokeClient] / [pairedClients]), and the TLS fingerprint ([tlsFingerprint]) to pin in a
 * pairing QR.
 */
class DroidMcp private constructor(
    private val registry: ToolRegistry,
    private val resources: ResourceRegistry,
    private val prompts: PromptRegistry,
    private val httpTransport: HttpTransport?,
    private val inProcessTransport: InProcessTransport,
) {
    /** The HTTP server's effective bearer token, or null if the server is disabled or open (no auth). */
    val serverToken: String? get() = httpTransport?.effectiveToken

    /** The enabled tools as live [McpTool] instances (in-process); gated-off tools are excluded. */
    fun listTools(): List<McpTool> = inProcessTransport.listTools()

    /** The registered tools serialised to a JSON catalogue; see [InProcessTransport.listToolsJson]. */
    fun listToolsJson(): String = inProcessTransport.listToolsJson()

    /** Invoke a tool directly in-process, bypassing JSON-RPC. Honours runtime gating. */
    suspend fun callTool(name: String, params: Map<String, Any>): ToolResult =
        inProcessTransport.callTool(name, params)

    /** Like [callTool], delivering the tool's [reportProgress] updates to [onProgress]. */
    suspend fun callTool(
        name: String,
        params: Map<String, Any>,
        onProgress: suspend (ProgressUpdate) -> Unit,
    ): ToolResult = inProcessTransport.callTool(name, params, onProgress)

    /**
     * Register more tools after [Builder.build] — e.g. once the user grants the permission they
     * need. Connected HTTP clients get `notifications/tools/list_changed`.
     */
    fun registerTools(toolList: List<McpTool>) = registry.registerAll(toolList)

    /** Remove a tool at runtime (e.g. after its permission is revoked). @return true if it was registered. */
    fun unregisterTool(name: String): Boolean = registry.unregister(name)

    /** Add a resource provider at runtime; connected clients get `notifications/resources/list_changed`. */
    fun registerResourceProvider(provider: ResourceProvider) = resources.register(provider)

    /** Remove a resource provider added with [Builder.addResourceProvider] or [registerResourceProvider]. */
    fun unregisterResourceProvider(provider: ResourceProvider) = resources.unregister(provider)

    /** Every provider's advertised resources (in-process). */
    suspend fun listResources(): List<McpResource> = inProcessTransport.listResources()

    /** Every provider's URI templates (in-process). */
    fun listResourceTemplates(): List<McpResourceTemplate> = inProcessTransport.listResourceTemplates()

    /** The contents at [uri], or null when nothing handles it (in-process). */
    suspend fun readResource(uri: String): List<ResourceContents>? = inProcessTransport.readResource(uri)

    /** Prompts whose required tools are all enabled (in-process). */
    fun listPrompts(): List<McpPrompt> = inProcessTransport.listPrompts()

    /** Render a prompt in-process; see [InProcessTransport.getPrompt]. */
    suspend fun getPrompt(name: String, arguments: Map<String, String> = emptyMap()): PromptResult? =
        inProcessTransport.getPrompt(name, arguments)

    /** Start the HTTP server. Throws if the server was not enabled via [Builder.enableHttpServer]. */
    fun startServer() {
        httpTransport?.start() ?: error("HTTP server not enabled. Use enableHttpServer() in builder.")
    }

    /** Stop the HTTP server if running; no-op when the server is disabled. */
    fun stopServer() {
        httpTransport?.stop()
    }

    /** Whether the HTTP server is currently running. */
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

    /**
     * Fluent builder for [DroidMcp]. Add tools, optionally enable the HTTP server, TLS, and an
     * audit sink, then call [build]. All setters return `this` for chaining.
     */
    class Builder {
        private val tools = mutableListOf<McpTool>()
        private val resourceProviders = mutableListOf<ResourceProvider>()
        private val promptList = mutableListOf<McpPrompt>()
        private var httpPort: Int? = null
        private var authToken: String? = null
        private var requireAuth: Boolean = true
        private var readOnly: Boolean = false
        private var androidContext: Context? = null
        private var auditSink: AuditSink? = null
        private var tls: TlsConfig? = null
        private var allowedOrigins: Set<String> = emptySet()
        private var toolTimeoutMs: Long = ToolRegistry.DEFAULT_TOOL_TIMEOUT_MS
        private var confirmer: ToolCallConfirmer? = null
        private var requiresConfirmation: (McpTool) -> Boolean = { it.annotations.destructiveHint }

        /** Add a single tool to the registry. */
        fun addTool(tool: McpTool) = apply { tools.add(tool) }

        /** Add every tool in [toolList] (e.g. a module provider's `all(context)`). */
        fun addTools(toolList: List<McpTool>) = apply { tools.addAll(toolList) }

        /** Serve [provider]'s resources (e.g. `MediaTools.resources(context)`) over the `resources/...` methods. */
        fun addResourceProvider(provider: ResourceProvider) = apply { resourceProviders.add(provider) }

        /** Add a prompt served over the `prompts/...` methods. */
        fun addPrompt(prompt: McpPrompt) = apply { promptList.add(prompt) }

        /** Add several prompts. */
        fun addPrompts(prompts: List<McpPrompt>) = apply { promptList.addAll(prompts) }

        /** Add every [BuiltInPrompts] prompt; each is listed only while the tools it uses are. */
        fun addBuiltInPrompts() = addPrompts(BuiltInPrompts.all())

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

        /**
         * Ask the host before running tools that match [requiresConfirmation] (by default every
         * tool with [ToolAnnotations.destructiveHint]) — on both transports. [confirmer] typically
         * shows a dialog; a decline, a missing answer within the tool timeout, or an exception
         * returns a `tool_call_declined` error and the tool never runs.
         */
        fun confirmToolCalls(
            requiresConfirmation: (McpTool) -> Boolean = { it.annotations.destructiveHint },
            confirmer: ToolCallConfirmer,
        ) = apply {
            this.requiresConfirmation = requiresConfirmation
            this.confirmer = confirmer
        }

        /**
         * Cap every tool call (both transports) at [millis]; an overrunning tool is cancelled
         * and reported as a `tool_timeout` error. Defaults to [ToolRegistry.DEFAULT_TOOL_TIMEOUT_MS].
         */
        fun toolTimeout(millis: Long) = apply {
            require(millis > 0) { "tool timeout must be positive" }
            this.toolTimeoutMs = millis
        }

        /**
         * Enable the HTTP transport (Ktor) for desktop MCP clients.
         *
         * @param port Plaintext port to bind (ignored when [enableTls] supplies an HTTPS port).
         * @param token Fixed bearer token; when null and [requireAuth] is true, one is generated
         *   via `SecureRandom` and exposed as [DroidMcp.serverToken].
         * @param requireAuth Require a `Bearer` token on every request (default true). When false
         *   the server is open on the local network.
         * @param readOnly Expose and accept only read-only tools (filters `tools/list`, rejects
         *   mutating `tools/call`).
         * @param context Android context, used for mDNS service broadcast when available.
         * @param allowedOrigins Browser origins (e.g. `"http://localhost:6274"` for MCP Inspector)
         *   allowed to call the server. Requests carrying any other `Origin` header are rejected
         *   with 403 to block DNS-rebinding and drive-by browser requests; native clients send no
         *   `Origin` and are unaffected.
         * @throws IllegalArgumentException if [token] is shorter than
         *   [HttpTransport.MIN_TOKEN_LENGTH] characters.
         */
        fun enableHttpServer(
            port: Int = 8080,
            token: String? = null,
            requireAuth: Boolean = true,
            readOnly: Boolean = false,
            context: Context? = null,
            allowedOrigins: Set<String> = emptySet(),
        ) = apply {
            require(token == null || token.length >= HttpTransport.MIN_TOKEN_LENGTH) {
                "token must be at least ${HttpTransport.MIN_TOKEN_LENGTH} characters"
            }
            this.httpPort = port
            this.allowedOrigins = allowedOrigins
            this.authToken = token
            this.requireAuth = requireAuth
            this.readOnly = readOnly
            this.androidContext = context ?: this.androidContext
        }

        /** Assemble the [DroidMcp] instance: build the registry, wire transports, return it (does not start the server). */
        fun build(): DroidMcp {
            val registry = ToolRegistry(toolTimeoutMs, confirmer, requiresConfirmation)
            registry.registerAll(tools)
            val resources = ResourceRegistry().apply { resourceProviders.forEach(::register) }
            val prompts = PromptRegistry().apply { registerAll(promptList) }
            val inProcess = InProcessTransport(registry, resources, prompts)
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
                    allowedOrigins = allowedOrigins,
                    resources = resources,
                    prompts = prompts,
                )
            }
            return DroidMcp(registry, resources, prompts, http, inProcess)
        }
    }

    companion object {
        /** Start configuring a new [DroidMcp] instance. */
        fun builder() = Builder()
    }
}
