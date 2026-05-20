@file:Suppress("DEPRECATION")

package io.droidmcp.accessibility

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.ParameterType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AccessibilityToolsTest {

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
    fun `provider exposes the full tool set including 0_7_0 additions`() {
        val names = AccessibilityTools.all(context).map { it.name }
        assertThat(names).containsExactly(
            "query_screen",
            "find_node",
            "wait_for_text",
            "click_node",
            "long_click_node",
            "set_node_text",
            "scroll_node",
            "gesture",
            "global_action",
            "get_active_window_info",
            "take_screenshot_via_a11y",
            // 0.7.0 additions
            "tap",
            "long_press",
            "find_and_tap",
            "scroll_to_find",
        )
    }

    @Test
    fun `read tools declare readOnly idempotent annotations`() {
        val readOnly = setOf("query_screen", "find_node", "get_active_window_info")
        AccessibilityTools.all(context).forEach { tool ->
            if (tool.name in readOnly) {
                assertThat(tool.annotations.readOnlyHint).isTrue()
                assertThat(tool.annotations.idempotentHint).isTrue()
            }
        }
    }

    @Test
    fun `mutating tools declare destructive annotation`() {
        val destructive = setOf(
            "click_node", "long_click_node", "set_node_text", "scroll_node",
            "gesture", "global_action",
        )
        AccessibilityTools.all(context).forEach { tool ->
            if (tool.name in destructive) {
                assertThat(tool.annotations.destructiveHint).isTrue()
            }
        }
    }

    @Test
    fun `find_node selector params are strings or integer limit`() {
        val byName = FindNodeTool(context).parameters.associateBy { it.name }
        assertThat(byName["text"]?.type).isEqualTo(ParameterType.STRING)
        assertThat(byName["limit"]?.type).isEqualTo(ParameterType.INTEGER)
    }

    @Test
    fun `query_screen errors when service not connected`() = runTest {
        val result = QueryScreenTool(context).execute(emptyMap())
        assertThat(result.isSuccess).isFalse()
        // 0.7.0: query_screen now uses the short-form error code.
        assertThat(result.errorMessage).contains("accessibility_not_enabled")
    }

    @Test
    fun `find_node rejects empty selector`() = runTest {
        val service = mockNode("root", className = "android.widget.FrameLayout").let { mockService(it) }
        AccessibilityServiceHolder.set(service)
        val result = FindNodeTool(context).execute(emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("At least one of")
    }

    @Test
    fun `query_screen happy path returns root projection and masks passwords`() = runTest {
        val child = mockNode("child-vid", text = "secret", contentDescription = "Password field", isPassword = true)
        val root = mockNode("root-vid", text = "Hello", className = "android.widget.FrameLayout", children = listOf(child))
        AccessibilityServiceHolder.set(mockService(root))

        val result = QueryScreenTool(context).execute(mapOf("max_nodes" to 10))
        assertThat(result.isSuccess).isTrue()

        @Suppress("UNCHECKED_CAST")
        val nodes = result.data?.get("nodes") as List<Map<String, Any?>>
        assertThat(nodes).hasSize(2)
        val rootProjection = nodes[0]
        val childProjection = nodes[1]

        assertThat(rootProjection["view_id"]).isEqualTo("root-vid")
        assertThat(rootProjection["text"]).isEqualTo("Hello")
        assertThat(rootProjection["is_password"]).isEqualTo(false)
        assertThat(rootProjection["depth"]).isEqualTo(0)

        // Password masking: text and content_description are nulled but is_password=true
        assertThat(childProjection["view_id"]).isEqualTo("child-vid")
        assertThat(childProjection["text"]).isNull()
        assertThat(childProjection["content_description"]).isNull()
        assertThat(childProjection["is_password"]).isEqualTo(true)
        assertThat(childProjection["depth"]).isEqualTo(1)

        // Child was recycled by the walker; root recycled by withRoot
        verify { child.recycle() }
        verify { root.recycle() }
    }

    @Test
    fun `query_screen truncated flag reflects max_nodes limit`() = runTest {
        val children = (1..5).map { mockNode("c$it") }
        val root = mockNode("root", children = children)
        AccessibilityServiceHolder.set(mockService(root))

        val result = QueryScreenTool(context).execute(mapOf("max_nodes" to 3))
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("count")).isEqualTo(3)
        assertThat(result.data?.get("truncated")).isEqualTo(true)
    }

    @Test
    fun `find_node returns matches in BFS order and respects limit`() = runTest {
        val grandchild = mockNode("gc", text = "needle")
        val a = mockNode("a", text = "needle", children = listOf(grandchild))
        val b = mockNode("b", text = "haystack")
        val root = mockNode("root", children = listOf(a, b))
        AccessibilityServiceHolder.set(mockService(root))

        val result = FindNodeTool(context).execute(mapOf("text" to "needle", "limit" to 5))
        assertThat(result.isSuccess).isTrue()
        @Suppress("UNCHECKED_CAST")
        val nodes = result.data?.get("nodes") as List<Map<String, Any?>>
        assertThat(nodes.map { it["view_id"] }).containsExactly("a", "gc").inOrder()
    }

    @Test
    fun `selectNode rejects negative index loudly`() = runTest {
        val root = mockNode("root")
        AccessibilityServiceHolder.set(mockService(root))
        val result = ClickNodeTool(context).execute(mapOf("text" to "anything", "index" to -1))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("index must be >= 0")
    }

    @Test
    fun `gesture rejects malformed points`() = runTest {
        AccessibilityServiceHolder.set(mockService(mockNode("root")))
        val result = GestureTool(context).execute(mapOf("points" to listOf(listOf(10, 20), "garbage", listOf(30, 40))))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("points[1]")
    }

    @Test
    fun `gesture rejects fewer than two points`() = runTest {
        AccessibilityServiceHolder.set(mockService(mockNode("root")))
        val result = GestureTool(context).execute(mapOf("points" to listOf(listOf(10, 20))))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("at least 2 [x, y] points")
    }

    @Test
    fun `global_action rejects unknown action`() = runTest {
        AccessibilityServiceHolder.set(mockService(mockNode("root")))
        val result = GlobalActionTool(context).execute(mapOf("action" to "explode"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("Unknown action")
    }

    @Test
    fun `holder is_connected reflects set and clear`() {
        AccessibilityServiceHolder.clear(null)
        assertThat(AccessibilityServiceHolder.isConnected()).isFalse()
        AccessibilityServiceHolder.set(mockService(mockNode("root")))
        assertThat(AccessibilityServiceHolder.isConnected()).isTrue()
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
        packageName: String = "io.test",
        isPassword: Boolean = false,
        children: List<AccessibilityNodeInfo> = emptyList(),
    ): AccessibilityNodeInfo = mockk(relaxed = true) {
        every { viewIdResourceName } returns viewId
        every { this@mockk.text } returns text
        every { this@mockk.contentDescription } returns contentDescription
        every { this@mockk.className } returns className
        every { this@mockk.packageName } returns packageName
        every { this@mockk.isPassword } returns isPassword
        every { childCount } returns children.size
        children.forEachIndexed { i, c -> every { getChild(i) } returns c }
        // getBoundsInScreen is a no-op via relaxed mocking — bounds default to 0,0,0,0.
        every { isClickable } returns false
        every { isLongClickable } returns false
        every { isScrollable } returns false
        every { isEditable } returns false
        every { isFocused } returns false
        every { isSelected } returns false
        every { isEnabled } returns true
        every { isChecked } returns false
        every { windowId } returns 1
    }
}
