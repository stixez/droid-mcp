package io.droidmcp.playback

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus
import io.droidmcp.notification.NotificationListenerHolder

/**
 * Provider for the playback tools: [GetNowPlayingTool] and [MediaControlTool].
 *
 * Both tools read/control media sessions through the system, which requires
 * Notification Listener access. The host app must register its listener
 * ComponentName via [NotificationListenerHolder] and have the listener enabled
 * in Settings; use [permissionStatus] to check and prompt for that access.
 */
object PlaybackTools {

    /** Instantiates all playback tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetNowPlayingTool(context),
        MediaControlTool(context),
    )

    /**
     * No runtime permissions: Notification Listener access is a special access
     * granted via Settings, not a runtime permission.
     */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always `true`; there are no runtime permissions to check (see [permissionStatus]). */
    fun hasPermissions(context: Context): Boolean = true

    /**
     * Returns whether the host's registered notification listener (from
     * [NotificationListenerHolder]) is actually enabled in system Settings.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        val component = NotificationListenerHolder.componentName ?: return false
        return nm.isNotificationListenerAccessGranted(component)
    }

    /**
     * Reports notification-listener access as a [PermissionStatus]; when not
     * granted, carries an intent to the notification-access Settings screen.
     */
    fun permissionStatus(context: Context): PermissionStatus =
        if (isNotificationListenerEnabled(context)) {
            PermissionStatus.Granted("Notification listener access granted")
        } else {
            PermissionStatus.NotGranted(
                message = "Notification listener access not granted. Enable in Settings > Apps > Special access > Notification access.",
                intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            )
        }

    /** The names of all playback tools provided by this module. */
    fun supportedTools(context: Context): Set<String> =
        all(context).map { it.name }.toSet()
}
