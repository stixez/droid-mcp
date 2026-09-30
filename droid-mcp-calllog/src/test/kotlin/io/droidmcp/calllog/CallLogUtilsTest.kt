package io.droidmcp.calllog

import android.provider.CallLog
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CallLogUtilsTest {

    @Test
    fun `callTypeName maps every known type and falls back to unknown`() {
        assertThat(callTypeName(CallLog.Calls.INCOMING_TYPE)).isEqualTo("incoming")
        assertThat(callTypeName(CallLog.Calls.OUTGOING_TYPE)).isEqualTo("outgoing")
        assertThat(callTypeName(CallLog.Calls.MISSED_TYPE)).isEqualTo("missed")
        assertThat(callTypeName(CallLog.Calls.REJECTED_TYPE)).isEqualTo("rejected")
        assertThat(callTypeName(CallLog.Calls.BLOCKED_TYPE)).isEqualTo("blocked")
        assertThat(callTypeName(CallLog.Calls.VOICEMAIL_TYPE)).isEqualTo("unknown")
        assertThat(callTypeName(-1)).isEqualTo("unknown")
    }

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

    private fun contains(query: String, text: String) = like("%${escapeLike(query)}%", text)

    @Test
    fun `escapeLike escapes percent, underscore and backslash`() {
        assertThat(escapeLike("100%")).isEqualTo("100\\%")
        assertThat(escapeLike("a_b")).isEqualTo("a\\_b")
        assertThat(escapeLike("C:\\dir")).isEqualTo("C:\\\\dir")
        assertThat(escapeLike("plain text")).isEqualTo("plain text")
        assertThat(escapeLike("")).isEqualTo("")
    }

    @Test
    fun `escapeLike escapes backslash first so escapes are not double-escaped`() {
        // Input \% must become \\\% (escaped backslash + escaped percent), not \\% .
        assertThat(escapeLike("\\%")).isEqualTo("\\\\\\%")
        assertThat(escapeLike("\\_")).isEqualTo("\\\\\\_")
        assertThat(escapeLike("%_\\")).isEqualTo("\\%\\_\\\\")
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
}
