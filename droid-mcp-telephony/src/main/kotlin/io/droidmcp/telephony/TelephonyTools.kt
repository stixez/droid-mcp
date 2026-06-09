package io.droidmcp.telephony

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the telephony tools: phone number, SIM info, network operator, and call state.
 * The declared permissions gate the identity-bearing tools ([GetPhoneNumberTool],
 * [GetSimInfoTool], and call state on API 31+); [GetNetworkOperatorTool] needs none. Each
 * tool degrades gracefully (null fields) when its permission is absent rather than erroring.
 */
object TelephonyTools {

    /** All telephony [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetPhoneNumberTool(context),
        GetSimInfoTool(context),
        GetNetworkOperatorTool(context),
        GetCallStateTool(context),
    )

    /** Permissions backing the identity/state tools. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_SMS,
    )

    /** True when [requiredPermissions] are granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
