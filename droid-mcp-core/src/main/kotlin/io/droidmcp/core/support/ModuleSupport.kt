package io.droidmcp.core.support

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.CalendarContract
import android.provider.Settings
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.LocalDate
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

/** Usage access (`PACKAGE_USAGE_STATS`), a special access granted in Settings, not at runtime. */
object UsageAccess {

    /**
     * True if this app has Usage access: the `GET_USAGE_STATS` app op is allowed (the Settings
     * toggle), or left at its default with `PACKAGE_USAGE_STATS` granted (e.g. `adb shell pm grant`),
     * which the system's usage-stats service accepts too.
     */
    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        // checkOpNoThrow(String, …) was deprecated for unsafeCheckOpNoThrow on API 29–35, then
        // un-deprecated on 36 (which deprecated unsafeCheckOpNoThrow instead); both behave identically.
        return when (appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }
}

/** Suggestions for `completion/complete` completers. */
object CompletionSupport {

    /**
     * Today, the next six days, then yesterday, as `yyyy-MM-dd`, keeping those that start with
     * what the user typed ([value], trimmed).
     */
    fun upcomingDates(value: String, today: LocalDate = LocalDate.now()): List<String> =
        ((0L..6L).map { today.plusDays(it) } + today.minusDays(1))
            .map { it.toString() }
            .filter { it.startsWith(value.trim()) }
}

/** App display names for one tool call, cached so each package is looked up once. */
class AppLabels(private val packageManager: PackageManager) {
    private val cache = HashMap<String, String?>()

    /**
     * The display name of [packageName], or null when it can't be resolved. Pass [appInfo] when
     * the caller already has it (a notification carries its app's): that works even when
     * Android 11+ package visibility hides the app from `getApplicationInfo`.
     */
    fun labelFor(packageName: String, appInfo: ApplicationInfo? = null): String? {
        // A miss is retried when this call brings app info the earlier one lacked.
        if (cache.containsKey(packageName) && (cache[packageName] != null || appInfo == null)) return cache[packageName]
        val label = runCatching {
            packageManager.getApplicationLabel(appInfo ?: packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
        cache[packageName] = label
        return label
    }
}
