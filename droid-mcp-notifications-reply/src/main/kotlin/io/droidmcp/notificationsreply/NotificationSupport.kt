package io.droidmcp.notificationsreply

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.service.notification.StatusBarNotification
import io.droidmcp.core.ToolResult
import io.droidmcp.notification.NotificationListenerHolder

/**
 * The error to return when the listener can't serve notifications yet (no ComponentName
 * configured, or access not granted), else null.
 */
internal fun listenerUnavailable(context: Context): ToolResult? = when {
    NotificationListenerHolder.componentName == null ->
        ToolResult.error("NotificationListenerService not configured. The host app must call NotificationListenerHolder.set() with its listener ComponentName.")
    !NotificationsReplyTools.isNotificationListenerEnabled(context) ->
        ToolResult.error("Notification listener access not granted. Enable it in Settings > Apps > Special access > Notification access.")
    else -> null
}

/**
 * The posting app's [ApplicationInfo], which `Notification.Builder` stores in the extras
 * (`android.appInfo`). Resolving the label from it works for apps that Android 11+ package
 * visibility hides from `getApplicationInfo`.
 */
internal fun StatusBarNotification.postingAppInfo(): ApplicationInfo? = runCatching {
    val extras = notification?.extras ?: return@runCatching null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
