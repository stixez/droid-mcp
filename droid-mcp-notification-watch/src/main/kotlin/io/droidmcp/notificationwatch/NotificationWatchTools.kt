package io.droidmcp.notificationwatch

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus
import io.droidmcp.notification.NotificationListenerHolder

/**
 * Provider for the notification-watch module. Wires up [WatchNotificationsTool],
 * [UnwatchNotificationsTool], and [ListNotificationWatchesTool], all backed by [WatchRegistry].
 * Watches match against the live stream via
 * [NotificationListenerBus.events][io.droidmcp.notification.NotificationListenerBus] rather
 * than polling — the host's listener service must be bound and notification listener access
 * granted for events to flow. As with the sibling `NotificationsReplyTools` module, this is a
 * special-access permission granted via Settings, so [requiredPermissions] is empty and
 * [hasPermissions] always returns `true`; use [isNotificationListenerEnabled] or
 * [permissionStatus] to check actual readiness.
 */
object NotificationWatchTools {

    fun all(context: Context): List<McpTool> = listOf(
        WatchNotificationsTool(context),
        UnwatchNotificationsTool(context),
        ListNotificationWatchesTool(context),
    )

    /**
     * Notification listener access is a special access permission granted via
     * Settings, not at runtime — no manifest entries to request.
     */
    fun requiredPermissions(): List<String> = emptyList()

    /**
     * Convention method matching other modules; always returns `true` because
     * the runtime-permission concept doesn't apply. Use [isNotificationListenerEnabled]
     * (or [permissionStatus]) to know whether the watch tools will actually
     * succeed.
     */
    fun hasPermissions(context: Context): Boolean = true

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        val component = NotificationListenerHolder.componentName ?: return false
        return nm.isNotificationListenerAccessGranted(component)
    }

    fun permissionStatus(context: Context): PermissionStatus =
        if (isNotificationListenerEnabled(context)) {
            PermissionStatus.Granted("Notification listener access granted")
        } else {
            PermissionStatus.NotGranted(
                message = "Notification listener access not granted. Enable in Settings > Apps > Special access > Notification access.",
                intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            )
        }

    /**
     * All watch tools are device-independent — no API floor. `supportedTools`
     * always returns the full set.
     */
    fun supportedTools(context: Context): Set<String> =
        all(context).map { it.name }.toSet()
}
