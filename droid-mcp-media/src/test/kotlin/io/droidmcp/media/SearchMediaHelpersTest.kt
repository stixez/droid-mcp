package io.droidmcp.media

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Date
import io.mockk.mockk

class SearchMediaHelpersTest {

    private val tool = SearchMediaTool(mockk(relaxed = true))

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

    private fun contains(query: String, text: String) = like("%${tool.escapeLike(query)}%", text)

    @Test
    fun `escapeLike escapes percent, underscore and backslash`() {
        assertThat(tool.escapeLike("100%")).isEqualTo("100\\%")
        assertThat(tool.escapeLike("a_b")).isEqualTo("a\\_b")
        assertThat(tool.escapeLike("C:\\dir")).isEqualTo("C:\\\\dir")
        assertThat(tool.escapeLike("plain text")).isEqualTo("plain text")
        assertThat(tool.escapeLike("")).isEqualTo("")
    }

    @Test
    fun `escapeLike escapes backslash first so escapes are not double-escaped`() {
        // Input \% must become \\\% (escaped backslash + escaped percent), not \\% .
        assertThat(tool.escapeLike("\\%")).isEqualTo("\\\\\\%")
        assertThat(tool.escapeLike("\\_")).isEqualTo("\\\\\\_")
        assertThat(tool.escapeLike("%_\\")).isEqualTo("\\%\\_\\\\")
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
        assertThat(ymd(tool.parseDate("2026-03-01"))).isEqualTo(Triple(2026, 3, 1))
        assertThat(ymd(tool.parseDate("2024-02-29"))).isEqualTo(Triple(2024, 2, 29))
        assertThat(ymd(tool.parseDate("2026-12-31"))).isEqualTo(Triple(2026, 12, 31))
        assertThat(ymd(tool.parseDate("  2026-03-01 \t"))).isEqualTo(Triple(2026, 3, 1))
        val cal = Calendar.getInstance().apply { time = tool.parseDate("2026-03-01")!! }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(0)
    }

    @Test
    fun `rejects impossible dates instead of rolling them over`() {
        listOf("2026-02-30", "2026-02-29", "2026-04-31", "2026-13-01", "2026-00-10", "2026-01-00", "2026-01-32")
            .forEach { assertWithMessage(it).that(tool.parseDate(it)).isNull() }
    }

    @Test
    fun `rejects trailing garbage and other formats`() {
        listOf("2026-03-01x", "2026-03-01 10:00", "2026-03-01T00:00", "2026-03-01Z", "2026/03/01",
            "01-03-2026", "March 1 2026", "", "   ", "2026-03")
            .forEach { assertWithMessage(it).that(tool.parseDate(it)).isNull() }
    }
}
