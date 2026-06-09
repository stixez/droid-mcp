package io.droidmcp.keyguard

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the keyguard module: [GetLockStateTool] and [GetKeyguardInfoTool].
 *
 * All tools are read-only and require no permissions.
 */
object KeyguardTools {

    /** All keyguard tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetLockStateTool(context),
        GetKeyguardInfoTool(context),
    )

    /** Empty — keyguard reads need no runtime permissions. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; no permissions are required. */
    fun hasPermissions(context: Context): Boolean = true
}
