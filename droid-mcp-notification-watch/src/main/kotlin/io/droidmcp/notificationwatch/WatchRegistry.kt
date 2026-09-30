package io.droidmcp.notificationwatch

import io.droidmcp.notification.NotificationEvent
import io.droidmcp.notification.NotificationListenerBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-global registry of active notification watches. Owns its own
 * coroutine scope (mirrors the [io.droidmcp.notification.NotificationStore]
 * precedent — hosts don't manage lifecycle for the listener pipeline).
 *
 * Filter semantics:
 *  - AND within a watch (package + sender + keyword all must match).
 *  - Case-insensitive substring on sender_pattern and keyword.
 *  - Fire-once-per-key by default; pass `fire_on_update = true` to opt into
 *    repeated emits for the same notification key.
 *  - No replay — watches start empty and only fire on newly-posted events.
 *
 * Per-watch mutable state (which keys have already fired, and the buffer of
 * matched events awaiting [poll]) lives here, keyed by watch id, rather than
 * inside [WatchSpec] — keeps the spec pure data.
 *
 * Bounds: at most [MAX_WATCHES] active watches ([register] refuses beyond
 * that), and each watch buffers at most [MAX_BUFFERED_EVENTS] matched events
 * in a ring — when full, the oldest event is dropped and counted in
 * [PollResult.dropped].
 */
internal object WatchRegistry {

    private val watches = ConcurrentHashMap<String, WatchSpec>()
    private val firedKeysByWatch = ConcurrentHashMap<String, MutableSet<String>>()
    private val buffersByWatch = ConcurrentHashMap<String, EventBuffer>()

    /** Maximum concurrently-active watches. */
    const val MAX_WATCHES = 50

    /** Maximum matched events buffered per watch before the oldest are dropped. */
    const val MAX_BUFFERED_EVENTS = 50

    /** Bounded FIFO ring of matched events; guarded by its own monitor. */
    private class EventBuffer {
        val events = ArrayDeque<NotificationEvent>()
        var dropped = 0
    }

    /**
     * Result of [poll].
     *
     * @property events Matched events in arrival order (oldest first).
     * @property dropped Events discarded because the ring was full since the
     *   last clearing poll.
     */
    data class PollResult(val events: List<NotificationEvent>, val dropped: Int)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var collector: Job? = null

    fun ensureCollecting() {
        if (collector?.isActive == true) return
        synchronized(this) {
            if (collector?.isActive == true) return
            collector = scope.launch {
                NotificationListenerBus.events.collect { event -> dispatch(event) }
            }
        }
    }

    /**
     * Match [event] against every live watch, recording fired keys and
     * buffering the event for each watch it fires. Per-watch state is looked
     * up with a plain `get` (never created here) so a watch unregistered or
     * swept concurrently is skipped instead of being resurrected.
     */
    internal fun dispatch(event: NotificationEvent) {
        sweepExpired()
        watches.values.forEach { watch ->
            if (!watch.matches(event)) return@forEach
            val firedSet = firedKeysByWatch[watch.id] ?: return@forEach
            val buffer = buffersByWatch[watch.id] ?: return@forEach
            val seen = event.key in firedSet
            if (seen && !watch.fireOnUpdate) return@forEach
            firedSet += event.key
            synchronized(buffer) {
                if (buffer.events.size >= MAX_BUFFERED_EVENTS) {
                    buffer.events.removeFirst()
                    buffer.dropped++
                }
                buffer.events.addLast(event)
            }
        }
    }

    fun newWatchId(): String = UUID.randomUUID().toString().take(8)

    /**
     * Register [spec] and start the collector if needed.
     *
     * @return false (and registers nothing) when [MAX_WATCHES] live watches
     *   already exist.
     */
    fun register(spec: WatchSpec): Boolean {
        synchronized(this) {
            sweepExpired()
            if (!watches.containsKey(spec.id) && watches.size >= MAX_WATCHES) return false
            firedKeysByWatch[spec.id] = ConcurrentHashMap.newKeySet()
            buffersByWatch[spec.id] = EventBuffer()
            watches[spec.id] = spec
        }
        ensureCollecting()
        return true
    }

    fun unregister(id: String): Boolean {
        val removed = watches.remove(id) != null
        firedKeysByWatch.remove(id)
        buffersByWatch.remove(id)
        return removed
    }

    /**
     * Return the events buffered for watch [id], optionally draining them.
     *
     * @param clear When true, the buffer and its dropped counter are reset.
     * @return The buffered events, or null when the watch is unknown/expired.
     */
    fun poll(id: String, clear: Boolean): PollResult? {
        get(id) ?: return null
        val buffer = buffersByWatch[id] ?: return null
        return synchronized(buffer) {
            val result = PollResult(buffer.events.toList(), buffer.dropped)
            if (clear) {
                buffer.events.clear()
                buffer.dropped = 0
            }
            result
        }
    }

    fun list(): List<WatchSpec> {
        sweepExpired()
        return watches.values.toList()
    }

    fun get(id: String): WatchSpec? {
        val spec = watches[id]
        if (spec != null && spec.isExpired()) {
            unregister(id)
            return null
        }
        return spec
    }

    /** Fired-keys count for a registered watch, or 0 if unknown / expired. */
    fun firedCount(id: String): Int = firedKeysByWatch[id]?.size ?: 0

    /**
     * Test-only: reset registry state between tests.
     */
    internal fun clearForTest() {
        watches.clear()
        firedKeysByWatch.clear()
        buffersByWatch.clear()
    }

    private fun sweepExpired(now: Long = System.currentTimeMillis()) {
        val expired = watches.values.filter { it.isExpired(now) }
        expired.forEach { spec ->
            watches.remove(spec.id)
            firedKeysByWatch.remove(spec.id)
            buffersByWatch.remove(spec.id)
        }
    }
}
