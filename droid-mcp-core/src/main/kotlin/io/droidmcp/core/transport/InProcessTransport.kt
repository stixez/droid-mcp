package io.droidmcp.core.transport

import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import kotlinx.serialization.json.*

/**
 * Direct, in-process access to a [ToolRegistry] — no JSON-RPC, no network. Intended for
 * on-device LLMs that call tools straight from the same process. Mirrors the surface an
 * MCP client gets over [HttpTransport] (list + call) without the protocol envelope.
 */
class InProcessTransport(private val registry: ToolRegistry) {

    /** The registered tools as live [McpTool] instances. */
    fun listTools(): List<McpTool> = registry.listTools()

    /**
     * The registered tools serialised to a JSON array, each entry carrying `name`,
     * `description`, and a `parameters` JSON Schema (`type: object` with `properties`
     * and a `required` list). For LLM runtimes that want the tool catalogue as a string.
     */
    fun listToolsJson(): String {
        val tools = registry.listTools().map { tool ->
            buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        tool.parameters.forEach { param ->
                            putJsonObject(param.name) {
                                param.toJsonSchema().forEach { (k, v) ->
                                    put(k, JsonPrimitive(v.toString()))
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        tool.parameters.filter { it.required }.forEach { add(it.name) }
                    }
                }
            }
        }
        return Json.encodeToString(JsonArray.serializer(), JsonArray(tools))
    }

    suspend fun callTool(name: String, params: Map<String, Any>): ToolResult =
        registry.executeTool(name, params)
}
