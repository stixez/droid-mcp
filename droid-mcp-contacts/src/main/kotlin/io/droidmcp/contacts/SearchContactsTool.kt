package io.droidmcp.contacts

import android.content.Context
import android.provider.ContactsContract
import io.droidmcp.core.*

/**
 * Searches contacts whose `DISPLAY_NAME_PRIMARY` matches `query` (SQL `LIKE` substring),
 * then attaches each match's phone numbers and email addresses via sub-queries. Requires
 * `READ_CONTACTS`. Output: `contacts` (list of {id, name, phones (list of strings), emails
 * (list of strings)}), `count`, and the echoed `query`, capped at `limit` (1–100, default 10).
 */
class SearchContactsTool(private val context: Context) : McpTool {

    override val name = "search_contacts"
    override val description = "Search contacts by display name (substring match)"
    override val parameters = listOf(
        ToolParameter("query", "Search query matched against the contact's display name", ParameterType.STRING, required = true),
        ToolParameter("limit", "Max results. Default 10.", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )

        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

        val contacts = mutableListOf<Map<String, Any?>>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                val hasPhone = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
                val phones = if (hasPhone) getPhoneNumbers(contactId) else emptyList()
                val emails = getEmails(contactId)

                contacts.add(mapOf(
                    "id" to contactId,
                    "name" to name,
                    "phones" to phones,
                    "emails" to emails,
                ))
                count++
            }
        }

        return ToolResult.success(mapOf(
            "contacts" to contacts,
            "count" to contacts.size,
            "query" to query,
        ))
    }

    /** Returns the contact's phone numbers as plain strings. */
    private fun getPhoneNumbers(contactId: Long): List<String> {
        val phones = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { phones.add(it) }
            }
        }
        return phones
    }

    /** Returns the contact's email addresses as plain strings. */
    private fun getEmails(contactId: Long): List<String> {
        val emails = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { emails.add(it) }
            }
        }
        return emails
    }
}
