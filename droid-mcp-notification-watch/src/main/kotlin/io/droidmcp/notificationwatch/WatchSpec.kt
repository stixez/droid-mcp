package io.droidmcp.notificationwatch

import io.droidmcp.notification.NotificationEvent

/**
 * Internal pure-data record of a registered watch. Created by
 * [WatchNotificationsTool], consumed by [WatchRegistry]. Mutable per-watch
 * state (e.g. which keys have already fired) lives in [WatchRegistry] keyed
 * by [id] so this record stays equals/hashCode-safe and replaceable.
 */
internal data class WatchSpec(
    val id: String,
    val packageName: String?,
    val senderPattern: String?,
    val keyword: String?,
    val ttlSeconds: Int,
    val fireOnUpdate: Boolean,
    val createdAt: Long,
) {
    val expiresAt: Long get() = createdAt + ttlSeconds * 1000L
    fun isExpired(now: Long = System.currentTimeMillis()) = now >= expiresAt
}

internal fun WatchSpec.matches(event: NotificationEvent): Boolean {
    if (packageName != null && event.packageName != packageName) return false
    if (senderPattern != null && !containsCi(event.title, senderPattern)) return false
    if (keyword != null) {
        val haystack = listOfNotNull(event.text, event.bigText, event.tickerText, event.subText)
            .joinToString(" ")
        if (!containsCi(haystack, keyword)) return false
    }
    return true
}

private fun containsCi(haystack: String?, needle: String): Boolean =
    haystack?.contains(needle, ignoreCase = true) == true
