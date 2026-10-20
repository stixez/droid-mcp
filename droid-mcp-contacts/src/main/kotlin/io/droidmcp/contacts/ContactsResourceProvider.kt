package io.droidmcp.contacts

import android.content.Context
import io.droidmcp.core.McpResourceTemplate
import io.droidmcp.core.ResourceContents
import io.droidmcp.core.ResourceProvider

/**
 * Serves one contact per `droidmcp://contacts/{id}` URI as JSON — the same shape `read_contact`
 * returns. Nothing is listed up front (an address book is too large and too personal to
 * advertise); clients find ids with `search_contacts` and read them by template. Needs
 * `READ_CONTACTS`; without it every read fails as not found. Hidden while `read_contact` is gated off.
 */
class ContactsResourceProvider(private val context: Context) : ResourceProvider {

    private val reader = ReadContactTool(context)

    override val requiredTools = setOf("read_contact")

    override val templates = listOf(
        McpResourceTemplate(
            "$PREFIX{id}",
            name = "contact",
            title = "Contact by id",
            description = "A contact's name, phones, emails and addresses; id is the `id` from search_contacts.",
            mimeType = "application/json",
        ),
    )

    override fun handles(uri: String): Boolean = idOf(uri) != null

    override suspend fun read(uri: String): List<ResourceContents>? {
        val id = idOf(uri) ?: return null
        if (!ContactsTools.hasPermissions(context)) return null
        val result = try {
            reader.execute(mapOf("contact_id" to id))
        } catch (_: SecurityException) {
            return null // revoked between the check and the query
        }
        val data = result.data?.takeIf { result.isSuccess } ?: return null
        return listOf(ResourceContents.json(uri, data))
    }

    private fun idOf(uri: String): Long? =
        uri.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toLongOrNull()?.takeIf { it > 0 }
}

private const val PREFIX = "droidmcp://contacts/"
