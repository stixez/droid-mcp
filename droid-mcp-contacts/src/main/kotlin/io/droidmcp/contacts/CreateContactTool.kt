package io.droidmcp.contacts

import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Creates a contact with one `ContactsContract` batch: a `RawContacts` row in the local device
 * account (null account name/type), a `StructuredName` row from `name`, and optional `Phone` and
 * `Email` rows. Requires `WRITE_CONTACTS` (plus `READ_CONTACTS` to look up the aggregated id).
 *
 * `phone` must use digits and dialler punctuation (3-20 digits); `email` must look like
 * `local@domain.tld`. `phone_type` (mobile/home/work/other, default mobile) and `email_type`
 * (home/work/other, default home) are case-insensitive. Some devices route new contacts to a
 * cloud default account or refuse local-account inserts; the provider's error is returned as-is.
 *
 * Output: `contact_id` (the aggregated contact id usable with `read_contact`; null if the
 * provider hasn't aggregated the raw contact yet), `raw_contact_id`, and the echoed `name`,
 * `phone` and `email` (null when not given).
 */
class CreateContactTool(private val context: Context) : McpTool {

    override val name = "create_contact"
    override val description = "Create a new contact on the device with a name and optional phone number and email"
    override val parameters = listOf(
        ToolParameter("name", "Full display name of the contact", ParameterType.STRING, required = true),
        ToolParameter("phone", "Phone number", ParameterType.STRING),
        ToolParameter("phone_type", "Phone label. Default mobile.", ParameterType.STRING, enumValues = ContactsUtils.PHONE_TYPES.keys.toList()),
        ToolParameter("email", "Email address", ParameterType.STRING),
        ToolParameter("email_type", "Email label. Default home.", ParameterType.STRING, enumValues = ContactsUtils.EMAIL_TYPES.keys.toList()),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val name = params["name"]?.toString()?.trim()
        if (name.isNullOrEmpty()) return ToolResult.error("name is required")
        val phone = params["phone"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val email = params["email"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        if (phone != null && !ContactsUtils.isValidPhone(phone)) {
            return ToolResult.error("Invalid phone '$phone'. Use digits with optional +, spaces, dashes or parentheses")
        }
        if (email != null && !ContactsUtils.isValidEmail(email)) {
            return ToolResult.error("Invalid email '$email'")
        }
        val phoneType = ContactsUtils.resolveType(params["phone_type"]?.toString(), ContactsUtils.PHONE_TYPES, "mobile")
            ?: return ToolResult.error("phone_type must be one of: ${ContactsUtils.PHONE_TYPES.keys.joinToString()}")
        val emailType = ContactsUtils.resolveType(params["email_type"]?.toString(), ContactsUtils.EMAIL_TYPES, "home")
            ?: return ToolResult.error("email_type must be one of: ${ContactsUtils.EMAIL_TYPES.keys.joinToString()}")

        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
            dataInsert(StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name)
                .build(),
        )
        if (phone != null) {
            ops += dataInsert(Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, phone)
                .withValue(Phone.TYPE, phoneType)
                .build()
        }
        if (email != null) {
            ops += dataInsert(Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, email)
                .withValue(Email.TYPE, emailType)
                .build()
        }

        val results = try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            return ToolResult.error("Failed to create contact: ${e.message}")
        }
        val rawContactId = results.firstOrNull()?.uri?.lastPathSegment?.toLongOrNull()
            ?: return ToolResult.error("Failed to create contact: provider returned no raw contact id")

        return ToolResult.success(mapOf(
            "contact_id" to lookupContactId(rawContactId),
            "raw_contact_id" to rawContactId,
            "name" to name,
            "phone" to phone,
            "email" to email,
        ))
    }

    /** A `Data` insert whose `RAW_CONTACT_ID` points back at the raw contact created by op 0. */
    private fun dataInsert(mimeType: String): ContentProviderOperation.Builder =
        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
            .withValue(ContactsContract.Data.MIMETYPE, mimeType)

    /** The aggregated `Contacts._ID` of [rawContactId], or null if it isn't aggregated (yet). */
    private fun lookupContactId(rawContactId: Long): Long? =
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts._ID} = ?",
            arrayOf(rawContactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
}
