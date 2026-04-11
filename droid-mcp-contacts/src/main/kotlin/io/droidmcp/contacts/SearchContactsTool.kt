package io.droidmcp.contacts

import android.content.Context
import android.provider.ContactsContract
import io.droidmcp.core.*

class SearchContactsTool(private val context: Context) : McpTool {

    override val name = "search_contacts"
    override val description = "Search contacts by name, phone number, or email address"
    override val parameters = listOf(
        ToolParameter("query", "Search query (name, phone, or email)", ParameterType.STRING, required = true),
        ToolParameter("limit", "Max results. Default 10.", ParameterType.INTEGER),
    )

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
