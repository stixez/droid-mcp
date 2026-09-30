package io.droidmcp.alarms

import android.app.AlarmManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports the next scheduled alarm clock via [AlarmManager.getNextAlarmClock]. Android has no
 * public API to list alarms (each clock app stores its own), so this is the only alarm the
 * platform exposes: the soonest one any app registered with `AlarmManager.setAlarmClock` for the
 * current user — normally the clock app's next alarm. Needs no permission.
 *
 * Output: `has_alarm`, `trigger_time` (ISO-8601 local date-time in the device timezone, e.g.
 * `2026-03-01T07:30:00`), `trigger_millis` (epoch millis), and `creator_package` (the package
 * that created the alarm's show intent, usually the clock app; null when unavailable). The last
 * three are null when no alarm is scheduled.
 */
class GetNextAlarmTool(private val context: Context) : McpTool {

    override val name = "get_next_alarm"
    override val description = "Get the next scheduled alarm clock (the soonest alarm clock registered with the system, usually by the clock app). " +
        "Android offers no API to list all alarms."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
            ?: return ToolResult.error("AlarmManager is not available on this device")
        val info = alarmManager.nextAlarmClock
        return ToolResult.success(
            AlarmsUtils.nextAlarmResult(info?.triggerTime, info?.showIntent?.creatorPackage),
        )
    }
}
