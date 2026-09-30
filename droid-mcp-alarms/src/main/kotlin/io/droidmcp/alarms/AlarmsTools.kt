package io.droidmcp.alarms

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the alarms module: [CreateAlarmTool], [CreateTimerTool], and [CreateReminderTool].
 * Alarm/timer tools rely only on `SET_ALARM` (a normal, install-time permission) and the system
 * clock app, so they are always registered. The reminder tool writes a calendar event and is
 * registered only when READ/WRITE_CALENDAR are granted — it is gated inside [all] (mirroring how
 * `SettingsTools` gates its write tools) so missing calendar access never hides alarms/timers.
 */
object AlarmsTools {

    private val calendarPermissions = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    /** Tools for the current grant state: alarm + timer always, [CreateReminderTool] only with calendar access. */
    fun all(context: Context): List<McpTool> = buildList {
        add(CreateAlarmTool(context))
        add(CreateTimerTool(context))
        if (PermissionHelper.hasPermissions(context, calendarPermissions)) {
            add(CreateReminderTool(context))
        }
    }

    /**
     * Permissions needed to register the module at all: just `SET_ALARM`. The calendar
     * permissions used by [CreateReminderTool] are optional and checked per tool in [all].
     */
    fun requiredPermissions(): List<String> = listOf(
        "com.android.alarm.permission.SET_ALARM",
    )

    /** `true` only when every entry in [requiredPermissions] is granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
