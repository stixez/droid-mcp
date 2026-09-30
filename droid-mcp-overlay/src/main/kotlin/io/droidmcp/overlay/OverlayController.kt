package io.droidmcp.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Programmatic floating-overlay primitive. Host apps wire this to a settings
 * toggle; no LLM tools expose it directly. Uses `TYPE_APPLICATION_OVERLAY` so
 * it survives on top of other apps (requires `SYSTEM_ALERT_WINDOW` permission
 * granted via [permissionIntent]).
 *
 * **Threading.** [show] and [hide] may be called from any thread: WindowManager
 * view operations must run on the main thread, so off-main calls are posted to
 * the main looper (and therefore take effect asynchronously). Calls made on the
 * main thread run synchronously.
 */
class OverlayController(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var current: View? = null

    @Volatile
    private var currentParams: WindowManager.LayoutParams? = null

    /**
     * Captured during [show] so [hide] can cancel a pending long-press timer
     * before tearing the view down. Without this, the runnable can fire after
     * `hide()` and invoke `onLongPress` on an already-removed overlay.
     */
    @Volatile
    private var pendingLongPress: Runnable? = null

    fun isPermissionGranted(): Boolean = Settings.canDrawOverlays(context)

    fun permissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    /**
     * Show the overlay. Idempotent — calling while already showing replaces
     * the existing overlay with the new config. Safe to call from any thread
     * (off-main calls are posted to the main looper). No-op when the overlay
     * permission is missing, or when the previous overlay could not be removed
     * (so overlays never stack).
     */
    fun show(config: OverlayConfig) {
        runOnMain { showOnMain(config) }
    }

    /** Convenience overload — equivalent to `show(OverlayConfig(label = label, onClick = onClick))`. */
    fun show(label: String, onClick: () -> Unit) = show(OverlayConfig(label = label, onClick = onClick))

    /**
     * Remove the overlay if showing. Safe to call from any thread (off-main
     * calls are posted to the main looper). The controller only forgets the
     * view once `WindowManager.removeView` succeeds (or the view is already
     * detached), so a failed removal can be retried.
     */
    fun hide() {
        runOnMain { hideOnMain() }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post { block() }
    }

    private fun showOnMain(config: OverlayConfig) {
        if (!isPermissionGranted()) return
        hideOnMain()
        if (current != null) return // previous overlay couldn't be removed; don't stack

        val view = buildView(config)
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 64
            y = 256
        }

        wireDragAndClick(view, params, config)
        val added = runCatching { windowManager.addView(view, params) }.isSuccess
        if (added) {
            current = view
            currentParams = params
        } else {
            pendingLongPress = null
        }
    }

    private fun hideOnMain() {
        val view = current ?: return
        // Cancel a pending long-press timer before removing the view so the
        // runnable can't fire on a detached overlay.
        pendingLongPress?.let { runnable ->
            view.removeCallbacks(runnable)
        }
        pendingLongPress = null
        val removed = runCatching { windowManager.removeView(view) }.isSuccess
        // removeView throws IllegalArgumentException for a view that is no
        // longer attached — in that case there's nothing left to remove.
        if (removed || !view.isAttachedToWindow) {
            current = null
            currentParams = null
        }
    }

    private fun buildView(config: OverlayConfig): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            background = FrameLayout(context).background // host theme background
            isClickable = true
        }
        if (config.iconRes != null) {
            container.addView(ImageView(context).apply { setImageResource(config.iconRes) })
        }
        if (config.label != null) {
            container.addView(TextView(context).apply {
                text = config.label
                textSize = 14f
                if (config.iconRes != null) setPadding(16, 0, 0, 0)
            })
        }
        return container
    }

    private fun wireDragAndClick(
        view: View,
        params: WindowManager.LayoutParams,
        config: OverlayConfig,
    ) {
        var downX = 0
        var downY = 0
        var rawDownX = 0f
        var rawDownY = 0f
        var dragged = false
        var longPressFired = false
        val touchSlop = 12
        val longPressMs = 500L

        val longPressRunnable = Runnable {
            // Re-check that this runnable is still the active one — `hide()`
            // nulls the field, and a subsequent `show()` installs a new
            // runnable. Without this check, a stale post could still fire
            // when the looper drains its queue.
            if (pendingLongPress !== null && !dragged && config.onLongPress != null) {
                longPressFired = true
                config.onLongPress.invoke()
            }
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = params.x
                    downY = params.y
                    rawDownX = event.rawX
                    rawDownY = event.rawY
                    dragged = false
                    longPressFired = false
                    if (config.onLongPress != null) {
                        pendingLongPress = longPressRunnable
                        view.postDelayed(longPressRunnable, longPressMs)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - rawDownX).toInt()
                    val dy = (event.rawY - rawDownY).toInt()
                    if (!dragged && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragged = true
                        view.removeCallbacks(longPressRunnable)
                        pendingLongPress = null
                    }
                    if (dragged) {
                        params.x = downX + dx
                        params.y = downY + dy
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(longPressRunnable)
                    pendingLongPress = null
                    if (dragged) {
                        config.onDragEnd?.invoke(params.x, params.y)
                    } else if (!longPressFired) {
                        config.onClick.invoke()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(longPressRunnable)
                    pendingLongPress = null
                    true
                }
                else -> false
            }
        }
    }
}
