package io.droidmcp.core.protocol

import io.droidmcp.core.AuditSink
import io.droidmcp.core.DROID_MCP_VERSION
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolCallAudit
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import kotlinx.serialization.json.*

/**
 * The default [McpProtocol] implementation: a JSON-RPC 2.0 handler over a [ToolRegistry].
 * Services `initialize`, `tools/list`, `tools/call`, `ping`, and `notifications/initialized`.
 * Malformed input yields a `-32700` parse error and an unknown method a `-32601`; the handler
 * never throws back to the transport.
 *
 * Honours [readOnly] mode (filters `tools/list` to read-only tools and rejects mutating
 * `tools/call`s with an `isError` content payload) and emits a [ToolCallAudit] to [auditSink]
 * after every call — a failing sink is swallowed so it can never break a tool call.
 *
 * @property registry Source of registered tools and the executor for `tools/call`.
 * @property serverName Server name reported in the `initialize` handshake.
 * @property serverVersion Server version reported in `initialize`; defaults to [DROID_MCP_VERSION].
 * @property readOnly When true, only [ToolAnnotations.readOnlyHint] tools are visible/callable.
 * @property auditSink Optional hook invoked once per `tools/call` with timing and outcome; null disables auditing.
 */
class McpProtocolImpl(
    private val registry: ToolRegistry,
    private val serverName: String = "droid-mcp",
    private val serverVersion: String = DROID_MCP_VERSION,
    private val readOnly: Boolean = false,
    private val auditSink: AuditSink? = null,
) : McpProtocol {

    private fun visibleTools(): List<McpTool> =
        if (readOnly) registry.listEnabledTools().filter { it.annotations.readOnlyHint }
        else registry.listEnabledTools()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun handleMessage(jsonRequest: String): String =
        handleMessage(jsonRequest, clientLabel = null)

    override suspend fun handleMessage(jsonRequest: String, clientLabel: String?): String {
        return try {
            val request = json.parseToJsonElement(jsonRequest).jsonObject
            val id = request["id"]
            val method = request["method"]?.jsonPrimitive?.content
            val params = request["params"]?.jsonObject ?: JsonObject(emptyMap())

            when (method) {
                "initialize" -> handleInitialize(id, params)
                "tools/list" -> handleToolsList(id)
                "tools/call" -> handleToolsCall(id, params, clientLabel)
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
        val tools = visibleTools().map { tool ->
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
                annotationsJson(tool.annotations)?.let { put("annotations", it) }
            }
        }
        val result = buildJsonObject {
            putJsonArray("tools") { tools.forEach { add(it) } }
        }
        return jsonRpcResponse(id, result)
    }

    private suspend fun handleToolsCall(
        id: JsonElement?,
        params: JsonObject,
        clientLabel: String?,
    ): String {
        val toolName = params["name"]?.jsonPrimitive?.content
            ?: return jsonRpcError(id, -32602, "Missing tool name")
        if (readOnly) {
            val tool = registry.listTools().firstOrNull { it.name == toolName }
            if (tool != null && !tool.annotations.readOnlyHint) {
                val content = buildJsonArray {
                    addJsonObject {
                        put("type", "text")
                        put("text", "Tool '$toolName' is not available in read-only mode")
                    }
                }
                return jsonRpcResponse(id, buildJsonObject {
                    put("content", content)
                    put("isError", true)
                })
            }
        }
        val argumentsJson = params["arguments"]?.jsonObject?.toString()
        val arguments = params["arguments"]?.jsonObject?.let { args ->
            args.entries.associate { (k, v) ->
                k to when {
                    v is JsonPrimitive && v.isString -> v.content
                    v is JsonPrimitive -> v.content
                    else -> v.toString()
                }
            }
        } ?: emptyMap()

        val startedAt = System.nanoTime()
        val toolResult = registry.executeTool(toolName, arguments)
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000
        recordAudit(toolName, clientLabel, argumentsJson, toolResult, durationMs)

        return if (toolResult.isSuccess) {
            val content = buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", Json.encodeToString(JsonObject.serializer(),
                        buildJsonObject {
                            toolResult.data?.forEach { (k, v) ->
                                put(k, anyToJsonElement(v))
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

    private fun recordAudit(
        toolName: String,
        clientLabel: String?,
        argumentsJson: String?,
        result: ToolResult,
        durationMs: Long,
    ) {
        val sink = auditSink ?: return
        try {
            sink.record(
                ToolCallAudit(
                    timestamp = System.currentTimeMillis(),
                    toolName = toolName,
                    clientLabel = clientLabel,
                    argumentsJson = argumentsJson,
                    success = result.isSuccess,
                    errorMessage = result.errorMessage,
                    durationMs = durationMs,
                )
            )
        } catch (_: Exception) {
            // A broken audit backend must never fail a tool call.
        }
    }

    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> buildJsonObject {
            value.forEach { (k, v) ->
                if (k is String) put(k, anyToJsonElement(v))
            }
        }
        is Iterable<*> -> buildJsonArray {
            value.forEach { add(anyToJsonElement(it)) }
        }
        is Array<*> -> buildJsonArray {
            value.forEach { add(anyToJsonElement(it)) }
        }
        else -> JsonPrimitive(value.toString())
    }

    private fun annotationsJson(a: ToolAnnotations): JsonObject? {
        val default = ToolAnnotations()
        if (a == default) return null
        return buildJsonObject {
            if (a.readOnlyHint != default.readOnlyHint) put("readOnlyHint", a.readOnlyHint)
            if (a.destructiveHint != default.destructiveHint) put("destructiveHint", a.destructiveHint)
            if (a.idempotentHint != default.idempotentHint) put("idempotentHint", a.idempotentHint)
            if (a.openWorldHint != default.openWorldHint) put("openWorldHint", a.openWorldHint)
            a.title?.let { put("title", it) }
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
