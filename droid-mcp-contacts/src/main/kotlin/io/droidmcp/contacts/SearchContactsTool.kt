package io.droidmcp.contacts

import android.content.Context
import android.provider.ContactsContract
import io.droidmcp.core.*

/**
 * Searches contacts whose `DISPLAY_NAME_PRIMARY` matches `query` (SQL `LIKE` substring;
 * `%`, `_` and `\` in `query` match literally), then attaches every match's phone numbers and
 * email addresses with a single `ContactsContract.Data` query (`CONTACT_ID IN (...)`). Requires
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
        )

        val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ? ESCAPE '\\'"
        val selectionArgs = arrayOf("%${escapeLike(query)}%")
        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

        // Pass 1: collect up to `limit` matching contacts (id + name), preserving sort order.
        val matches = LinkedHashMap<Long, String?>()
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext() && matches.size < limit) {
                matches[cursor.getLong(idIdx)] = cursor.getString(nameIdx)
            }
        }

        // Pass 2: one Data query for all phones + emails of those contacts, grouped in memory
        // (previously two sub-queries per contact — up to 200 extra queries at limit=100).
        val phones = HashMap<Long, MutableList<String>>()
        val emails = HashMap<Long, MutableList<String>>()
        if (matches.isNotEmpty()) {
            val ids = matches.keys.toList()
            val placeholders = ids.joinToString(",") { "?" }
            val dataSelection = "${ContactsContract.Data.CONTACT_ID} IN ($placeholders) AND " +
                "${ContactsContract.Data.MIMETYPE} IN (?, ?)"
            val dataArgs = ids.map { it.toString() } + listOf(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            )
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.Data.MIMETYPE,
                    // Phone.NUMBER and Email.ADDRESS are both aliases of DATA1.
                    ContactsContract.Data.DATA1,
                ),
                dataSelection,
                dataArgs.toTypedArray(),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(0)
                    val value = cursor.getString(2) ?: continue
                    when (cursor.getString(1)) {
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                            phones.getOrPut(contactId) { mutableListOf() }.add(value)
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                            emails.getOrPut(contactId) { mutableListOf() }.add(value)
                    }
                }
            }
        }

        val contacts = matches.map { (id, name) ->
            mapOf(
                "id" to id,
                "name" to name,
                "phones" to (phones[id] ?: emptyList<String>()),
                "emails" to (emails[id] ?: emptyList<String>()),
            )
        }

        return ToolResult.success(mapOf(
            "contacts" to contacts,
            "count" to contacts.size,
            "query" to query,
        ))
    }

    /** Escapes `\`, `%`, `_` so [value] matches literally inside a `LIKE ? ESCAPE '\'` clause. */
    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
