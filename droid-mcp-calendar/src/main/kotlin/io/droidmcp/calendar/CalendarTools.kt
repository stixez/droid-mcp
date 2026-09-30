package io.droidmcp.calendar

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the calendar tool module. Wires up [ReadCalendarTool], [SearchEventsTool],
 * [CreateEventTool], [UpdateEventTool] and [DeleteEventTool], which read/write events via
 * `CalendarContract`.
 *
 * The read tools need only `READ_CALENDAR`, so that is all [requiredPermissions] reports — a
 * host with read-only calendar access still gets the read surface. The write tools
 * ([CreateEventTool], [UpdateEventTool], [DeleteEventTool]) are gated inside [all]: they are
 * registered only when `WRITE_CALENDAR` is also granted (mirroring how `SettingsTools` gates its
 * write tools).
 */
object CalendarTools {

    /** Calendar [McpTool]s for the current grant state: read tools always, the write tools only with `WRITE_CALENDAR`. */
    fun all(context: Context): List<McpTool> = buildList {
        add(ReadCalendarTool(context))
        add(SearchEventsTool(context))
        if (PermissionHelper.hasPermissions(context, listOf(Manifest.permission.WRITE_CALENDAR))) {
            add(CreateEventTool(context))
            add(UpdateEventTool(context))
            add(DeleteEventTool(context))
        }
    }

    /** Permissions needed to register this module at all: `READ_CALENDAR` (write access is gated per tool in [all]). */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CALENDAR,
    )

    /** True if all [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
