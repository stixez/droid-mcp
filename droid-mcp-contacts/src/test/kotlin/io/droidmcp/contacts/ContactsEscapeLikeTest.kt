package io.droidmcp.contacts

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ContactsEscapeLikeTest {

    private val tool = SearchContactsTool(mockk(relaxed = true))

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
}
