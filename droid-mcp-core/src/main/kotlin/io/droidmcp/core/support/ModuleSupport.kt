package io.droidmcp.core.support

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Helpers shared by droid-mcp tool modules. Each module depends only on core, so logic that
// several modules need lives here once instead of drifting apart in copies.

/** Android 10+ background-activity-launch rules, as far as a library can check them. */
object ActivityLaunch {

    /**
     * True if this process may start an activity right now. On API 29+ Android silently drops
     * activity starts from background apps — no exception, so a naive `startActivity` "succeeds"
     * while nothing opens. The app qualifies when its process is exactly
     * [IMPORTANCE_FOREGROUND][ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND] or
     * [IMPORTANCE_VISIBLE][ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE], or it holds
     * `SYSTEM_ALERT_WINDOW` ([Settings.canDrawOverlays]).
     *
     * A foreground *service* (importance 125, between the two) is deliberately not enough: it is
     * not a background-launch exemption, so an MCP server running in one can't open activities
     * unless the app is also visible or holds the overlay permission. Always true below API 29.
     */
    fun canStartActivity(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        if (info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        ) {
            return true
        }
        return Settings.canDrawOverlays(context)
    }
}

/** SQL `LIKE` helpers for ContentResolver selections. */
object SqlLike {

    /**
     * Escapes `\`, `%` and `_` in [value] so it matches literally inside a `LIKE` pattern. Pair
     * it with `ESCAPE '\'` in the selection, e.g. `"$COLUMN LIKE ? ESCAPE '\\'"` with the
     * argument `"%${SqlLike.escape(query)}%"`.
     */
    fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

/** Strict date parsing for tool parameters. */
object StrictDates {

    /**
     * Parses [input] (trimmed) with [pattern] in [timeZone], strictly: non-lenient, so impossible
     * dates such as `2026-02-30` or `24:00` are rejected instead of rolled over, and the whole
     * string must be consumed (no trailing text). Null when it doesn't match.
     */
    fun parse(pattern: String, input: String, timeZone: TimeZone = TimeZone.getDefault()): Date? {
        val text = input.trim()
        val format = SimpleDateFormat(pattern, Locale.US).apply {
            isLenient = false
            this.timeZone = timeZone
        }
        val pos = ParsePosition(0)
        val date = format.parse(text, pos) ?: return null
        return if (pos.errorIndex < 0 && pos.index == text.length) date else null
    }
}

/** Calendar-provider helpers shared by the calendar and alarms modules. */
object CalendarSupport {

    /**
     * The id of a calendar the app can write events to: the primary calendar if the provider
     * exposes `IS_PRIMARY`, otherwise the first visible calendar with at least contributor
     * access. Read-only (holiday, subscribed, birthday) and hidden calendars are never chosen.
     * Null if there is no such calendar. Needs `READ_CALENDAR`.
     */
    fun findWritableCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val writable = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= " +
            "${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${CalendarContract.Calendars.VISIBLE} = 1"
        // IS_PRIMARY is a computed alias on some providers, not a real column — several OEM
        // calendar providers throw SQLiteException ("no such column") for it. Swallow that and
        // fall through to the plain writable-calendar query.
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.IS_PRIMARY} = 1 AND $writable", null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
        } catch (_: Exception) {
            // Fall through.
        }
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, writable, null,
            "${CalendarContract.Calendars._ID} ASC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }
}
