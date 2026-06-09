package io.droidmcp.device

import android.os.Build
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports static device identity and display metrics: `manufacturer`, `model`, `brand`,
 * `os_version`, `sdk_version`, and `screen_width`/`screen_height`/`screen_density`. No permissions.
 */
class GetDeviceInfoTool(private val context: Context) : McpTool {

    override val name = "get_device_info"
    override val description = "Get device information: model, manufacturer, OS version, screen size"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

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
