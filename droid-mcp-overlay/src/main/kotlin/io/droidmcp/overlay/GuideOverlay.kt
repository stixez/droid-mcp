package io.droidmcp.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws a "look here" guide on top of every app: a pulsing ring around a target rectangle, an
 * arrow towards it, and a message card with **Done** and **Close** buttons. Everything except the
 * card lets touches through, so the user can tap the real control underneath.
 *
 * Only one guide shows at a time. Each [show] returns an id; [hide] with that id removes the guide
 * only while it is still the one showing, so a late timeout never removes a newer guide. Every
 * guide's callback runs exactly once, with how it ended.
 *
 * Coordinates are screen pixels, the same space as `getBoundsInScreen` (what the accessibility
 * tools report). Needs the "display over other apps" permission. All methods may be called from
 * any thread; window work happens on the main thread. Use [shared] so every caller in the process
 * sees the same guide.
 */
class GuideOverlay(context: Context) {

    /** How a guide ended. */
    enum class Outcome {
        /** The user tapped Done. */
        Done,

        /** The user tapped Close. */
        Closed,

        /** Another guide took its place. */
        Replaced,

        /** [hide] removed it. */
        Cleared,

        /** The window couldn't be added (e.g. the overlay permission was revoked). */
        Failed,
    }

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val ids = AtomicLong()

    // Main thread only.
    private var current: Showing? = null

    private class Showing(val id: Long, val pointer: PointerView, val card: View?, val onFinished: (Outcome) -> Unit)

    fun canShow(): Boolean = Settings.canDrawOverlays(appContext)

    /**
     * Show a guide around [target] with [message], replacing any guide already showing (whose
     * callback gets [Outcome.Replaced]). [onFinished] runs once on the main thread when this guide
     * ends. Returns the guide's id for [hide].
     */
    fun show(target: Rect, message: String, onFinished: (Outcome) -> Unit): Long {
        val id = ids.incrementAndGet()
        main.post {
            finish(null, Outcome.Replaced)
            val screen = screenSize()
            // The card goes on the side with more room, and the arrow comes from that same side.
            val cardBelow = screen.height() - target.bottom >= target.top
            val pointerView = PointerView(appContext, Rect(target), arrowBelow = cardBelow)
            if (runCatching { windowManager.addView(pointerView, pointerParams()) }.isFailure) {
                onFinished(Outcome.Failed)
                return@post
            }
            val cardView = buildCard(id, message)
            val cardAdded = runCatching { windowManager.addView(cardView, cardParams(cardView, target, screen, cardBelow)) }.isSuccess
            current = Showing(id, pointerView, if (cardAdded) cardView else null, onFinished)
            pointerView.start()
            // Without the card the user can't finish the guide, so report it rather than wait.
            if (!cardAdded) finish(id, Outcome.Failed)
        }
        return id
    }

    /** Remove the guide with [id], or whichever guide is showing when [id] is null. */
    fun hide(id: Long? = null) {
        main.post { finish(id, Outcome.Cleared) }
    }

    /** Remove the current guide (if it matches [id]) and tell its caller [outcome]. Main thread. */
    private fun finish(id: Long?, outcome: Outcome) {
        val showing = current ?: return
        if (id != null && showing.id != id) return
        current = null
        showing.pointer.stop()
        runCatching { windowManager.removeView(showing.pointer) }
        showing.card?.let { runCatching { windowManager.removeView(it) } }
        showing.onFinished(outcome)
    }

