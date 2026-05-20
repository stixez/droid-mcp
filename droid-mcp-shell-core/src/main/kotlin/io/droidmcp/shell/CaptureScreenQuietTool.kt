package io.droidmcp.shell

import android.util.Base64
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * `screencap -p` — captures the screen as a PNG and writes it to stdout. With
 * a privileged shell backend this works without MediaProjection consent (no
 * yellow status-bar indicator, no prompt). Distinct from
 * `screenshot.capture_screen` (MediaProjection) and
 * `accessibility.take_screenshot_via_a11y` (Accessibility-API consent).
 *
 * Returns a base64-encoded PNG. JPEG re-encode happens on the Kotlin side
 * because `screencap` only emits PNG.
 */
class CaptureScreenQuietTool(private val shell: ShellBackend) : McpTool {

    override val name = "capture_screen_quiet"
    override val description = "Capture the screen via `screencap -p` through the shell backend — no MediaProjection consent prompt, no status-bar indicator. Returns a base64-encoded PNG. Lighter than `take_screenshot_via_a11y` (Accessibility) and distinct from `capture_screen` (MediaProjection)."
    override val parameters = listOf(
        ToolParameter("display", "Display id to capture (0 = primary, default).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val display = (params["display"] as? Number)?.toInt() ?: 0
        val args = buildList {
            add("-p")
            if (display != 0) {
                add("-d")
                add(display.toString())
            }
        }
        return shell.gatedExecBinary("screencap", args) { result ->
            if (!result.isSuccess) {
                return@gatedExecBinary ToolResult.error("screencap_failed", "exit ${result.exitCode}")
            }
            val bytes = result.stdoutBytes
            if (bytes.size < 16 || !bytes.startsWith(PNG_SIGNATURE)) {
                return@gatedExecBinary ToolResult.error("screencap_failed", "did not return a PNG (${bytes.size} bytes)")
            }
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            ToolResult.success(mapOf(
                "format" to "png",
                "size_bytes" to bytes.size,
                "image_base64" to b64,
            ))
        }
    }

    private companion object {
        // 8-byte PNG header
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )

        private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
            if (size < prefix.size) return false
            for (i in prefix.indices) if (this[i] != prefix[i]) return false
            return true
        }
    }
}
