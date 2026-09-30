package io.droidmcp.calendar

import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.support.StrictDates
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Snapshot of one (non-deleted) `CalendarContract.Events` row, as needed by [UpdateEventTool]
 * and [DeleteEventTool]. [dtEnd] is null when the provider stores a `DURATION` instead (recurring
 * events); [recurring] is true when the row carries an `RRULE` or `RDATE`.
 */
internal data class EventSnapshot(
    val id: Long,
    val title: String?,
    val dtStart: Long,
    val dtEnd: Long?,
    val allDay: Boolean,
    val recurring: Boolean,
    val accessLevel: Int,
)

/** Outcome of [CalendarUtils.resolveTimeUpdate] for a partial start/end/all_day update. */
internal sealed interface TimeUpdate {
    /** No time field changes; only non-time columns are written. */
    data object Unchanged : TimeUpdate

    /** The event's new `DTSTART`/`DTEND` (epoch millis) and `ALL_DAY` flag, already validated. */
    data class Changed(val startMillis: Long, val endMillis: Long, val allDay: Boolean) : TimeUpdate

    /** The requested change is invalid; [message] is returned to the caller as the tool error. */
    data class Invalid(val message: String) : TimeUpdate
}

/** Shared helpers for the calendar tools: event-time parsing, formatting and event lookup. */
internal object CalendarUtils {

    private const val DAY_MILLIS = 86_400_000L

    /** UTC — the timezone `CalendarContract` stores all-day events in (midnight UTC boundaries). */
    val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /** Formats epoch [millis] as `yyyy-MM-dd HH:mm` — in UTC for all-day events, device zone otherwise. */
    fun formatTime(millis: Long, allDay: Boolean): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = if (allDay) UTC else TimeZone.getDefault()
        }.format(Date(millis))

    /**
     * Parses an event boundary. Timed events ([allDay] false) take `yyyy-MM-dd HH:mm` in
     * [timeZone]. All-day events take `yyyy-MM-dd` (or `yyyy-MM-dd 00:00`, the shape
     * `read_calendar` prints) at UTC midnight, the form `CalendarContract` stores them in; any
     * other time of day is rejected. Strict: null on any mismatch.
     */
    fun parseEventTime(input: String, allDay: Boolean, timeZone: TimeZone = TimeZone.getDefault()): Long? {
        if (!allDay) return StrictDates.parse("yyyy-MM-dd HH:mm", input, timeZone)?.time
        StrictDates.parse("yyyy-MM-dd", input, UTC)?.let { return it.time }
        return StrictDates.parse("yyyy-MM-dd HH:mm", input, UTC)?.time?.takeIf { it % DAY_MILLIS == 0L }
    }

    /**
     * Works out the new times for a partial event update. [start]/[end]/[allDay] are the caller's
     * optional inputs; the `current*` values are the stored event's. A side that is not supplied
     * keeps its stored value, and `end > start` is checked against the *effective* pair, so moving
     * only the start past the stored end is rejected. Changing [allDay] needs both [start] and
     * [end], since a timed instant and a UTC-midnight date don't convert into each other.
     */
    fun resolveTimeUpdate(
        currentStart: Long,
        currentEnd: Long?,
        currentAllDay: Boolean,
        start: String?,
        end: String?,
        allDay: Boolean?,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): TimeUpdate {
        if (start == null && end == null && (allDay == null || allDay == currentAllDay)) return TimeUpdate.Unchanged
        val targetAllDay = allDay ?: currentAllDay
        if (targetAllDay != currentAllDay && (start == null || end == null)) {
            return TimeUpdate.Invalid("Changing all_day requires both start and end")
        }
        val format = if (targetAllDay) "YYYY-MM-DD (all-day)" else "YYYY-MM-DD HH:mm"
        val newStart = if (start != null) {
            parseEventTime(start, targetAllDay, timeZone)
                ?: return TimeUpdate.Invalid("Invalid start '$start'. Use format: $format")
        } else {
            currentStart
        }
        val newEnd = if (end != null) {
            parseEventTime(end, targetAllDay, timeZone)
                ?: return TimeUpdate.Invalid("Invalid end '$end'. Use format: $format")
        } else {
            currentEnd ?: return TimeUpdate.Invalid("The event has no stored end time; provide end as well")
        }
        if (newEnd <= newStart) {
            return TimeUpdate.Invalid(
                when {
                    start == null -> "end must be after the event's existing start"
                    end == null -> "start must be before the event's existing end"
                    else -> "end must be after start"
                },
            )
        }
        return TimeUpdate.Changed(newStart, newEnd, targetAllDay)
    }

    /**
     * Looks up event [eventId] in `CalendarContract.Events`, skipping rows already marked
     * `DELETED` (a sync adapter hasn't purged them yet). The row carries its calendar's
     * `CALENDAR_ACCESS_LEVEL`, so callers can refuse read-only calendars. Null if not found.
     */
    fun findEvent(context: Context, eventId: Long): EventSnapshot? {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.CALENDAR_ACCESS_LEVEL,
        )
        val selection = "${CalendarContract.Events._ID} = ? AND ${CalendarContract.Events.DELETED} != 1"
        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, arrayOf(eventId.toString()), null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            EventSnapshot(
                id = cursor.getLong(0),
                title = cursor.getString(1),
                dtStart = cursor.getLong(2),
                dtEnd = if (cursor.isNull(3)) null else cursor.getLong(3),
                allDay = cursor.getInt(4) == 1,
                recurring = !cursor.getString(5).isNullOrEmpty() || !cursor.getString(6).isNullOrEmpty(),
                accessLevel = cursor.getInt(7),
            )
        }
    }

    /** Reads an id param given as a number or numeric string; null if absent, non-numeric or not positive. */
    fun parseId(value: Any?): Long? =
        ((value as? Number)?.toLong() ?: value?.toString()?.trim()?.toLongOrNull())?.takeIf { it > 0 }
}