    private fun buildCard(id: Long, message: String): View {
        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, appContext.resources.displayMetrics).toInt() }
        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(16f), dp(12f), dp(8f))
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(CARD_COLOR)
            }
            elevation = dp(8f).toFloat()
            // The message is free text from a connected client, drawn over another app. Say where it
            // comes from, so it can't pass as that app asking for a password or PIN.
            addView(
                TextView(appContext).apply {
                    text = SOURCE_LABEL
                    setTextColor(ACCENT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setPadding(0, 0, dp(8f), dp(4f))
                },
            )
            addView(
                TextView(appContext).apply {
                    text = message
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setLineSpacing(0f, 1.15f)
                    setPadding(0, 0, dp(8f), dp(4f))
                },
            )
            addView(
                LinearLayout(appContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    addView(textButton("Close", Color.argb(200, 255, 255, 255)) { finish(id, Outcome.Closed) })
                    addView(textButton("Done", ACCENT_COLOR) { finish(id, Outcome.Done) })
                },
            )
        }
    }

    private fun textButton(label: String, color: Int, onClick: () -> Unit) = Button(appContext, null, android.R.attr.borderlessButtonStyle).apply {
        text = label
        isAllCaps = false
        setTextColor(color)
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setOnClickListener { onClick() }
    }

    private fun pointerParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        overlayType(),
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        // Android 12+ drops touches that pass through another app's overlay unless the overlay is
        // at most this opaque, which would stop the user tapping the very control we point at.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val input = appContext.getSystemService(InputManager::class.java)
            alpha = minOf(input?.maximumObscuringOpacityForTouch ?: MAX_PASS_THROUGH_ALPHA, MAX_PASS_THROUGH_ALPHA)
        }
    }

    /**
     * The card sits below the target or above it ([below]), and always fully on screen so Done and
     * Close stay reachable; for a target that fills the screen it overlaps the target instead.
     */
    private fun cardParams(card: View, target: Rect, screen: Rect, below: Boolean): WindowManager.LayoutParams {
        val density = appContext.resources.displayMetrics.density
        val margin = (16 * density).toInt()
        // Clear of the status and navigation bars.
        val edge = (48 * density).toInt()
        // Room for the ring's halo and the arrow (about 70dp) between the target and the card.
        val gap = (84 * density).toInt()
        val width = screen.width() - 2 * margin
        card.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = card.measuredHeight
        val wanted = if (below) target.bottom + gap else target.top - gap - height
        val top = wanted.coerceIn(edge, maxOf(edge, screen.height() - height - edge))
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = margin
            y = top
        }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Rect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds
    } else {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }

    private fun overlayType() = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

    /**
     * Full-screen, click-through layer: a breathing ring around the target and an arrow to it from
     * the card's side ([arrowBelow]).
     */
    private class PointerView(context: Context, private val target: Rect, private val arrowBelow: Boolean) : View(context) {
        private val density = resources.displayMetrics.density
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * density
            color = ACCENT_COLOR
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10 * density
            color = ACCENT_COLOR
        }
        private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = ACCENT_COLOR
        }
        private val location = IntArray(2)
        private val box = RectF()
        private val halo = RectF()
        private val head = Path()
        private var phase = 0f
        private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                phase = it.animatedValue as Float
                invalidate()
            }
        }

        fun start() = animator.start()

        fun stop() = animator.cancel()

        override fun onDraw(canvas: Canvas) {
            // The window may not start at the screen origin; shift screen coordinates into ours.
            getLocationOnScreen(location)
            box.set(target)
            box.offset(-location[0].toFloat(), -location[1].toFloat())
            val pad = 8 * density
            box.inset(-pad, -pad)
            val corner = 16 * density

            // Expanding, fading halo.
            val grow = phase * 18 * density
            glow.alpha = ((1f - phase) * 110).toInt()
            halo.set(box)
            halo.inset(-grow, -grow)
            canvas.drawRoundRect(halo, corner + grow, corner + grow, glow)
            canvas.drawRoundRect(box, corner, corner, ring)

            // Arrow from the card's side, bobbing towards the target.
            val pointsDown = !arrowBelow
            val bob = (sin(phase * 2 * PI) * 6 * density).toFloat()
            val cx = box.centerX()
            val size = 22 * density
            val tipY = if (pointsDown) box.top - 10 * density + bob else box.bottom + 10 * density - bob
            val baseY = if (pointsDown) tipY - size else tipY + size
            // No room for it (a target that fills the screen): the ring alone is enough.
            val arrowEnd = if (pointsDown) baseY - size * 1.6f else baseY + size * 1.6f
            if (arrowEnd < 0f || arrowEnd > height.toFloat()) return
            head.rewind()
            head.moveTo(cx, tipY)
            head.lineTo(cx - size * 0.75f, baseY)
            head.lineTo(cx + size * 0.75f, baseY)
            head.close()
            canvas.drawPath(head, arrow)
            val shaftTop = if (pointsDown) baseY - size * 1.6f else baseY
            val shaftBottom = if (pointsDown) baseY else baseY + size * 1.6f
            canvas.drawRoundRect(cx - size * 0.22f, shaftTop, cx + size * 0.22f, shaftBottom, 4 * density, 4 * density, arrow)
        }
    }

    companion object {
        @Volatile
        private var instance: GuideOverlay? = null

        /** The process-wide guide, so a new set of tools still sees (and can clear) an older guide. */
        fun shared(context: Context): GuideOverlay =
            instance ?: synchronized(this) { instance ?: GuideOverlay(context).also { instance = it } }

        // The default maximum obscuring opacity; anything above it blocks pass-through touches.
        private const val MAX_PASS_THROUGH_ALPHA = 0.8f

        private const val SOURCE_LABEL = "From your AI assistant, not this app"
        private val ACCENT_COLOR = Color.rgb(0x7F, 0xD8, 0xBE)
        private val CARD_COLOR = Color.rgb(0x1A, 0x22, 0x1F)
    }
}
