package io.droidmcp.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class GetNextAlarmTest {

    private val plus2: ZoneId = ZoneOffset.ofHours(2)

    private fun utcMillis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0, ms: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s, ms * 1_000_000).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `isoLocal renders local date-time in the given zone without fractional seconds`() {
        assertThat(AlarmsUtils.isoLocal(utcMillis(2026, 3, 1, 5, 30), plus2)).isEqualTo("2026-03-01T07:30:00")
        assertThat(AlarmsUtils.isoLocal(utcMillis(2026, 12, 31, 23, 15, 42, 999), plus2)).isEqualTo("2027-01-01T01:15:42")
    }

    @Test
    fun `result with an alarm`() {
        val millis = utcMillis(2026, 3, 1, 5, 30)
        assertThat(AlarmsUtils.nextAlarmResult(millis, "com.google.android.deskclock", plus2)).containsExactly(
            "has_alarm", true,
            "trigger_time", "2026-03-01T07:30:00",
            "trigger_millis", millis,
            "creator_package", "com.google.android.deskclock",
        )
        assertThat(AlarmsUtils.nextAlarmResult(millis, null, plus2)["creator_package"]).isNull()
    }

    @Test
    fun `result without an alarm has only nulls`() {
        assertThat(AlarmsUtils.nextAlarmResult(null, "ignored", plus2)).containsExactly(
            "has_alarm", false,
            "trigger_time", null,
            "trigger_millis", null,
            "creator_package", null,
        )
    }

    @Test
    fun `tool reads AlarmManager next alarm clock`() = runTest {
        val pending = mockk<PendingIntent> { every { creatorPackage } returns "com.android.deskclock" }
        val info = mockk<AlarmManager.AlarmClockInfo> {
            every { triggerTime } returns 1_000L
            every { showIntent } returns pending
        }
        val alarmManager = mockk<AlarmManager> { every { nextAlarmClock } returns info }
        val context = mockk<Context> { every { getSystemService(AlarmManager::class.java) } returns alarmManager }

        val data = GetNextAlarmTool(context).execute(emptyMap()).data!!
        assertThat(data["has_alarm"]).isEqualTo(true)
        assertThat(data["trigger_millis"]).isEqualTo(1_000L)
        assertThat(data["creator_package"]).isEqualTo("com.android.deskclock")

        every { alarmManager.nextAlarmClock } returns null
        assertThat(GetNextAlarmTool(context).execute(emptyMap()).data!!["has_alarm"]).isEqualTo(false)
    }
}
