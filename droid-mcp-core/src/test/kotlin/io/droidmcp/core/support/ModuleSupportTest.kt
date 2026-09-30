package io.droidmcp.core.support

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class ModuleSupportTest {

    // ---- SqlLike ------------------------------------------------------------------------------

    /** Minimal SQL `LIKE ? ESCAPE '\\'` evaluator (case-insensitive, like SQLite for ASCII). */
    private fun like(pattern: String, text: String): Boolean {
        val regex = StringBuilder()
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> {
                    require(i + 1 < pattern.length) { "dangling escape" }
                    regex.append(Regex.escape(pattern[i + 1].toString()))
                    i++
                }
                c == '%' -> regex.append(".*")
                c == '_' -> regex.append(".")
                else -> regex.append(Regex.escape(c.toString()))
            }
            i++
        }
        return Regex(regex.toString(), setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).matches(text)
    }

    private fun contains(query: String, text: String) = like("%${SqlLike.escape(query)}%", text)

    @Test
    fun `escape makes LIKE wildcards literal`() {
        assertThat(SqlLike.escape("50%_off\\")).isEqualTo("50\\%\\_off\\\\")
        assertThat(SqlLike.escape("100%")).isEqualTo("100\\%")
        assertThat(SqlLike.escape("a_b")).isEqualTo("a\\_b")
        assertThat(SqlLike.escape("C:\\dir")).isEqualTo("C:\\\\dir")
        assertThat(SqlLike.escape("plain")).isEqualTo("plain")
        assertThat(SqlLike.escape("plain text")).isEqualTo("plain text")
        assertThat(SqlLike.escape("")).isEqualTo("")
    }

    @Test
    fun `escape handles backslash first so escapes are not double-escaped`() {
        // Input \% must become \\\% (escaped backslash + escaped percent), not \\% .
        assertThat(SqlLike.escape("\\%")).isEqualTo("\\\\\\%")
        assertThat(SqlLike.escape("\\_")).isEqualTo("\\\\\\_")
        assertThat(SqlLike.escape("%_\\")).isEqualTo("\\%\\_\\\\")
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

    // ---- StrictDates --------------------------------------------------------------------------

    private fun utcMillis(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Calendar fields of [date] in the device timezone: year, month (1-based), day, hour, minute. */
    private fun fields(date: Date?): List<Int>? = date?.let {
        val c = Calendar.getInstance().apply { time = it }
        listOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun day(input: String) = StrictDates.parse("yyyy-MM-dd", input)
    private fun dateTime(input: String) = StrictDates.parse("yyyy-MM-dd HH:mm", input)

    @Test
    fun `strict parse rejects impossible dates, overflow and trailing text`() {
        val utc = TimeZone.getTimeZone("UTC")
        assertThat(StrictDates.parse("yyyy-MM-dd", "2026-02-28", utc)).isNotNull()
        assertThat(StrictDates.parse("yyyy-MM-dd", " 2026-02-28 ", utc)).isNotNull()
        assertThat(StrictDates.parse("yyyy-MM-dd", "2026-02-30", utc)).isNull()
        assertThat(StrictDates.parse("yyyy-MM-dd", "2026-02-28x", utc)).isNull()
        assertThat(StrictDates.parse("yyyy-MM-dd HH:mm", "2026-02-28 24:00", utc)).isNull()
        assertThat(StrictDates.parse("yyyy-MM-dd HH:mm", "2026-02-28 23:59", utc)!!.time)
            .isEqualTo(1772323140000L)
    }

    @Test
    fun `date pattern parses valid dates at local midnight in the default timezone`() {
        assertThat(fields(day("2026-03-01"))).containsExactly(2026, 3, 1, 0, 0).inOrder()
        assertThat(fields(day("2024-02-29"))).containsExactly(2024, 2, 29, 0, 0).inOrder()
        assertThat(fields(day("2026-12-31"))).containsExactly(2026, 12, 31, 0, 0).inOrder()
        assertThat(fields(day("  2026-03-01 \t"))).containsExactly(2026, 3, 1, 0, 0).inOrder()
    }

    @Test
    fun `date pattern rejects impossible dates instead of rolling them over`() {
        listOf("2026-02-30", "2026-02-29", "2026-04-31", "2026-13-01", "2026-00-10", "2026-01-00", "2026-01-32")
            .forEach { assertWithMessage(it).that(day(it)).isNull() }
    }

    @Test
    fun `date pattern rejects trailing garbage and other formats`() {
        listOf("2026-03-01x", "2026-03-01 10:00", "2026-03-01T00:00", "2026-03-01Z", "2026/03/01",
            "01-03-2026", "March 1 2026", "", "   ", "2026-03")
            .forEach { assertWithMessage(it).that(day(it)).isNull() }
    }

    @Test
    fun `date-time pattern parses valid values in the default timezone`() {
        assertThat(fields(dateTime("2026-03-01 07:30"))).containsExactly(2026, 3, 1, 7, 30).inOrder()
        assertThat(fields(dateTime("2026-12-31 23:59"))).containsExactly(2026, 12, 31, 23, 59).inOrder()
        assertThat(fields(dateTime("2024-02-29 00:00"))).containsExactly(2024, 2, 29, 0, 0).inOrder()
        assertThat(fields(dateTime("  2026-03-01 07:30\n"))).containsExactly(2026, 3, 1, 7, 30).inOrder()
    }

    @Test
    fun `date-time pattern rejects impossible dates and times`() {
        listOf("2026-02-30 10:00", "2026-02-29 10:00", "2026-13-01 10:00", "2026-04-31 10:00",
            "2026-03-01 24:00", "2026-03-01 25:00", "2026-03-01 10:60", "2026-03-01 -1:00")
            .forEach { assertWithMessage(it).that(dateTime(it)).isNull() }
    }

    @Test
    fun `date-time pattern rejects partial input, trailing garbage and other formats`() {
        listOf("2026-03-01", "2026-03-01 10", "2026-03-01 10:00:00", "2026-03-01 10:00x", "2026-03-01T10:00",
            "2026-03-01 10:00 PM", "2026-03-01 10:30pm", "03/01/2026 10:00", "", "  ")
            .forEach { assertWithMessage(it).that(dateTime(it)).isNull() }
    }

    @Test
    fun `strict parse honours the requested timezone`() {
        val utc = TimeZone.getTimeZone("UTC")
        assertThat(StrictDates.parse("yyyy-MM-dd", "2026-03-01", utc)!!.time).isEqualTo(utcMillis(2026, 3, 1))
        assertThat(StrictDates.parse("yyyy-MM-dd HH:mm", "2026-03-01 23:59", utc)!!.time)
            .isEqualTo(utcMillis(2026, 3, 1, 23, 59))
        val plus2 = TimeZone.getTimeZone("GMT+02:00")
        assertThat(StrictDates.parse("yyyy-MM-dd HH:mm", "2026-03-01 10:30", plus2)!!.time)
            .isEqualTo(utcMillis(2026, 3, 1, 8, 30))
    }
}
