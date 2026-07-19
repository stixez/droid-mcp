package io.droidmcp.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory snapshot of active notifications maintained by
 * [McpNotificationListenerServiceBase]. Tools read from it; the service writes
 * to it on lifecycle callbacks.
 *
 * Two parallel caches are kept: the raw [StatusBarNotification] map for
 * existence checks (e.g. dismiss) and a projected [RepliableNotification] map
 * for reply tools that should not be coupled to platform types. The projection
 * is recomputed on every put and dropped when no free-form RemoteInput exists.
 *
 * Assumes a single connected [McpNotificationListenerServiceBase] per process,
 * which Android enforces for NotificationListenerService anyway.
 */
object NotificationStore {

    private val active = ConcurrentHashMap<String, StatusBarNotification>()
    private val repliable = ConcurrentHashMap<String, RepliableNotification>()

    fun snapshot(): List<StatusBarNotification> = active.values.toList()

    fun findByKey(key: String): StatusBarNotification? = active[key]

    fun containsKey(key: String): Boolean = active.containsKey(key)

    fun repliableSnapshot(): List<RepliableNotification> = repliable.values.toList()

    fun findRepliable(key: String): RepliableNotification? = repliable[key]

    internal fun put(sbn: StatusBarNotification) {
        active[sbn.key] = sbn
        val projected = sbn.toRepliableOrNull()
        if (projected != null) repliable[sbn.key] = projected else repliable.remove(sbn.key)
    }

    internal fun remove(key: String) {
        active.remove(key)
        repliable.remove(key)
    }

    /**
     * Replace the cache contents with the listener's current active set. Uses
     * retain-then-put rather than clear-then-put so a concurrent
     * `onNotificationPosted` racing this call can't be lost between the clear
     * and the fill — keys present in [initial] are upserted, keys absent are
     * evicted.
     */
    internal fun reset(initial: Array<StatusBarNotification>?) {
        val incoming = initial?.associateBy { it.key }.orEmpty()
        active.keys.retainAll(incoming.keys)
        repliable.keys.retainAll(incoming.keys)
        incoming.forEach { (k, sbn) ->
            active[k] = sbn
            val projected = sbn.toRepliableOrNull()
            if (projected != null) repliable[k] = projected else repliable.remove(k)
        }
    }

    internal fun clear() {
        active.clear()
        repliable.clear()
    }

    /**
     * Test-only path that bypasses the platform projection. Lets tests in this
     * module populate the DTO cache directly without mocking
     * [StatusBarNotification].
     */
    internal fun putRepliableForTest(dto: RepliableNotification) {
        repliable[dto.key] = dto
    }

    /**
     * Test-only path that clears both caches.
     */
    internal fun clearForTest() {
        clear()
    }

    private fun StatusBarNotification.toRepliableOrNull(): RepliableNotification? {
        val actions: Array<Notification.Action>? = notification?.actions
        val reply = actions?.asSequence()?.mapNotNull { it.toReplyActionOrNull() }?.firstOrNull()
        if (reply == null) return null
        val extras = notification?.extras
        return RepliableNotification(
            key = key,
            packageName = packageName,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            postedAt = postTime,
            replyAction = reply,
        )
    }
}
