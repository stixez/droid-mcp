package io.droidmcp.notificationsreply

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus
import io.droidmcp.notification.NotificationListenerHolder

/**
 * Provider for the notification-reply module. Wires up [ListRepliableNotificationsTool],
 * [ReplyToNotificationTool], [DismissNotificationTool], and [InvokeNotificationActionTool].
 * All four read/act against [NotificationStore][io.droidmcp.notification.NotificationStore] and
 * require the host's [McpNotificationListenerServiceBase][io.droidmcp.notification.McpNotificationListenerServiceBase]
 * to be bound with notification listener access granted — a special-access permission granted
 * via Settings, not a runtime dialog, hence [requiredPermissions] is empty and
 * [hasPermissions] always returns `true`. Use [isNotificationListenerEnabled] or
 * [permissionStatus] to check actual readiness.
 */
object NotificationsReplyTools {

    fun all(context: Context): List<McpTool> = listOf(
        ListRepliableNotificationsTool(context),
        ReplyToNotificationTool(context),
        DismissNotificationTool(context),
        InvokeNotificationActionTool(context),
    )

    // Notification listener is a special permission granted via Settings, not runtime
    fun requiredPermissions(): List<String> = emptyList()

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
     * No API-floor tools in this module — full set is always supported.
     */
    fun supportedTools(context: Context): Set<String> =
        all(context).map { it.name }.toSet()
}
