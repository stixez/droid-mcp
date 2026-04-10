package io.droidmcp.calendar

import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class ReadCalendarTool(private val context: Context) : McpTool {

    override val name = "read_calendar"
    override val description = "Read calendar events for a given date or date range. Returns title, start/end time, location, and description."
    override val parameters = listOf(
        ToolParameter("start_date", "Start date in YYYY-MM-DD format", ParameterType.STRING, required = true),
        ToolParameter("end_date", "End date in YYYY-MM-DD format. Defaults to start_date.", ParameterType.STRING),
        ToolParameter("limit", "Max number of events to return. Default 20.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startDate = params["start_date"]?.toString()
            ?: return ToolResult.error("start_date is required")
        val endDate = params["end_date"]?.toString() ?: startDate
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

        val startMillis = try {
            dateFormat.parse(startDate)?.time ?: return ToolResult.error("Invalid start_date format")
        } catch (e: Exception) {
            return ToolResult.error("Invalid start_date: ${e.message}")
        }
        val endMillis = try {
            val parsed = dateFormat.parse(endDate) ?: return ToolResult.error("Invalid end_date format")
            parsed.time + 86_400_000 // end of day
        } catch (e: Exception) {
            return ToolResult.error("Invalid end_date: ${e.message}")
        }

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.ALL_DAY,
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC LIMIT $limit"

        val events = mutableListOf<Map<String, Any?>>()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val dtEnd = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                events.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)),
                    "title" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)),
                    "start" to timeFormat.format(Date(dtStart)),
                    "end" to timeFormat.format(Date(dtEnd)),
                    "location" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)),
                    "description" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)),
                    "all_day" to (cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1),
                ))
            }
        }

        return ToolResult.success(mapOf(
            "events" to events,
            "count" to events.size,
        ))
    }
}
