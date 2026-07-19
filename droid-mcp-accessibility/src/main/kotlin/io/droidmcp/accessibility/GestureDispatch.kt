package io.droidmcp.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Shared `dispatchGesture` helper used by the coord-based gesture tools
 * ([TapTool], [LongPressTool], [ScrollToFindTool]; [GestureTool] inlines its
 * own equivalent). Awaits the system callback so the tool returns when the
 * gesture actually finishes (or is cancelled).
 *
 * @param service The bound accessibility service to dispatch through.
 * @param gesture The gesture description to dispatch.
 * @return true if the gesture completed; false if it was cancelled or
 *   `dispatchGesture` refused to enqueue it.
 */
internal suspend fun dispatchAwait(
    service: AccessibilityService,
    gesture: GestureDescription,
): Boolean = suspendCancellableCoroutine { cont ->
    val callback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(g: GestureDescription?) {
            if (cont.isActive) cont.resume(true)
        }
        override fun onCancelled(g: GestureDescription?) {
            if (cont.isActive) cont.resume(false)
        }
    }
    val dispatched = service.dispatchGesture(gesture, callback, null)
    if (!dispatched && cont.isActive) cont.resume(false)
}

/** Build a single-point "tap" path. Adds a 0.5px offset because Android's
 *  Path validator rejects strictly-degenerate paths on some versions. */
internal fun tapPath(x: Float, y: Float): Path = Path().apply {
    moveTo(x, y)
    lineTo(x + 0.5f, y + 0.5f)
}
