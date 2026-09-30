package io.droidmcp.print

import android.app.Activity
import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the print module: [ListPrintersTool] and [PrintContentTool].
 *
 * Requires no permissions; printing is mediated entirely by the system print UI.
 *
 * [PrintContentTool] needs a live, resumed [Activity]: `PrintManager.print()` throws when called
 * from an application context. Pass an [activityProvider] that returns the host's current
 * foreground Activity (or `null` when none is showing). If the provider returns `null` and the
 * supplied [Context] is not itself an Activity, `print_content` returns a clear error instead of
 * pretending to succeed.
 */
object PrintTools {

    /**
     * All print tools bound to [context].
     *
     * @param activityProvider Returns the Activity to launch the system print UI from, or `null`
     *   when none is available. Defaults to always-`null` (only works if [context] is an Activity).
     */
    fun all(
        context: Context,
        activityProvider: () -> Activity? = { null },
    ): List<McpTool> = listOf(
        ListPrintersTool(context),
        PrintContentTool(context, activityProvider),
    )

    /** Empty — printing needs no runtime permissions. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; no permissions are required. */
    fun hasPermissions(context: Context): Boolean = true
}
