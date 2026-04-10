package io.droidmcp.sms

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object SmsTools {

    fun all(context: Context): List<McpTool> = listOf(
        ReadMessagesTool(context),
        SendMessageTool(),
        SearchMessagesTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
