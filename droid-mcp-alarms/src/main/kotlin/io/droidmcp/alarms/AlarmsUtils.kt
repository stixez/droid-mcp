package io.droidmcp.alarms

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/** Module-local helpers shared by the alarms tools. */
internal object AlarmsUtils {

    /** Error returned when an `AlarmClock` intent would be silently dropped by background-activity-launch rules. */
    const val BACKGROUND_LAUNCH_ERROR =
        "Cannot start the clock app from the background: since Android 10 the host app must be in the " +
            "foreground (visible) or hold the SYSTEM_ALERT_WINDOW (\"Display over other apps\") permission " +
            "for this tool to work"

    /**
     * True if this process may start an activity right now. On API 29+ Android silently drops
     * activity starts from background apps (no exception is thrown, so a naive `startActivity`
     * "succeeds" while nothing happens). The app qualifies when its process is foreground/visible
     * (per [ActivityManager.getMyMemoryState]) or it holds `SYSTEM_ALERT_WINDOW`
     * ([Settings.canDrawOverlays]), the two exemptions a library can check. Always true below API 29.
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

    /**
     * Parses [input] as `yyyy-MM-dd HH:mm` (device timezone) strictly: non-lenient (impossible
     * dates/times rejected rather than rolled over) and the whole string must be consumed.
     */
    fun parseDateTime(input: String): Date? {
        val text = input.trim()
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }
        val pos = ParsePosition(0)
        val date = format.parse(text, pos) ?: return null
        return if (pos.errorIndex < 0 && pos.index == text.length) date else null
    }

    /**
     * Returns the id of a calendar the app can write events to: the primary calendar if the
     * provider exposes `IS_PRIMARY`, otherwise the first visible calendar with at least
     * contributor access. Read-only (holiday/subscribed/birthday) and hidden calendars are never
     * chosen. Null if no such calendar exists.
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
                "${CalendarContract.Calendars.IS_PRIMARY} = 1 AND $writable", null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(0)
            }
        } catch (e: Exception) {
            // Fall through.
        }
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, writable, null,
            "${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    /** [millis] as an ISO-8601 local date-time (`2026-03-01T07:30:00`) in [zone], truncated to whole seconds. */
    fun isoLocal(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /**
     * The `get_next_alarm` result map: `has_alarm` plus `trigger_time`/`trigger_millis`/
     * `creator_package`, which are all null when [triggerMillis] is null (no alarm scheduled).
     */
    fun nextAlarmResult(triggerMillis: Long?, creatorPackage: String?, zone: ZoneId = ZoneId.systemDefault()): Map<String, Any?> =
        mapOf(
            "has_alarm" to (triggerMillis != null),
            "trigger_time" to triggerMillis?.let { isoLocal(it, zone) },
            "trigger_millis" to triggerMillis,
            "creator_package" to creatorPackage?.takeIf { triggerMillis != null },
        )
}
