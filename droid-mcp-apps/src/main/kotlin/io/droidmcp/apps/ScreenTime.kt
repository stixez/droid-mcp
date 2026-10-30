package io.droidmcp.apps

/** One usage event that matters for screen time, lifted out of `UsageEvents.Event`. */
internal data class UsageTransition(
    val kind: Kind,
    val timestamp: Long,
    val packageName: String = "",
    val className: String? = null,
) {
    enum class Kind {
        /** An activity of [packageName] resumed. */
        FOREGROUND,

        /** An activity of [packageName] paused. */
        BACKGROUND,

        /** Screen off or shutdown: nothing is in front any more. */
        STOP_ALL,

        /** Boot. Anything still open died with the previous session, at its last event. */
        STARTUP,
    }
}

/** Foreground time and last activity for one app over a window. */
internal data class AppScreenTime(
    val packageName: String,
    val foregroundMillis: Long,
    val lastUsed: Long,
)

/**
 * Turns usage events into per-app foreground time within `[start, end)`. Events before [start]
 * (a lookback, so an app opened before the window is seen as open) are clamped to [start]. An
 * app counts as in front while any of its activities is resumed, so switching between two of its
 * activities is one stretch. Screen-off and shutdown close every stretch; after a boot without a
 * shutdown event (battery pulled, crash) stretches end at the last event before the boot. An app
 * whose first event is a pause was already open at [start]; one still open is counted to [end].
 */
internal object ScreenTime {

    fun aggregate(transitions: List<UsageTransition>, start: Long, end: Long): List<AppScreenTime> {
        val open = mutableMapOf<String, MutableSet<String?>>()
        val openSince = mutableMapOf<String, Long>()
        val total = mutableMapOf<String, Long>()
        val lastUsed = mutableMapOf<String, Long>()
        var lastEventAt = start

        fun close(pkg: String, at: Long) {
            val since = openSince.remove(pkg) ?: return
            open[pkg]?.clear()
            total[pkg] = (total[pkg] ?: 0L) + (at - since).coerceAtLeast(0L)
            lastUsed[pkg] = maxOf(lastUsed[pkg] ?: 0L, at)
        }

        for (t in transitions.sortedBy { it.timestamp }) {
            val time = t.timestamp.coerceIn(start, end)
            when (t.kind) {
                UsageTransition.Kind.STOP_ALL -> openSince.keys.toList().forEach { close(it, time) }
                UsageTransition.Kind.STARTUP -> openSince.keys.toList().forEach { close(it, lastEventAt) }
                UsageTransition.Kind.FOREGROUND, UsageTransition.Kind.BACKGROUND -> {
                    val pkg = t.packageName
                    val firstSeen = pkg !in lastUsed
                    lastUsed[pkg] = maxOf(lastUsed[pkg] ?: 0L, time)
                    val activities = open.getOrPut(pkg) { mutableSetOf() }
                    if (t.kind == UsageTransition.Kind.FOREGROUND) {
                        if (activities.isEmpty()) openSince[pkg] = time
                        activities.add(t.className)
                    } else if (firstSeen && activities.isEmpty()) {
                        // A pause with nothing open: the app was already in front when the window began.
                        total[pkg] = (total[pkg] ?: 0L) + (time - start)
                    } else if (activities.remove(t.className) && activities.isEmpty()) {
                        close(pkg, time)
                    }
                }
            }
            lastEventAt = time
        }
        openSince.keys.toList().forEach { close(it, end) }

        return total.filterValues { it > 0 }
            .map { (pkg, millis) -> AppScreenTime(pkg, millis, lastUsed.getValue(pkg)) }
            .sortedByDescending { it.foregroundMillis }
    }
}
