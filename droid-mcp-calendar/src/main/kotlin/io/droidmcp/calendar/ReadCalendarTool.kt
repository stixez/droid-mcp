package io.droidmcp.calendar

import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reads calendar events in a date range via `ContentResolver` on `CalendarContract.Events`,
 * filtered by `DTSTART` between `start_date` and end-of-`end_date` (soft-deleted rows excluded
 * via `DELETED != 1`). Requires `READ_CALENDAR`.
 *
 * Queries `Events` directly rather than `CalendarContract.Instances`, so **recurring events are
 * not expanded** — a repeating event's individual occurrences won't appear here, only its
 * single base row (whose own `DTSTART` may or may not fall in range). A recurring event stores
 * `DURATION` instead of `DTEND`, which reads back as `0` — `end` is reported as `null` in that
 * case rather than the epoch-derived `"1970-01-01 00:00"`.
 *
 * Output: `events` (list of {id, title, start, end, location, description, all_day} with times
 * formatted `yyyy-MM-dd HH:mm`) and `count`, capped at `limit` (1–100, default 10).
 */
class ReadCalendarTool(private val context: Context) : McpTool {

    override val name = "read_calendar"
    override val description = "Read calendar events for a given date or date range. Returns title, start/end time, location, and description."
    override val parameters = listOf(
        ToolParameter("start_date", "Start date in YYYY-MM-DD format", ParameterType.STRING, required = true),
        ToolParameter("end_date", "End date in YYYY-MM-DD format. Defaults to start_date.", ParameterType.STRING),
        ToolParameter("limit", "Max number of events to return. Default 10.", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startDate = params["start_date"]?.toString()
            ?: return ToolResult.error("start_date is required")
        val endDate = params["end_date"]?.toString() ?: startDate
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        val startMillis = try {
            dateFormat.parse(startDate)?.time ?: return ToolResult.error("Invalid start_date format")
        } catch (e: Exception) {
            return ToolResult.error("Invalid start_date: ${e.message}")
        }
        val endMillis = try {
            val parsed = dateFormat.parse(endDate) ?: return ToolResult.error("Invalid end_date format")
            // Calendar.add (not a raw +86_400_000) so a DST transition landing inside this
            // window doesn't shift the boundary by an hour.
            val cal = Calendar.getInstance()
            cal.time = parsed
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.timeInMillis
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

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ? " +
            "AND ${CalendarContract.Events.DELETED} != 1"
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val events = mutableListOf<Map<String, Any?>>()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val dtEnd = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                events.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)),
                    "title" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)),
                    "start" to timeFormat.format(Date(dtStart)),
                    // Recurring events store DURATION instead of DTEND, which reads back as 0 —
                    // format that as null rather than the misleading "1970-01-01 00:00".
                    "end" to (if (dtEnd > 0) timeFormat.format(Date(dtEnd)) else null),
                    "location" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)),
                    "description" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)),
                    "all_day" to (cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1),
                ))
                count++
            }
        }

        return ToolResult.success(mapOf(
            "events" to events,
            "count" to events.size,
        ))
    }
}
