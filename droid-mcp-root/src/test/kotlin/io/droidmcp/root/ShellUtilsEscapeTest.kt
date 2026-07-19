package io.droidmcp.root

import com.google.common.truth.Truth.assertThat
import com.topjohnwu.superuser.ShellUtils
import org.junit.jupiter.api.Test

/**
 * Spec-lock for libsu's [ShellUtils.escapedString], which [RootShellBackend]
 * uses to argv-quote values that get pasted into a single-line command
 * before being written to the persistent root shell's stdin.
 *
 * The function is libsu-maintained, but we lock the contract here so a
 * future libsu upgrade that changed the escape semantics shows up as a
 * regression instead of silently breaking command-injection defences.
 *
 * If these assertions ever start failing after a libsu version bump,
 * audit every shell-tool callsite for injection risk before adjusting.
 */
class ShellUtilsEscapeTest {

    @Test
    fun `empty string returns empty single-quotes`() {
        assertThat(ShellUtils.escapedString("")).isEqualTo("''")
    }

    @Test
    fun `simple alphanumeric value is wrapped in single quotes`() {
        // libsu unconditionally single-quotes (unlike some hand-rolled
        // quoters that skip "safe" chars). That's fine — quoting a safe
        // value is correct and ensures predictable output.
        assertThat(ShellUtils.escapedString("hello")).isEqualTo("'hello'")
    }

    @Test
    fun `embedded single quote is escaped via close-quote-backslash-quote-open-quote`() {
        // The canonical sh idiom: 'foo' + \' + 'bar' parses as foo'bar
        assertThat(ShellUtils.escapedString("foo'bar")).isEqualTo("'foo'\\''bar'")
    }

    @Test
    fun `dollar sign is suppressed by single quoting`() {
        assertThat(ShellUtils.escapedString("\$PATH")).isEqualTo("'\$PATH'")
    }

    @Test
    fun `backticks are suppressed by single quoting`() {
        assertThat(ShellUtils.escapedString("`id`")).isEqualTo("'`id`'")
    }

    @Test
    fun `semicolon cannot chain commands`() {
        // The key injection vector this guards against — a malicious value
        // like `foo; rm -rf /` becomes a single argv entry, NOT a chained
        // command.
        assertThat(ShellUtils.escapedString("a;b")).isEqualTo("'a;b'")
    }

    @Test
    fun `multiple embedded quotes all escape correctly`() {
        assertThat(ShellUtils.escapedString("'a'b'c'")).isEqualTo("''\\''a'\\''b'\\''c'\\'''")
    }
}
