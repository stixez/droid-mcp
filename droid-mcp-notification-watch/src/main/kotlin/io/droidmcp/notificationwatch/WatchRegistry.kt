package io.droidmcp.notificationwatch

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
 * Per-watch mutable state (which keys have already fired) lives here, keyed by
 * watch id, rather than inside [WatchSpec] — keeps the spec pure data.
 */
internal object WatchRegistry {

    private val watches = ConcurrentHashMap<String, WatchSpec>()
    private val firedKeysByWatch = ConcurrentHashMap<String, MutableSet<String>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var collector: Job? = null

    fun ensureCollecting() {
        if (collector?.isActive == true) return
        synchronized(this) {
            if (collector?.isActive == true) return
            collector = scope.launch {
                NotificationListenerBus.events.collect { event ->
                    sweepExpired()
                    watches.values.forEach { watch ->
                        if (!watch.matches(event)) return@forEach
                        val firedSet = firedKeysFor(watch.id)
                        val seen = event.key in firedSet
                        if (seen && !watch.fireOnUpdate) return@forEach
                        firedSet += event.key
                        // The event is already on NotificationListenerBus — watches
                        // are just tracking fire-once semantics for the LLM-tool
                        // surface, not re-emitting.
                    }
                }
            }
        }
    }

    fun newWatchId(): String = UUID.randomUUID().toString().take(8)

    fun register(spec: WatchSpec) {
        watches[spec.id] = spec
        firedKeysByWatch[spec.id] = ConcurrentHashMap.newKeySet()
        ensureCollecting()
    }

    fun unregister(id: String): Boolean {
        firedKeysByWatch.remove(id)
        return watches.remove(id) != null
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
    }

    private fun firedKeysFor(watchId: String): MutableSet<String> =
        firedKeysByWatch.computeIfAbsent(watchId) { ConcurrentHashMap.newKeySet() }

    private fun sweepExpired(now: Long = System.currentTimeMillis()) {
        val expired = watches.values.filter { it.isExpired(now) }
        expired.forEach { spec ->
            watches.remove(spec.id)
            firedKeysByWatch.remove(spec.id)
        }
    }
}
