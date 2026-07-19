package io.droidmcp.notificationsreply

import android.app.PendingIntent
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.NotificationListenerHolder
import io.droidmcp.notification.NotificationStore

/**
 * Fires a notification's non-reply `Notification.Action` `PendingIntent` (e.g. Mark as read,
 * Snooze, Archive) so the LLM doesn't need per-app intent shims. Requires the listener service
 * bound and notification listener access granted. Exactly one of `action_label` (case-
 * insensitive substring match on the action title) or `action_index` must be supplied —
 * supplying both or neither is `conflicting_args`. Looks the notification up by key in
 * [NotificationStore]; a notification with no actions, or no match for the given
 * label/index, returns `action_not_found`. Output on success: `success` (true), the echoed
 * `key`, the resolved `action_label`, and `package_name`.
 */
class InvokeNotificationActionTool(private val context: Context) : McpTool {

    override val name = "invoke_notification_action"
    override val description = "Trigger a non-reply action on an active notification (e.g. Mark as read, Snooze, Archive) without writing per-app PendingIntent shims. Provide exactly one of action_label or action_index. Returns the resolved action label on success."
    override val parameters = listOf(
        ToolParameter("key", "Notification key from list_repliable_notifications or notification-watch event.", ParameterType.STRING, required = true),
        ToolParameter("action_label", "Case-insensitive substring match against the action title.", ParameterType.STRING, required = false),
        ToolParameter("action_index", "Zero-indexed action position.", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (NotificationListenerHolder.componentName == null) {
            return ToolResult.error("notification_listener_not_enabled", null)
        }
        if (!NotificationsReplyTools.isNotificationListenerEnabled(context)) {
            return ToolResult.error("notification_listener_not_enabled", null)
        }

        val key = params["key"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("invalid_selector", "key is required")

        val label = (params["action_label"] as? String)?.takeIf { it.isNotBlank() }
        val index = (params["action_index"] as? Number)?.toInt()
        when {
            label != null && index != null ->
                return ToolResult.error("conflicting_args", "provide exactly one of action_label or action_index")
            label == null && index == null ->
                return ToolResult.error("conflicting_args", "provide exactly one of action_label or action_index")
        }

        val sbn = NotificationStore.findByKey(key)
            ?: return ToolResult.error("notification_not_found", null)
        val actions = sbn.notification?.actions
        if (actions.isNullOrEmpty()) return ToolResult.error("action_not_found", "notification has no actions")

        val action = when {
            index != null -> actions.getOrNull(index)
                ?: return ToolResult.error("action_not_found", "index $index out of range (0..${actions.lastIndex})")
            label != null -> actions.firstOrNull {
                it.title?.toString()?.contains(label, ignoreCase = true) == true
            } ?: return ToolResult.error("action_not_found", "no action title matches '$label'")
            else -> return ToolResult.error("conflicting_args", "unreachable")
        }

        val actionLabel = action.title?.toString()
        return try {
            action.actionIntent.send()
            ToolResult.success(mapOf(
                "success" to true,
                "key" to key,
                "action_label" to actionLabel,
                "package_name" to sbn.packageName,
            ))
        } catch (e: PendingIntent.CanceledException) {
            ToolResult.error("action_not_invokable", e.message ?: "PendingIntent.send canceled")
        }
    }
}
