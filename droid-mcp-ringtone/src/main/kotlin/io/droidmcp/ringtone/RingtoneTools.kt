package io.droidmcp.ringtone

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the ringtone module: [ListRingtonesTool], [GetActiveRingtoneTool], [SetRingtoneTool].
 *
 * Reports no required permissions because the read tools need none and `WRITE_SETTINGS` (needed by
 * [SetRingtoneTool]) is a special access granted via system settings rather than a runtime grant —
 * [SetRingtoneTool] checks for it at call time.
 */
object RingtoneTools {

    /** All ringtone tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ListRingtonesTool(context),
        GetActiveRingtoneTool(context),
        SetRingtoneTool(context),
    )

    // WRITE_SETTINGS is a special permission; read operations work without it
    /** Empty — see object KDoc; `WRITE_SETTINGS` is checked at call time by [SetRingtoneTool]. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; the read tools need no permissions. */
    fun hasPermissions(context: Context): Boolean = true
}
