package io.droidmcp.core.protocol

import io.droidmcp.core.ToolRegistry
import kotlinx.serialization.json.*

class McpProtocolImpl(
    private val registry: ToolRegistry,
    private val serverName: String = "droid-mcp",
    private val serverVersion: String = "0.3.0",
) : McpProtocol {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun handleMessage(jsonRequest: String): String {
        return try {
            val request = json.parseToJsonElement(jsonRequest).jsonObject
            val id = request["id"]
            val method = request["method"]?.jsonPrimitive?.content
            val params = request["params"]?.jsonObject ?: JsonObject(emptyMap())

            when (method) {
                "initialize" -> handleInitialize(id, params)
                "tools/list" -> handleToolsList(id)
                "tools/call" -> handleToolsCall(id, params)
                "notifications/initialized" -> ""
                "ping" -> jsonRpcResponse(id, buildJsonObject { put("status", "ok") })
                else -> jsonRpcError(id, -32601, "Method not found: $method")
            }
        } catch (e: Exception) {
            jsonRpcError(null, -32700, "Parse error: ${e.message}")
        }
    }

    private fun handleInitialize(id: JsonElement?, params: JsonObject): String {
        val result = buildJsonObject {
            put("protocolVersion", "2024-11-05")
            putJsonObject("capabilities") {
                putJsonObject("tools") {
                    put("listChanged", false)
                }
            }
            putJsonObject("serverInfo") {
                put("name", serverName)
                put("version", serverVersion)
            }
        }
        return jsonRpcResponse(id, result)
    }

    private fun handleToolsList(id: JsonElement?): String {
        val tools = registry.listTools().map { tool ->
            buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                putJsonObject("inputSchema") {
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
        val result = buildJsonObject {
            putJsonArray("tools") { tools.forEach { add(it) } }
        }
        return jsonRpcResponse(id, result)
    }

    private suspend fun handleToolsCall(id: JsonElement?, params: JsonObject): String {
        val toolName = params["name"]?.jsonPrimitive?.content
            ?: return jsonRpcError(id, -32602, "Missing tool name")
        val arguments = params["arguments"]?.jsonObject?.let { args ->
            args.entries.associate { (k, v) ->
                k to when {
                    v is JsonPrimitive && v.isString -> v.content
                    v is JsonPrimitive -> v.content
                    else -> v.toString()
                }
            }
        } ?: emptyMap()

        val toolResult = registry.executeTool(toolName, arguments)

        return if (toolResult.isSuccess) {
            val content = buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", Json.encodeToString(JsonObject.serializer(),
                        buildJsonObject {
                            toolResult.data?.forEach { (k, v) ->
                                when (v) {
                                    is String -> put(k, v)
                                    is Number -> put(k, v as Number)
                                    is Boolean -> put(k, v)
                                    null -> put(k, JsonNull)
                                    else -> put(k, v.toString())
                                }
                            }
                        }
                    ))
                }
            }
            val result = buildJsonObject {
                put("content", content)
                put("isError", false)
            }
            jsonRpcResponse(id, result)
        } else {
            val content = buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", toolResult.errorMessage ?: "Unknown error")
                }
            }
            val result = buildJsonObject {
                put("content", content)
                put("isError", true)
            }
            jsonRpcResponse(id, result)
        }
    }

    private fun jsonRpcResponse(id: JsonElement?, result: JsonObject): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            put("result", result)
        })

    private fun jsonRpcError(id: JsonElement?, code: Int, message: String): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            id?.let { put("id", it) }
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        })
}
