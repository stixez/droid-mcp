package io.droidmcp.contacts

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object ContactsTools {

    fun all(context: Context): List<McpTool> = listOf(
        SearchContactsTool(context),
        ReadContactTool(context),
        ListContactsTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CONTACTS,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
