package io.droidmcp.notifications

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the notifications module — currently exposes only [GetActiveNotificationsTool],
 * which reads the host app's own status-bar notifications. Requires no manifest permissions.
 */
object NotificationsTools {

    /** All tools in this module: [GetActiveNotificationsTool]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetActiveNotificationsTool(context),
    )

    /**
     * No manifest permissions required for reading own-app notifications.
     * Full cross-app notification access requires NotificationListenerService.
     */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always `true` — this module needs no permissions for own-app notification reads. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
