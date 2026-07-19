package io.droidmcp.notification

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide SharedFlow of [NotificationEvent]s emitted by
 * [McpNotificationListenerServiceBase] when notifications post.
 *
 * Hosts that want to react to incoming notifications subscribe to [events] from
 * their own coroutine scope. The watch tools in
 * `:droid-mcp-notification-watch` are one such subscriber but not the only
 * intended consumer — apps with bespoke flows (auto-reply skills, monitor
 * dashboards, etc.) can subscribe directly without pulling in the watch
 * module.
 *
 * Replay is 0 — joining late doesn't backfill. Use `notifications-reply`'s
 * `list_repliable_notifications` for snapshot reads of the active set.
 *
 * Buffer is 64 with `DROP_OLDEST` overflow so a slow collector can't
 * back-pressure the listener service.
 */
object NotificationListenerBus {

    private val _events = MutableSharedFlow<NotificationEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events: SharedFlow<NotificationEvent> = _events.asSharedFlow()

    /**
     * Cancel a notification by its key. Returns true on dispatch, false when
     * no listener service is currently bound or the underlying binder
     * rejected the call. Delegates to
     * [McpNotificationListenerServiceBase.cancelByKey].
     */
    fun dismiss(key: String): Boolean = McpNotificationListenerServiceBase.cancelByKey(key)

    internal fun publish(event: NotificationEvent) {
        _events.tryEmit(event)
    }
}
