package io.droidmcp.calendar

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Date
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.TimeZone

class CalendarUtilsTest {

    private fun parseDay(input: String) = CalendarUtils.parseStrict("yyyy-MM-dd", input)

    // ---- LIKE escaping ------------------------------------------------------------------------

    /** Minimal SQL `LIKE ? ESCAPE '\\'` evaluator (case-insensitive, like SQLite for ASCII). */
    private fun like(pattern: String, text: String): Boolean {
        val regex = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> { require(i + 1 < pattern.length) { "dangling escape" }; regex.append(Regex.escape(pattern[i + 1].toString())); i++ }
                c == '%' -> regex.append(".*")
                c == '_' -> regex.append(".")
                else -> regex.append(Regex.escape(c.toString()))
            }
            i++
        }
        return Regex(regex.toString(), setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).matches(text)
    }

    private fun contains(query: String, text: String) = like("%${CalendarUtils.escapeLike(query)}%", text)

    @Test
    fun `escapeLike escapes percent, underscore and backslash`() {
        assertThat(CalendarUtils.escapeLike("100%")).isEqualTo("100\\%")
        assertThat(CalendarUtils.escapeLike("a_b")).isEqualTo("a\\_b")
        assertThat(CalendarUtils.escapeLike("C:\\dir")).isEqualTo("C:\\\\dir")
        assertThat(CalendarUtils.escapeLike("plain text")).isEqualTo("plain text")
        assertThat(CalendarUtils.escapeLike("")).isEqualTo("")
    }

    @Test
    fun `escapeLike escapes backslash first so escapes are not double-escaped`() {
        // Input \% must become \\\% (escaped backslash + escaped percent), not \\% .
        assertThat(CalendarUtils.escapeLike("\\%")).isEqualTo("\\\\\\%")
        assertThat(CalendarUtils.escapeLike("\\_")).isEqualTo("\\\\\\_")
        assertThat(CalendarUtils.escapeLike("%_\\")).isEqualTo("\\%\\_\\\\")
    }

    @Test
    fun `escaped query matches only literally inside LIKE`() {
        assertThat(contains("100%", "save 100% now")).isTrue()
        assertThat(contains("100%", "save 1000 now")).isFalse()
        assertThat(contains("a_b", "x a_b y")).isTrue()
        assertThat(contains("a_b", "x aXb y")).isFalse()
        assertThat(contains("%", "no percent here")).isFalse()
        assertThat(contains("_", "")).isFalse()
        assertThat(contains("C:\\dir", "path C:\\dir\\file")).isTrue()
        assertThat(contains("C:\\dir", "path C:dir")).isFalse()
        assertThat(contains("\\%", "a\\%b")).isTrue()
        assertThat(contains("\\%", "a\\b")).isFalse()
        assertThat(contains("Meeting", "team meeting notes")).isTrue()
    }

    // ---- strict date parsing ------------------------------------------------------------------

    private fun ymd(date: Date?): Triple<Int, Int, Int>? = date?.let {
        val cal = Calendar.getInstance().apply { time = it }
        Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `parses valid dates in the device timezone`() {
        assertThat(ymd(parseDay("2026-03-01"))).isEqualTo(Triple(2026, 3, 1))
        assertThat(ymd(parseDay("2024-02-29"))).isEqualTo(Triple(2024, 2, 29))
        assertThat(ymd(parseDay("2026-12-31"))).isEqualTo(Triple(2026, 12, 31))
        assertThat(ymd(parseDay("  2026-03-01 \t"))).isEqualTo(Triple(2026, 3, 1))
        val cal = Calendar.getInstance().apply { time = parseDay("2026-03-01")!! }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(0)
    }

    @Test
    fun `rejects impossible dates instead of rolling them over`() {
        listOf("2026-02-30", "2026-02-29", "2026-04-31", "2026-13-01", "2026-00-10", "2026-01-00", "2026-01-32")
            .forEach { assertWithMessage(it).that(parseDay(it)).isNull() }
    }

    @Test
    fun `rejects trailing garbage and other formats`() {
        listOf("2026-03-01x", "2026-03-01 10:00", "2026-03-01T00:00", "2026-03-01Z", "2026/03/01",
            "01-03-2026", "March 1 2026", "", "   ", "2026-03")
            .forEach { assertWithMessage(it).that(parseDay(it)).isNull() }
    }

    private fun utcMillis(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `parseStrict honours the requested timezone`() {
        assertThat(CalendarUtils.parseStrict("yyyy-MM-dd", "2026-03-01", CalendarUtils.UTC)!!.time)
            .isEqualTo(utcMillis(2026, 3, 1))
        val plus2 = TimeZone.getTimeZone("GMT+02:00")
        assertThat(CalendarUtils.parseStrict("yyyy-MM-dd HH:mm", "2026-03-01 10:30", plus2)!!.time)
            .isEqualTo(utcMillis(2026, 3, 1, 8, 30))
    }

    @Test
    fun `parseStrict with a time pattern rejects impossible times and partial input`() {
        val p = "yyyy-MM-dd HH:mm"
        assertThat(CalendarUtils.parseStrict(p, "2026-03-01 23:59", CalendarUtils.UTC)!!.time)
            .isEqualTo(utcMillis(2026, 3, 1, 23, 59))
        listOf("2026-03-01 24:00", "2026-03-01 25:00", "2026-03-01 10:60", "2026-03-01", "2026-03-01 10:30:00",
            "2026-03-01 10:30pm", "2026-02-30 10:00")
            .forEach { assertWithMessage(it).that(CalendarUtils.parseStrict(p, it, CalendarUtils.UTC)).isNull() }
    }

    @Test
    fun `formatTime uses UTC for all-day events`() {
        assertThat(CalendarUtils.formatTime(utcMillis(2026, 3, 1), allDay = true)).isEqualTo("2026-03-01 00:00")
    }
}
