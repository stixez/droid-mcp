package io.droidmcp.sample

import io.droidmcp.ime.DroidMcpInputMethodService

/**
 * Concrete InputMethodService for the sample app.
 *
 * The user adds the keyboard from Settings > System > Languages & input >
 * Manage on-screen keyboards, then selects it via the IME picker. Subclassing
 * the SDK base wires the service into InputMethodServiceHolder so the IME
 * tools can drive it.
 */
class McpInputMethodService : DroidMcpInputMethodService()
