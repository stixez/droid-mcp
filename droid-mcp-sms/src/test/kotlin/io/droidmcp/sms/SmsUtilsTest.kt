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

class SmsUtilsTest {

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

    // ---- SQL address pre-filter ---------------------------------------------------------------

    @Test
    fun `numeric filter pre-filters on its last seven digits in order`() {
        val f = SmsUtils.addressPrefilter("+1 (555) 010-9999")!!
        assertThat(f.selection).isEqualTo("address LIKE ? ESCAPE '\\'")
        assertThat(f.arg).isEqualTo("%0%1%0%9%9%9%9%")
        assertThat(SmsUtils.addressPrefilter("555-0109")!!.arg).isEqualTo("%5%5%5%0%1%0%9%")
        assertThat(SmsUtils.addressPrefilter("1234")!!.arg).isEqualTo("%1%2%3%4%")
    }

    @Test
    fun `alphanumeric filter pre-filters on the escaped literal text`() {
        assertThat(SmsUtils.addressPrefilter(" BANK ")!!.arg).isEqualTo("%BANK%")
        assertThat(SmsUtils.addressPrefilter("A%_B")!!.arg).isEqualTo("%A\\%\\_B%")
    }

    @Test
    fun `no pre-filter when LIKE could not safely narrow the rows`() {
        // 1-3 digits: too short to pin down, and addressMatches still accepts digit substrings.
        assertThat(SmsUtils.addressPrefilter("555")).isNull()
        assertThat(SmsUtils.addressPrefilter("ACME 1")).isNull()
        // SQLite LIKE folds case for ASCII only; addressMatches ignores case for all letters.
        assertThat(SmsUtils.addressPrefilter("Банк")).isNull()
        assertThat(SmsUtils.addressPrefilter("   ")).isNull()
    }

    @Test
    fun `pre-filter keeps every address that addressMatches accepts`() {
        every { PhoneNumberUtils.compare("+385911234567", "0911234567") } returns true
        listOf(
            "BANK" to "bank",
            "My-BANK" to "BANK",
            "+15551234567" to "+1 555-123-4567",
            "+15551234567" to "(555) 123-4567",
            "5551234567" to "555 123 4567",
            "+1 555-123-4567" to "+15551234567",
            "+1 (555) 010-9999" to "555-0109",
            "(555) 123-4567" to "5551234567",
            "1 555 123 4567" to "+15551234567",
            "+385911234567" to "0911234567",
            "06 12 34 56 78" to "0612345678",
        ).forEach { (address, filter) ->
            assertWithMessage("$address / $filter").that(SmsUtils.addressMatches(address, filter)).isTrue()
            val prefilter = SmsUtils.addressPrefilter(filter) ?: return@forEach
            assertWithMessage("$address / $filter -> ${prefilter.arg}").that(like(prefilter.arg, address)).isTrue()
        }
    }

    @Test
    fun `pre-filter excludes numbers without the filter's trailing digits`() {
        val arg = SmsUtils.addressPrefilter("555-0109")!!.arg
        assertThat(like(arg, "+1 (555) 999-0000")).isFalse()
        assertThat(like(arg, "BANK")).isFalse()
    }
}
