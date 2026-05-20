package io.droidmcp.shell

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RunShellAllowlistTest {

    private val context: android.content.Context = mockk(relaxed = true)

    @BeforeEach
    fun reset() {
        ShellAllowlist.set(emptySet())
    }

    @AfterEach
    fun teardown() {
        ShellAllowlist.set(emptySet())
    }

    @Test
    fun `empty allowlist refuses all commands`() = runTest {
        val shell = FakeShellBackend()
        val result = RunShellTool(shell).execute(mapOf("command" to "pm list packages"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("run_shell_not_enabled")
        // The shell should not have been called.
        assertThat(shell.invocations).isEmpty()
    }

    @Test
    fun `prefix match permits the command`() = runTest {
        ShellAllowlist.set(setOf("pm "))
        val shell = FakeShellBackend().apply { stubAlwaysSucceed("ok") }
        val result = RunShellTool(shell).execute(mapOf("command" to "pm list packages"))
        assertThat(result.isSuccess).isTrue()
        assertThat(shell.invocations.single()).isEqualTo("pm" to listOf("list", "packages"))
    }

    @Test
    fun `non-matching command is rejected even with non-empty allowlist`() = runTest {
        ShellAllowlist.set(setOf("pm ", "am "))
        val shell = FakeShellBackend()
        val result = RunShellTool(shell).execute(mapOf("command" to "rm -rf /sdcard"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("run_shell_not_enabled")
        assertThat(shell.invocations).isEmpty()
    }

    @Test
    fun `stdout is truncated at max_stdout_bytes`() = runTest {
        ShellAllowlist.set(setOf("echo"))
        val longOutput = "x".repeat(20_000)
        val shell = FakeShellBackend().apply {
            stubAlwaysSucceed(longOutput)
        }
        val result = RunShellTool(shell).execute(mapOf("command" to "echo hi", "max_stdout_bytes" to 1024))
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("stdout_truncated")).isEqualTo(true)
        val out = result.data?.get("stdout") as String
        assertThat(out.length).isLessThan(longOutput.length)
        assertThat(out).contains("[truncated")
    }
}
