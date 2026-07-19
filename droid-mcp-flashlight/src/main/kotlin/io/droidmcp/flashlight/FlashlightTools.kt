package io.droidmcp.flashlight

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for flashlight/torch tools: [ToggleFlashlightTool] and [SetFlashlightBrightnessTool].
 *
 * Torch control ([android.hardware.camera2.CameraManager.setTorchMode]) needs no runtime
 * permission — it's a distinct, permission-free API precisely so apps can control the
 * flashlight without requesting full `CAMERA` access.
 */
object FlashlightTools {
    /** All flashlight tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ToggleFlashlightTool(context),
        SetFlashlightBrightnessTool(context)
    )

    /** No permissions required — see object KDoc. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; see [requiredPermissions]. */
    fun hasPermissions(context: Context): Boolean = true
}
