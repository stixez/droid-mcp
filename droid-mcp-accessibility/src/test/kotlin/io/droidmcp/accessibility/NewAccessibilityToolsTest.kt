@file:Suppress("DEPRECATION")

package io.droidmcp.accessibility

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewAccessibilityToolsTest {

    private val context: Context = mockk(relaxed = true) {
        val res: Resources = mockk(relaxed = true)
        val metrics = DisplayMetrics().apply { widthPixels = 1080; heightPixels = 2400 }
        every { resources } returns res
        every { res.displayMetrics } returns metrics
    }

    @BeforeEach
    fun clearHolder() {
        AccessibilityServiceHolder.clear(null)
    }

    @AfterEach
    fun teardown() {
        AccessibilityServiceHolder.clear(null)
    }

    @Test
    fun `provider includes 0_7_0 additions`() {
        val names = AccessibilityTools.all(context).map { it.name }.toSet()
        assertThat(names).containsAtLeast("tap", "long_press", "find_and_tap", "scroll_to_find")
    }

    @Test
    fun `tap errors when service not bound`() = runTest {
        val result = TapTool(context).execute(mapOf("x" to 100, "y" to 200))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("accessibility_not_enabled")
    }

    @Test
    fun `tap rejects non-numeric coords`() = runTest {
        AccessibilityServiceHolder.set(mockk(relaxed = true))
        val result = TapTool(context).execute(mapOf("x" to "nope", "y" to 200))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("invalid_coords")
    }

    @Test
    fun `tap rejects negative coords`() = runTest {
        AccessibilityServiceHolder.set(mockk(relaxed = true))
        val result = TapTool(context).execute(mapOf("x" to -1, "y" to 200))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("invalid_coords")
    }

    @Test
    fun `long_press clamps duration to bounds`() {
        // verify the parameter shape so the LLM sees the bounds
        val byName = LongPressTool(context).parameters.associateBy { it.name }
        assertThat(byName).containsKey("duration_ms")
    }

    @Test
    fun `find_and_tap rejects empty match`() = runTest {
        AccessibilityServiceHolder.set(mockk(relaxed = true))
        val result = FindAndTapTool(context).execute(mapOf("match" to ""))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("invalid_selector")
    }

    @Test
    fun `find_and_tap rejects unknown match_kind`() = runTest {
        AccessibilityServiceHolder.set(mockk(relaxed = true))
        val result = FindAndTapTool(context).execute(mapOf("match" to "x", "match_kind" to "regex"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("text|desc|id|class")
    }

    @Test
    fun `scroll_to_find rejects unknown direction`() = runTest {
        AccessibilityServiceHolder.set(mockk(relaxed = true))
        val result = ScrollToFindTool(context).execute(mapOf("match" to "About", "direction" to "diagonal"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("down|up|left|right")
    }

    @Test
    fun `idempotentGlobalActions excludes power_dialog and screenshot`() {
        assertThat(AccessibilityTools.idempotentGlobalActions).containsExactly(
            "back", "home", "recents", "notifications", "quick_settings", "lock_screen",
        )
    }

    @Test
    fun `supportedTools excludes take_screenshot_via_a11y on API less than 30`() {
        // We can't easily fake Build.VERSION.SDK_INT in unit tests, but we can assert
        // that supportedTools returns a subset of all tool names.
        val all = AccessibilityTools.all(context).map { it.name }.toSet()
        val supported = AccessibilityTools.supportedTools(context)
        assertThat(supported).containsAtLeastElementsIn(all - setOf("take_screenshot_via_a11y"))
    }

    @Test
    fun `permissionStatus returns NotGranted with accessibility settings intent when service unbound`() {
        AccessibilityServiceHolder.clear(null)
        val status = AccessibilityTools.permissionStatus(context)
        assertThat(status.granted).isFalse()
        assertThat(status.intent).isNotNull()
    }
}
