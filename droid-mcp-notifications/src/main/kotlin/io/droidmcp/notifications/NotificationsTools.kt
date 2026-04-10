package io.droidmcp.notifications

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object NotificationsTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetActiveNotificationsTool(context),
    )

    /**
     * No manifest permissions required for reading own-app notifications.
     * Full cross-app notification access requires NotificationListenerService.
     */
    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
