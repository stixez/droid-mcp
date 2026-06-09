package io.droidmcp.calendar

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the calendar tool module. Wires up [ReadCalendarTool], [CreateEventTool],
 * and [SearchEventsTool], which read/write events via `CalendarContract`. Requires
 * `READ_CALENDAR` (reads) and `WRITE_CALENDAR` (event creation).
 */
object CalendarTools {

    /** Returns all calendar [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ReadCalendarTool(context),
        CreateEventTool(context),
        SearchEventsTool(context),
    )

    /** Permissions this module needs: `READ_CALENDAR` and `WRITE_CALENDAR`. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    /** True if all [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
