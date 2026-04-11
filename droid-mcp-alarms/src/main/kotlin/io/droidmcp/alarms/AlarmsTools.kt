package io.droidmcp.alarms

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object AlarmsTools {

    fun all(context: Context): List<McpTool> = listOf(
        CreateAlarmTool(context),
        CreateTimerTool(context),
        CreateReminderTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        "com.android.alarm.permission.SET_ALARM",
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
