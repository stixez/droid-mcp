package io.droidmcp.sample

import io.droidmcp.accessibility.DroidMcpAccessibilityService

/**
 * Concrete AccessibilityService for the sample app.
 *
 * The user must enable it from Settings > Accessibility > Installed apps >
 * droid-mcp sample > toggle on. Subclassing the SDK base wires the service
 * into AccessibilityServiceHolder so the accessibility tools can drive it.
 */
class McpAccessibilityService : DroidMcpAccessibilityService()
