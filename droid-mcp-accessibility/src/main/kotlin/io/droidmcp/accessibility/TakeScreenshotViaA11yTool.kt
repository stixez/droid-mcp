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

class TakeScreenshotViaA11yTool(private val context: Context) : McpTool {

    override val name = "take_screenshot_via_a11y"
    override val description = "Capture the screen via AccessibilityService.takeScreenshot — no MediaProjection consent prompt required. Returns a base64-encoded PNG (default) or JPEG. Requires API 30+. Returns a specific error when the foreground window has FLAG_SECURE set or the screenshot rate-limit was hit."
    override val parameters = listOf(
        ToolParameter("format", "'png' (default, lossless) or 'jpeg'.", ParameterType.STRING, required = false),
        ToolParameter("quality", "JPEG quality 1-100, default 80. Ignored for PNG.", ParameterType.INTEGER, required = false),
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

        val format = (params["format"] as? String)?.lowercase() ?: "png"
        val compressFormat = when (format) {
            "png" -> Bitmap.CompressFormat.PNG
            "jpeg", "jpg" -> Bitmap.CompressFormat.JPEG
            else -> return ToolResult.error("format must be 'png' or 'jpeg'.")
        }
        val quality = (params["quality"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 80

        val outcome = suspendCancellableCoroutine<CaptureOutcome> { cont ->
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    val hwBuffer = screenshot.hardwareBuffer
                    val bm = try {
                        Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        hwBuffer.close()
                    }
                    if (cont.isActive) {
                        cont.resume(if (bm != null) CaptureOutcome.Success(bm) else CaptureOutcome.Failure(-1))
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

        val width = bitmap.width
        val height = bitmap.height
        val baos = ByteArrayOutputStream()
        bitmap.compress(compressFormat, quality, baos)
        bitmap.recycle()
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        return ToolResult.success(mapOf(
            "format" to format,
            "width" to width,
            "height" to height,
            "image_base64" to b64,
        ))
    }

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
}
