package io.droidmcp.calendar

import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.*

/**
 * Searches calendar events whose `TITLE` or `DESCRIPTION` contains `query` (SQL `LIKE`
 * substring; `%`, `_` and `\` in `query` match literally), via `ContentResolver` on
 * `CalendarContract.Events`, newest first. Requires `READ_CALENDAR`. Output: `events` (list of
 * {id, title, start, end, location, description, all_day} with times formatted
 * `yyyy-MM-dd HH:mm` — device timezone for timed events, UTC for all-day events, which the
 * provider stores at UTC-midnight boundaries), `count`, and the echoed `query`, capped at
 * `limit` (1–100, default 10). Recurring events appear once (their base row), not per occurrence.
 */
class SearchEventsTool(private val context: Context) : McpTool {

    override val name = "search_events"
    override val description = "Search calendar events by keyword in title or description"
    override val parameters = listOf(
        ToolParameter("query", "Search keyword", ParameterType.STRING, required = true),
        ToolParameter("limit", "Max results. Default 10.", ParameterType.INTEGER, minimum = 1.0, maximum = 100.0),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.ALL_DAY,
        )

        val selection = "(${CalendarContract.Events.TITLE} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Events.DESCRIPTION} LIKE ? ESCAPE '\\') " +
            "AND ${CalendarContract.Events.DELETED} != 1"
        val pattern = "%${CalendarUtils.escapeLike(query)}%"
        val selectionArgs = arrayOf(pattern, pattern)
        val sortOrder = "${CalendarContract.Events.DTSTART} DESC"

        val events = mutableListOf<Map<String, Any?>>()

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val dtEnd = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                val allDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1
                events.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)),
                    "title" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)),
                    "start" to CalendarUtils.formatTime(dtStart, allDay),
                    // Recurring events store DURATION instead of DTEND, which reads back as 0.
                    "end" to (if (dtEnd > 0) CalendarUtils.formatTime(dtEnd, allDay) else null),
                    "location" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)),
                    "description" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)),
                    "all_day" to allDay,
                ))
                count++
            }
        }

        return ToolResult.success(mapOf(
            "events" to events,
            "count" to events.size,
            "query" to query,
        ))
    }
}
