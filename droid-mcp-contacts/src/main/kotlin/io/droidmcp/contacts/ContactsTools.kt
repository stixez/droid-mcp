package io.droidmcp.contacts

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the contacts tool module. Wires up [SearchContactsTool], [ReadContactTool],
 * [ListContactsTool] and [CreateContactTool], which query and insert via `ContactsContract`.
 *
 * The read tools need only `READ_CONTACTS`, so that is all [requiredPermissions] reports — a
 * host with read-only contacts access still gets the read surface. [CreateContactTool] is gated
 * inside [all]: it is registered only when `WRITE_CONTACTS` is also granted (mirroring how
 * `CalendarTools` gates its write tools).
 */
object ContactsTools {

    /** Contacts [McpTool]s for the current grant state: read tools always, [CreateContactTool] only with `WRITE_CONTACTS`. */
    fun all(context: Context): List<McpTool> = buildList {
        add(SearchContactsTool(context))
        add(ReadContactTool(context))
        add(ListContactsTool(context))
        if (PermissionHelper.hasPermissions(context, listOf(Manifest.permission.WRITE_CONTACTS))) {
            add(CreateContactTool(context))
        }
    }

    /** Permissions needed to register this module at all: `READ_CONTACTS` (write access is gated per tool in [all]). */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CONTACTS,
    )

    /** True if all [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
