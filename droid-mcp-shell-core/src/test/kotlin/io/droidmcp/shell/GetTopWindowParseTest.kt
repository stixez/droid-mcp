package io.droidmcp.shell

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Verifies the dumpsys-window parser handles real-world output shapes.
 */
class GetTopWindowParseTest {

    private val context: android.content.Context = mockk(relaxed = true)

    @Test
    fun `parses mCurrentFocus with package and activity`() = runTest {
        val stdout = """
            WINDOW MANAGER POLICY STATE (dumpsys window policy)
              mCurrentFocus=Window{abc12345 u0 com.example.app/com.example.app.MainActivity}
              displayId=0
        """.trimIndent()
        val shell = FakeShellBackend().apply {
            stub("dumpsys", listOf("window"), ShellResult.ofText(0, stdout, ""))
        }
        val result = GetTopWindowTool(shell).execute(emptyMap())
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("package_name")).isEqualTo("com.example.app")
        assertThat(result.data?.get("activity")).isEqualTo("com.example.app.MainActivity")
        assertThat(result.data?.get("display_id")).isEqualTo(0)
    }

    @Test
    fun `parses mFocusedApp when mCurrentFocus is a system window`() = runTest {
        val stdout = """
              mCurrentFocus=Window{abc12345 u0 NavigationBar0}
              mFocusedApp=ActivityRecord{deadbeef u0 com.example.app/.MainActivity t42}
              displayId=0
        """.trimIndent()
        val shell = FakeShellBackend().apply {
            stub("dumpsys", listOf("window"), ShellResult.ofText(0, stdout, ""))
        }
        val result = GetTopWindowTool(shell).execute(emptyMap())
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("package_name")).isEqualTo("com.example.app")
    }

    @Test
    fun `errors with dumpsys_failed on non-zero exit`() = runTest {
        val shell = FakeShellBackend().apply { stubAlwaysFail("permission denied", exitCode = 1) }
        val result = GetTopWindowTool(shell).execute(emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("dumpsys_failed")
    }
}
