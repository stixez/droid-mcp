package io.droidmcp.mlkit

import android.content.Context
import io.droidmcp.core.McpTool

object MlKitTools {
    fun all(context: Context): List<McpTool> = listOf(
        RecognizeTextTool(context),
        LabelImageTool(context),
        DetectFacesTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
