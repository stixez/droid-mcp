package io.droidmcp.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.*
import java.util.*

/**
 * Inserts a new event into `CalendarContract.Events` via `ContentResolver`. Requires
 * `WRITE_CALENDAR` (plus `READ_CALENDAR` for the default-calendar lookup). `start`/`end` are
 * parsed strictly as `yyyy-MM-dd HH:mm` (device timezone; impossible dates and trailing text are
 * rejected) and `end` must be after `start`. When `calendar_id` is omitted it falls back to the
 * primary calendar, else the first visible calendar with at least contributor access (see
 * [CalendarUtils.findWritableCalendarId]). Output: `event_id` (the inserted row id, may be null if the URI
 * lacks a numeric segment), plus echoed `title`, `start`, and `end`.
 */
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
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val title = params["title"]?.toString()
            ?: return ToolResult.error("title is required")
        val startStr = params["start"]?.toString()
            ?: return ToolResult.error("start is required")
        val endStr = params["end"]?.toString()
            ?: return ToolResult.error("end is required")

        val startMillis = CalendarUtils.parseStrict("yyyy-MM-dd HH:mm", startStr)?.time
            ?: return ToolResult.error("Invalid start '$startStr'. Use format: YYYY-MM-DD HH:mm")
        val endMillis = CalendarUtils.parseStrict("yyyy-MM-dd HH:mm", endStr)?.time
            ?: return ToolResult.error("Invalid end '$endStr'. Use format: YYYY-MM-DD HH:mm")
        if (endMillis <= startMillis) {
            return ToolResult.error("end must be after start")
        }

        val calendarId = (params["calendar_id"] as? Number)?.toLong() ?: CalendarUtils.findWritableCalendarId(context)
            ?: return ToolResult.error("No writable calendar found on device")

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
}
