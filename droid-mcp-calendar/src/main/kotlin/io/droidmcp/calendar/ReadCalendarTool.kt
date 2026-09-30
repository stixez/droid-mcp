package io.droidmcp.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.*
import java.util.*

/**
 * Reads calendar events in a date range via `ContentResolver` on `CalendarContract.Instances`,
 * so **recurring events are expanded** into their individual occurrences and multi-day events
 * that began before `start_date` but are still running are included. An instance is returned
 * when it *overlaps* the window `[start_date 00:00, end_date + 1 day 00:00)`. (The provider drops
 * a soft-deleted event's instance rows itself, so no `DELETED` filter is needed — `Instances`
 * doesn't expose that column.) Requires `READ_CALENDAR`.
 *
 * Timed events are windowed and formatted in the device timezone. All-day events are stored by
 * the provider at UTC-midnight boundaries, so they are windowed against the same calendar dates
 * in UTC and their `start`/`end` are formatted in UTC — an all-day event on 2024-05-01 reads back
 * as `2024-05-01 00:00` – `2024-05-02 00:00` regardless of the device's offset.
 *
 * Output: `events` (list of {id, title, start, end, location, description, all_day} with times
 * formatted `yyyy-MM-dd HH:mm`; `id` is the underlying event id, so all occurrences of a
 * recurring event share it) and `count`, sorted by instance start and capped at `limit`
 * (1–100, default 10). `start_date`/`end_date` are strict `yyyy-MM-dd`.
 */
class ReadCalendarTool(private val context: Context) : McpTool {

    override val name = "read_calendar"
    override val description = "Read calendar events for a given date or date range, including occurrences of recurring events and multi-day events overlapping the range. Returns title, start/end time, location, and description."
    override val parameters = listOf(
        ToolParameter("start_date", "Start date in YYYY-MM-DD format", ParameterType.STRING, required = true),
        ToolParameter("end_date", "End date in YYYY-MM-DD format. Defaults to start_date.", ParameterType.STRING),
        ToolParameter("limit", "Max number of events to return. Default 10.", ParameterType.INTEGER, minimum = 1.0, maximum = 100.0),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val startDate = params["start_date"]?.toString()
            ?: return ToolResult.error("start_date is required")
        val endDate = params["end_date"]?.toString() ?: startDate
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        val localStart = CalendarUtils.parseStrict("yyyy-MM-dd", startDate)
            ?: return ToolResult.error("Invalid start_date '$startDate'. Use format: YYYY-MM-DD")
        val localEndDay = CalendarUtils.parseStrict("yyyy-MM-dd", endDate)
            ?: return ToolResult.error("Invalid end_date '$endDate'. Use format: YYYY-MM-DD")
        val utcStart = CalendarUtils.parseStrict("yyyy-MM-dd", startDate, CalendarUtils.UTC)!!
        val utcEndDay = CalendarUtils.parseStrict("yyyy-MM-dd", endDate, CalendarUtils.UTC)!!
        if (localEndDay.before(localStart)) {
            return ToolResult.error("end_date must not be before start_date")
        }

        // Calendar.add (not a raw +86_400_000) so a DST transition landing inside this
        // window doesn't shift the boundary by an hour.
        val localWindowStart = localStart.time
        val localWindowEnd = Calendar.getInstance().apply {
            time = localEndDay
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val utcWindowStart = utcStart.time
        val utcWindowEnd = utcEndDay.time + DAY_MILLIS

        // Query a window wide enough to cover both the local and the UTC interpretation (UTC
        // offsets span -12h..+14h), then filter precisely per row below.
        val queryBegin = minOf(localWindowStart, utcWindowStart) - DAY_MILLIS
        val queryEnd = maxOf(localWindowEnd, utcWindowEnd) + DAY_MILLIS
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, queryBegin)
            ContentUris.appendId(it, queryEnd)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
        )
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        val events = mutableListOf<Map<String, Any?>>()

        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val descIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
            val allDayIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            while (cursor.moveToNext() && events.size < limit) {
                val begin = cursor.getLong(beginIdx)
                val end = cursor.getLong(endIdx)
                val allDay = cursor.getInt(allDayIdx) == 1
                val (winStart, winEnd) = if (allDay) {
                    utcWindowStart to utcWindowEnd
                } else {
                    localWindowStart to localWindowEnd
                }
                // Overlap test; a zero-length instance counts as occupying its start instant.
                if (begin >= winEnd || maxOf(end, begin + 1) <= winStart) continue
                events.add(mapOf(
                    "id" to cursor.getLong(idIdx),
                    "title" to cursor.getString(titleIdx),
                    "start" to CalendarUtils.formatTime(begin, allDay),
                    "end" to (if (end > 0) CalendarUtils.formatTime(end, allDay) else null),
                    "location" to cursor.getString(locIdx),
                    "description" to cursor.getString(descIdx),
                    "all_day" to allDay,
                ))
            }
        }

        return ToolResult.success(mapOf(
            "events" to events,
            "count" to events.size,
        ))
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
