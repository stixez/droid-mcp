package io.droidmcp.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe collection of registered [McpTool]s, keyed by [McpTool.name]. Backed by a
 * [ConcurrentHashMap] so tools can be registered and invoked concurrently. Also tracks a
 * host-controlled runtime gate (see [setDisabledTools]) that hides/blocks tools without
 * unregistering them.
 *
 * This is the single source of truth the transports and [McpProtocol][io.droidmcp.core.protocol.McpProtocol]
 * read from for `tools/list` and `tools/call`.
 *
 * @property toolTimeoutMs Upper bound on a single [executeTool] call; a tool still running
 *   after this is cancelled and reported as a `tool_timeout` error. Must be positive. The same
 *   bound applies to waiting for [confirmer].
 * @property confirmer Optional host approval step run before tools matching
 *   [requiresConfirmation]; a decline, timeout or exception returns `tool_call_declined`.
 * @property requiresConfirmation Which tools go through [confirmer]; defaults to those with
 *   [ToolAnnotations.destructiveHint].
 */
class ToolRegistry(
    val toolTimeoutMs: Long = DEFAULT_TOOL_TIMEOUT_MS,
    private val confirmer: ToolCallConfirmer? = null,
    private val requiresConfirmation: (McpTool) -> Boolean = { it.annotations.destructiveHint },
) {

    init {
        require(toolTimeoutMs > 0) { "toolTimeoutMs must be positive" }
    }

    private val tools = ConcurrentHashMap<String, McpTool>()

    /**
     * Names the host has gated off at runtime. A disabled tool is hidden from
     * [listEnabledTools] (and therefore `tools/list`) and rejected by
     * [executeTool] (and therefore `tools/call`) without being unregistered, so
     * it can be toggled back on without rebuilding the registry.
     *
     * Held as an immutable set behind a `@Volatile` reference: reads on the
     * request path are lock-free and always see a consistent snapshot, and
     * writes swap the whole reference atomically (so a gate edit can never
     * expose a half-applied set to an in-flight call).
     */
    @Volatile
    private var disabled: Set<String> = emptySet()

    /** Register [tool], replacing any existing tool with the same [McpTool.name]. */
    fun register(tool: McpTool) {
        tools[tool.name] = tool
    }

    /** Register every tool in [toolList] (e.g. the output of a module provider's `all(context)`). */
    fun registerAll(toolList: List<McpTool>) {
        toolList.forEach { register(it) }
    }

    /** The registered tool with this [name], or null if none is registered. */
    fun getTool(name: String): McpTool? = tools[name]

    /** All registered tools, regardless of gate state. See [listEnabledTools] for the client-visible set. */
    fun listTools(): List<McpTool> = tools.values.toList()

    /** Tools currently visible to clients — registered and not gated off. */
    fun listEnabledTools(): List<McpTool> = tools.values.filter { it.name !in disabled }

    /** Whether the named tool is currently enabled (registered tools are enabled by default). */
    fun isEnabled(name: String): Boolean = name !in disabled

    /** Names currently gated off. */
    fun disabledTools(): Set<String> = disabled

    /**
     * Enable or disable a single tool by name. `@Synchronized` so concurrent
     * single-tool toggles don't lose updates in the copy-on-write swap.
     */
    @Synchronized
    fun setToolEnabled(name: String, enabled: Boolean) {
        disabled = if (enabled) disabled - name else disabled + name
    }

    /**
     * Replace the entire disabled set in one atomic swap (e.g. from a checkbox
     * grid). `@Synchronized` on the same monitor as [setToolEnabled] so a full
     * replace can't interleave with a single-tool toggle and lose its update.
     */
    @Synchronized
    fun setDisabledTools(names: Set<String>) {
        disabled = names.toSet()
    }

    /**
     * Execute the named tool with [params] on [Dispatchers.IO], bounded by [toolTimeoutMs].
     * Returns a `tool_disabled` error if the tool is gated off, an `Unknown tool` error if it
     * isn't registered, a `tool_timeout` error if it overran, and converts any exception thrown
     * by [McpTool.execute] into a failed [ToolResult]. Coroutine cancellation of the *caller* is
     * rethrown rather than swallowed, so a cancelled request stops its tool.
     *
     * When a [confirmer] is set and [requiresConfirmation] matches, the host is asked first; the
     * tool runs only on approval, otherwise the result is a `tool_call_declined` error.
     *
     * @param clientLabel The HTTP client making the call (passed to the [confirmer]); null in-process.
     */
    suspend fun executeTool(name: String, params: Map<String, Any>, clientLabel: String? = null): ToolResult {
        if (name in disabled) {
            return ToolResult.error("tool_disabled", "Tool '$name' is disabled by the host")
        }
        val tool = tools[name]
            ?: return ToolResult.error("Unknown tool: $name")
        if (confirmer != null && requiresConfirmation(tool)) {
            confirm(confirmer, ToolCallRequest(name, params, tool.annotations, clientLabel))?.let { return it }
        }
        return try {
            // withTimeout inside withContext(IO) so the deadline runs on a real-time clock even
            // when the caller sits on a virtual-time test dispatcher.
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(toolTimeoutMs) { tool.execute(params) }
            } ?: ToolResult.error("tool_timeout", "Tool '$name' timed out after ${toolTimeoutMs}ms")
        } catch (e: CancellationException) {
            // Rethrow when the caller itself was cancelled; otherwise the tool leaked its own
            // internal cancellation (e.g. an escaped TimeoutCancellationException) — report it.
            currentCoroutineContext().ensureActive()
            ToolResult.error("Tool '$name' failed: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error("Tool '$name' failed: ${e.message}")
        }
    }

    /** Returns null when approved, or the `tool_call_declined` result to send back. */
    private suspend fun confirm(confirmer: ToolCallConfirmer, request: ToolCallRequest): ToolResult? {
        val approved = try {
            withTimeoutOrNull(toolTimeoutMs) { confirmer.confirm(request) }
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Exception) {
            false
        }
        return when (approved) {
            true -> null
            false -> ToolResult.error("tool_call_declined", "The user declined '${request.toolName}'")
            null -> ToolResult.error("tool_call_declined", "No confirmation for '${request.toolName}' within ${toolTimeoutMs}ms")
        }
    }

    companion object {
        /** Default per-call ceiling: generous enough for TTS playback and camera capture. */
        const val DEFAULT_TOOL_TIMEOUT_MS: Long = 5 * 60 * 1000L
    }
}
