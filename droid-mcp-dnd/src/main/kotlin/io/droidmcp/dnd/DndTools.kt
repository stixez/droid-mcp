package io.droidmcp.dnd

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the Do Not Disturb tools: [GetDndStatusTool] and [SetDndModeTool].
 *
 * Reading status needs no special access; [SetDndModeTool] additionally requires
 * DND policy access granted via Settings (the `ACCESS_NOTIFICATION_POLICY`
 * manifest permission gates the request, but the access itself is user-granted).
 */
object DndTools {

    /** Instantiates all DND tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetDndStatusTool(context),
        SetDndModeTool(context),
    )

    /** The `ACCESS_NOTIFICATION_POLICY` permission associated with DND control. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_NOTIFICATION_POLICY,
    )

    /** Returns whether [context] holds the notification-policy permission. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
