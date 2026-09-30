package io.droidmcp.calendar

import android.content.Context
import android.provider.CalendarContract
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Shared helpers for the calendar tools: strict date parsing, LIKE escaping, calendar lookup. */
internal object CalendarUtils {

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
}
