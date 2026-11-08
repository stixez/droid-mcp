package io.droidmcp.overlay

import android.graphics.Rect
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.core.reportProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Points the person holding the phone at something on screen and waits for them. For steps a
 * model can't or shouldn't do itself (payments, 2FA codes, "pick the photo you mean") or to teach
 * someone where something is. Android hides other apps' overlays over system Settings and
 * permission dialogs, so the guide doesn't show there.
 *
 * Output on success: `result` (`done` when the user tapped Done, `closed` when they closed the
 * guide, `timeout` when the wait ran out, `replaced` when another guide took its place, `cleared`
 * when clear_guide removed it, `shown` when `wait` was false) and `waited_ms`. While waiting it
 * reports progress every few seconds, so clients that reset their timeout on progress keep waiting.
 */
class GuideUserTool(private val overlay: GuideOverlay) : McpTool {

    override val name = "guide_user"
    override val description =
        "Point the user at an on-screen spot (bounds from find_node or query_screen) with a short instruction " +
            "for a step they should do themselves, like a payment or a code, and return result=done, closed, " +
            "timeout, replaced or cleared once they act (or shown with wait=false; remove that with clear_guide). " +
            "Android hides it over system Settings and permission dialogs."
    override val parameters = listOf(
        ToolParameter("left", "Target's left edge in screen pixels", ParameterType.INTEGER, required = true, minimum = 0.0),
        ToolParameter("top", "Target's top edge in screen pixels", ParameterType.INTEGER, required = true, minimum = 0.0),
        ToolParameter("right", "Target's right edge in screen pixels", ParameterType.INTEGER, required = true, minimum = 0.0),
        ToolParameter("bottom", "Target's bottom edge in screen pixels", ParameterType.INTEGER, required = true, minimum = 0.0),
        ToolParameter("message", "What the user should do, in one or two short sentences", ParameterType.STRING, required = true),
        ToolParameter(
            "wait",
            "Wait for the user to tap Done or Close (default true). False returns at once and leaves the guide up",
            ParameterType.BOOLEAN,
        ),
        ToolParameter(
            "timeout_seconds",
            "How long to wait before giving up and removing the guide (5-240, default 120)",
            ParameterType.INTEGER,
            minimum = MIN_TIMEOUT_S.toDouble(),
            maximum = MAX_TIMEOUT_S.toDouble(),
        ),
    )
    override val annotations = ToolAnnotations(title = "Guide the user")
    override val outputSchema = OutputSchema.obj {
        string("result", "done, closed, timeout, replaced, cleared or shown")
        integer("waited_ms")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (!overlay.canShow()) {
            return ToolResult.error(
                "overlay_permission_missing: the app needs \"Display over other apps\" to show a guide",
            )
        }
        val left = (params["left"] as? Number)?.toInt()
        val top = (params["top"] as? Number)?.toInt()
        val right = (params["right"] as? Number)?.toInt()
        val bottom = (params["bottom"] as? Number)?.toInt()
        if (left == null || top == null || right == null || bottom == null) {
            return ToolResult.error("left, top, right and bottom are required")
        }
        if (left < 0 || top < 0 || right <= left || bottom <= top) {
            return ToolResult.error("bounds must satisfy 0 <= left < right and 0 <= top < bottom")
        }
        val message = params["message"]?.toString()?.trim().orEmpty()
        if (message.isEmpty()) return ToolResult.error("message is required")
        val wait = params["wait"] as? Boolean ?: true
        val timeoutMs = ((params["timeout_seconds"] as? Number)?.toLong() ?: DEFAULT_TIMEOUT_S).coerceIn(MIN_TIMEOUT_S, MAX_TIMEOUT_S) * 1000

        val outcome = CompletableDeferred<GuideOverlay.Outcome>()
        val started = System.currentTimeMillis()
        val id = overlay.show(Rect(left, top, right, bottom), message.take(MAX_MESSAGE)) { outcome.complete(it) }
        if (!wait) return ToolResult.success(mapOf("result" to "shown", "waited_ms" to 0))

        val result = try {
            withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    // Keep progress-aware clients from timing out while the user reads and acts.
                    val ticker = launch {
                        while (true) {
                            delay(PROGRESS_EVERY_MS)
                            val waited = System.currentTimeMillis() - started
                            reportProgress(waited / 1000.0, timeoutMs / 1000.0, "Waiting for the user")
                        }
                    }
                    outcome.await().also { ticker.cancel() }
                }
            }
        } catch (e: CancellationException) {
            // The client cancelled the call: don't leave the guide on screen.
            overlay.hide(id)
            throw e
        }
        if (result == null) overlay.hide(id)
        if (result == GuideOverlay.Outcome.Failed) {
            return ToolResult.error("overlay_failed: Android didn't let the app draw the guide")
        }
        return ToolResult.success(
            mapOf(
                "result" to when (result) {
                    GuideOverlay.Outcome.Done -> "done"
                    GuideOverlay.Outcome.Closed -> "closed"
                    GuideOverlay.Outcome.Replaced -> "replaced"
                    GuideOverlay.Outcome.Cleared -> "cleared"
                    GuideOverlay.Outcome.Failed, null -> "timeout"
                },
                "waited_ms" to (System.currentTimeMillis() - started),
            ),
        )
    }

    private companion object {
        private const val DEFAULT_TIMEOUT_S = 120L
        private const val MIN_TIMEOUT_S = 5L

        // Stays under the SDK's default 5-minute tool timeout, so the guide's own timeout wins.
        private const val MAX_TIMEOUT_S = 240L
        private const val MAX_MESSAGE = 300
        private const val PROGRESS_EVERY_MS = 10_000L
    }
}

/** Removes a guide shown by [GuideUserTool], e.g. one shown with `wait=false`. */
class ClearGuideTool(private val overlay: GuideOverlay) : McpTool {
    override val name = "clear_guide"
    override val description = "Remove the guide shown by guide_user, if any. Returns cleared=true."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(idempotentHint = true, title = "Clear the guide")
    override val outputSchema = OutputSchema.obj { boolean("cleared") }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        overlay.hide()
        return ToolResult.success(mapOf("cleared" to true))
    }
}
