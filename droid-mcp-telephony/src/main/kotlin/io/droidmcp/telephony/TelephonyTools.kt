package io.droidmcp.telephony

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object TelephonyTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetPhoneNumberTool(context),
        GetSimInfoTool(context),
        GetNetworkOperatorTool(context),
        GetCallStateTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_SMS,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
