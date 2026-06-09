package io.droidmcp.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe collection of registered [McpTool]s, keyed by [McpTool.name]. Backed by a
 * [ConcurrentHashMap] so tools can be registered and invoked concurrently. Also tracks a
 * host-controlled runtime gate (see [setDisabledTools]) that hides/blocks tools without
 * unregistering them.
 *
 * This is the single source of truth the transports and [McpProtocol][io.droidmcp.core.protocol.McpProtocol]
 * read from for `tools/list` and `tools/call`.
 */
class ToolRegistry {

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
     * Execute the named tool with [params]. Returns a `tool_disabled` error if the tool is
     * gated off, an `Unknown tool` error if it isn't registered, and converts any exception
     * thrown by [McpTool.execute] into a failed [ToolResult] — this method never throws.
     */
    suspend fun executeTool(name: String, params: Map<String, Any>): ToolResult {
        if (name in disabled) {
            return ToolResult.error("tool_disabled", "Tool '$name' is disabled by the host")
        }
        val tool = tools[name]
            ?: return ToolResult.error("Unknown tool: $name")
        return try {
            tool.execute(params)
        } catch (e: Exception) {
            ToolResult.error("Tool '$name' failed: ${e.message}")
        }
    }
}
