package io.droidmcp.calendar

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object CalendarTools {

    fun all(context: Context): List<McpTool> = listOf(
        ReadCalendarTool(context),
        CreateEventTool(context),
        SearchEventsTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
