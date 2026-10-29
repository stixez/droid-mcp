package io.droidmcp.notificationsreply

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.NotificationEvent
import io.droidmcp.notification.NotificationListenerHolder
import io.droidmcp.notification.NotificationStore

/**
 * Lists every active notification from any app, read from
 * [NotificationStore.eventSnapshot][io.droidmcp.notification.NotificationStore.eventSnapshot].
 * Requires the listener service bound and notification listener access granted, else an error
 * naming which precondition failed. Results are sorted most-recent-first, optionally filtered to
 * one `package_name`, and capped by `limit` (default 20, clamped 1-100). Ongoing notifications
 * (media players, foreground services) are left out unless `include_ongoing` is true. `text`
 * prefers the expanded body (`EXTRA_BIG_TEXT`), where messaging apps put the full message.
 */
class ListNotificationsTool(private val context: Context) : McpTool {

    override val name = "list_notifications"
    override val description = "List the notifications currently showing from all apps, newest first. Returns each one's key, app, title, text and action labels; use list_repliable_notifications for just the ones you can reply to."
    override val parameters = listOf(
        ToolParameter("limit", "Maximum notifications to return (1-100, default 20).", ParameterType.INTEGER, required = false, minimum = 1.0, maximum = 100.0),
        ToolParameter("package_name", "Only return notifications from this app, e.g. com.whatsapp.", ParameterType.STRING, required = false),
        ToolParameter("include_ongoing", "Include ongoing notifications such as media players and running services. Default false.", ParameterType.BOOLEAN, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        integer("count")
        array("notifications") {
            string("key")
            string("package_name")
            string("app_label", nullable = true)
            string("title", nullable = true)
            string("text", nullable = true)
            string("category", nullable = true)
            integer("posted_at", "Epoch millis")
            boolean("is_ongoing")
            boolean("has_reply_action")
            arrayOf("action_labels", OutputSchema.string())
        }
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (NotificationListenerHolder.componentName == null) {
            return ToolResult.error("NotificationListenerService not configured. The host app must call NotificationListenerHolder.set() with its listener ComponentName.")
        }
        if (!NotificationsReplyTools.isNotificationListenerEnabled(context)) {
            return ToolResult.error("Notification listener access not granted. Enable it in Settings > Apps > Special access > Notification access.")
        }

        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
        val packageName = params["package_name"]?.toString()?.takeIf { it.isNotBlank() }
        val includeOngoing = params["include_ongoing"] as? Boolean ?: false

        val pm = context.packageManager
        val labelCache = mutableMapOf<String, String?>()
        fun labelFor(pkg: String): String? = labelCache.getOrPut(pkg) {
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        }

        val items = select(NotificationStore.eventSnapshot(), limit, packageName, includeOngoing)
            .map { it.toMap(labelFor(it.packageName)) }

        return ToolResult.success(mapOf(
            "count" to items.size,
            "notifications" to items,
        ))
    }

    private fun NotificationEvent.toMap(appLabel: String?): Map<String, Any?> = mapOf(
        "key" to key,
        "package_name" to packageName,
        "app_label" to appLabel,
        "title" to title,
        "text" to (bigText?.takeIf { it.isNotBlank() } ?: text),
        "category" to category,
        "posted_at" to postedAt,
        "is_ongoing" to isOngoing,
        "has_reply_action" to hasReplyAction,
        "action_labels" to actionLabels,
    )

    internal companion object {
        fun select(
            events: List<NotificationEvent>,
            limit: Int,
            packageName: String?,
            includeOngoing: Boolean,
        ): List<NotificationEvent> = events
            .filter { packageName == null || it.packageName == packageName }
            .filter { includeOngoing || !it.isOngoing }
            .sortedByDescending { it.postedAt }
            .take(limit)
    }
}
