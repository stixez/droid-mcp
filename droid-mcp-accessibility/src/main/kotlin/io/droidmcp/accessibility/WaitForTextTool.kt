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
 * Two-modal: text presence OR window/activity change. Both modes poll the
 * accessibility tree at `poll_ms` intervals (default 200ms) until either the
 * condition matches or `timeout_ms` elapses.
 *
 * **Result shape:** `{ status: "matched" | "timeout", elapsed_ms: Long, ... }`
 * — callers branch on `status`, NOT on the error envelope. Timeout is a
 * normal outcome.
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
