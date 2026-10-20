package io.droidmcp.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Edits an existing contact (the aggregated `contact_id` from `search_contacts` / `read_contact`)
 * in one `ContactsContract` batch: renames it, and adds or removes a phone number or email.
 * Requires `WRITE_CONTACTS` and `READ_CONTACTS`.
 *
 * - `name` replaces the display name on every raw contact; the provider re-derives the given /
 *   family name parts from it. A contact with no name row yet (e.g. saved with only a number)
 *   gets one on its first raw contact.
 * - `add_phone` / `add_email` go onto the contact's first raw contact, labelled by `phone_type`
 *   (default mobile) / `email_type` (default home), validated like `create_contact`.
 * - `remove_phone` deletes every number with the same digits (formatting is ignored);
 *   `remove_email` deletes every matching address (case-insensitive). Removing something the
 *   contact doesn't have is an error, so a typo isn't silently ignored.
 *
 * Contacts synced from a read-only account may reject the batch; the provider's error is returned.
 * Output: `contact_id`, `updated` (the changes applied, e.g. `"name"`, `"add_phone"`), and
 * `phones_removed` / `emails_removed` counts.
 */
class UpdateContactTool(private val context: Context) : McpTool {

    override val name = "update_contact"
    override val description = "Edit an existing contact: change its name, add or remove a phone number, add or remove an email. Pass only what should change."
    override val parameters = listOf(
        ToolParameter("contact_id", "ID of the contact to edit (the `id` from search_contacts or read_contact)", ParameterType.INTEGER, required = true, minimum = 1.0),
        ToolParameter("name", "New full display name", ParameterType.STRING),
        ToolParameter("add_phone", "Phone number to add", ParameterType.STRING),
        ToolParameter("phone_type", "Label for add_phone. Default mobile.", ParameterType.STRING, enumValues = ContactsUtils.PHONE_TYPES.keys.toList()),
        ToolParameter("remove_phone", "Phone number to remove (matched by digits)", ParameterType.STRING),
        ToolParameter("add_email", "Email address to add", ParameterType.STRING),
        ToolParameter("email_type", "Label for add_email. Default home.", ParameterType.STRING, enumValues = ContactsUtils.EMAIL_TYPES.keys.toList()),
        ToolParameter("remove_email", "Email address to remove (case-insensitive)", ParameterType.STRING),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)
    override val outputSchema = OutputSchema.obj {
        integer("contact_id")
        arrayOf("updated", OutputSchema.string())
        integer("phones_removed")
        integer("emails_removed")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val contactId = ContactsUtils.parseId(params["contact_id"])
            ?: return ToolResult.error("contact_id is required and must be a positive number")
        fun text(key: String) = params[key]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val name = text("name")
        val addPhone = text("add_phone")
        val removePhone = text("remove_phone")
        val addEmail = text("add_email")
        val removeEmail = text("remove_email")
        if (listOf(name, addPhone, removePhone, addEmail, removeEmail).all { it == null }) {
            return ToolResult.error("Nothing to update: pass name, add_phone, remove_phone, add_email or remove_email")
        }
        if (addPhone != null && !ContactsUtils.isValidPhone(addPhone)) {
            return ToolResult.error("Invalid phone '$addPhone'. Use digits with optional +, spaces, dashes or parentheses")
        }
        if (addEmail != null && !ContactsUtils.isValidEmail(addEmail)) return ToolResult.error("Invalid email '$addEmail'")
        val phoneType = ContactsUtils.resolveType(params["phone_type"]?.toString(), ContactsUtils.PHONE_TYPES, "mobile")
            ?: return ToolResult.error("phone_type must be one of: ${ContactsUtils.PHONE_TYPES.keys.joinToString()}")
        val emailType = ContactsUtils.resolveType(params["email_type"]?.toString(), ContactsUtils.EMAIL_TYPES, "home")
            ?: return ToolResult.error("email_type must be one of: ${ContactsUtils.EMAIL_TYPES.keys.joinToString()}")

        val rawContactIds = rawContactIds(contactId)
        if (rawContactIds.isEmpty()) return ToolResult.error("Contact not found with ID: $contactId")

        val ops = arrayListOf<ContentProviderOperation>()
        val updated = mutableListOf<String>()
        val targetRaw = rawContactIds.first()

        if (name != null && !hasNameRow(contactId)) {
            ops += dataInsert(targetRaw, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name)
                .build()
            updated += "name"
        } else if (name != null) {
            ops += ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                .withSelection(
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contactId.toString(), StructuredName.CONTENT_ITEM_TYPE),
                )
                .withValue(StructuredName.DISPLAY_NAME, name)
                // Cleared so the provider re-parses them from the new display name.
                .withValue(StructuredName.GIVEN_NAME, null)
                .withValue(StructuredName.MIDDLE_NAME, null)
                .withValue(StructuredName.FAMILY_NAME, null)
                .withValue(StructuredName.PREFIX, null)
                .withValue(StructuredName.SUFFIX, null)
                .build()
            updated += "name"
        }

