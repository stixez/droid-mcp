package io.droidmcp.device

import android.os.Build
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports static device identity and display metrics: `manufacturer`, `model`, `brand`,
 * `os_version`, `sdk_version`, and `screen_width`/`screen_height`/`screen_density`. No permissions.
 */
class GetDeviceInfoTool(private val context: Context) : McpTool {

    override val name = "get_device_info"
    override val description = "Get the phone's manufacturer, model, Android version, SDK level and screen size."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        string("manufacturer", nullable = true)
        string("model", nullable = true)
        string("brand", nullable = true)
        string("os_version", nullable = true)
        integer("sdk_version")
        integer("screen_width")
        integer("screen_height")
        number("screen_density")
    }

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
