package io.droidmcp.alarms

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.text.SimpleDateFormat
import java.util.*

class CreateReminderTool(private val context: Context) : McpTool {

    override val name = "create_reminder"
    override val description = "Create a reminder as a calendar event with an alert. Requires READ_CALENDAR and WRITE_CALENDAR permissions."
    override val parameters = listOf(
        ToolParameter("title", "Title of the reminder", ParameterType.STRING, required = true),
        ToolParameter("datetime", "Date and time in YYYY-MM-DD HH:mm format", ParameterType.STRING, required = true),
        ToolParameter("minutes_before", "Minutes before the event to trigger the alert (default: 10)", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val readGranted = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val writeGranted = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!readGranted || !writeGranted) {
            return ToolResult.error("READ_CALENDAR and WRITE_CALENDAR permissions are required to create reminders")
        }

        val title = params["title"]?.toString()
            ?: return ToolResult.error("title is required")
        val datetimeStr = params["datetime"]?.toString()
            ?: return ToolResult.error("datetime is required")
        val minutesBefore = (params["minutes_before"] as? Number)?.toInt() ?: 10

        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val startMillis = try {
            format.parse(datetimeStr)?.time ?: return ToolResult.error("Invalid datetime format")
        } catch (e: Exception) {
            return ToolResult.error("Invalid datetime: ${e.message}")
        }

        val calendarId = getPrimaryCalendarId()
            ?: return ToolResult.error("No calendar found on device")

        val eventValues = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + 30 * 60 * 1000L) // 30 min event
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }

        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
            ?: return ToolResult.error("Failed to create reminder event")

        val eventId = eventUri.lastPathSegment?.toLongOrNull()
            ?: return ToolResult.error("Failed to get event ID")

        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

        return ToolResult.success(mapOf(
            "success" to true,
            "event_id" to eventId,
            "title" to title,
            "datetime" to datetimeStr,
            "minutes_before" to minutesBefore,
        ))
    }

    private fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.IS_PRIMARY} = 1"
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }
}
