package io.droidmcp.device

import android.os.Build
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetDeviceInfoTool(private val context: Context) : McpTool {

    override val name = "get_device_info"
    override val description = "Get device information: model, manufacturer, OS version, screen size"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val display = context.resources.displayMetrics
        return ToolResult.success(mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "os_version" to Build.VERSION.RELEASE,
            "sdk_version" to Build.VERSION.SDK_INT,
            "screen_width" to display.widthPixels,
            "screen_height" to display.heightPixels,
            "screen_density" to display.density,
        ))
    }
}
