package io.droidmcp.notificationsreply

import android.content.Context
import android.content.pm.PackageManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.NotificationListenerHolder
import io.droidmcp.notification.NotificationStore
import io.droidmcp.notification.RepliableNotification

/**
 * Lists active notifications that expose a free-form `RemoteInput` reply action (WhatsApp,
 * Signal, Messenger, Slack, SMS, Gmail, etc.), read from
 * [NotificationStore.repliableSnapshot][io.droidmcp.notification.NotificationStore.repliableSnapshot].
 * Requires the listener service bound and notification listener access granted, else an error
 * naming which precondition failed. Results are sorted most-recent-first and capped by `limit`
 * (default 20, clamped 1-100). Each entry includes the app's display label (resolved via
 * `PackageManager`, cached per call) alongside the notification's key, title, text, post time,
 * and reply-action label/hint — everything [ReplyToNotificationTool] needs.
 */
class ListRepliableNotificationsTool(private val context: Context) : McpTool {

    override val name = "list_repliable_notifications"
    override val description = "List active notifications that expose a free-form RemoteInput reply action (WhatsApp, Signal, Messenger, Slack, SMS, Gmail, etc.). Requires notification listener access."
    override val parameters = listOf(
        ToolParameter("limit", "Maximum notifications to return (1-100, default 20).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (NotificationListenerHolder.componentName == null) {
            return ToolResult.error("NotificationListenerService not configured. The host app must call NotificationListenerHolder.set() with its listener ComponentName.")
        }
        if (!NotificationsReplyTools.isNotificationListenerEnabled(context)) {
            return ToolResult.error("Notification listener access not granted. Enable it in Settings > Apps > Special access > Notification access.")
        }

        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

        val pm = context.packageManager
        val labelCache = mutableMapOf<String, String?>()
        fun labelFor(pkg: String): String? = labelCache.getOrPut(pkg) {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }

        val items = NotificationStore.repliableSnapshot()
            .sortedByDescending { it.postedAt }
            .take(limit)
            .map { it.toMap(labelFor(it.packageName)) }

        return ToolResult.success(mapOf(
            "count" to items.size,
            "notifications" to items,
        ))
    }

    private fun RepliableNotification.toMap(appLabel: String?): Map<String, Any?> = mapOf(
        "key" to key,
        "package_name" to packageName,
        "app_label" to appLabel,
        "title" to title,
        "text" to text,
        "posted_at" to postedAt,
        "action_label" to replyAction?.label,
        "hint_label" to replyAction?.hintLabel,
    )
}
