package io.droidmcp.playback

/**
 * Re-export of the shared notification-listener holder. Kept as a typealias for
 * backward compatibility with consumers that imported
 * `io.droidmcp.playback.NotificationListenerHolder` before 0.5.0.
 *
 * New code should use [io.droidmcp.notification.NotificationListenerHolder] directly.
 */
typealias NotificationListenerHolder = io.droidmcp.notification.NotificationListenerHolder
