package io.droidmcp.contacts

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the contacts tool module. Wires up [SearchContactsTool], [ReadContactTool],
 * and [ListContactsTool], which query `ContactsContract` read-only. Requires `READ_CONTACTS`.
 */
object ContactsTools {

    /** Returns all contacts [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        SearchContactsTool(context),
        ReadContactTool(context),
        ListContactsTool(context),
    )

    /** Permissions this module needs: `READ_CONTACTS`. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CONTACTS,
    )

    /** True if all [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
