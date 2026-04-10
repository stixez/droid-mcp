package io.droidmcp.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class CreateEventTool(private val context: Context) : McpTool {

    override val name = "create_event"
    override val description = "Create a new calendar event with title, date/time, optional location and description"
    override val parameters = listOf(
        ToolParameter("title", "Event title", ParameterType.STRING, required = true),
        ToolParameter("start", "Start date/time in YYYY-MM-DD HH:mm format", ParameterType.STRING, required = true),
        ToolParameter("end", "End date/time in YYYY-MM-DD HH:mm format", ParameterType.STRING, required = true),
        ToolParameter("location", "Event location", ParameterType.STRING),
        ToolParameter("description", "Event description", ParameterType.STRING),
        ToolParameter("calendar_id", "Calendar ID. Defaults to primary calendar.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val title = params["title"]?.toString()
            ?: return ToolResult.error("title is required")
        val startStr = params["start"]?.toString()
            ?: return ToolResult.error("start is required")
        val endStr = params["end"]?.toString()
            ?: return ToolResult.error("end is required")

        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val startMillis = try {
            format.parse(startStr)?.time ?: return ToolResult.error("Invalid start format")
        } catch (e: Exception) {
            return ToolResult.error("Invalid start: ${e.message}")
        }
        val endMillis = try {
            format.parse(endStr)?.time ?: return ToolResult.error("Invalid end format")
        } catch (e: Exception) {
            return ToolResult.error("Invalid end: ${e.message}")
        }

        val calendarId = (params["calendar_id"] as? Number)?.toLong() ?: getPrimaryCalendarId()
            ?: return ToolResult.error("No calendar found on device")

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            params["location"]?.toString()?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            params["description"]?.toString()?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return ToolResult.error("Failed to create event")

        val eventId = uri.lastPathSegment?.toLongOrNull()

        return ToolResult.success(mapOf(
            "event_id" to eventId,
            "title" to title,
            "start" to startStr,
            "end" to endStr,
        ))
    }

    private fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.IS_PRIMARY} = 1"
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        // Fallback: get first available calendar
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return null
    }
}
