package io.droidmcp.core

/**
 * One audited `tools/call`. Captured by [McpProtocolImpl][io.droidmcp.core.protocol.McpProtocolImpl]
 * after the tool runs and handed to the host's [AuditSink].
 *
 * @property timestamp wall-clock time the call completed (epoch millis)
 * @property toolName the invoked tool
 * @property clientLabel which bearer credential made the call
 *   (`primary` / a paired-client label / `anonymous` on an open server), or
 *   `null` for transports that don't authenticate (in-process)
 * @property argumentsJson the raw `arguments` object as sent, or `null` if none
 * @property success whether the tool reported success
 * @property errorMessage the failure envelope when [success] is false
 * @property durationMs how long [execute][McpTool.execute] took
 */
data class ToolCallAudit(
    val timestamp: Long,
    val toolName: String,
    val clientLabel: String?,
    val argumentsJson: String?,
    val success: Boolean,
    val errorMessage: String?,
    val durationMs: Long,
)

/**
 * Host-supplied sink for `tools/call` audit records. Implementations must be
 * cheap and non-throwing on the calling path — the protocol invokes [record]
 * synchronously after each call and swallows exceptions so a broken audit
 * backend never fails a tool. The Room-backed implementation lives in the
 * opt-in `droid-mcp-audit` module; hosts that don't depend on it pay nothing.
 */
fun interface AuditSink {
    fun record(entry: ToolCallAudit)

    companion object {
        /** No-op sink. The default when the host wires no audit backend. */
        val NONE: AuditSink = AuditSink { }
    }
}
