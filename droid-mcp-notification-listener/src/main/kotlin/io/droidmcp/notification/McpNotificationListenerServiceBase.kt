package io.droidmcp.notification

import android.app.NotificationManager
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
        // Every sbn is attacker-shaped (any app can post one), so never let a
        // malformed notification throw out of a listener callback — that runs
        // on the host's main thread and would crash the host process.
        runCatching { NotificationStore.reset(runCatching { activeNotifications }.getOrNull()) }
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
        // Isolated so a malformed notification from any app can't crash the
        // host (this callback runs on the main thread); the store and the bus
        // are updated independently so one failing doesn't block the other.
        runCatching { NotificationStore.put(sbn) }
        runCatching { NotificationListenerBus.publish(sbn.toEvent(channelImportanceFor(sbn))) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { runCatching { NotificationStore.remove(it.key) } }
    }

    /**
     * Resolves the effective importance for `sbn` from the listener's
     * [getCurrentRanking] (`Ranking.importance`, which reflects the channel's
     * user-set importance on O+). Unlike `getNotificationChannels`, this needs
     * no companion-device association. Returns `-1` when no ranking is
     * available for the key (listener disconnected, notification already gone)
     * or the importance is unspecified.
     */
    private fun channelImportanceFor(sbn: StatusBarNotification): Int {
        return runCatching {
            val rankingMap = currentRanking ?: return@runCatching null
            val ranking = Ranking()
            if (!rankingMap.getRanking(sbn.key, ranking)) return@runCatching null
            ranking.importance.takeIf { it != NotificationManager.IMPORTANCE_UNSPECIFIED }
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
