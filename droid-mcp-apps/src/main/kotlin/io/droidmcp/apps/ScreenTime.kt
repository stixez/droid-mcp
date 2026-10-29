package io.droidmcp.apps

/** One activity moving to or from the foreground, lifted out of `UsageEvents.Event`. */
internal data class ActivityTransition(
    val packageName: String,
    val className: String?,
    val foreground: Boolean,
    val timestamp: Long,
)

/** Foreground time and last activity for one app over a window. */
internal data class AppScreenTime(
    val packageName: String,
    val foregroundMillis: Long,
    val lastUsed: Long,
)

/**
 * Turns activity transitions into per-app foreground time within `[start, end)`. An app counts as
 * in the foreground while any of its activities is resumed, so switching between two activities
 * of the same app is one continuous stretch. An app whose first event in the window is a pause
 * was already open at [start]; one still resumed at the end is counted up to [end].
 */
internal object ScreenTime {

    fun aggregate(transitions: List<ActivityTransition>, start: Long, end: Long): List<AppScreenTime> {
        val open = mutableMapOf<String, MutableSet<String?>>()
        val openSince = mutableMapOf<String, Long>()
        val total = mutableMapOf<String, Long>()
        val lastUsed = mutableMapOf<String, Long>()

        for (t in transitions.sortedBy { it.timestamp }) {
            val time = t.timestamp.coerceIn(start, end)
            val pkg = t.packageName
            val firstSeen = pkg !in lastUsed
            lastUsed[pkg] = maxOf(lastUsed[pkg] ?: 0L, time)
            val activities = open.getOrPut(pkg) { mutableSetOf() }
            if (t.foreground) {
                if (activities.isEmpty()) openSince[pkg] = time
                activities.add(t.className)
            } else {
                // A pause with nothing open: the app was already in front when the window began.
                if (firstSeen && activities.isEmpty()) {
                    total[pkg] = (total[pkg] ?: 0L) + (time - start)
                    continue
                }
                if (!activities.remove(t.className)) continue
                if (activities.isEmpty()) {
                    total[pkg] = (total[pkg] ?: 0L) + (time - (openSince.remove(pkg) ?: time))
                }
            }
        }
        for ((pkg, since) in openSince) {
            if (open[pkg].isNullOrEmpty()) continue
            total[pkg] = (total[pkg] ?: 0L) + (end - since)
            lastUsed[pkg] = end
        }

        return total.filterValues { it > 0 }
            .map { (pkg, millis) -> AppScreenTime(pkg, millis, lastUsed.getValue(pkg)) }
            .sortedByDescending { it.foregroundMillis }
    }
}
