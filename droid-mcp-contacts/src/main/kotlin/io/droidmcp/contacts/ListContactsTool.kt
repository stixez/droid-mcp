package io.droidmcp.contacts

import android.content.Context
import android.provider.ContactsContract
import io.droidmcp.core.*

class ListContactsTool(private val context: Context) : McpTool {

    override val name = "list_contacts"
    override val description = "List all contacts with basic info, paginated"
    override val parameters = listOf(
        ToolParameter("limit", "Max results per page. Default 50.", ParameterType.INTEGER),
        ToolParameter("offset", "Number of contacts to skip. Default 0.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 50
        val offset = (params["offset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
        )

        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $limit OFFSET $offset"

        val contacts = mutableListOf<Map<String, Any?>>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, projection, null, null, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                contacts.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)),
                    "name" to cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)),
                    "has_phone" to (cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0),
                ))
            }
        }

        return ToolResult.success(mapOf(
            "contacts" to contacts,
            "count" to contacts.size,
            "offset" to offset,
            "limit" to limit,
        ))
    }
}
