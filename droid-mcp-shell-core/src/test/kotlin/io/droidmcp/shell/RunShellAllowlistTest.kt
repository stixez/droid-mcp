package io.droidmcp.shell

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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

    @Test
    fun `allowlist matches whole tokens, not raw string prefixes`() = runTest {
        ShellAllowlist.set(setOf("pm list"))
        assertThat(ShellAllowlist.isAllowed(listOf("pm", "list", "packages"))).isTrue()
        assertThat(ShellAllowlist.isAllowed(listOf("pm", "listx"))).isFalse()
        assertThat(ShellAllowlist.isAllowed(listOf("pmx", "list"))).isFalse()
        assertThat(ShellAllowlist.isAllowed(listOf("pm"))).isFalse()
    }

    @Test
    fun `argv form arg containing a space does not match separate allowlist tokens`() = runTest {
        ShellAllowlist.set(setOf("settings put global"))
        val shell = FakeShellBackend().apply { stubAlwaysSucceed("") }
        val result = RunShellTool(shell).execute(mapOf(
            "command" to "settings",
            "args" to listOf("put global", "adb_enabled", "1"),
        ))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("run_shell_not_enabled")
        assertThat(shell.invocations).isEmpty()
    }

    @Test
    fun `argv form matches on the real argv list`() = runTest {
        ShellAllowlist.set(setOf("dumpsys battery"))
        val shell = FakeShellBackend().apply { stubAlwaysSucceed("ok") }
        val result = RunShellTool(shell).execute(mapOf("command" to "dumpsys", "args" to listOf("battery")))
        assertThat(result.isSuccess).isTrue()
        assertThat(shell.invocations.single()).isEqualTo("dumpsys" to listOf("battery"))
    }

    @Test
    fun `interpreter entries are rejected at set time`() {
        for (entry in listOf("sh", "sh -c", "/system/bin/sh", "toybox", "busybox sh", "su", "env", "xargs", "app_process")) {
            assertThrows<IllegalArgumentException> { ShellAllowlist.set(setOf(entry)) }
        }
        assertThrows<IllegalArgumentException> { ShellAllowlist.set(setOf("  ")) }
        // A rejected set() leaves the previous allowlist untouched.
        assertThat(ShellAllowlist.snapshot()).isEmpty()
    }

    @Test
    fun `backend-side truncation is reported`() = runTest {
        ShellAllowlist.set(setOf("cat"))
        val shell = FakeShellBackend().apply {
            stub("cat", listOf("big"), ShellResult(-1, "partial".toByteArray(), "", outputTruncated = true))
        }
        val result = RunShellTool(shell).execute(mapOf("command" to "cat big"))
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("stdout_truncated")).isEqualTo(true)
        assertThat(result.data?.get("exit_code")).isEqualTo(-1)
    }
}
