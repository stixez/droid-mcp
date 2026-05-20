package io.droidmcp.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Abstract base service that host apps subclass to grant droid-mcp the ability
 * to read the on-screen UI and dispatch clicks / gestures / scrolls.
 *
 * The host app:
 *   1. Declares a concrete subclass in its manifest with
 *      `android.permission.BIND_ACCESSIBILITY_SERVICE` and an
 *      `<intent-filter>` for `android.accessibilityservice.AccessibilityService`.
 *   2. Points `<meta-data android:name="android.accessibilityservice"
 *      android:resource="@xml/droid_mcp_accessibility_config" />` at the
 *      shipped default config (or supplies its own).
 *   3. The user enables the service from Settings > Accessibility > Installed
 *      apps. The SDK exposes a helper to open that screen directly.
 *
 * Subclasses MAY override [onAccessibilityEvent] / [onInterrupt] to react to
 * events but should call super so the holder stays consistent.
 */
abstract class DroidMcpAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityServiceHolder.set(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityServiceHolder.clear(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityServiceHolder.clear(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op by default. Subclasses may override.
    }

    override fun onInterrupt() {
        // No-op by default.
    }
}
