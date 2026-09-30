package io.droidmcp.core.transport

import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import io.droidmcp.core.protocol.ToolSchemas
import kotlinx.serialization.json.*

/**
 * Direct, in-process access to a [ToolRegistry] — no JSON-RPC, no network. Intended for
 * on-device LLMs that call tools straight from the same process. Mirrors the surface an
 * MCP client gets over [HttpTransport] (list + call) without the protocol envelope.
 */
class InProcessTransport(private val registry: ToolRegistry) {

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
}
