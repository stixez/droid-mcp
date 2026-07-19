package io.droidmcp.notificationwatch

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Removes a watch by id via [WatchRegistry.unregister]. Idempotent — removing an id that
 * doesn't exist (already expired, already removed, or never valid) returns success with
 * `removed=false` rather than an error, so callers don't need to check existence first. Output:
 * the echoed `watch_id` and `removed`.
 */
class UnwatchNotificationsTool(private val context: Context) : McpTool {

    override val name = "unwatch_notifications"
    override val description = "Remove a notification watch by id. Idempotent — removing a non-existent id returns success with removed=false rather than an error."
    override val parameters = listOf(
        ToolParameter("watch_id", "Watch id returned by watch_notifications.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val id = params["watch_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("invalid_selector", "watch_id is required")
        val removed = WatchRegistry.unregister(id)
        return ToolResult.success(mapOf(
            "watch_id" to id,
            "removed" to removed,
        ))
    }
}
