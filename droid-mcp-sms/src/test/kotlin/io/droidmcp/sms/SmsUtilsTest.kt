// SmsUtils deliberately uses the deprecated two-arg PhoneNumberUtils.compare (no country ISO
// is known), so the tests mock and verify that same overload.
@file:Suppress("DEPRECATION")

package io.droidmcp.sms

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import android.telephony.PhoneNumberUtils
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Date

class SmsUtilsTest {

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

    private fun contains(query: String, text: String) = like("%${SmsUtils.escapeLike(query)}%", text)

    @Test
    fun `escapeLike escapes percent, underscore and backslash`() {
        assertThat(SmsUtils.escapeLike("100%")).isEqualTo("100\\%")
        assertThat(SmsUtils.escapeLike("a_b")).isEqualTo("a\\_b")
        assertThat(SmsUtils.escapeLike("C:\\dir")).isEqualTo("C:\\\\dir")
        assertThat(SmsUtils.escapeLike("plain text")).isEqualTo("plain text")
        assertThat(SmsUtils.escapeLike("")).isEqualTo("")
    }

    @Test
    fun `escapeLike escapes backslash first so escapes are not double-escaped`() {
        // Input \% must become \\\% (escaped backslash + escaped percent), not \\% .
        assertThat(SmsUtils.escapeLike("\\%")).isEqualTo("\\\\\\%")
        assertThat(SmsUtils.escapeLike("\\_")).isEqualTo("\\\\\\_")
        assertThat(SmsUtils.escapeLike("%_\\")).isEqualTo("\\%\\_\\\\")
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
        assertThat(ymd(SmsUtils.parseDate("2026-03-01"))).isEqualTo(Triple(2026, 3, 1))
        assertThat(ymd(SmsUtils.parseDate("2024-02-29"))).isEqualTo(Triple(2024, 2, 29))
        assertThat(ymd(SmsUtils.parseDate("2026-12-31"))).isEqualTo(Triple(2026, 12, 31))
        assertThat(ymd(SmsUtils.parseDate("  2026-03-01 \t"))).isEqualTo(Triple(2026, 3, 1))
        val cal = Calendar.getInstance().apply { time = SmsUtils.parseDate("2026-03-01")!! }
        assertThat(cal.get(Calendar.HOUR_OF_DAY)).isEqualTo(0)
        assertThat(cal.get(Calendar.MINUTE)).isEqualTo(0)
    }

    @Test
    fun `rejects impossible dates instead of rolling them over`() {
        listOf("2026-02-30", "2026-02-29", "2026-04-31", "2026-13-01", "2026-00-10", "2026-01-00", "2026-01-32")
            .forEach { assertWithMessage(it).that(SmsUtils.parseDate(it)).isNull() }
    }

    @Test
    fun `rejects trailing garbage and other formats`() {
        listOf("2026-03-01x", "2026-03-01 10:00", "2026-03-01T00:00", "2026-03-01Z", "2026/03/01",
            "01-03-2026", "March 1 2026", "", "   ", "2026-03")
            .forEach { assertWithMessage(it).that(SmsUtils.parseDate(it)).isNull() }
    }

    // ---- address matching ---------------------------------------------------------------------

    @BeforeEach
    fun mockPhoneNumberUtils() {
        mockkStatic(PhoneNumberUtils::class)
        every { PhoneNumberUtils.compare(any<String>(), any<String>()) } returns false
    }

    @AfterEach
    fun unmock() = unmockkAll()

    @Test
    fun `normalizeNumber keeps digits and a leading plus only`() {
        assertThat(SmsUtils.normalizeNumber("+1 (555) 010-9999")).isEqualTo("+15550109999")
        assertThat(SmsUtils.normalizeNumber("  555.123.4567 ")).isEqualTo("5551234567")
        assertThat(SmsUtils.normalizeNumber(" +385 91 123 4567")).isEqualTo("+385911234567")
        assertThat(SmsUtils.normalizeNumber("1+2")).isEqualTo("12")
        assertThat(SmsUtils.normalizeNumber("BANK")).isEqualTo("")
        assertThat(SmsUtils.normalizeNumber("+")).isEqualTo("+")
    }

    @Test
    fun `null address never matches`() {
        assertThat(SmsUtils.addressMatches(null, "555")).isFalse()
    }

    @Test
    fun `raw substring match is case-insensitive and trims the filter`() {
        assertThat(SmsUtils.addressMatches("BANK-ALERTS", "bank")).isTrue()
        assertThat(SmsUtils.addressMatches("MyBank", "  BANK  ")).isTrue()
        assertThat(SmsUtils.addressMatches("+15551234567", "555123")).isTrue()
        assertThat(SmsUtils.addressMatches("AMAZON", "bank")).isFalse()
    }

    @Test
    fun `formatted filter matches compact stored address via digit normalization`() {
        assertThat(SmsUtils.addressMatches("+15551234567", "+1 555-123-4567")).isTrue()
        assertThat(SmsUtils.addressMatches("+15551234567", "(555) 123-4567")).isTrue()
        assertThat(SmsUtils.addressMatches("5551234567", "555 123 4567")).isTrue()
    }

    @Test
    fun `compact filter matches formatted stored address via digit normalization`() {
        assertThat(SmsUtils.addressMatches("+1 555-123-4567", "+15551234567")).isTrue()
        assertThat(SmsUtils.addressMatches("+1 (555) 010-9999", "555-0109")).isTrue()
        assertThat(SmsUtils.addressMatches("(555) 123-4567", "5551234567")).isTrue()
    }

    @Test
    fun `leading plus on the filter does not prevent a national-format match`() {
        // Filter digits drop their '+', so "+1555..." still finds "1555..." stored without it.
        assertThat(SmsUtils.addressMatches("1 555 123 4567", "+15551234567")).isTrue()
    }

    @Test
    fun `non-matching numbers fall through to PhoneNumberUtils compare`() {
        assertThat(SmsUtils.addressMatches("+15551234567", "5559999")).isFalse()
        verify(exactly = 1) { PhoneNumberUtils.compare("+15551234567", "5559999") }
    }

    @Test
    fun `PhoneNumberUtils compare catches national vs international forms`() {
        every { PhoneNumberUtils.compare("+385911234567", "0911234567") } returns true
        assertThat(SmsUtils.addressMatches("+385911234567", "0911234567")).isTrue()
    }

    @Test
    fun `short or digit-less filters never reach PhoneNumberUtils`() {
        every { PhoneNumberUtils.compare(any<String>(), any<String>()) } returns true
        assertThat(SmsUtils.addressMatches("+15551234567", "abc")).isFalse()
        assertThat(SmsUtils.addressMatches("15551234567", "+")).isFalse()
        assertThat(SmsUtils.addressMatches("+15551234567", "98")).isFalse()
        verify(exactly = 0) { PhoneNumberUtils.compare(any<String>(), any<String>()) }
    }

    @Test
    fun `digit normalization does not make alphanumeric senders match numbers`() {
        assertThat(SmsUtils.addressMatches("BANK", "555")).isFalse()
    }
}
