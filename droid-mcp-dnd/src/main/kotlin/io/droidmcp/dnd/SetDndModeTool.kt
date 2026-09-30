package io.droidmcp.dnd

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Sets the Do Not Disturb interruption filter.
 *
 * Calls [android.app.NotificationManager.setInterruptionFilter]. Requires DND
 * policy access (`isNotificationPolicyAccessGranted`), a special access granted
 * via Settings > Apps > Special access > Do Not Disturb access; without it the
 * tool returns an error. Idempotent (setting the same mode twice has the same
 * effect).
 *
 * **Android 15+ (API 35):** for apps targeting API 35, `setInterruptionFilter` no longer changes
 * the global DND state directly — it activates/deactivates the calling app's implicit
 * `AutomaticZenRule`. The system merges that rule with the user's own settings and other apps'
 * rules, so the resulting global filter may differ from the one requested (e.g. requesting
 * `off` cannot override DND the user turned on). The tool therefore reads back
 * [NotificationManager.getCurrentInterruptionFilter] after the call and reports it as
 * `actual_mode`; `applied` is true only when it matches the requested mode, and `note` explains
 * any mismatch.
 *
 * Output keys: `success`, `mode` (requested), `actual_mode` (`off`/`priority`/`alarms`/`none`/
 * `unknown`), `applied`, `note` (present only when the actual mode differs).
 */
class SetDndModeTool(private val context: Context) : McpTool {

    override val name = "set_dnd_mode"
    override val description = "Set Do Not Disturb mode. Requires notification policy access (Settings > Apps > Special access > Do Not Disturb access). On Android 15+ this toggles the app's own DND rule, which the system merges with the user's settings — check actual_mode in the result."
    override val parameters = listOf(
        ToolParameter("mode", "DND mode: 'off' (all notifications), 'priority' (priority only), 'alarms' (alarms only), 'none' (total silence)", ParameterType.STRING, required = true, enumValues = listOf("off", "priority", "alarms", "none")),
    )
    override val annotations = ToolAnnotations(idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val mode = params["mode"]?.toString()?.trim()?.lowercase()
            ?: return ToolResult.error("mode is required")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) {
            return ToolResult.error("Notification policy access not granted. Enable it in Settings > Apps > Special access > Do Not Disturb access.")
        }

        val filter = when (mode) {
            "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
            "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
            else -> return ToolResult.error("mode must be one of: off, priority, alarms, none")
        }

        try {
            nm.setInterruptionFilter(filter)
        } catch (e: SecurityException) {
            return ToolResult.error("Not permitted to change Do Not Disturb: ${e.message}")
        }

        val actualFilter = nm.currentInterruptionFilter
        val actualMode = filterToMode(actualFilter)
        val applied = actualFilter == filter

        val result = mutableMapOf<String, Any>(
            "success" to true,
            "mode" to mode,
            "actual_mode" to actualMode,
            "applied" to applied,
        )
        if (!applied) {
            result["note"] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                "Requested '$mode' but the device is in '$actualMode'. On Android 15+ this app only " +
                    "controls its own DND rule, which the system merges with the user's settings and other rules."
            } else {
                "Requested '$mode' but the device reports '$actualMode'; the change may be delayed or overridden."
            }
        }
        return ToolResult.success(result)
    }

    private fun filterToMode(filter: Int): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
        else -> "unknown"
    }
}
