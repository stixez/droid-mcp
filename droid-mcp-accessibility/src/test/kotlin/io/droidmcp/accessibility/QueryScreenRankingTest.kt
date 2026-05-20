@file:Suppress("DEPRECATION")

package io.droidmcp.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Locks in `query_screen`'s ranked-output contract: clickable > has-text >
 * scrollable > rest. EdgeClaw relies on this ordering when truncating to a
 * token budget on their side.
 */
class QueryScreenRankingTest {

    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun clearHolder() {
        AccessibilityServiceHolder.clear(null)
    }

    @AfterEach
    fun teardown() {
        AccessibilityServiceHolder.clear(null)
    }

    @Test
    fun `nodes are ranked clickable then has-text then scrollable then rest`() = runTest {
        // Build a tree: root (rest) → 4 children with different affordances.
        val clickable = mockNode(viewId = "btn", clickable = true)
        val hasText = mockNode(viewId = "lbl", text = "Hello")
        val scrollable = mockNode(viewId = "list", scrollable = true)
        val rest = mockNode(viewId = "spacer")
        val root = mockNode(
            viewId = "root",
            children = listOf(rest, scrollable, hasText, clickable), // intentionally reverse-ranked
        )
        AccessibilityServiceHolder.set(mockService(root))

        val result = QueryScreenTool(context).execute(mapOf("max_nodes" to 20))
        assertThat(result.isSuccess).isTrue()

        @Suppress("UNCHECKED_CAST")
        val nodes = result.data?.get("nodes") as List<Map<String, Any?>>
        val ids = nodes.map { it["view_id"] }

        // First node should be the clickable one; before any of has-text / scrollable / rest.
        val clickableIdx = ids.indexOf("btn")
        val hasTextIdx = ids.indexOf("lbl")
        val scrollableIdx = ids.indexOf("list")
        val restIdx = ids.indexOf("spacer")

        assertThat(clickableIdx).isAtLeast(0)
        assertThat(hasTextIdx).isAtLeast(0)
        assertThat(scrollableIdx).isAtLeast(0)
        assertThat(restIdx).isAtLeast(0)

        assertThat(clickableIdx).isLessThan(hasTextIdx)
        assertThat(hasTextIdx).isLessThan(scrollableIdx)
        assertThat(scrollableIdx).isLessThan(restIdx)
    }

    @Test
    fun `clickable + has-text outranks pure clickable`() = runTest {
        val clickableWithText = mockNode(viewId = "ctxt", clickable = true, text = "Submit")
        val clickableOnly = mockNode(viewId = "conly", clickable = true)
        val root = mockNode(viewId = "root", children = listOf(clickableOnly, clickableWithText))
        AccessibilityServiceHolder.set(mockService(root))

        val result = QueryScreenTool(context).execute(emptyMap())
        @Suppress("UNCHECKED_CAST")
        val nodes = result.data?.get("nodes") as List<Map<String, Any?>>
        val ids = nodes.map { it["view_id"] }
        assertThat(ids.indexOf("ctxt")).isLessThan(ids.indexOf("conly"))
    }

    private fun mockService(root: AccessibilityNodeInfo): DroidMcpAccessibilityService =
        mockk(relaxed = true) {
            every { rootInActiveWindow } returns root
        }

    private fun mockNode(
        viewId: String,
        text: String? = null,
        contentDescription: String? = null,
        className: String? = "android.view.View",
        clickable: Boolean = false,
        longClickable: Boolean = false,
        scrollable: Boolean = false,
        isPassword: Boolean = false,
        children: List<AccessibilityNodeInfo> = emptyList(),
    ): AccessibilityNodeInfo = mockk(relaxed = true) {
        every { viewIdResourceName } returns viewId
        every { this@mockk.text } returns text
        every { this@mockk.contentDescription } returns contentDescription
        every { this@mockk.className } returns className
        every { this@mockk.packageName } returns "io.test"
        every { this@mockk.isPassword } returns isPassword
        every { isClickable } returns clickable
        every { isLongClickable } returns longClickable
        every { isScrollable } returns scrollable
        every { isEditable } returns false
        every { isFocused } returns false
        every { isSelected } returns false
        every { isEnabled } returns true
        every { isChecked } returns false
        every { childCount } returns children.size
        children.forEachIndexed { i, c -> every { getChild(i) } returns c }
        every { windowId } returns 1
    }
}
