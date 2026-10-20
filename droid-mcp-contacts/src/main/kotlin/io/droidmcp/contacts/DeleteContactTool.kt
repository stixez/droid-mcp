package io.droidmcp.contacts

import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Deletes a contact (the aggregated `contact_id`) and every raw contact merged into it. Requires
 * `WRITE_CONTACTS` and `READ_CONTACTS`. Errors when the contact doesn't exist, so a repeated call
 * reports not-found. On synced accounts the provider marks the raw contacts deleted and the sync
 * adapter removes them on its next sync.
 * Output: `contact_id`, `deleted` (true), and the deleted contact's `name`.
 */
class DeleteContactTool(private val context: Context) : McpTool {

    override val name = "delete_contact"
    override val description = "Delete a contact by ID, including all its phone numbers and emails. Cannot be undone."
    override val parameters = listOf(
        ToolParameter("contact_id", "ID of the contact to delete (the `id` from search_contacts or read_contact)", ParameterType.INTEGER, required = true, minimum = 1.0),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        integer("contact_id")
        boolean("deleted")
        string("name", nullable = true)
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val contactId = ContactsUtils.parseId(params["contact_id"])
            ?: return ToolResult.error("contact_id is required and must be a positive number")

        var found = false
        val name = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                found = true
                cursor.getString(0)
            } else {
                null
            }
        }
        if (!found) return ToolResult.error("Contact not found with ID: $contactId")

        val rows = try {
            context.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), null, null)
        } catch (e: Exception) {
            return ToolResult.error("Failed to delete contact $contactId: ${e.message}")
        }
        if (rows == 0) return ToolResult.error("Failed to delete contact $contactId")
        return ToolResult.success(mapOf("contact_id" to contactId, "deleted" to true, "name" to name))
    }
}
