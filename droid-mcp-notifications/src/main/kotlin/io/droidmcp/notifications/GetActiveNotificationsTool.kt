package io.droidmcp.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reads the host app's currently active (status-bar) notifications via [NotificationManager.getActiveNotifications].
 *
 * No permissions required. KNOWN LIMITATION: this only returns notifications posted by the MCP
 * host app itself. Cross-app access needs a `NotificationListenerService` — use the
 * `droid-mcp-notifications-reply` / `droid-mcp-notification-watch` modules for that.
 *
 * Result keys: `notifications` (list of maps with `id`, `tag`, `package_name`, `title`, `text`,
 * `timestamp`, `is_ongoing`, `is_foreground_service`), `count`, and a `note` describing the limitation.
 */
class GetActiveNotificationsTool(private val context: Context) : McpTool {

    override val name = "get_active_notifications"
    override val description = """
        Read currently active (visible in the status bar) notifications.
        LIMITATION: This tool only reads notifications posted by this app itself.
        To read other apps' notifications, use list_repliable_notifications or watch_notifications
        if they are registered (they need notification access granted in Settings).
    """.trimIndent()
    override val parameters = listOf(
        ToolParameter("limit", "Max number of notifications to return. Default 10.", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return ToolResult.error("get_active_notifications requires Android 6.0 (API 23) or higher")
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val activeNotifications = nm.activeNotifications ?: emptyArray()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val notifications = activeNotifications
            .take(limit)
            .map { sbn ->
                val notification = sbn.notification
                val extras = notification.extras
                mapOf(
                    "id" to sbn.id,
                    "tag" to sbn.tag,
                    "package_name" to sbn.packageName,
                    "title" to extras.getCharSequence("android.title")?.toString(),
                    "text" to extras.getCharSequence("android.text")?.toString(),
                    "timestamp" to dateFormat.format(Date(sbn.postTime)),
                    "is_ongoing" to (notification.flags and Notification.FLAG_ONGOING_EVENT != 0),
                    "is_foreground_service" to (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0),
                )
            }

        return ToolResult.success(mapOf(
            "notifications" to notifications,
            "count" to notifications.size,
            "note" to "Only shows notifications posted by this app. Full cross-app notification access requires NotificationListenerService setup.",
        ))
    }
}
