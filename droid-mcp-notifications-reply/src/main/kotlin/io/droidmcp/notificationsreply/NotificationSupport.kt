package io.droidmcp.notificationsreply

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.service.notification.StatusBarNotification
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.McpNotificationListenerServiceBase
import io.droidmcp.notification.NotificationListenerHolder

/**
 * The error to return when the listener can't serve notifications (no ComponentName configured,
 * access not granted, or the service not connected, which would make the store look empty),
 * else null.
 */
internal fun listenerUnavailable(context: Context): ToolResult? = when {
    NotificationListenerHolder.componentName == null ->
        ToolResult.error("NotificationListenerService not configured. The host app must call NotificationListenerHolder.set() with its listener ComponentName.")
    !NotificationsReplyTools.isNotificationListenerEnabled(context) ->
        ToolResult.error("Notification listener access not granted. Enable it in Settings > Apps > Special access > Notification access.")
    !McpNotificationListenerServiceBase.isConnected ->
        ToolResult.error("Notification listener is not connected yet. Android binds it shortly after access is granted or the app restarts; try again in a moment.")
    else -> null
}

/**
 * The posting app's [ApplicationInfo], which `Notification.Builder` stores in the extras
 * (`android.appInfo`). Resolving the label from it works for apps that Android 11+ package
 * visibility hides from `getApplicationInfo`.
 */
internal fun StatusBarNotification.postingAppInfo(): ApplicationInfo? = runCatching {
    val extras = notification?.extras ?: return@runCatching null
    // The typed overload is unreliable on API 33 (AndroidX BundleCompat avoids it there too).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        extras.getParcelable(EXTRA_APP_INFO, ApplicationInfo::class.java)
    } else {
        @Suppress("DEPRECATION")
        extras.getParcelable(EXTRA_APP_INFO)
    }
}.getOrNull()

/** True for a bundle's summary ("3 new messages"), which repeats its children. */
internal val StatusBarNotification.isGroupSummary: Boolean
    get() = ((notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY) != 0

/** `Notification.EXTRA_BUILDER_APPLICATION_INFO`, hidden from the SDK. */
private const val EXTRA_APP_INFO = "android.appInfo"
