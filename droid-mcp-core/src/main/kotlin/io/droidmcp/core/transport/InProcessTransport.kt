package io.droidmcp.core.transport

import io.droidmcp.core.McpPrompt
import io.droidmcp.core.McpResource
import io.droidmcp.core.McpResourceTemplate
import io.droidmcp.core.McpTool
import io.droidmcp.core.ProgressSink
import io.droidmcp.core.ProgressUpdate
import io.droidmcp.core.PromptRegistry
import io.droidmcp.core.PromptResult
import io.droidmcp.core.ResourceContents
import io.droidmcp.core.ResourceRegistry
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import io.droidmcp.core.missingArguments
import io.droidmcp.core.runProvider
import io.droidmcp.core.protocol.ToolSchemas
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Direct, in-process access to a [ToolRegistry] — no JSON-RPC, no network. Intended for
 * on-device LLMs that call tools straight from the same process. Mirrors the surface an
 * MCP client gets over [HttpTransport] (tools, resources, prompts) without the protocol envelope.
 */
class InProcessTransport(
    private val registry: ToolRegistry,
    private val resources: ResourceRegistry = ResourceRegistry(),
    private val prompts: PromptRegistry = PromptRegistry(),
) {

    /** The enabled tools as live [McpTool] instances — tools the host gated off are excluded. */
    fun listTools(): List<McpTool> = registry.listEnabledTools()

    /**
     * The enabled tools serialised to a JSON array, each entry carrying `name`, `description`,
     * a `parameters` JSON Schema (`type: object` with `properties` and a `required` list), plus
     * `title`, `outputSchema` and `annotations` when set. For LLM runtimes that want the tool
     * catalogue as a string. Gated-off tools are excluded, matching `tools/list` over HTTP.
     */
    fun listToolsJson(): String {
        val tools = registry.listEnabledTools().map { tool ->
            buildJsonObject {
                put("name", tool.name)
                tool.annotations.title?.let { put("title", it) }
                put("description", tool.description)
                put("parameters", ToolSchemas.inputSchema(tool))
                tool.outputSchema?.let { put("outputSchema", ToolSchemas.toJsonElement(it)) }
                ToolSchemas.annotations(tool.annotations)?.let { put("annotations", it) }
            }
        }
        return Json.encodeToString(JsonArray.serializer(), JsonArray(tools))
    }

    /** Invoke a tool; honours runtime gating and runs on `Dispatchers.IO` (see [ToolRegistry.executeTool]). */
    suspend fun callTool(name: String, params: Map<String, Any>): ToolResult =
        registry.executeTool(name, params)

    /** Like [callTool], delivering the tool's [reportProgress][io.droidmcp.core.reportProgress] updates to [onProgress]. */
    suspend fun callTool(
        name: String,
        params: Map<String, Any>,
        onProgress: suspend (ProgressUpdate) -> Unit,
    ): ToolResult = withContext(ProgressSink(onProgress)) { registry.executeTool(name, params) }

    private fun enabledToolNames(): Set<String> = registry.listEnabledTools().mapTo(HashSet()) { it.name }

    /**
     * Resources of every provider whose required tools are enabled, like `resources/list`
     * (unpaginated). Providers run in parallel; one that fails or times out contributes nothing.
     */
    suspend fun listResources(): List<McpResource> =
        resources.listVisible(enabledToolNames(), registry.toolTimeoutMs).map { it.second }

    /** URI templates of every provider whose required tools are enabled, like `resources/templates/list`. */
    fun listResourceTemplates(): List<McpResourceTemplate> =
        resources.visibleProviders(enabledToolNames()).flatMap { it.templates }

    /**
     * The contents at [uri], or null when no visible provider handles it, it can't be read, or the
     * host's confirmer (configured for the provider's required tools) declined.
     */
    suspend fun readResource(uri: String): List<ResourceContents>? {
        val provider = resources.providerFor(uri, enabledToolNames()) ?: return null
        if (registry.confirmAccess(provider.requiredTools, mapOf("uri" to uri), clientLabel = null) != null) return null
        return onIo { provider.read(uri) }?.takeIf { it.isNotEmpty() }
    }

    /** Prompts whose [McpPrompt.requiredTools] are all enabled, like `prompts/list`. */
    fun listPrompts(): List<McpPrompt> = prompts.visiblePrompts(enabledToolNames())

    /**
     * Render the named prompt, or return null when it isn't listed.
     * @throws IllegalArgumentException when a required argument is missing or a value is invalid.
     */
    suspend fun getPrompt(name: String, arguments: Map<String, String> = emptyMap()): PromptResult? {
        val prompt = listPrompts().firstOrNull { it.name == name } ?: return null
        val missing = prompt.missingArguments(arguments)
        require(missing.isEmpty()) { "missing required argument(s): ${missing.joinToString()}" }
        return prompt.render(arguments)
    }

    /** Provider code, bounded like a tool call; null on timeout or failure (see [runProvider]). */
    private suspend fun <T> onIo(block: suspend () -> T): T? = runProvider(registry.toolTimeoutMs, block)?.getOrNull()
}
