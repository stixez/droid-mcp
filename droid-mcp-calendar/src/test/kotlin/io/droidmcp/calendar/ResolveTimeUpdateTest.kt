package io.droidmcp.calendar

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

class ResolveTimeUpdateTest {

    private val plus2: TimeZone = TimeZone.getTimeZone("GMT+02:00")

    private fun utcMillis(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    // A timed event 2026-03-01 10:00-11:00 at GMT+2 (08:00-09:00 UTC).
    private val timedStart = utcMillis(2026, 3, 1, 8)
    private val timedEnd = utcMillis(2026, 3, 1, 9)

    // An all-day event on 2026-03-01 (stored as UTC midnight to next UTC midnight).
    private val allDayStart = utcMillis(2026, 3, 1)
    private val allDayEnd = utcMillis(2026, 3, 2)

    private fun timed(start: String? = null, end: String? = null, allDay: Boolean? = null, currentEnd: Long? = timedEnd) =
        CalendarUtils.resolveTimeUpdate(timedStart, currentEnd, false, start, end, allDay, plus2)

    private fun allDayEvent(start: String? = null, end: String? = null, allDay: Boolean? = null) =
        CalendarUtils.resolveTimeUpdate(allDayStart, allDayEnd, true, start, end, allDay, plus2)

    private fun assertInvalid(result: TimeUpdate, messagePart: String) {
        assertThat(result).isInstanceOf(TimeUpdate.Invalid::class.java)
        assertThat((result as TimeUpdate.Invalid).message).contains(messagePart)
    }

    @Test
    fun `no time fields means unchanged`() {
        assertThat(timed()).isEqualTo(TimeUpdate.Unchanged)
        assertThat(timed(allDay = false)).isEqualTo(TimeUpdate.Unchanged)
        assertThat(allDayEvent(allDay = true)).isEqualTo(TimeUpdate.Unchanged)
    }

    @Test
    fun `both sides replaced and parsed in the given zone`() {
        assertThat(timed(start = "2026-03-02 14:00", end = "2026-03-02 15:30"))
            .isEqualTo(TimeUpdate.Changed(utcMillis(2026, 3, 2, 12), utcMillis(2026, 3, 2, 13, 30), false))
    }

    @Test
    fun `only end given is validated against the stored start`() {
        assertThat(timed(end = "2026-03-01 12:00"))
            .isEqualTo(TimeUpdate.Changed(timedStart, utcMillis(2026, 3, 1, 10), false))
        assertInvalid(timed(end = "2026-03-01 09:00"), "existing start")
        assertInvalid(timed(end = "2026-03-01 10:00"), "existing start")
    }

    @Test
    fun `only start given is validated against the stored end`() {
        assertThat(timed(start = "2026-03-01 09:30"))
            .isEqualTo(TimeUpdate.Changed(utcMillis(2026, 3, 1, 7, 30), timedEnd, false))
        assertInvalid(timed(start = "2026-03-01 11:00"), "existing end")
        assertInvalid(timed(start = "2026-03-02 09:00"), "existing end")
    }

    @Test
    fun `explicit pair must be ordered`() {
        assertInvalid(timed(start = "2026-03-02 10:00", end = "2026-03-02 10:00"), "end must be after start")
        assertInvalid(timed(start = "2026-03-02 10:00", end = "2026-03-02 09:00"), "end must be after start")
    }

    @Test
    fun `strict parsing rejects bad input`() {
        listOf("2026-02-30 10:00", "2026-03-01 25:00", "2026-03-01", "2026-03-01 10:00x", "tomorrow")
            .forEach { assertWithMessage(it).that(timed(start = it)).isInstanceOf(TimeUpdate.Invalid::class.java) }
        assertInvalid(timed(end = "nope"), "Invalid end 'nope'")
    }

    @Test
    fun `missing stored end needs an explicit end`() {
        assertInvalid(timed(start = "2026-03-01 09:00", currentEnd = null), "no stored end")
        assertThat(timed(start = "2026-03-01 09:00", end = "2026-03-01 09:30", currentEnd = null))
            .isInstanceOf(TimeUpdate.Changed::class.java)
    }

    @Test
    fun `changing all_day requires both sides`() {
        assertInvalid(timed(allDay = true), "requires both start and end")
        assertInvalid(timed(start = "2026-03-05", allDay = true), "requires both start and end")
        assertInvalid(allDayEvent(end = "2026-03-01 12:00", allDay = false), "requires both start and end")
    }

    @Test
    fun `timed to all-day stores UTC midnights`() {
        assertThat(timed(start = "2026-03-05", end = "2026-03-06", allDay = true))
            .isEqualTo(TimeUpdate.Changed(utcMillis(2026, 3, 5), utcMillis(2026, 3, 6), true))
        // read_calendar prints all-day bounds as "yyyy-MM-dd 00:00"; that shape round-trips.
        assertThat(timed(start = "2026-03-05 00:00", end = "2026-03-07 00:00", allDay = true))
            .isEqualTo(TimeUpdate.Changed(utcMillis(2026, 3, 5), utcMillis(2026, 3, 7), true))
        assertInvalid(timed(start = "2026-03-05 10:00", end = "2026-03-06", allDay = true), "all-day")
    }

    @Test
    fun `all-day to timed parses in the device zone`() {
        assertThat(allDayEvent(start = "2026-03-01 10:00", end = "2026-03-01 11:00", allDay = false))
            .isEqualTo(TimeUpdate.Changed(timedStart, timedEnd, false))
    }

    @Test
    fun `all-day partial update keeps the other stored side`() {
        assertThat(allDayEvent(end = "2026-03-04"))
            .isEqualTo(TimeUpdate.Changed(allDayStart, utcMillis(2026, 3, 4), true))
        assertInvalid(allDayEvent(end = "2026-03-01"), "existing start")
        assertInvalid(allDayEvent(start = "2026-03-02"), "existing end")
    }

    @Test
    fun `parseId accepts positive numbers and numeric strings`() {
        assertThat(CalendarUtils.parseId(42)).isEqualTo(42L)
        assertThat(CalendarUtils.parseId(42.0)).isEqualTo(42L)
        assertThat(CalendarUtils.parseId(" 7 ")).isEqualTo(7L)
        listOf(null, 0, -3, "abc", "", "1.5").forEach { assertWithMessage("$it").that(CalendarUtils.parseId(it)).isNull() }
    }
}
