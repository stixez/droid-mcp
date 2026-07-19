package io.droidmcp.sample

import io.droidmcp.notification.McpNotificationListenerServiceBase

/**
 * Required for media session access (getActiveSessions) and notification-reply tools.
 * The app must be granted notification listener access in
 * Settings > Apps > Special access > Notification access.
 *
 * Extending the SDK base class wires the active-notification cache used by
 * notifications-reply; declaring the subclass in the manifest grants the
 * permission needed by playback.
 */
class McpNotificationListenerService : McpNotificationListenerServiceBase()
