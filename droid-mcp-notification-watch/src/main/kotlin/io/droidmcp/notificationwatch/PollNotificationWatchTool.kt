package io.droidmcp.notificationwatch

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.NotificationEvent

/**
 * Returns the notifications a watch has matched since it was registered (or since the last
 * clearing poll), via [WatchRegistry.poll]. Each watch buffers at most
 * [WatchRegistry.MAX_BUFFERED_EVENTS] events; older ones are dropped and counted in `dropped`.
 * `clear` (default `true`) drains the buffer so the next poll only sees newer events; pass
 * `false` to peek. An unknown or expired `watch_id` returns `watch_not_found`.
 *
 * Output: `watch_id`, `count`, `dropped`, `expires_at`, and `events` — each a map with `key`,
 * `package_name`, `title`, `text`, `big_text`, `sub_text`, `ticker_text`, `category`,
 * `channel_id`, `group_key`, `is_ongoing`, `is_clearable`, `channel_importance`, `posted_at`,
 * `when`, `has_reply_action`, `action_labels`. The `key` works with the notifications-reply
 * tools (`reply_to_notification`, `invoke_notification_action`, `dismiss_notification`).
 */
class PollNotificationWatchTool(private val context: Context) : McpTool {

    override val name = "poll_notification_watch"
    override val description = "Return the notifications matched by a watch (from watch_notifications) since registration or the last clearing poll, oldest first. clear=true (default) drains the buffer; clear=false peeks. Up to ${WatchRegistry.MAX_BUFFERED_EVENTS} events are kept per watch; overflow drops the oldest and is reported in `dropped`."
    override val parameters = listOf(
        ToolParameter("watch_id", "Watch id returned by watch_notifications.", ParameterType.STRING, required = true),
        ToolParameter("clear", "Drain the buffer after reading. Default true.", ParameterType.BOOLEAN, required = false),
    )
    override val annotations = ToolAnnotations()
    override val outputSchema = OutputSchema.obj {
        string("watch_id")
        integer("count")
        integer("dropped", "Events lost because the buffer was full")
        integer("expires_at", "Epoch millis")
        array("events") {
            string("key")
            string("package_name")
            string("title", nullable = true)
            string("text", nullable = true)
            string("big_text", nullable = true)
            string("sub_text", nullable = true)
            string("ticker_text", nullable = true)
            string("category", nullable = true)
            string("channel_id", nullable = true)
            string("group_key", nullable = true)
            boolean("is_ongoing")
            boolean("is_clearable")
            integer("channel_importance")
            integer("posted_at", "Epoch millis")
            integer("when", "Epoch millis")
            boolean("has_reply_action")
            arrayOf("action_labels", OutputSchema.string())
        }
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val id = params["watch_id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("invalid_selector", "watch_id is required")
        val clear = (params["clear"] as? Boolean) ?: true
        val spec = WatchRegistry.get(id)
            ?: return ToolResult.error("watch_not_found", null)
        val polled = WatchRegistry.poll(id, clear)
            ?: return ToolResult.error("watch_not_found", null)
        return ToolResult.success(mapOf(
            "watch_id" to id,
            "count" to polled.events.size,
            "dropped" to polled.dropped,
            "expires_at" to spec.expiresAt,
            "events" to polled.events.map { it.toResultMap() },
        ))
    }
}

/** Snake-case wire projection of a [NotificationEvent]. */
internal fun NotificationEvent.toResultMap(): Map<String, Any?> = mapOf(
    "key" to key,
    "package_name" to packageName,
    "title" to title,
    "text" to text,
    "big_text" to bigText,
    "sub_text" to subText,
    "ticker_text" to tickerText,
    "category" to category,
    "channel_id" to channelId,
    "group_key" to groupKey,
    "is_ongoing" to isOngoing,
    "is_clearable" to isClearable,
    "channel_importance" to channelImportance,
    "posted_at" to postedAt,
    "when" to `when`,
    "has_reply_action" to hasReplyAction,
    "action_labels" to actionLabels,
)
