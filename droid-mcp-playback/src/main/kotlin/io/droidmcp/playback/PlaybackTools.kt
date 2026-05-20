package io.droidmcp.playback

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus
import io.droidmcp.notification.NotificationListenerHolder

object PlaybackTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetNowPlayingTool(context),
        MediaControlTool(context),
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

    fun supportedTools(context: Context): Set<String> =
        all(context).map { it.name }.toSet()
}
