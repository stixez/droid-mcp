package io.droidmcp.core

import android.content.Context
import io.droidmcp.core.transport.HttpTransport
import io.droidmcp.core.transport.InProcessTransport

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

    class Builder {
        private val tools = mutableListOf<McpTool>()
        private var httpPort: Int? = null
        private var authToken: String? = null
        private var requireAuth: Boolean = true
        private var readOnly: Boolean = false
        private var androidContext: Context? = null

        fun addTool(tool: McpTool) = apply { tools.add(tool) }

        fun addTools(toolList: List<McpTool>) = apply { tools.addAll(toolList) }

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
                )
            }
            return DroidMcp(registry, http, inProcess)
        }
    }

    companion object {
        fun builder() = Builder()
    }
}
