package io.droidmcp.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.view.Display
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * `take_screenshot_via_a11y` — capture the screen via
 * `AccessibilityService.takeScreenshot`, awaiting the system callback. Unlike
 * the screenshot module's MediaProjection path, this needs no consent prompt,
 * but it requires API 30+ (the tool is filtered out of
 * [AccessibilityTools.supportedTools] below that).
 *
 * Params: `format` (`jpeg`/`jpg` default, or `png` lossless); `quality` (JPEG
 * only, clamped 1–100, default 80; ignored for PNG); `max_dimension` (clamped
 * 64–4096, default 1280) — the capture is downscaled (aspect preserved) so its
 * longest side is at most this many pixels before encoding, keeping the
 * base64 payload a sensible size for LLM context windows.
 *
 * On success returns `format`, `width`, `height` (of the encoded image, i.e.
 * after downscaling), `original_width`, `original_height` (the raw capture),
 * and `image_base64` (NO_WRAP base64 of the encoded image). Every intermediate
 * bitmap is recycled, including one delivered after the call was cancelled. Errors are long-form messages, one
 * per failure mode: API-too-old, [notConnectedError] (service not bound),
 * invalid `format`, and the framework `ERROR_TAKE_SCREENSHOT_*` codes —
 * notably FLAG_SECURE windows ([errorMessageFor]) and the rate-limit
 * (`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`).
 */
class TakeScreenshotViaA11yTool(private val context: Context) : McpTool {

    override val name = "take_screenshot_via_a11y"
    override val description = "Capture the screen via AccessibilityService.takeScreenshot — no MediaProjection consent prompt required. Returns a base64-encoded JPEG (default) or PNG, downscaled so the longest side is at most `max_dimension` (default 1280). Requires API 30+. Returns a specific error when the foreground window has FLAG_SECURE set or the screenshot rate-limit was hit."
    override val parameters = listOf(
        ToolParameter("format", "'jpeg' (default) or 'png' (lossless, larger).", ParameterType.STRING, required = false),
        ToolParameter("quality", "JPEG quality 1-100, default 80. Ignored for PNG.", ParameterType.INTEGER, required = false),
        ToolParameter("max_dimension", "Downscale so the longest side is at most this many pixels (64-4096, default 1280).", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true)

    private sealed class CaptureOutcome {
        data class Success(val bitmap: Bitmap) : CaptureOutcome()
        data class Failure(val errorCode: Int) : CaptureOutcome()
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ToolResult.error("take_screenshot_via_a11y requires Android 11 (API 30) or newer. Use screenshot module's capture_screen (MediaProjection) on older devices.")
        }
        val svc = AccessibilityServiceHolder.service ?: return ToolResult.error(notConnectedError())

        val format = (params["format"] as? String)?.lowercase() ?: "jpeg"
        val compressFormat = when (format) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            else -> return ToolResult.error("format must be 'png' or 'jpeg'.")
        }
        val quality = (params["quality"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 80
        val maxDimension = (params["max_dimension"] as? Number)?.toInt()?.coerceIn(64, 4096) ?: 1280

        val outcome = suspendCancellableCoroutine<CaptureOutcome> { cont ->
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val hwBuffer = screenshot.hardwareBuffer
                    val bm = try {
                        val wrapped = Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
                        try {
                            wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            wrapped?.recycle()
                        }
                    } finally {
                        hwBuffer.close()
                    }
                    if (cont.isActive) {
                        cont.resume(if (bm != null) CaptureOutcome.Success(bm) else CaptureOutcome.Failure(-1))
                    } else {
                        // Caller was cancelled / timed out before the callback fired.
                        bm?.recycle()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(CaptureOutcome.Failure(errorCode))
                }
            }
            svc.takeScreenshot(Display.DEFAULT_DISPLAY, context.mainExecutor, callback)
        }

        val bitmap = when (outcome) {
            is CaptureOutcome.Failure -> return ToolResult.error(errorMessageFor(outcome.errorCode))
            is CaptureOutcome.Success -> outcome.bitmap
        }

        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val scaled = downscaleToFit(bitmap, maxDimension)
        val width = scaled.width
        val height = scaled.height
        val baos = ByteArrayOutputStream()
        try {
            scaled.compress(compressFormat, quality, baos)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        return ToolResult.success(mapOf(
            "format" to format,
            "width" to width,
            "height" to height,
            "original_width" to originalWidth,
            "original_height" to originalHeight,
            "image_base64" to b64,
        ))
    }

    /** Map a framework `ERROR_TAKE_SCREENSHOT_*` code (or the sentinel `-1`
     *  meaning "bitmap decode produced no image") to a human-readable message. */
    private fun errorMessageFor(code: Int): String = when (code) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR ->
            "Screenshot failed: internal error (ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR)."
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            "Screenshot failed: accessibility access was revoked mid-call (ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS)."
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            "Screenshot rate-limited: wait before retrying (ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT)."
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
            "Screenshot failed: invalid display (ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY)."
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW ->
            "Screenshot blocked by FLAG_SECURE window — the foreground app prohibits capture (ERROR_TAKE_SCREENSHOT_SECURE_WINDOW)."
        -1 -> "Screenshot succeeded but bitmap decoding produced no image."
        else -> "Screenshot failed with error code $code."
    }

    /**
     * Return [src] scaled (bilinear, aspect preserved) so its longest side is
     * at most [maxDimension], or [src] itself when it already fits. The caller
     * owns both bitmaps and must recycle the result when it differs from [src].
     */
    private fun downscaleToFit(src: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxDimension) return src
        val scale = maxDimension.toFloat() / longest
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
