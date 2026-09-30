package io.droidmcp.intent

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the intent-dispatch tools ([SendIntentTool], [ShareContentTool],
 * [OpenDeepLinkTool]). Declares no manifest permissions; [SendIntentTool] enforces a
 * safe-action allowlist, and both URI-bearing tools enforce a data-URI scheme allowlist
 * ([IntentGuards.ALLOWED_SCHEMES]). All three refuse to launch while the host is in the
 * background on API 29+ unless it holds `SYSTEM_ALERT_WINDOW`.
 */
object IntentTools {

    /** All intent tools, bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        SendIntentTool(context),
        ShareContentTool(context),
        OpenDeepLinkTool(context),
    )

    /** None — these tools require no runtime permissions. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always `true`; no permissions are needed. */
    fun hasPermissions(context: Context): Boolean = true
}
