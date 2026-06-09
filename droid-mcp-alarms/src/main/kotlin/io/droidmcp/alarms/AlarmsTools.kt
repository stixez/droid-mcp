package io.droidmcp.alarms

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the alarms module: [CreateAlarmTool], [CreateTimerTool], and [CreateReminderTool].
 * Alarm/timer tools rely on `SET_ALARM` (and the system clock app); the reminder tool writes a
 * calendar event and needs read/write calendar access.
 */
object AlarmsTools {

    /** All tools in this module: [CreateAlarmTool], [CreateTimerTool], [CreateReminderTool]. */
    fun all(context: Context): List<McpTool> = listOf(
        CreateAlarmTool(context),
        CreateTimerTool(context),
        CreateReminderTool(context),
    )

    /** Union of permissions across the module: `SET_ALARM` plus READ/WRITE_CALENDAR (for reminders). */
    fun requiredPermissions(): List<String> = listOf(
        "com.android.alarm.permission.SET_ALARM",
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    /** `true` only when every entry in [requiredPermissions] is granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
