package io.droidmcp.sample

import android.service.notification.NotificationListenerService

/**
 * Required for media session access (getActiveSessions).
 * The app must be granted notification listener access in
 * Settings > Apps > Special access > Notification access.
 * This service doesn't need to do anything — its existence
 * in the manifest is what grants the permission.
 */
class McpNotificationListenerService : NotificationListenerService()
