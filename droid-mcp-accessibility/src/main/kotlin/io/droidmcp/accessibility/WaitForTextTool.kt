@file:Suppress("DEPRECATION")

package io.droidmcp.accessibility

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.delay

/**
 * `wait_for_text` — block (with timeout) until a condition holds on screen.
 * Two modes selected by the `condition` param:
 *  - `text` (default): waits for `text` to appear (case-insensitive substring
 *    against text + content-description).
 *  - `window_change`: waits for the foreground package, root class, or window
 *    id to differ from the snapshot taken on the first poll.
 *
 * Both modes poll the accessibility tree at `poll_ms` intervals (clamped
 * 50–2000ms, default 200) until either the condition matches or `timeout_ms`
 * (clamped 100–60000ms, default 5000) elapses.
 *
 * **Result shape — timeout is NOT an error.** A successful call returns
 * `status` = `"matched"` or `"timeout"` plus `elapsed_ms` (Long) and
 * `condition`. On a `text` match it also returns `text`; on a `window_change`
 * match it adds `package_name` and `root_class`. Callers branch on `status`,
 * not on the error envelope.
 *
 * Error codes: `invalid_selector` (unknown `condition`, or `text` missing when
 * `condition='text'`) and `accessibility_not_enabled` (service not bound).
 */
class WaitForTextTool(private val context: Context) : McpTool {

    override val name = "wait_for_text"
    override val description = "Block (with timeout) until a text substring appears on screen, or the active window changes. Returns a structured result with status='matched' or status='timeout'. Timeout is NOT an error — branch on status."
    override val parameters = listOf(
        ToolParameter("condition", "'text' (default) waits for `text` to appear; 'window_change' waits for the foreground package or root activity to change.", ParameterType.STRING, required = false),
        ToolParameter("text", "Substring to wait for (required when condition='text'; matched against text + contentDescription, case-insensitive).", ParameterType.STRING, required = false),
        ToolParameter("timeout_ms", "Max wait in milliseconds (100-60000, default 5000).", ParameterType.INTEGER, required = false),
        ToolParameter("poll_ms", "Polling interval in milliseconds (50-2000, default 200).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val condition = (params["condition"] as? String)?.lowercase() ?: "text"
        val timeoutMs = (params["timeout_ms"] as? Number)?.toLong()?.coerceIn(100L, 60_000L) ?: 5_000L
        val pollMs = (params["poll_ms"] as? Number)?.toLong()?.coerceIn(50L, 2_000L) ?: 200L
        val start = System.currentTimeMillis()
        val deadline = start + timeoutMs

        return when (condition) {
            "text" -> waitForText(params, start, deadline, pollMs)
            "window_change" -> waitForWindowChange(start, deadline, pollMs)
            else -> ToolResult.error("invalid_selector", "condition must be 'text' or 'window_change'")
        }
    }

    private suspend fun waitForText(
        params: Map<String, Any>,
        start: Long,
        deadline: Long,
        pollMs: Long,
    ): ToolResult {
        val text = (params["text"] as? String)?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("invalid_selector", "text is required when condition='text'")

        while (true) {
            var found = false
            val walked = NodeQuery.withRoot { root ->
                NodeQuery.walk(root) { node, _ ->
                    if (NodeQuery.matches(node, text, null, null, null)) {
                        found = true
                        false
                    } else {
                        true
                    }
                }
                true
            } ?: return ToolResult.error("accessibility_not_enabled", null)
            if (!walked) return ToolResult.error("accessibility_not_enabled", null)

            if (found) {
                return ToolResult.success(mapOf(
                    "status" to "matched",
                    "elapsed_ms" to (System.currentTimeMillis() - start),
                    "condition" to "text",
                    "text" to text,
                ))
            }
            if (System.currentTimeMillis() >= deadline) {
                return ToolResult.success(mapOf(
                    "status" to "timeout",
                    "elapsed_ms" to (System.currentTimeMillis() - start),
                    "condition" to "text",
                ))
            }
            delay(pollMs)
        }
    }

    private suspend fun waitForWindowChange(
        start: Long,
        deadline: Long,
        pollMs: Long,
    ): ToolResult {
        var initial: WindowSnapshot? = null
        while (true) {
            val snapshot = NodeQuery.withRoot { root ->
                WindowSnapshot(
                    pkg = root.packageName?.toString(),
                    rootClass = root.className?.toString(),
                    windowId = root.windowId,
                )
            } ?: return ToolResult.error("accessibility_not_enabled", null)

            if (initial == null) {
                initial = snapshot
            } else if (snapshot != initial) {
                return ToolResult.success(mapOf(
                    "status" to "matched",
                    "elapsed_ms" to (System.currentTimeMillis() - start),
                    "condition" to "window_change",
                    "package_name" to snapshot.pkg,
                    "root_class" to snapshot.rootClass,
                ))
            }
            if (System.currentTimeMillis() >= deadline) {
                return ToolResult.success(mapOf(
                    "status" to "timeout",
                    "elapsed_ms" to (System.currentTimeMillis() - start),
                    "condition" to "window_change",
                ))
            }
            delay(pollMs)
        }
    }

    private data class WindowSnapshot(
        val pkg: String?,
        val rootClass: String?,
        val windowId: Int,
    )
}
