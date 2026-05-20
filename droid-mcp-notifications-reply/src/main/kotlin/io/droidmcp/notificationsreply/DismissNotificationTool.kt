package io.droidmcp.notificationsreply

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.McpNotificationListenerServiceBase
import io.droidmcp.notification.NotificationListenerHolder
import io.droidmcp.notification.NotificationStore

class DismissNotificationTool(private val context: Context) : McpTool {

    override val name = "dismiss_notification"
    override val description = "Cancel a notification by key. Requires notification listener access. Source apps may immediately repost identical notifications."
    override val parameters = listOf(
        ToolParameter("key", "Notification key from list_repliable_notifications.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (NotificationListenerHolder.componentName == null) {
            return ToolResult.error("NotificationListenerService not configured. The host app must call NotificationListenerHolder.set() with its listener ComponentName.")
        }
        if (!NotificationsReplyTools.isNotificationListenerEnabled(context)) {
            return ToolResult.error("Notification listener access not granted. Enable it in Settings > Apps > Special access > Notification access.")
        }

        val key = params["key"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("key is required")

        if (!NotificationStore.containsKey(key)) {
            return ToolResult.error("No active notification found for key '$key'.")
        }

        val dispatched = McpNotificationListenerServiceBase.cancelByKey(key)
        return if (dispatched) {
            ToolResult.success(mapOf("success" to true, "key" to key))
        } else {
            ToolResult.error("Listener service not bound. Host app must extend McpNotificationListenerServiceBase and the listener must be connected. If access was just granted, toggle Notification access off and on for this app in Settings to re-bind.")
        }
    }
}
