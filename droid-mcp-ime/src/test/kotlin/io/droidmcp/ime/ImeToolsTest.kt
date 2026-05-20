package io.droidmcp.ime

import android.content.Context
import android.view.inputmethod.InputConnection
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImeToolsTest {

    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun clearHolder() {
        InputMethodServiceHolder.clear(null)
    }

    @AfterEach
    fun teardown() {
        InputMethodServiceHolder.clear(null)
    }

    @Test
    fun `provider exposes all seven tools`() {
        val names = ImeTools.all(context).map { it.name }
        assertThat(names).containsExactly(
            "is_ime_active",
            "type_text",
            "commit_keystroke",
            "delete_text",
            "set_selection",
            "get_text_around_cursor",
            "switch_to_previous_ime",
        )
    }

    @Test
    fun `read-shaped tools declare readOnly`() {
        val tools = ImeTools.all(context).associateBy { it.name }
        assertThat(tools["is_ime_active"]?.annotations?.readOnlyHint).isTrue()
        assertThat(tools["get_text_around_cursor"]?.annotations?.readOnlyHint).isTrue()
    }

    @Test
    fun `mutating tools declare destructive`() {
        val destructive = setOf(
            "type_text", "commit_keystroke", "delete_text", "set_selection",
            "switch_to_previous_ime",
        )
        ImeTools.all(context).forEach { tool ->
            if (tool.name in destructive) {
                assertThat(tool.annotations.destructiveHint).isTrue()
            }
        }
    }

    @Test
    fun `type_text errors when IME not active`() = runTest {
        val result = TypeTextTool(context).execute(mapOf("text" to "hi"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("not the active keyboard")
    }

    @Test
    fun `type_text happy path commits exactly once`() = runTest {
        val ic = mockk<InputConnection>(relaxed = true) {
            every { commitText(any(), any()) } returns true
        }
        InputMethodServiceHolder.set(mockService(ic, bound = true))

        val result = TypeTextTool(context).execute(mapOf("text" to "hello"))

        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("length")).isEqualTo(5)
        verify(exactly = 1) { ic.commitText("hello", 1) }
    }

    @Test
    fun `is_ime_active reports bound state`() = runTest {
        val ic = mockk<InputConnection>(relaxed = true)
        InputMethodServiceHolder.set(mockService(ic, bound = true))
        val result = IsImeActiveTool(context).execute(emptyMap())
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("active")).isEqualTo(true)
        assertThat(result.data?.get("bound")).isEqualTo(true)
    }

    @Test
    fun `delete_text rejects zero before and after`() = runTest {
        val ic = mockk<InputConnection>(relaxed = true)
        InputMethodServiceHolder.set(mockService(ic, bound = true))
        val result = DeleteTextTool(context).execute(emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("At least one of before or after")
    }

    @Test
    fun `delete_text clamps high values to 2000`() = runTest {
        val ic = mockk<InputConnection>(relaxed = true) {
            every { deleteSurroundingText(any(), any()) } returns true
        }
        InputMethodServiceHolder.set(mockService(ic, bound = true))
        val result = DeleteTextTool(context).execute(mapOf("before" to 999_999, "after" to 0))
        assertThat(result.isSuccess).isTrue()
        verify(exactly = 1) { ic.deleteSurroundingText(2000, 0) }
    }

    @Test
    fun `set_selection requires non-negative monotonic offsets`() = runTest {
        val ic = mockk<InputConnection>(relaxed = true)
        InputMethodServiceHolder.set(mockService(ic, bound = true))
        val result = SetSelectionTool(context).execute(mapOf("start" to -1, "end" to 0))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("start >= 0")
    }

    @Test
    fun `commit_keystroke maps named keys`() = runTest {
        val ic = mockk<InputConnection>(relaxed = true) {
            every { sendKeyEvent(any()) } returns true
        }
        InputMethodServiceHolder.set(mockService(ic, bound = true))
        val result = CommitKeystrokeTool(context).execute(mapOf("key" to "enter"))
        assertThat(result.isSuccess).isTrue()
        verify(exactly = 2) { ic.sendKeyEvent(any()) } // ACTION_DOWN + ACTION_UP
    }

    @Test
    fun `commit_keystroke rejects unknown key`() = runTest {
        val ic = mockk<InputConnection>(relaxed = true)
        InputMethodServiceHolder.set(mockService(ic, bound = true))
        val result = CommitKeystrokeTool(context).execute(mapOf("key" to "explode"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("Unknown key")
    }

    @Test
    fun `get_text_around_cursor returns the connection's text`() = runTest {
        val ic = mockk<InputConnection>(relaxed = true) {
            every { getTextBeforeCursor(any(), any()) } returns "before "
            every { getTextAfterCursor(any(), any()) } returns " after"
        }
        InputMethodServiceHolder.set(mockService(ic, bound = true))
        val result = GetTextAroundCursorTool(context).execute(mapOf("before" to 100, "after" to 100))
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("before")).isEqualTo("before ")
        assertThat(result.data?.get("after")).isEqualTo(" after")
    }

    private fun mockService(
        ic: InputConnection,
        bound: Boolean,
    ): DroidMcpInputMethodService = mockk(relaxed = true) {
        every { connection() } returns if (bound) ic else null
        every { hasInputConnection() } returns bound
    }
}
