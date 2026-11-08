package io.droidmcp.overlay

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus

/**
 * Overlay tools: `guide_user` points the user at something on screen and waits for them, and
 * `clear_guide` removes it. Both need "display over other apps" ([permissionStatus]). The
 * floating chip ([OverlayController]) stays a host-only API.
 */
object OverlayTools {

    /** Both tools, sharing the process-wide [GuideOverlay.shared] so rebuilt tools still see an older guide. */
    fun all(context: Context): List<McpTool> {
        val overlay = GuideOverlay.shared(context)
        return listOf(GuideUserTool(overlay), ClearGuideTool(overlay))
    }

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = OverlayController(context).isPermissionGranted()

    fun permissionStatus(context: Context): PermissionStatus {
        val controller = OverlayController(context)
        return if (controller.isPermissionGranted()) {
            PermissionStatus.Granted("Overlay permission granted")
        } else {
            PermissionStatus.NotGranted(
                message = "Overlay permission (SYSTEM_ALERT_WINDOW) not granted. Open Settings to enable.",
                intent = controller.permissionIntent(),
            )
        }
    }

    fun supportedTools(context: Context): Set<String> = setOf("guide_user", "clear_guide")
}
