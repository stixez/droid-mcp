package io.droidmcp.screen

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetDisplayInfoTool(private val context: Context) : McpTool {

    override val name = "get_display_info"
    override val description = "Get display details including resolution, density, refresh rate, and HDR capabilities"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return ToolResult.error("Could not get default display")

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.density
        val densityDpi = metrics.densityDpi

        val refreshRate = display.refreshRate

        val hdrCapable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            display.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
        } else {
            false
        }

        return ToolResult.success(mapOf(
            "width" to width,
            "height" to height,
            "density" to density,
            "density_dpi" to densityDpi,
            "refresh_rate" to refreshRate,
            "hdr_capable" to hdrCapable,
        ))
    }
}
