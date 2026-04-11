package io.droidmcp.sensors

import android.content.Context
import io.droidmcp.core.McpTool

object SensorTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetAccelerometerTool(context),
        GetGyroscopeTool(context),
        GetLightLevelTool(context),
        GetProximityTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
