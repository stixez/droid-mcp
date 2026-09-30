package io.droidmcp.core.protocol

import io.droidmcp.core.AuditSink
import io.droidmcp.core.DROID_MCP_VERSION
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolCallAudit
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/**
 * The default [McpProtocol] implementation: a JSON-RPC 2.0 handler over a [ToolRegistry].
 * Services `initialize`, `tools/list`, `tools/call` and `ping`. Messages without an `id` are
 * notifications: they are accepted silently and never answered (the transport replies 202).
 *
 * `initialize` negotiates the protocol version: the client's requested version is echoed when
 * it is in [SUPPORTED_PROTOCOL_VERSIONS], otherwise the newest supported version is offered.
 *
 * Errors follow JSON-RPC 2.0: `-32700` only for unparseable JSON, `-32600` for a structurally
 * invalid request (non-object, batch array, wrong `jsonrpc`, missing `method`), `-32601` for an
 * unknown method, `-32602` for malformed params (keeping the request `id`), and `-32603` for an
 * unexpected internal failure. Error responses always carry `id` (`null` when unknown). The
 * handler never throws back to the transport.
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
        val element = try {
            json.parseToJsonElement(jsonRequest)
        } catch (_: Exception) {
            return jsonRpcError(null, -32700, "Parse error")
        }
        if (element is JsonArray) {
            return jsonRpcError(null, -32600, "Batch requests are not supported")
        }
        val request = element as? JsonObject
            ?: return jsonRpcError(null, -32600, "Invalid Request: expected a JSON object")

        // A JSON-RPC id must be a string, number, or null; anything else is invalid.
        val rawId = request["id"]
        val id = rawId?.takeIf { it is JsonPrimitive }
        if (rawId != null && id == null) {
            return jsonRpcError(null, -32600, "Invalid Request: id must be a string or number")
        }
        if ((request["jsonrpc"] as? JsonPrimitive)?.contentOrNull != "2.0") {
            return if (id == null) "" else jsonRpcError(id, -32600, "Invalid Request: jsonrpc must be \"2.0\"")
        }
        val method = (request["method"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (method == null) {
            // A client-side response (result/error, no method) needs no reply.
            if ("result" in request || "error" in request) return ""
            return jsonRpcError(id, -32600, "Invalid Request: missing method")
        }

        // Notifications (no id) must never be answered — including unknown ones such as
        // notifications/cancelled or notifications/roots/list_changed.
        if (rawId == null) return ""

        val rawParams = request["params"]
        val params = when (rawParams) {
            null, is JsonNull -> JsonObject(emptyMap())
            is JsonObject -> rawParams
            else -> return jsonRpcError(id, -32602, "Invalid params: expected an object")
        }

        return try {
            when (method) {
                "initialize" -> handleInitialize(id, params)
                "tools/list" -> handleToolsList(id)
                "tools/call" -> handleToolsCall(id, params, clientLabel)
                "ping" -> jsonRpcResponse(id, JsonObject(emptyMap()))
                else -> jsonRpcError(id, -32601, "Method not found: $method")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidParamsException) {
            jsonRpcError(id, -32602, "Invalid params: ${e.message}")
        } catch (_: Exception) {
            jsonRpcError(id, -32603, "Internal error")
        }
    }

    private fun handleInitialize(id: JsonElement?, params: JsonObject): String {
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        val negotiated = requested?.takeIf { it in SUPPORTED_PROTOCOL_VERSIONS }
            ?: SUPPORTED_PROTOCOL_VERSIONS.first()
        val result = buildJsonObject {
            put("protocolVersion", negotiated)
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
                tool.annotations.title?.let { put("title", it) }
                put("description", tool.description)
                put("inputSchema", ToolSchemas.inputSchema(tool))
                tool.outputSchema?.let { put("outputSchema", ToolSchemas.toJsonElement(it)) }
                ToolSchemas.annotations(tool.annotations)?.let { put("annotations", it) }
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
        val toolName = (params["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw InvalidParamsException("missing or non-string tool name")
        val rawArguments = params["arguments"]
        val argumentsObject = when (rawArguments) {
            null, is JsonNull -> null
            is JsonObject -> rawArguments
            else -> throw InvalidParamsException("arguments must be an object")
        }
        val argumentsJson = argumentsObject?.toString()

        if (readOnly) {
            val tool = registry.getTool(toolName)
            if (tool != null && !tool.annotations.readOnlyHint) {
                val message = "Tool '$toolName' is not available in read-only mode"
                recordAudit(toolName, clientLabel, argumentsJson, ToolResult.error(message), 0)
                return jsonRpcResponse(id, errorContent(message))
            }
        }
        // A JSON null value is dropped rather than kept as a null entry — tools read params via
        // `params["x"] as? Type`, which already treats a missing key the same as an explicit
        // null, and McpTool.execute's Map<String, Any> signature doesn't accept null values.
        val arguments = argumentsObject?.let { args ->
            args.entries.mapNotNull { (k, v) -> v.toNativeValue()?.let { k to it } }.toMap()
        } ?: emptyMap()

        val startedAt = System.nanoTime()
        val toolResult = registry.executeTool(toolName, arguments)
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000
        recordAudit(toolName, clientLabel, argumentsJson, toolResult, durationMs)

        return if (toolResult.isSuccess) {
            val structured = buildJsonObject {
                toolResult.data?.forEach { (k, v) -> put(k, ToolSchemas.toJsonElement(v)) }
            }
            val content = buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", Json.encodeToString(JsonObject.serializer(), structured))
                }
            }
            jsonRpcResponse(id, buildJsonObject {
                put("content", content)
                // 2025-06-18+: the same payload as typed JSON; older clients ignore it.
                put("structuredContent", structured)
                put("isError", false)
            })
        } else {
            jsonRpcResponse(id, errorContent(toolResult.errorMessage ?: "Unknown error"))
        }
    }

    private fun errorContent(message: String): JsonObject = buildJsonObject {
        putJsonArray("content") {
            addJsonObject {
                put("type", "text")
                put("text", message)
            }
        }
        put("isError", true)
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

    private fun jsonRpcResponse(id: JsonElement?, result: JsonObject): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            put("result", result)
        })

    private fun jsonRpcError(id: JsonElement?, code: Int, message: String): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
            }
        })

    /**
     * Converts a [JsonElement] into the plain Kotlin type tool `execute()` implementations
     * actually check for (`as? Number`, `as? Boolean`, `as? List<*>`, `as? Map<*, *>`, `toString()`).
     *
     * Every non-string JSON primitive — numbers, booleans — used to fall through to
     * [JsonPrimitive.content] regardless of type, which is *always* the raw string form (`"5"`,
     * `"true"`) even for a bare JSON number or boolean literal. Arrays and objects used to become
     * their `toString()` JSON text. The net effect: any tool parameter that wasn't already a JSON
     * string silently became a `String` here, so `params["x"] as? Number`/`as? Boolean`/`as? List<*>`
     * in every tool's `execute()` always failed and fell back to that parameter's default — meaning
     * non-string arguments sent over the HTTP transport were never honored.
     */
    private fun JsonElement.toNativeValue(): Any? = when (this) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            this.isString -> this.content
            this.content == "true" -> true
            this.content == "false" -> false
            else -> this.content.toLongOrNull() ?: this.content.toDoubleOrNull() ?: this.content
        }
        is JsonArray -> this.map { it.toNativeValue() }
        is JsonObject -> this.entries.associate { (k, v) -> k to v.toNativeValue() }
    }

    /** Thrown by handlers for structurally bad params; mapped to a `-32602` with the request id. */
    private class InvalidParamsException(message: String) : Exception(message)

    companion object {
        /**
         * MCP protocol revisions this server speaks, newest first. `initialize` echoes the
         * client's version when listed here and otherwise offers the first entry; the HTTP
         * transport rejects an `MCP-Protocol-Version` header outside this list.
         */
        val SUPPORTED_PROTOCOL_VERSIONS: List<String> =
            listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")
    }
}
