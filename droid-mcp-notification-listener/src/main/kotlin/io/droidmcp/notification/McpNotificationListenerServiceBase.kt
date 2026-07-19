package io.droidmcp.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Abstract NotificationListenerService that mirrors active notifications into
 * [NotificationStore], emits [NotificationEvent]s on [NotificationListenerBus]
 * when notifications post, and registers itself as the live instance for tools
 * that need to call `cancelNotification`.
 *
 * Host apps subclass this with a concrete name in their own package and
 * declare the subclass in their manifest with `BIND_NOTIFICATION_LISTENER_SERVICE`.
 *
 * Subclasses may override the lifecycle hooks but should call super.
 */
abstract class McpNotificationListenerServiceBase : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        NotificationStore.reset(runCatching { activeNotifications }.getOrNull())
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        NotificationStore.clear()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        // Android may destroy the service without first calling
        // onListenerDisconnected (process kill, permission revoked silently,
        // etc.). Clear the static handle so cancelByKey doesn't dispatch into
        // a dead binder.
        if (instance === this) instance = null
        NotificationStore.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        NotificationStore.put(sbn)
        NotificationListenerBus.publish(sbn.toEvent(channelImportanceFor(sbn)))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { NotificationStore.remove(it.key) }
    }

    /**
     * Resolves the channel importance for `sbn` via
     * `NotificationListenerService.getNotificationChannels(pkg, user)`. Returns
     * `-1` when the channel can't be located (no channel id, listener
     * disconnected, source app revoked metadata, etc.).
     */
    private fun channelImportanceFor(sbn: StatusBarNotification): Int {
        val channelId = sbn.notification?.channelId ?: return -1
        return runCatching {
            getNotificationChannels(sbn.packageName, sbn.user)
                ?.firstOrNull { it.id == channelId }
                ?.importance
        }.getOrNull() ?: -1
    }

    companion object {
        @Volatile
        internal var instance: McpNotificationListenerServiceBase? = null
            private set

        /**
         * Cancels the notification with the given key if a listener is currently
         * connected. Returns true on successful dispatch, false when no
         * listener is bound or the underlying binder rejected the call.
         */
        fun cancelByKey(key: String): Boolean {
            val live = instance ?: return false
            return runCatching { live.cancelNotification(key); true }.getOrElse { false }
        }
    }
}
