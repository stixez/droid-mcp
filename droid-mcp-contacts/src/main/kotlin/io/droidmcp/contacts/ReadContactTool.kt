package io.droidmcp.contacts

import android.content.Context
import android.provider.ContactsContract
import io.droidmcp.core.*

class ReadContactTool(private val context: Context) : McpTool {

    override val name = "read_contact"
    override val description = "Get full details for a specific contact by ID"
    override val parameters = listOf(
        ToolParameter("contact_id", "The contact ID", ParameterType.INTEGER, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val contactId = (params["contact_id"] as? Number)?.toLong()
            ?: params["contact_id"]?.toString()?.toLongOrNull()
            ?: return ToolResult.error("contact_id is required and must be a number")

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )

        val contact = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            "${ContactsContract.Contacts._ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
            } else null
        } ?: return ToolResult.error("Contact not found with ID: $contactId")

        val phones = getPhoneNumbers(contactId)
        val emails = getEmails(contactId)
        val addresses = getAddresses(contactId)

        return ToolResult.success(mapOf(
            "id" to contactId,
            "name" to contact,
            "phones" to phones,
            "emails" to emails,
            "addresses" to addresses,
        ))
    }

    private fun getPhoneNumbers(contactId: Long): List<Map<String, String>> {
        val phones = mutableListOf<Map<String, String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
            ),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(0) ?: continue
                val type = when (cursor.getInt(1)) {
                    ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
                    ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
                    else -> "other"
                }
                phones.add(mapOf("number" to number, "type" to type))
            }
        }
        return phones
    }

    private fun getEmails(contactId: Long): List<Map<String, String>> {
        val emails = mutableListOf<Map<String, String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE,
            ),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(0) ?: continue
                val type = when (cursor.getInt(1)) {
                    ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "home"
                    ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "work"
                    else -> "other"
                }
                emails.add(mapOf("address" to address, "type" to type))
            }
        }
        return emails
    }

    private fun getAddresses(contactId: Long): List<String> {
        val addresses = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),
            "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { addresses.add(it) }
            }
        }
        return addresses
    }
}
