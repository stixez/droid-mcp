package io.droidmcp.core.transport

import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import kotlinx.serialization.json.*

class InProcessTransport(private val registry: ToolRegistry) {

    fun listTools(): List<McpTool> = registry.listTools()

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
