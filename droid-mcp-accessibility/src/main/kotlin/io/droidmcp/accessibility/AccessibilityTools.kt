package io.droidmcp.accessibility

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus

/**
 * Provider object for the accessibility module — the entry point host apps and
 * dispatchers use to register the tools and inspect their special-access state.
 *
 * Every tool here operates on [android.view.accessibility.AccessibilityNodeInfo]
 * trees obtained from the host's bound [DroidMcpAccessibilityService] (tracked
 * by [AccessibilityServiceHolder]). Because accessibility is a *special access*
 * permission granted from system Settings — not a runtime permission —
 * [requiredPermissions] is empty and [hasPermissions] is a no-op `true`; use
 * [isAccessibilityEnabled] / [permissionStatus] to learn whether tools will
 * actually succeed.
 *
 * Tools surface a mix of error shapes: the 0.7.0 coord/find tools and the
 * polling tools emit short-form codes (`accessibility_not_enabled`,
 * `node_not_found`, `node_not_editable`, `invalid_selector`, `invalid_coords`,
 * `gesture_failed`, `scroll_exhausted`); the older tools emit the long-form
 * [notConnectedError] sentence (see each tool's KDoc).
 */
object AccessibilityTools {

    /**
     * All accessibility tools, constructed against [context]. Includes
     * `take_screenshot_via_a11y` regardless of API level — call
     * [supportedTools] to get the device-filtered set instead.
     */
    fun all(context: Context): List<McpTool> = listOf(
        QueryScreenTool(context),
        FindNodeTool(context),
        WaitForTextTool(context),
        ClickNodeTool(context),
        LongClickNodeTool(context),
        SetNodeTextTool(context),
        ScrollNodeTool(context),
        GestureTool(context),
        GlobalActionTool(context),
        GetActiveWindowInfoTool(context),
        TakeScreenshotViaA11yTool(context),
        // 0.7.0 additions
        TapTool(context),
        LongPressTool(context),
        FindAndTapTool(context),
        ScrollToFindTool(context),
    )

    /**
     * Accessibility-service access is a *special access* permission granted
     * via system Settings, not a runtime permission — so there are no
     * `Manifest.permission.*` entries to declare or request.
     */
    fun requiredPermissions(): List<String> = emptyList()

    /**
     * Convention method matching other modules; always returns `true` because
     * the runtime-permission concept doesn't apply here. To know whether the
     * service is actually bound and tools will succeed, call
     * [isAccessibilityEnabled] (or [permissionStatus]) instead.
     */
    fun hasPermissions(context: Context): Boolean = true

    /**
     * True when the host app's [DroidMcpAccessibilityService] is currently
     * bound to the system.
     */
    fun isAccessibilityEnabled(): Boolean = AccessibilityServiceHolder.isConnected()

    /**
     * Special-access status of the accessibility service. Returns a
     * `Granted` when bound, otherwise `NotGranted` carrying the Settings
     * intent so the host can render an inline Grant card.
     */
    fun permissionStatus(context: Context): PermissionStatus =
        if (isAccessibilityEnabled()) {
            PermissionStatus.Granted("Accessibility service bound")
        } else {
            PermissionStatus.NotGranted(
                message = "Accessibility service not enabled. Open Settings > Accessibility > Installed apps and toggle the host app's service on.",
                intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            )
        }

    /**
     * The subset of `global_action` action strings that are safe to retry —
     * state-toggles or navigations whose final state is the same regardless
     * of how many times the call runs. Dispatchers consume this to attach
     * `idempotentHint = true` at call-site rather than splitting
     * `global_action` into one tool per action.
     *
     * `power_dialog` is excluded (genuine user-facing modal, not idempotent).
     * `screenshot` is also excluded — it's a capture action, not nav/state.
     */
    val idempotentGlobalActions: Set<String> = setOf(
        "back", "home", "recents", "notifications", "quick_settings", "lock_screen",
    )

    /**
     * Tool names supported on the current device. Filters out API-gated tools
     * the runtime can't actually run (e.g. `take_screenshot_via_a11y` on API
     * <30). Dispatchers use this to hide unsupported tools from the LLM
     * before tool-call time.
     */
    fun supportedTools(context: Context): Set<String> = buildSet {
        addAll(all(context).map { it.name })
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            remove("take_screenshot_via_a11y")
        }
    }
}
