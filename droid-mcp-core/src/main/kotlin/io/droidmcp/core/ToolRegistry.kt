package io.droidmcp.core

import java.util.concurrent.ConcurrentHashMap

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

    fun register(tool: McpTool) {
        tools[tool.name] = tool
    }

    fun registerAll(toolList: List<McpTool>) {
        toolList.forEach { register(it) }
    }

    fun getTool(name: String): McpTool? = tools[name]

    fun listTools(): List<McpTool> = tools.values.toList()

    /** Tools currently visible to clients — registered and not gated off. */
    fun listEnabledTools(): List<McpTool> = tools.values.filter { it.name !in disabled }

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
