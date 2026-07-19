package io.droidmcp.print

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the print module: [ListPrintersTool] and [PrintContentTool].
 *
 * Requires no permissions; printing is mediated entirely by the system print UI.
 */
object PrintTools {

    /** All print tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ListPrintersTool(context),
        PrintContentTool(context),
    )

    /** Empty — printing needs no runtime permissions. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; no permissions are required. */
    fun hasPermissions(context: Context): Boolean = true
}
