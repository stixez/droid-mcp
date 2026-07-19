package io.droidmcp.core

import android.content.Intent

/**
 * Uniform shape returned by every special-access module's `permissionStatus(context)`
 * helper. Lets host apps render permission cards inline (e.g. in chat) before the
 * LLM tries to call a tool, with a single switch over a closed type.
 *
 * - [Granted] — the special access is currently active. Tools in this module will
 *   succeed.
 * - [NotGranted] — the user hasn't enabled the permission. Optionally carries an
 *   [intent] the host can launch to take them to the relevant Settings page.
 * - [Unavailable] — the device can't support this module at all (e.g. API floor
 *   too low). Renders as "Not supported on this device" rather than a "Grant"
 *   button. Currently reserved for future hard-floor modules; no module returns
 *   it as of 0.7.0.
 */
sealed class PermissionStatus {
    abstract val granted: Boolean
    abstract val message: String
    abstract val intent: Intent?

    data class Granted(
        override val message: String = "Granted",
    ) : PermissionStatus() {
        override val granted: Boolean = true
        override val intent: Intent? = null
    }

    data class NotGranted(
        override val message: String,
        override val intent: Intent?,
    ) : PermissionStatus() {
        override val granted: Boolean = false
    }

    data class Unavailable(
        override val message: String,
        val requiredApiLevel: Int,
    ) : PermissionStatus() {
        override val granted: Boolean = false
        override val intent: Intent? = null
    }
}
