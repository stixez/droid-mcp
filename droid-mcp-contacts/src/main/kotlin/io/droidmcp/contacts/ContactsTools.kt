package io.droidmcp.contacts

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper
import io.droidmcp.core.ResourceProvider

/**
 * Provider for the contacts tool module. Wires up [SearchContactsTool], [ReadContactTool],
 * [ListContactsTool], [CreateContactTool], [UpdateContactTool] and [DeleteContactTool], which
 * query and edit via `ContactsContract`.
 *
 * The read tools need only `READ_CONTACTS`, so that is all [requiredPermissions] reports — a
 * host with read-only contacts access still gets the read surface. The write tools are gated
 * inside [all]: they are registered only when `WRITE_CONTACTS` is also granted (mirroring how
 * `CalendarTools` gates its write tools).
 */
object ContactsTools {

    /** Contacts [McpTool]s for the current grant state: read tools always, the write tools only with `WRITE_CONTACTS`. */
    fun all(context: Context): List<McpTool> = buildList {
        add(SearchContactsTool(context))
        add(ReadContactTool(context))
        add(ListContactsTool(context))
        if (PermissionHelper.hasPermissions(context, listOf(Manifest.permission.WRITE_CONTACTS))) {
            add(CreateContactTool(context))
            add(UpdateContactTool(context))
            add(DeleteContactTool(context))
        }
    }

    /** Contacts by id as MCP resources; register with `DroidMcp.Builder.addResourceProvider`. */
    fun resources(context: Context): ResourceProvider = ContactsResourceProvider(context)

    /** Permissions needed to register this module at all: `READ_CONTACTS` (write access is gated per tool in [all]). */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.READ_CONTACTS,
    )

    /** True if all [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
