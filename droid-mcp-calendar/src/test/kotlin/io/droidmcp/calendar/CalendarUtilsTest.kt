package io.droidmcp.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class CalendarUtilsTest {

    private fun utcMillis(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `formatTime uses UTC for all-day events`() {
        assertThat(CalendarUtils.formatTime(utcMillis(2026, 3, 1), allDay = true)).isEqualTo("2026-03-01 00:00")
    }
}