        val phonesToRemove = removePhone?.let { target ->
            dataRows(contactId, Phone.CONTENT_ITEM_TYPE, Phone.NUMBER)
                .filter { (_, number) -> ContactsUtils.samePhone(number, target) }
                .map { it.first }
        }.orEmpty()
        if (removePhone != null && phonesToRemove.isEmpty()) return ToolResult.error("Contact $contactId has no phone number $removePhone")
        val emailsToRemove = removeEmail?.let { target ->
            dataRows(contactId, Email.CONTENT_ITEM_TYPE, Email.ADDRESS)
                .filter { (_, address) -> address.equals(target, ignoreCase = true) }
                .map { it.first }
        }.orEmpty()
        if (removeEmail != null && emailsToRemove.isEmpty()) return ToolResult.error("Contact $contactId has no email $removeEmail")

        (phonesToRemove + emailsToRemove).forEach { dataId ->
            ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(dataId.toString()))
                .build()
        }
        if (removePhone != null) updated += "remove_phone"
        if (removeEmail != null) updated += "remove_email"

        if (addPhone != null) {
            ops += dataInsert(targetRaw, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, addPhone)
                .withValue(Phone.TYPE, phoneType)
                .build()
            updated += "add_phone"
        }
        if (addEmail != null) {
            ops += dataInsert(targetRaw, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, addEmail)
                .withValue(Email.TYPE, emailType)
                .build()
            updated += "add_email"
        }

        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            return ToolResult.error("Failed to update contact $contactId: ${e.message}")
        }
        return ToolResult.success(mapOf(
            "contact_id" to contactId,
            "updated" to updated,
            "phones_removed" to phonesToRemove.size,
            "emails_removed" to emailsToRemove.size,
        ))
    }

    private fun dataInsert(rawContactId: Long, mimeType: String): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            .withValue(ContactsContract.Data.MIMETYPE, mimeType)

    /** Non-deleted raw contacts aggregated into [contactId]. */
    private fun rawContactIds(contactId: Long): List<Long> {
        val ids = mutableListOf<Long>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        return ids
    }

    /** Whether [contactId] has a `StructuredName` row (its display name may be null). */
    private fun hasNameRow(contactId: Long): Boolean =
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), StructuredName.CONTENT_ITEM_TYPE),
            null,
        )?.use { it.moveToFirst() } ?: false

    /** (Data `_ID`, [valueColumn]) for every [mimeType] row of [contactId]. */
    private fun dataRows(contactId: Long, mimeType: String, valueColumn: String): List<Pair<Long, String>> {
        val rows = mutableListOf<Pair<Long, String>>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, valueColumn),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), mimeType),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val value = cursor.getString(1) ?: continue
                rows += cursor.getLong(0) to value
            }
        }
        return rows
    }
}
