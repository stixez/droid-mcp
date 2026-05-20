package io.droidmcp.shell

import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Foreground activity/window from `dumpsys window mCurrentFocus` and
 * `dumpsys window mFocusedApp`. This is the standard way to get the
 * foreground package on devices where Accessibility isn't enabled — it's
 * what the LLM-agent should call when it just needs "what app is on top
 * right now" without driving the UI.
 */
class GetTopWindowTool(private val shell: ShellBackend) : McpTool {

    override val name = "get_top_window"
    override val description = "Get the current foreground window/activity from `dumpsys window`. Returns package name + activity class. Faster + lighter than accessibility's `get_active_window_info` for callers that just need to know which app is on top."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult =
        shell.gatedExec("dumpsys", listOf("window")) { result ->
            if (!result.isSuccess) {
                return@gatedExec ToolResult.error("dumpsys_failed", "dumpsys window exited ${result.exitCode}")
            }
            val parsed = parse(result.stdout)
            ToolResult.success(buildMap<String, Any?> {
                put("package_name", parsed.packageName)
                put("activity", parsed.activity)
                put("window_class", parsed.windowClass)
                put("display_id", parsed.displayId)
            })
        }

    private fun parse(stdout: String): TopWindow {
        var pkg: String? = null
        var activity: String? = null
        var windowClass: String? = null
        var displayId: Int? = null
        for (line in stdout.lineSequence().map { it.trim() }) {
            // mCurrentFocus=Window{abc12345 u0 com.example.app/com.example.app.MainActivity}
            CURRENT_FOCUS.find(line)?.let { m ->
                val token = m.groupValues[1]
                val slash = token.indexOf('/')
                if (slash > 0) {
                    pkg = token.substring(0, slash)
                    activity = token.substring(slash + 1)
                } else {
                    windowClass = token
                }
            }
            // mFocusedApp=ActivityRecord{... u0 com.example.app/.MainActivity t123}
            FOCUSED_APP.find(line)?.let { m ->
                val token = m.groupValues[1]
                val slash = token.indexOf('/')
                if (slash > 0 && pkg == null && activity == null) {
                    pkg = token.substring(0, slash)
                    activity = token.substring(slash + 1).let { a ->
                        if (a.startsWith('.')) "${token.substring(0, slash)}$a" else a
                    }
                }
            }
            // displayId=0
            DISPLAY_ID.find(line)?.let { displayId = it.groupValues[1].toIntOrNull() }
        }
        return TopWindow(pkg, activity, windowClass, displayId)
    }

    private data class TopWindow(
        val packageName: String?,
        val activity: String?,
        val windowClass: String?,
        val displayId: Int?,
    )

    private companion object {
        // mCurrentFocus=Window{... u0 <pkg>/<activity>} OR mCurrentFocus=Window{... u0 <window_class>}
        private val CURRENT_FOCUS = Regex("""mCurrentFocus=Window\{[^}]*\s+(\S+)\}""")
        // mFocusedApp=ActivityRecord{... u0 <pkg>/<activity> ...}
        private val FOCUSED_APP = Regex("""mFocusedApp=\S+\{[^}]*\s(\S+/\S+)""")
        private val DISPLAY_ID = Regex("""displayId=(\d+)""")
    }
}
