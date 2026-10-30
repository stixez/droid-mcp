package io.droidmcp.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory snapshot of active notifications maintained by
 * [McpNotificationListenerServiceBase]. Tools read from it; the service writes
 * to it on lifecycle callbacks.
 *
 * Three parallel caches are kept: the raw [StatusBarNotification] map for
 * existence checks (e.g. dismiss), a [NotificationEvent] map holding a
 * pure-data projection of every active notification, and a projected
 * [RepliableNotification] map for reply tools that should not be coupled to
 * platform types. Projections are recomputed on every put; the repliable one
 * is dropped when no free-form RemoteInput exists.
 *
 * Assumes a single connected [McpNotificationListenerServiceBase] per process,
 * which Android enforces for NotificationListenerService anyway.
 */
object NotificationStore {

    private val active = ConcurrentHashMap<String, StatusBarNotification>()
    private val events = ConcurrentHashMap<String, NotificationEvent>()
    private val repliable = ConcurrentHashMap<String, RepliableNotification>()

    fun snapshot(): List<StatusBarNotification> = active.values.toList()

    fun findByKey(key: String): StatusBarNotification? = active[key]

    fun containsKey(key: String): Boolean = active.containsKey(key)

    /** Pure-data view of every active notification, from any app. */
    fun eventSnapshot(): List<NotificationEvent> = events.values.toList()

    fun repliableSnapshot(): List<RepliableNotification> = repliable.values.toList()

    fun findRepliable(key: String): RepliableNotification? = repliable[key]

    /** Caches [sbn]; [event] is its projection, built once by the caller and shared with the bus. */
    internal fun put(sbn: StatusBarNotification, event: NotificationEvent?) {
        active[sbn.key] = sbn
        if (event != null) events[sbn.key] = event else events.remove(sbn.key)
        val projected = sbn.toRepliableOrNull()
        if (projected != null) repliable[sbn.key] = projected else repliable.remove(sbn.key)
    }

    internal fun remove(key: String) {
        active.remove(key)
        events.remove(key)
        repliable.remove(key)
    }

    /**
     * Replace the cache contents with the listener's current active set. Uses
     * retain-then-put rather than clear-then-put so a concurrent
     * `onNotificationPosted` racing this call can't be lost between the clear
     * and the fill — keys present in [initial] are upserted, keys absent are
     * evicted.
     */
    internal fun reset(
        initial: Array<StatusBarNotification>?,
        channelImportanceOf: (StatusBarNotification) -> Int = { -1 },
    ) {
        val incoming = initial?.associateBy { it.key }.orEmpty()
        active.keys.retainAll(incoming.keys)
        events.keys.retainAll(incoming.keys)
        repliable.keys.retainAll(incoming.keys)
        incoming.forEach { (k, sbn) ->
            active[k] = sbn
            // Any app can post a malformed notification; skip its projections, not the rest.
            val event = runCatching { sbn.toEvent(channelImportanceOf(sbn)) }.getOrNull()
            if (event != null) events[k] = event else events.remove(k)
            val projected = runCatching { sbn.toRepliableOrNull() }.getOrNull()
            if (projected != null) repliable[k] = projected else repliable.remove(k)
        }
    }

    internal fun clear() {
        active.clear()
        events.clear()
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
     * Test-only path that populates the event cache without mocking
     * [StatusBarNotification].
     */
    internal fun putEventForTest(event: NotificationEvent) {
        events[event.key] = event
    }

    /**
     * Test-only path that clears every cache.
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
