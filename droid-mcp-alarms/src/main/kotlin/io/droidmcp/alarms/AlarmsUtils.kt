package io.droidmcp.alarms

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Module-local helpers shared by the alarms tools. */
internal object AlarmsUtils {

    /** Error returned when an `AlarmClock` intent would be silently dropped by background-activity-launch rules. */
    const val BACKGROUND_LAUNCH_ERROR =
        "Cannot start the clock app from the background: since Android 10 the host app must be in the " +
            "foreground (visible) or hold the SYSTEM_ALERT_WINDOW (\"Display over other apps\") permission " +
            "for this tool to work"

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
