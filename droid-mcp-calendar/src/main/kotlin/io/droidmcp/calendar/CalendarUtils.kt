package io.droidmcp.calendar

import android.content.Context
import android.provider.CalendarContract
import java.text.ParsePosition
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

/** Shared helpers for the calendar tools: strict date parsing, LIKE escaping, calendar and event lookup. */
internal object CalendarUtils {

    private const val DAY_MILLIS = 86_400_000L

    /** UTC — the timezone `CalendarContract` stores all-day events in (midnight UTC boundaries). */
    val UTC: TimeZone = TimeZone.getTimeZone("UTC")

    /**
     * Parses [input] against [pattern] strictly: non-lenient (so `2024-02-30` or `25:00` are
     * rejected instead of rolled over) and the *whole* string must be consumed (so trailing
     * garbage like `2024-01-01xyz` is rejected). Returns null on any mismatch.
     */
    fun parseStrict(pattern: String, input: String, timeZone: TimeZone = TimeZone.getDefault()): Date? {
        val text = input.trim()
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            this.timeZone = timeZone
        }
        val pos = ParsePosition(0)
        val date = format.parse(text, pos) ?: return null
        return if (pos.errorIndex < 0 && pos.index == text.length) date else null
    }

    /** Escapes `\`, `%`, `_` so [value] matches literally inside a `LIKE ? ESCAPE '\'` clause. */
    fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /** Formats epoch [millis] as `yyyy-MM-dd HH:mm` — in UTC for all-day events, device zone otherwise. */
    fun formatTime(millis: Long, allDay: Boolean): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = if (allDay) UTC else TimeZone.getDefault()
        }.format(Date(millis))

    /**
     * Returns the id of a calendar the app can write events to: the primary calendar if the
     * provider exposes `IS_PRIMARY`, otherwise the first visible calendar with at least
     * contributor access. Read-only calendars (holidays, subscribed feeds, birthdays) and hidden
     * ones are never chosen. Null if no such calendar exists.
     */
    fun findWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val writable = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
            "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${CalendarContract.Calendars.VISIBLE} = 1"
        // IS_PRIMARY is a computed alias on some providers, not a real column — several OEM
        // calendar providers throw SQLiteException ("no such column") for it. Uncaught, that
        // would abort the tool entirely despite the perfectly good fallback query below.
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.IS_PRIMARY} = 1 AND $writable", null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
        } catch (e: Exception) {
            // Fall through to the writable-calendar query below.
        }
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, writable, null,
            "${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    /**
     * Parses an event boundary. Timed events ([allDay] false) take `yyyy-MM-dd HH:mm` in
     * [timeZone]. All-day events take `yyyy-MM-dd` (or `yyyy-MM-dd 00:00`, the shape
     * `read_calendar` prints) at UTC midnight, the form `CalendarContract` stores them in; any
     * other time of day is rejected. Strict: null on any mismatch.
     */
    fun parseEventTime(input: String, allDay: Boolean, timeZone: TimeZone = TimeZone.getDefault()): Long? {
        if (!allDay) return parseStrict("yyyy-MM-dd HH:mm", input, timeZone)?.time
        parseStrict("yyyy-MM-dd", input, UTC)?.let { return it.time }
        return parseStrict("yyyy-MM-dd HH:mm", input, UTC)?.time?.takeIf { it % DAY_MILLIS == 0L }
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
