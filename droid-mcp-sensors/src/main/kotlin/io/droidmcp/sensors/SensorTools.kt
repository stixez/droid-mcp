package io.droidmcp.sensors

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the motion/environment sensor tools (accelerometer, gyroscope, light,
 * proximity). None of these require any permission.
 */
object SensorTools {

    /** All sensor [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetAccelerometerTool(context),
        GetGyroscopeTool(context),
        GetLightLevelTool(context),
        GetProximityTool(context),
    )

    /** No permissions are needed for these sensors. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; the sensors require no permission. */
    fun hasPermissions(context: Context): Boolean = true
}
