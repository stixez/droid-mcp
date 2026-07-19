package io.droidmcp.notificationwatch

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Lists currently-active watches from [WatchRegistry.list], with each entry's TTL countdown
 * (`expires_in_seconds`, floored at 0) and how many times it has fired so far. Expired watches
 * are swept from the registry before the list is built, so this never returns a watch whose
 * TTL has already elapsed. No preconditions — always succeeds, even with an empty registry.
 */
class ListNotificationWatchesTool(private val context: Context) : McpTool {

    override val name = "list_notification_watches"
    override val description = "List currently-active notification watches with TTL countdown. Expired watches are swept before the list is returned."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val now = System.currentTimeMillis()
        val watches = WatchRegistry.list().map { spec ->
            mapOf(
                "watch_id" to spec.id,
                "package_name" to spec.packageName,
                "sender_pattern" to spec.senderPattern,
                "keyword" to spec.keyword,
                "fire_on_update" to spec.fireOnUpdate,
                "fired_count" to WatchRegistry.firedCount(spec.id),
                "expires_at" to spec.expiresAt,
                "expires_in_seconds" to ((spec.expiresAt - now) / 1000L).coerceAtLeast(0L),
            )
        }
        return ToolResult.success(mapOf(
            "count" to watches.size,
            "watches" to watches,
        ))
    }
}
