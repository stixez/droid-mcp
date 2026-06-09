package io.droidmcp.sms

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the SMS tool module. Wires up [ReadMessagesTool], [SendMessageTool], and
 * [SearchMessagesTool]. Reads query `Telephony.Sms` (needs `READ_SMS`); sending uses
 * `SmsManager` (needs `SEND_SMS`).
 */
object SmsTools {

    /** Returns all SMS [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ReadMessagesTool(context),
        SendMessageTool(context),
        SearchMessagesTool(context),
    )

    /** Permissions this module needs: `READ_SMS` and `SEND_SMS`. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
    )

    /** True if all [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
