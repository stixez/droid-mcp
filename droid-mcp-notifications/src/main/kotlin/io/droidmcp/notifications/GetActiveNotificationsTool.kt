package io.droidmcp.notifications

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class GetActiveNotificationsTool(private val context: Context) : McpTool {

    override val name = "get_active_notifications"
    override val description = """
        Read currently active (visible in the status bar) notifications.
        LIMITATION: This tool only reads notifications posted by this app itself.
        To read notifications from all apps, the app must be set up as a NotificationListenerService
        and the user must grant notification access in Settings > Apps > Special app access > Notification access.
        That integration is not included in this build — this tool is useful for testing and inspecting
        notifications the MCP host app has itself posted.
    """.trimIndent()
    override val parameters = listOf(
        ToolParameter("limit", "Max number of notifications to return. Default 20.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

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
                    "is_ongoing" to (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0),
                    "is_foreground_service" to (notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0),
                )
            }

        return ToolResult.success(mapOf(
            "notifications" to notifications,
            "count" to notifications.size,
            "note" to "Only shows notifications posted by this app. Full cross-app notification access requires NotificationListenerService setup.",
        ))
    }
}
