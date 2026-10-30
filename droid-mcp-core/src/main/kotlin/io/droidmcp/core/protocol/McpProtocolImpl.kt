package io.droidmcp.core.protocol

import io.droidmcp.core.AuditSink
import io.droidmcp.core.CompletionContext
import io.droidmcp.core.DROID_MCP_VERSION
import io.droidmcp.core.ElicitationResult
import io.droidmcp.core.ElicitationSink
import io.droidmcp.core.McpPrompt
import io.droidmcp.core.McpResource
import io.droidmcp.core.McpTool
import io.droidmcp.core.ProgressSink
import io.droidmcp.core.ProgressUpdate
import io.droidmcp.core.PromptRegistry
import io.droidmcp.core.ResourceContents
import io.droidmcp.core.ResourceProvider
import io.droidmcp.core.ResourceRegistry
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolCallAudit
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import io.droidmcp.core.missingArguments
import io.droidmcp.core.runProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.serialization.json.*

/**
 * The default [McpProtocol] implementation: a JSON-RPC 2.0 handler over a [ToolRegistry].
 * Services `initialize`, `tools/list`, `tools/call`, `resources/list`, `resources/templates/list`,
 * `resources/read`, `prompts/list`, `prompts/get`, `completion/complete` and `ping`. Messages without an `id` are
 * notifications: they are accepted silently and never answered (the transport replies 202).
 * `notifications/cancelled` cancels the caller's own in-flight `tools/call` with that
 * `requestId`; the cancelled request then gets no response.
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
 * after every call, every `resources/read` (tool name `resources/read`) and every tool a
 * completion reads through (tool name `completion/complete`) — a failing sink is
 * swallowed so it can never break a call. A resource provider is visible only while its
 * [ResourceProvider.requiredTools][io.droidmcp.core.ResourceProvider.requiredTools] are, so tool
 * gating and read-only mode apply to the same data served as resources.
 *
 * @property registry Source of registered tools and the executor for `tools/call`.
 * @property serverName Server name reported in the `initialize` handshake.
 * @property serverVersion Server version reported in `initialize`; defaults to [DROID_MCP_VERSION].
 * @property readOnly When true, only [ToolAnnotations.readOnlyHint] tools are visible/callable.
 * @property auditSink Optional hook invoked once per `tools/call` with timing and outcome; null disables auditing.
 * @property resources Resource providers served by the `resources/...` methods.
 * @property prompts Prompts served by the `prompts/...` methods; a prompt is listed only while its
 *   [McpPrompt.requiredTools] are all visible.
 * @property notifiesListChanged Whether the transport delivers `notifications/…/list_changed`
 *   (advertised as `listChanged` in the `initialize` capabilities).
 */
