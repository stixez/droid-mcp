package io.droidmcp.notificationwatch

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.NotificationListenerHolder

class WatchNotificationsTool(private val context: Context) : McpTool {

    override val name = "watch_notifications"
    override val description = "Register a filter against the live notification stream. Returns a watch_id. At least one of package_name / sender_pattern / keyword is required; multiple fields AND-combine. Matching is case-insensitive substring. Fire-once-per-key by default — set fire_on_update=true to fire again on subsequent updates of the same notification."
    override val parameters = listOf(
        ToolParameter("package_name", "Filter to a specific app's notifications (exact match against the source package name).", ParameterType.STRING, required = false),
        ToolParameter("sender_pattern", "Case-insensitive substring match against the notification title.", ParameterType.STRING, required = false),
        ToolParameter("keyword", "Case-insensitive substring match against text / bigText / subText / tickerText.", ParameterType.STRING, required = false),
        ToolParameter("ttl_seconds", "Watch lifetime in seconds (60-86400, default 3600). Watch is auto-removed when it expires.", ParameterType.INTEGER, required = false),
        ToolParameter("fire_on_update", "When true, fire again on every update of the same notification key. Default false.", ParameterType.BOOLEAN, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (NotificationListenerHolder.componentName == null) {
            return ToolResult.error("notification_listener_not_enabled", null)
        }
        if (!NotificationWatchTools.isNotificationListenerEnabled(context)) {
            return ToolResult.error("notification_listener_not_enabled", null)
        }

        val pkg = (params["package_name"] as? String)?.takeIf { it.isNotBlank() }
        val sender = (params["sender_pattern"] as? String)?.takeIf { it.isNotBlank() }
        val keyword = (params["keyword"] as? String)?.takeIf { it.isNotBlank() }
        if (pkg == null && sender == null && keyword == null) {
            return ToolResult.error("invalid_filter", "at least one of package_name, sender_pattern, keyword is required")
        }

        val ttl = (params["ttl_seconds"] as? Number)?.toInt()?.coerceIn(60, 86_400) ?: 3600
        val fireOnUpdate = (params["fire_on_update"] as? Boolean) ?: false

        val spec = WatchSpec(
            id = WatchRegistry.newWatchId(),
            packageName = pkg,
            senderPattern = sender,
            keyword = keyword,
            ttlSeconds = ttl,
            fireOnUpdate = fireOnUpdate,
            createdAt = System.currentTimeMillis(),
        )
        WatchRegistry.register(spec)
        // ensureCollecting is called inside register; explicit here for clarity is unnecessary.

        return ToolResult.success(mapOf(
            "watch_id" to spec.id,
            "expires_at" to spec.expiresAt,
            "ttl_seconds" to ttl,
            "fire_on_update" to fireOnUpdate,
        ))
    }
}
