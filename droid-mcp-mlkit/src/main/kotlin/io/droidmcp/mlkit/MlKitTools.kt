package io.droidmcp.mlkit

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the ML Kit vision module: [RecognizeTextTool], [LabelImageTool], [DetectFacesTool].
 *
 * All tools run on-device and operate on local image files inside the external-storage sandbox
 * (see [PathValidator]), so no runtime permissions are declared; storage access is the host app's
 * concern.
 */
object MlKitTools {
    /** All ML Kit vision tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        RecognizeTextTool(context),
        LabelImageTool(context),
        DetectFacesTool(context),
    )

    /** Empty — image files are accessed within the host app's storage scope. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; no permissions are required. */
    fun hasPermissions(context: Context): Boolean = true
}
