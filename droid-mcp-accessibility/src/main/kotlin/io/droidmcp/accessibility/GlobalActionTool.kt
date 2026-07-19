package io.droidmcp.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * `global_action` — dispatch a system-wide `AccessibilityService` global
 * action: `back`, `home`, `recents`, `notifications`, `quick_settings`,
 * `power_dialog`, `lock_screen`, or `screenshot` (see the companion `MAP`).
 *
 * The action string is lowercased before lookup. [AccessibilityTools.idempotentGlobalActions]
 * lists the subset dispatchers may mark as safe to retry.
 *
 * Params: required `action`.
 *
 * On success returns `success = true` and the resolved `action` name. Errors
 * are long-form messages: `action is required`, an "Unknown action" message
 * listing the valid keys, the [notConnectedError] message when the service is
 * not bound, and a "performGlobalAction returned false" message when the system
 * rejects the action.
 */
class GlobalActionTool(private val context: Context) : McpTool {

    override val name = "global_action"
    override val description = "Dispatch a system-wide AccessibilityService global action: back, home, recents, notifications, quick_settings, power_dialog, lock_screen, screenshot."
    override val parameters = listOf(
        ToolParameter("action", "One of: back, home, recents, notifications, quick_settings, power_dialog, lock_screen, screenshot.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val svc = AccessibilityServiceHolder.service ?: return ToolResult.error(notConnectedError())
        val name = (params["action"] as? String)?.lowercase()
            ?: return ToolResult.error("action is required")
        val code = MAP[name] ?: return ToolResult.error(
            "Unknown action '$name'. Valid: ${MAP.keys.joinToString()}."
        )
        val ok = svc.performGlobalAction(code)
        return if (ok) {
            ToolResult.success(mapOf("success" to true, "action" to name))
        } else {
            ToolResult.error("performGlobalAction returned false for '$name'.")
        }
    }

    companion object {
        private val MAP = mapOf(
            "back" to AccessibilityService.GLOBAL_ACTION_BACK,
            "home" to AccessibilityService.GLOBAL_ACTION_HOME,
            "recents" to AccessibilityService.GLOBAL_ACTION_RECENTS,
            "notifications" to AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            "quick_settings" to AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
            "power_dialog" to AccessibilityService.GLOBAL_ACTION_POWER_DIALOG,
            "lock_screen" to AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
            "screenshot" to AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
        )
    }
}
