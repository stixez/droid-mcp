package io.droidmcp.notificationsreply

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.NotificationListenerHolder
import io.droidmcp.notification.NotificationStore

class ReplyToNotificationTool(private val context: Context) : McpTool {

    override val name = "reply_to_notification"
    override val description = "Send a reply via a notification's RemoteInput action. Use list_repliable_notifications to find the key. Returns success when the PendingIntent fires; does not confirm receiving-app delivery."
    override val parameters = listOf(
        ToolParameter("key", "Notification key from list_repliable_notifications.", ParameterType.STRING, required = true),
        ToolParameter("text", "Reply text to send.", ParameterType.STRING, required = true),
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
        val text = params["text"]?.toString()
            ?: return ToolResult.error("text is required")

        val repliable = NotificationStore.findRepliable(key)
            ?: return ToolResult.error("No repliable notification found for key '$key'.")
        val replyAction = repliable.replyAction
            ?: return ToolResult.error("Notification has no RemoteInput action.")

        // Use the action's original RemoteInput array verbatim so we don't drop
        // allowedDataTypes or other receiver-side metadata; only the result
        // bundle is ours.
        val fillIn = Intent().apply {
            val bundle = Bundle().apply { putCharSequence(replyAction.resultKey, text) }
            RemoteInput.addResultsToIntent(replyAction.remoteInputs, this, bundle)
        }

        return try {
            replyAction.pendingIntent.send(context, 0, fillIn)
            ToolResult.success(mapOf(
                "success" to true,
                "package_name" to repliable.packageName,
                "action_label" to replyAction.label,
            ))
        } catch (e: PendingIntent.CanceledException) {
            ToolResult.error("PendingIntent.send failed: ${e.message ?: "canceled"}")
        }
    }
}
