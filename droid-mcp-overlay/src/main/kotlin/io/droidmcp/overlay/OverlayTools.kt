package io.droidmcp.overlay

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionStatus

/**
 * No LLM tools — overlay is a programmatic-only primitive. The provider
 * object exists for shape parity with other modules (so the sample app can
 * uniformly query `permissionStatus(context)`).
 */
object OverlayTools {

    fun all(context: Context): List<McpTool> = emptyList()

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

    fun supportedTools(context: Context): Set<String> = emptySet()
}
