package io.droidmcp.apps

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.core.support.AppLabels
import io.droidmcp.core.support.UsageAccess
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Per-app screen time from [UsageStatsManager.queryEvents], computed by [ScreenTime] from
 * activity resume/pause/stop, screen-off and shutdown/boot events (more accurate than the
 * daily-bucketed `queryUsageStats`, whose buckets don't start at local midnight). Events are read
 * from [LOOKBACK_MS] before the window so an app already open at its start is counted. The default
 * home-screen launcher is left out. Needs Usage access ([UsageAccess]); without it the tool returns
 * an error, since `queryEvents` would silently return nothing. Params: `days` (1-7, default 1 = since local midnight today; Android keeps only a few
 * days of events), `limit` (1-100, default 10), `package_name` filter. Output: the window
 * (`start`, `end`), `total_seconds`, and `apps` most-used first with `foreground_seconds` and
 * `last_used` (`yyyy-MM-dd HH:mm`, device timezone). `app_label` is null for apps hidden by
 * package visibility (no launcher activity).
 */
class GetAppUsageTool(private val context: Context) : McpTool {

    override val name = "get_app_usage"
    override val description = "Get screen time per app for today or the last few days, most-used first. Needs Usage access."
    override val parameters = listOf(
        ToolParameter("days", "Days to cover, counting today (1-7, default 1 = today since midnight).", ParameterType.INTEGER, minimum = 1.0, maximum = 7.0),
        ToolParameter("limit", "Max apps to return. Default 10.", ParameterType.INTEGER, minimum = 1.0, maximum = 100.0),
        ToolParameter("package_name", "Only report this app, e.g. com.instagram.android.", ParameterType.STRING),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        string("start")
        string("end")
        integer("total_seconds", "Screen time across all apps in the window, before limit and package_name")
        array("apps") {
            string("package_name")
            string("app_label", nullable = true)
            integer("foreground_seconds")
            string("last_used")
        }
        integer("count")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (!UsageAccess.isGranted(context)) {
            return ToolResult.error("Usage access not granted. Enable it in Settings > Apps > Special access > Usage access.")
        }
        val days = (params["days"] as? Number)?.toInt()?.coerceIn(1, 7) ?: 1
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10
        val packageName = params["package_name"]?.toString()?.takeIf { it.isNotBlank() }

        val end = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            timeInMillis = end
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ToolResult.error("Usage stats are not available on this device")
        val transitions = mutableListOf<UsageTransition>()
        val events = usm.queryEvents(start - LOOKBACK_MS, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            kindOf(event.eventType)?.let { transitions.add(UsageTransition(it, event.timeStamp, event.packageName, event.className)) }
        }

        val home = homePackage()
        val usage = ScreenTime.aggregate(transitions, start, end).filter { it.packageName != home }
        val labels = AppLabels(context.packageManager)
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val apps = usage
            .filter { packageName == null || it.packageName == packageName }
            .take(limit)
            .map {
                mapOf(
                    "package_name" to it.packageName,
                    "app_label" to labels.labelFor(it.packageName),
                    "foreground_seconds" to it.foregroundMillis / 1000,
                    "last_used" to format.format(Date(it.lastUsed)),
                )
            }

        return ToolResult.success(mapOf(
            "start" to format.format(Date(start)),
            "end" to format.format(Date(end)),
            "total_seconds" to usage.sumOf { it.foregroundMillis } / 1000,
            "apps" to apps,
            "count" to apps.size,
        ))
    }

    /** The default launcher's package, or null when none is set (the chooser resolves to `android`). */
    private fun homePackage(): String? = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()?.takeIf { it != "android" }
}

/** How far before the window events are read, to find apps already open at its start. */
private const val LOOKBACK_MS = 24 * 60 * 60 * 1000L

// ACTIVITY_RESUMED / ACTIVITY_PAUSED (API 29) carry the same values as the
// MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND constants still needed for API 28.
@Suppress("DEPRECATION")
private fun kindOf(eventType: Int): UsageTransition.Kind? = when (eventType) {
    UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageTransition.Kind.FOREGROUND
    UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageTransition.Kind.BACKGROUND
    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageTransition.Kind.STOP_ALL
    else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        when (eventType) {
            // Normally right after ACTIVITY_PAUSED (then a no-op); it closes the stretch when an
            // app died without a pause being logged.
            UsageEvents.Event.ACTIVITY_STOPPED -> UsageTransition.Kind.BACKGROUND
            UsageEvents.Event.DEVICE_SHUTDOWN -> UsageTransition.Kind.STOP_ALL
            UsageEvents.Event.DEVICE_STARTUP -> UsageTransition.Kind.STARTUP
            else -> null
        }
    } else {
        null
    }
}