class McpProtocolImpl(
    private val registry: ToolRegistry,
    private val serverName: String = "droid-mcp",
    private val serverVersion: String = DROID_MCP_VERSION,
    private val readOnly: Boolean = false,
    private val auditSink: AuditSink? = null,
    private val resources: ResourceRegistry = ResourceRegistry(),
    private val prompts: PromptRegistry = PromptRegistry(),
    private val notifiesListChanged: Boolean = false,
) : McpProtocol {

    private fun visibleTools(): List<McpTool> =
        if (readOnly) registry.listEnabledTools().filter { it.annotations.readOnlyHint }
        else registry.listEnabledTools()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Running `tools/call`s keyed by (scope, JSON request id), so `notifications/cancelled` can stop
     * the right one. The scope is the MCP session (the client label when there is none), so one
     * session can't cancel another's call — even when both use the same token.
     */
    private val inFlight = ConcurrentHashMap<Pair<String?, String>, Job>()

    /**
     * Sessions whose client declared the `elicitation` capability at `initialize`. Entries live
     * exactly as long as the transport's session: it calls [endSession] on DELETE, eviction and stop.
     */
    private val elicitationSessions: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** A server-to-client request awaiting the client's response, which must come from [owner]'s scope. */
    private class PendingRequest(val owner: String?, val response: CompletableDeferred<JsonObject>)

    /** Keyed by random ids, so another client can't guess one it never received. */
    private val pending = ConcurrentHashMap<String, PendingRequest>()

    override suspend fun handleMessage(jsonRequest: String): String =
        handleMessage(jsonRequest, clientLabel = null)

    override suspend fun handleMessage(jsonRequest: String, clientLabel: String?): String =
        handleMessage(jsonRequest, clientLabel, sessionId = null, outbound = null)

    /**
     * Full form used by the HTTP transport. [sessionId] identifies the MCP session (for
     * capabilities declared at `initialize`); [outbound] carries progress notifications and
     * elicitation requests for a `tools/call` while it runs. With a null [outbound] both are off.
     */
    internal suspend fun handleMessage(
        jsonRequest: String,
        clientLabel: String?,
        sessionId: String?,
        outbound: McpOutbound?,
    ): String {
        val element = try {
            json.parseToJsonElement(jsonRequest)
        } catch (_: Exception) {
            return jsonRpcError(null, -32700, "Parse error")
        }
        return handleParsed(element, clientLabel, sessionId, outbound)
    }

    /** As [handleMessage], for a body the transport already parsed (it needs `method` first). */
    internal suspend fun handleParsed(
        element: JsonElement,
        clientLabel: String?,
        sessionId: String?,
        outbound: McpOutbound?,
    ): String {
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
            // A client-side response (result/error, no method): route it to the waiting server
            // request, if it's ours and came from the same client. It never gets a reply.
            if ("result" in request || "error" in request) {
                val key = (id as? JsonPrimitive)?.contentOrNull
                val waiting = key?.let { pending[it] }
                if (waiting != null && waiting.owner == (sessionId ?: clientLabel)) waiting.response.complete(request)
                return ""
            }
            return jsonRpcError(id, -32600, "Invalid Request: missing method")
        }

        // Notifications (no id) must never be answered. notifications/cancelled stops the named
        // in-flight tools/call; unknown ones (e.g. notifications/roots/list_changed) are ignored.
        if (rawId == null) {
            if (method == "notifications/cancelled") {
                val requestId = ((request["params"] as? JsonObject)?.get("requestId") as? JsonPrimitive)
                requestId?.let { inFlight[(sessionId ?: clientLabel) to it.toString()]?.cancel(CancellationException("Cancelled by client")) }
            }
            return ""
        }

        val rawParams = request["params"]
        val params = when (rawParams) {
            null, is JsonNull -> JsonObject(emptyMap())
            is JsonObject -> rawParams
            else -> return jsonRpcError(id, -32602, "Invalid params: expected an object")
        }

        return try {
            when (method) {
                "initialize" -> handleInitialize(id, params, sessionId)
                "tools/list" -> handleToolsList(id)
                "tools/call" -> handleToolsCall(id, params, clientLabel, sessionId, outbound)
                "resources/list" -> handleResourcesList(id, params, sessionId ?: clientLabel)
                "resources/templates/list" -> handleResourceTemplatesList(id)
                "resources/read" -> handleResourcesRead(id, params, clientLabel)
                "prompts/list" -> handlePromptsList(id)
                "prompts/get" -> handlePromptsGet(id, params)
                "completion/complete" -> handleComplete(id, params, clientLabel, sessionId)
                "ping" -> jsonRpcResponse(id, JsonObject(emptyMap()))
                else -> jsonRpcError(id, -32601, "Method not found: $method")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidParamsException) {
            jsonRpcError(id, -32602, "Invalid params: ${e.message}")
        } catch (e: ResourceNotFoundException) {
            jsonRpcError(id, RESOURCE_NOT_FOUND, "Resource not found", buildJsonObject { put("uri", e.uri) })
        } catch (_: Exception) {
            jsonRpcError(id, -32603, "Internal error")
        }
    }

    /** Forget per-session state (the HTTP transport calls this when a session ends). */
    internal fun endSession(sessionId: String) {
        elicitationSessions.remove(sessionId)
        // Unblock elicitations still waiting on this session; the tool sees a cancel.
        pending.values.filter { it.owner == sessionId }.forEach { it.response.complete(JsonObject(emptyMap())) }
    }

    private fun handleInitialize(id: JsonElement?, params: JsonObject, sessionId: String?): String {
        if (sessionId != null && (params["capabilities"] as? JsonObject)?.get("elicitation") is JsonObject) {
            elicitationSessions.add(sessionId)
        }
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        val negotiated = requested?.takeIf { it in SUPPORTED_PROTOCOL_VERSIONS }
            ?: SUPPORTED_PROTOCOL_VERSIONS.first()
        val result = buildJsonObject {
            put("protocolVersion", negotiated)
            putJsonObject("capabilities") {
                putJsonObject("tools") { put("listChanged", notifiesListChanged) }
                putJsonObject("resources") {
                    put("subscribe", false)
                    put("listChanged", notifiesListChanged)
                }
                putJsonObject("prompts") { put("listChanged", notifiesListChanged) }
                putJsonObject("completions") {}
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

    private fun visibleToolNames(): Set<String> = visibleTools().mapTo(HashSet()) { it.name }

    /**
     * Listings that span several pages, keyed by `<owner>|<snapshot id>`, so later pages come from
     * the same listing instead of re-querying every provider (and can't shift between pages). The
     * owner (session, else client) scopes a cursor to whoever got it. Each page drops entries whose
     * provider has since been hidden, so gating applies mid-pagination. LRU-bounded; an evicted
     * snapshot falls back to a fresh listing.
     */
    private val listSnapshots: MutableMap<String, List<Pair<ResourceProvider, McpResource>>> = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<Pair<ResourceProvider, McpResource>>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, List<Pair<ResourceProvider, McpResource>>>?) =
                size > MAX_LIST_SNAPSHOTS
        }
    )

    private suspend fun handleResourcesList(id: JsonElement?, params: JsonObject, owner: String?): String {
        val (snapshotId, offset) = parseCursor(params)
        val visibleTools = visibleToolNames()
        val snapshot = snapshotId?.let { listSnapshots["$owner|$it"] }
        val listing = snapshot ?: resources.listVisible(visibleTools, registry.toolTimeoutMs)
        if (offset > listing.size) throw InvalidParamsException("cursor is out of range")
        val more = offset + PAGE_SIZE < listing.size
        val nextId = when {
            !more -> null
            snapshot != null -> snapshotId
            else -> UUID.randomUUID().toString().also { listSnapshots["$owner|$it"] = listing }
        }
        val stillVisible = resources.visibleProviders(visibleTools).toSet()
        val page = listing.subList(offset, minOf(offset + PAGE_SIZE, listing.size))
            .filter { (provider, _) -> provider in stillVisible }
            .map { it.second }
        return jsonRpcResponse(id, buildJsonObject {
            putJsonArray("resources") {
                page.forEach { r ->
                    addJsonObject {
                        put("uri", r.uri)
                        put("name", r.name)
                        r.title?.let { put("title", it) }
                        r.description?.let { put("description", it) }
                        r.mimeType?.let { put("mimeType", it) }
                        r.size?.let { put("size", it) }
                    }
                }
            }
            if (nextId != null) put("nextCursor", "$nextId:${offset + PAGE_SIZE}")
        })
    }

    private fun handleResourceTemplatesList(id: JsonElement?): String =
        jsonRpcResponse(id, buildJsonObject {
            putJsonArray("resourceTemplates") {
                resources.visibleProviders(visibleToolNames()).flatMap { it.templates }.forEach { t ->
                    addJsonObject {
                        put("uriTemplate", t.uriTemplate)
                        put("name", t.name)
                        t.title?.let { put("title", it) }
                        t.description?.let { put("description", it) }
                        t.mimeType?.let { put("mimeType", it) }
                    }
                }
            }
        })

    private suspend fun handleResourcesRead(id: JsonElement?, params: JsonObject, clientLabel: String?): String {
        val uri = (params["uri"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw InvalidParamsException("missing or non-string uri")
        val startedAt = System.nanoTime()
        val argumentsJson = buildJsonObject { put("uri", uri) }.toString()
        fun audit(result: ToolResult) =
            recordAudit(RESOURCES_READ, clientLabel, argumentsJson, result, (System.nanoTime() - startedAt) / 1_000_000)

        val provider = resources.providerFor(uri, visibleToolNames())
        // The same data through a tool would go through the host's confirmer, so a read does too.
        provider?.let { registry.confirmAccess(it.requiredTools, mapOf("uri" to uri), clientLabel) }?.let { declined ->
            audit(declined)
            return jsonRpcError(id, REQUEST_DECLINED, declined.errorMessage ?: "Resource read declined")
        }
        val outcome = provider?.let { onIo { it.read(uri) } }
        if (provider != null && outcome == null) {
            audit(ToolResult.error("resource_timeout", uri))
            return jsonRpcError(id, -32603, "Resource read timed out")
        }
        // A provider that throws broke its contract; treat it like an unreadable resource.
        val contents = outcome?.getOrNull()?.takeIf { it.isNotEmpty() }
        if (contents == null) {
            audit(ToolResult.error("resource_not_found", uri))
            throw ResourceNotFoundException(uri)
        }
        audit(ToolResult.success(mapOf("uri" to uri)))
        return jsonRpcResponse(id, buildJsonObject {
            putJsonArray("contents") {
                contents.forEach { c ->
                    addJsonObject {
                        put("uri", c.uri)
                        c.mimeType?.let { put("mimeType", it) }
                        when (c) {
                            is ResourceContents.Text -> put("text", c.text)
                            is ResourceContents.Blob -> put("blob", c.base64)
                        }
                    }
                }
            }
        })
    }

    private fun visiblePrompts(): List<McpPrompt> = prompts.visiblePrompts(visibleToolNames())

    private fun handlePromptsList(id: JsonElement?): String =
        jsonRpcResponse(id, buildJsonObject {
            putJsonArray("prompts") {
                visiblePrompts().forEach { prompt ->
                    addJsonObject {
                        put("name", prompt.name)
                        prompt.title?.let { put("title", it) }
                        put("description", prompt.description)
                        putJsonArray("arguments") {
                            prompt.arguments.forEach { arg ->
                                addJsonObject {
                                    put("name", arg.name)
                                    put("description", arg.description)
                                    put("required", arg.required)
                                }
                            }
                        }
                    }
                }
            }
        })

    private suspend fun handlePromptsGet(id: JsonElement?, params: JsonObject): String {
        val name = (params["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw InvalidParamsException("missing or non-string prompt name")
        val prompt = visiblePrompts().firstOrNull { it.name == name }
            ?: throw InvalidParamsException("unknown prompt '$name'")
        val arguments = when (val raw = params["arguments"]) {
            null, is JsonNull -> emptyMap()
            is JsonObject -> raw.mapValues { (key, value) ->
                (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw InvalidParamsException("argument '$key' must be a string")
            }
            else -> throw InvalidParamsException("arguments must be an object")
        }
        val missing = prompt.missingArguments(arguments)
        if (missing.isNotEmpty()) throw InvalidParamsException("missing required argument(s): ${missing.joinToString()}")
        val result = try {
            prompt.render(arguments)
        } catch (e: IllegalArgumentException) {
            throw InvalidParamsException(e.message ?: "invalid arguments")
        }
        return jsonRpcResponse(id, buildJsonObject {
            result.description?.let { put("description", it) }
            putJsonArray("messages") {
                result.messages.forEach { message ->
                    addJsonObject {
                        put("role", message.role.wireName)
                        putJsonObject("content") {
                            put("type", "text")
                            put("text", message.text)
                        }
                    }
                }
            }
        })
    }

    /**
     * `completion/complete` for a prompt argument (`ref/prompt`) or a resource-template variable
     * (`ref/resource`, whose `uri` is the template). An unknown prompt or template is `-32602`;
     * an unknown argument, or a completer that fails or outlasts [COMPLETION_TIMEOUT_MS], just
     * yields no values. Clients send one per keystroke, so `notifications/cancelled` stops a
     * superseded one like a `tools/call` (and it gets no response).
     */
    private suspend fun handleComplete(id: JsonElement?, params: JsonObject, clientLabel: String?, sessionId: String?): String {
        val ref = params["ref"] as? JsonObject ?: throw InvalidParamsException("missing ref")
        val argument = params["argument"] as? JsonObject ?: throw InvalidParamsException("missing argument")
        val argName = (argument["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw InvalidParamsException("missing or non-string argument name")
        val value = (argument["value"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        val filled = ((params["context"] as? JsonObject)?.get("arguments") as? JsonObject)
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content } }
            ?.toMap()
            .orEmpty()
        val context = CompletionContext(filled) { name, toolParams -> completionToolCall(name, toolParams, clientLabel) }

        val completer: suspend () -> List<String> = when ((ref["type"] as? JsonPrimitive)?.contentOrNull) {
            "ref/prompt" -> {
                val name = (ref["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw InvalidParamsException("missing or non-string prompt name")
                val prompt = visiblePrompts().firstOrNull { it.name == name }
                    ?: throw InvalidParamsException("unknown prompt '$name'")
                val run: suspend () -> List<String> = { prompt.complete(argName, value, context) }
                run
            }
            "ref/resource" -> {
                val template = (ref["uri"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw InvalidParamsException("missing or non-string uri")
                val provider = resources.visibleProviders(visibleToolNames())
                    .firstOrNull { p -> p.templates.any { it.uriTemplate == template } }
                    ?: throw InvalidParamsException("unknown resource template '$template'")
                val run: suspend () -> List<String> = { provider.complete(template, argName, value, context) }
                run
            }
            else -> throw InvalidParamsException("ref.type must be ref/prompt or ref/resource")
        }
        val key = (sessionId ?: clientLabel) to id.toString()
        val outcome = try {
            coroutineScope {
                val call = async { runProvider(minOf(registry.toolTimeoutMs, COMPLETION_TIMEOUT_MS), completer) }
                inFlight[key] = call
                try {
                    call.await()
                } finally {
                    inFlight.remove(key, call)
                }
            }
        } catch (e: CancellationException) {
            // Rethrow if this handler itself was cancelled; otherwise the client cancelled it.
            currentCoroutineContext().ensureActive()
            return ""
        }
        val values = outcome?.getOrNull().orEmpty().distinct()
        return jsonRpcResponse(id, buildJsonObject {
            putJsonObject("completion") {
                putJsonArray("values") { values.take(MAX_COMPLETIONS).forEach { add(it) } }
                put("total", values.size)
                put("hasMore", values.size > MAX_COMPLETIONS)
            }
        })
    }

    /** [CompletionContext.callTool]: only visible, read-only tools that need no confirmation. */
    private suspend fun completionToolCall(name: String, params: Map<String, Any>, clientLabel: String?): ToolResult? {
        val tool = visibleTools().firstOrNull { it.name == name } ?: return null
        if (!tool.annotations.readOnlyHint || registry.needsConfirmation(tool)) return null
        val startedAt = System.nanoTime()
        val argumentsJson = buildJsonObject {
            put("tool", name)
            put("arguments", ToolSchemas.toJsonElement(params))
        }.toString()
        val result = try {
            registry.executeTool(name, params, clientLabel)
        } catch (e: CancellationException) {
            // The tool may already have read data, so a cut-short completion is still audited.
            recordAudit(COMPLETION_COMPLETE, clientLabel, argumentsJson, ToolResult.error("cancelled", "completion cancelled"), (System.nanoTime() - startedAt) / 1_000_000)
            throw e
        }
        recordAudit(COMPLETION_COMPLETE, clientLabel, argumentsJson, result, (System.nanoTime() - startedAt) / 1_000_000)
        return result
    }

    /** Pagination cursor `<snapshot id>:<offset>`, opaque to clients; no cursor is the first page. */
    private fun parseCursor(params: JsonObject): Pair<String?, Int> {
        val cursor = params["cursor"]
        if (cursor == null || cursor is JsonNull) return null to 0
        val raw = (cursor as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw InvalidParamsException("invalid cursor")
        val id = raw.substringBeforeLast(':', "").takeIf { it.isNotEmpty() }
        val offset = raw.substringAfterLast(':').toIntOrNull()?.takeIf { it >= 0 }
            ?: throw InvalidParamsException("invalid cursor")
        return id to offset
    }

    /** Provider code, bounded like a tool call; see [runProvider]. */
    private suspend fun <T> onIo(block: suspend () -> T): Result<T>? = runProvider(registry.toolTimeoutMs, block)

    /** A `notifications/<kind>/list_changed` message for the transport to push. */
    internal fun listChangedNotification(kind: ListKind): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/${kind.wireName}/list_changed")
        })

    /** Which catalogue changed, for [listChangedNotification]. */
    internal enum class ListKind(val wireName: String) { TOOLS("tools"), RESOURCES("resources"), PROMPTS("prompts") }

    private suspend fun handleToolsCall(
        id: JsonElement?,
        params: JsonObject,
        clientLabel: String?,
        sessionId: String?,
        outbound: McpOutbound?,
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
        val key = (sessionId ?: clientLabel) to id.toString()
        val toolResult = try {
            coroutineScope {
                val call = async(callContext(params, sessionId, outbound)) {
                    registry.executeTool(toolName, arguments, clientLabel)
                }
                inFlight[key] = call
                try {
                    call.await()
                } finally {
                    inFlight.remove(key, call)
                }
            }
        } catch (e: CancellationException) {
            // Rethrow if this handler itself was cancelled; otherwise the client cancelled the call.
            currentCoroutineContext().ensureActive()
            val durationMs = (System.nanoTime() - startedAt) / 1_000_000
            recordAudit(toolName, clientLabel, argumentsJson, ToolResult.error("cancelled", "by client"), durationMs)
            // JSON-RPC: a cancelled request gets no response (the HTTP transport answers 202).
            return ""
        }
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000
        recordAudit(toolName, clientLabel, argumentsJson, toolResult, durationMs)

        return if (toolResult.isSuccess) {
            // Image payloads travel once, as image blocks, not also inside the JSON.
            val imageKeys = toolResult.images.mapNotNull { it.dataKey }.toSet()
            val structured = buildJsonObject {
                toolResult.data?.forEach { (k, v) -> if (k !in imageKeys) put(k, ToolSchemas.toJsonElement(v)) }
            }
            val content = buildJsonArray {
                addJsonObject {
                    put("type", "text")
                    put("text", Json.encodeToString(JsonObject.serializer(), structured))
                }
                toolResult.images.forEach { image ->
                    addJsonObject {
                        put("type", "image")
                        put("data", image.base64)
                        put("mimeType", image.mimeType)
                    }
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

    /** Progress and elicitation sinks for one call, when the transport can stream to the client. */
    private fun callContext(
        params: JsonObject,
        sessionId: String?,
        outbound: McpOutbound?,
    ): CoroutineContext {
        if (outbound == null) return EmptyCoroutineContext
        var context: CoroutineContext = EmptyCoroutineContext
        val progressToken = (params["_meta"] as? JsonObject)?.get("progressToken") as? JsonPrimitive
        if (progressToken != null) {
            context += ProgressSink { update -> outbound.send(progressNotification(progressToken, update)) }
        }
        if (sessionId != null && sessionId in elicitationSessions) {
            context += ElicitationSink { message, schema -> requestElicitation(outbound, sessionId, message, schema) }
        }
        return context
    }

    private fun progressNotification(token: JsonPrimitive, update: ProgressUpdate): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "notifications/progress")
            putJsonObject("params") {
                put("progressToken", token)
                put("progress", update.progress)
                update.total?.let { put("total", it) }
                update.message?.let { put("message", it) }
            }
        })

    private suspend fun requestElicitation(
        outbound: McpOutbound,
        owner: String,
        message: String,
        schema: Map<String, Any>,
    ): ElicitationResult {
        val requestId = "droidmcp-${UUID.randomUUID()}"
        val response = CompletableDeferred<JsonObject>()
        pending[requestId] = PendingRequest(owner, response)
        try {
            outbound.send(Json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", requestId)
                put("method", "elicitation/create")
                putJsonObject("params") {
                    put("message", message)
                    put("requestedSchema", ToolSchemas.toJsonElement(schema))
                }
            }))
            val result = response.await()["result"] as? JsonObject
                ?: return ElicitationResult(ElicitationResult.Action.CANCEL)
            val action = when ((result["action"] as? JsonPrimitive)?.contentOrNull) {
                "accept" -> ElicitationResult.Action.ACCEPT
                "decline" -> ElicitationResult.Action.DECLINE
                else -> ElicitationResult.Action.CANCEL
            }
            val content = (result["content"] as? JsonObject)
                ?.entries?.associate { (k, v) -> k to v.toNativeValue() }
                .orEmpty()
            return ElicitationResult(action, if (action == ElicitationResult.Action.ACCEPT) content else emptyMap())
        } finally {
            pending.remove(requestId)
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

    private fun jsonRpcError(id: JsonElement?, code: Int, message: String, data: JsonElement? = null): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id ?: JsonNull)
            putJsonObject("error") {
                put("code", code)
                put("message", message)
                data?.let { put("data", it) }
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

    /** Thrown when no provider can read a URI; mapped to MCP's `-32002` with the uri in `data`. */
    private class ResourceNotFoundException(val uri: String) : Exception("Resource not found: $uri")

    companion object {
        /**
         * MCP protocol revisions this server speaks, newest first. `initialize` echoes the
         * client's version when listed here and otherwise offers the first entry; the HTTP
         * transport rejects an `MCP-Protocol-Version` header outside this list.
         */
        val SUPPORTED_PROTOCOL_VERSIONS: List<String> =
            listOf("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05")

        /** Audit tool name recorded for `resources/read`. */
        const val RESOURCES_READ = "resources/read"

        /** Audit tool name recorded when a completion reads through a tool. */
        const val COMPLETION_COMPLETE = "completion/complete"

        /** Longest a `completion/complete` may run (capped by the tool timeout); it answers a keystroke. */
        const val COMPLETION_TIMEOUT_MS = 5_000L

        /** Most values one `completion/complete` reply carries (the MCP limit). */
        private const val MAX_COMPLETIONS = 100

        /** Server-defined JSON-RPC code for a read the host's confirmer declined. */
        private const val REQUEST_DECLINED = -32001

        /** Multi-page `resources/list` snapshots kept for follow-up pages. */
        private const val MAX_LIST_SNAPSHOTS = 16

        /** MCP's error code for `resources/read` on an unknown URI. */
        private const val RESOURCE_NOT_FOUND = -32002

        /** Items per `resources/list` page. */
        private const val PAGE_SIZE = 100
    }
}
