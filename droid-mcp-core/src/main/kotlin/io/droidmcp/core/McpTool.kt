package io.droidmcp.core

/**
 * Behavioural hints attached to a tool, mirroring the MCP `tools/list` annotation
 * fields. They are advisory metadata for clients and LLMs — the SDK does not enforce
 * them — but the HTTP transport's `readOnly` server mode uses [readOnlyHint] to decide
 * which tools to expose.
 *
 * @property readOnlyHint The tool only observes device state; it never mutates anything.
 *   Read/query/get/list/find/wait tools set this. Required for a tool to survive
 *   `readOnly = true` server mode.
 * @property destructiveHint The tool changes device or app state (click, type, send,
 *   install, force-stop, write a setting, …). Mutually exclusive in spirit with
 *   [readOnlyHint].
 * @property idempotentHint Running the tool repeatedly with the same arguments leaves the
 *   device in the same final state (e.g. `enable_app`, `put_secure_setting`). Safe to retry.
 * @property openWorldHint The tool reaches beyond the local device (e.g. a network call).
 *   Not relevant for most droid-mcp tools, which touch only the local device.
 * @property title Optional human-friendly display name; falls back to [McpTool.name] when null.
 */
data class ToolAnnotations(
    val readOnlyHint: Boolean = false,
    val destructiveHint: Boolean = false,
    val idempotentHint: Boolean = false,
    val openWorldHint: Boolean = false,
    val title: String? = null,
)

/**
 * The contract every droid-mcp tool implements. A tool is a single, self-describing
 * capability — its [name], [description], and [parameters] are surfaced verbatim through
 * MCP `tools/list`, and [execute] services a `tools/call`.
 *
 * Tools are collected by a module's provider object (e.g. `CalendarTools.all(context)`)
 * and registered into a [ToolRegistry]. They are invoked off the main thread (on
 * `Dispatchers.IO`) and must degrade gracefully — when a required permission is missing
 * or the device lacks a capability, return [ToolResult.error] rather than throwing.
 *
 * The wire contract is the [name], the [parameters] (names + types), and the keys of the
 * map returned in [ToolResult.data]. These are frozen at 1.0; treat renames as breaking.
 */
interface McpTool {
    /** Stable, unique tool identifier in `snake_case` (e.g. `read_calendar`). Part of the wire contract. */
    val name: String

    /** One-line, human- and LLM-readable summary of what the tool does. Surfaced in `tools/list`. */
    val description: String

    /** Declared input parameters, rendered into the tool's JSON Schema `inputSchema`. */
    val parameters: List<ToolParameter>

    /** Behavioural hints for clients; defaults to all-false ([ToolAnnotations] with no hints set). */
    val annotations: ToolAnnotations get() = ToolAnnotations()

    /**
     * Run the tool. Called on a background dispatcher in response to a `tools/call`.
     *
     * @param params Caller-supplied arguments keyed by [ToolParameter.name]. Values arrive
     *   loosely typed (from JSON); coerce defensively and clamp numeric ranges.
     * @return [ToolResult.success] with a result map, or [ToolResult.error] on failure or
     *   missing permission — never throw for an expected failure mode.
     */
    suspend fun execute(params: Map<String, Any>): ToolResult
}
