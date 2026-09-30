package io.droidmcp.intent

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.droidmcp.core.ToolResult

/**
 * Shared safety checks for the intent tools: a data-URI scheme allowlist and a
 * background-activity-launch precheck.
 */
internal object IntentGuards {

    /**
     * URI schemes the intent tools will put on an intent's data. Each one opens a
     * user-visible handler that still requires the user to act before anything
     * irreversible happens:
     *  - `http` / `https` — browser or verified app link.
     *  - `geo` — map viewer.
     *  - `tel` — dialer (with `ACTION_VIEW`/`ACTION_DIAL` this pre-fills, never places a call;
     *    `ACTION_CALL` is not on the `send_intent` allowlist).
     *  - `mailto`, `sms`, `smsto`, `mms`, `mmsto` — pre-filled compose screens; the user
     *    must press send. `mms`/`mmsto` are the MMS siblings of `sms`/`smsto`.
     *  - `market` — Play Store listing page.
     *
     * Rejected: `file` (exposes local files / `FileUriExposedException`), `content`
     * (would hand the host's own provider grants to the receiver), `intent` and
     * `android-app` (can encode arbitrary explicit intents, bypassing the action
     * allowlist), `javascript`, `data`, and any unknown or app-private scheme
     * (arbitrary deep links can trigger in-app actions without user review).
     */
    val ALLOWED_SCHEMES: Set<String> = setOf(
        "http", "https", "geo", "tel", "mailto", "sms", "smsto", "mms", "mmsto", "market",
    )

    /**
     * Validate [raw] as a data URI for an outgoing intent.
     *
     * @return the parsed [Uri], or a failure whose message is the tool error.
     */
    fun parseAllowedUri(raw: String): Result<Uri> {
        val trimmed = raw.trim()
        val uri = Uri.parse(trimmed)
        val scheme = uri.scheme?.lowercase()
            ?: return Result.failure(IllegalArgumentException(
                "uri_scheme_not_allowed: URI has no scheme. Allowed schemes: ${ALLOWED_SCHEMES.sorted().joinToString(", ")}",
            ))
        if (scheme !in ALLOWED_SCHEMES) {
            return Result.failure(IllegalArgumentException(
                "uri_scheme_not_allowed: '$scheme' is not permitted. Allowed schemes: ${ALLOWED_SCHEMES.sorted().joinToString(", ")}",
            ))
        }
        return Result.success(uri.normalizeScheme())
    }

    /**
     * On API 29+ `startActivity` from a background app is silently dropped by the
     * system (no exception), so the tool would report a false success. Returns an
     * error result when the host is neither visible to the user
     * (`ActivityManager.getMyMemoryState` importance `<= IMPORTANCE_VISIBLE`) nor
     * holds `SYSTEM_ALERT_WINDOW` (which exempts it from the restriction); null when
     * the launch can proceed.
     */
    fun backgroundLaunchError(context: Context): ToolResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val state = ActivityManager.RunningAppProcessInfo()
        runCatching { ActivityManager.getMyMemoryState(state) }
        if (state.importance != 0 &&
            state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        ) return null
        if (runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)) return null
        return ToolResult.error(
            "background_activity_launch_blocked",
            "Android 10+ blocks starting activities while the host app is in the background. " +
                "Bring the host app to the foreground, or grant it 'Display over other apps' (SYSTEM_ALERT_WINDOW).",
        )
    }
}
