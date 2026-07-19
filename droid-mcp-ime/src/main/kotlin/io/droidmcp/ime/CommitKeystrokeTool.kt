package io.droidmcp.ime

import android.content.Context
import android.view.KeyEvent
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Sends one named key event (down+up) to the currently bound `InputConnection` via
 * [InputMethodServiceHolder]. Requires the droid-mcp IME to be active with an editor
 * focused, else [imeNotActiveError]. `key` is looked up case-insensitively in [KEY_MAP];
 * unknown keys return an error listing the valid set. Output on success: `success` (true)
 * and the echoed `key`.
 */
class CommitKeystrokeTool(private val context: Context) : McpTool {

    override val name = "commit_keystroke"
    override val description = "Send a single named key event to the focused editor — for Enter, Backspace, Tab, arrow keys, Escape, etc. Use type_text for character input."
    override val parameters = listOf(
        ToolParameter("key", "One of: enter, backspace, del, tab, escape, up, down, left, right, home, end, page_up, page_down.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val ic = InputMethodServiceHolder.service?.connection()
            ?: return ToolResult.error(imeNotActiveError())
        val name = (params["key"] as? String)?.lowercase()
            ?: return ToolResult.error("key is required")
        val code = KEY_MAP[name] ?: return ToolResult.error(
            "Unknown key '$name'. Valid: ${KEY_MAP.keys.joinToString()}."
        )
        val downOk = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        val upOk = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return if (downOk && upOk) {
            ToolResult.success(mapOf("success" to true, "key" to name))
        } else {
            ToolResult.error("sendKeyEvent returned false.")
        }
    }

    companion object {
        private val KEY_MAP = mapOf(
            "enter" to KeyEvent.KEYCODE_ENTER,
            "backspace" to KeyEvent.KEYCODE_DEL,
            "del" to KeyEvent.KEYCODE_FORWARD_DEL,
            "tab" to KeyEvent.KEYCODE_TAB,
            "escape" to KeyEvent.KEYCODE_ESCAPE,
            "up" to KeyEvent.KEYCODE_DPAD_UP,
            "down" to KeyEvent.KEYCODE_DPAD_DOWN,
            "left" to KeyEvent.KEYCODE_DPAD_LEFT,
            "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "home" to KeyEvent.KEYCODE_MOVE_HOME,
            "end" to KeyEvent.KEYCODE_MOVE_END,
            "page_up" to KeyEvent.KEYCODE_PAGE_UP,
            "page_down" to KeyEvent.KEYCODE_PAGE_DOWN,
        )
    }
}
