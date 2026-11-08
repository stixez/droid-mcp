package io.droidmcp.app.service

import android.content.Intent
import io.droidmcp.accessibility.DroidMcpAccessibilityService
import io.droidmcp.app.appGraph
import io.droidmcp.ime.DroidMcpInputMethodService
import io.droidmcp.notification.McpNotificationListenerServiceBase

/**
 * Screen control: enabled by the user in Settings > Accessibility. Android binds it on its own
 * schedule (also after a process restart), so the app re-checks permissions when it connects.
 */
class McpAccessibilityService : DroidMcpAccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        appGraph.permissions.refresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val result = super.onUnbind(intent)
        appGraph.permissions.refresh()
        return result
    }
}

/** Notification access: reading and replying to other apps' notifications, media control. */
class McpNotificationListenerService : McpNotificationListenerServiceBase()

/** The droid-mcp keyboard, for typing into fields the accessibility service can't fill. */
class McpInputMethodService : DroidMcpInputMethodService()
