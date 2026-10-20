package io.droidmcp.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.droidmcp.core.ToolResult
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ContactsUtilsTest {

    @Test
    fun `accepts real-world phone numbers`() {
        listOf("+1 (555) 123-4567", "555-1234", "112", "+44 20 7946 0958", "0049.30.123456", "*67 555 1234", "555 1234,,99#")
            .forEach { assertWithMessage(it).that(ContactsUtils.isValidPhone(it)).isTrue() }
    }

    @Test
    fun `rejects non-numbers and out-of-range digit counts`() {
        listOf("((((", "12", "call me", "555-CALL-NOW", "+1 555 123 4567 890 123 456 78", "1+555", "")
            .forEach { assertWithMessage(it).that(ContactsUtils.isValidPhone(it)).isFalse() }
    }

    @Test
    fun `email shape check`() {
        listOf("a@b.co", "first.last+tag@example.com", "x@sub.domain.org")
            .forEach { assertWithMessage(it).that(ContactsUtils.isValidEmail(it)).isTrue() }
        listOf("plain", "a@b", "@b.com", "a@.com ", "a b@c.com", "a@@b.com", "")
            .forEach { assertWithMessage(it).that(ContactsUtils.isValidEmail(it)).isFalse() }
    }

    @Test
    fun `type labels resolve case-insensitively with defaults`() {
        assertThat(ContactsUtils.resolveType(null, ContactsUtils.PHONE_TYPES, "mobile")).isEqualTo(Phone.TYPE_MOBILE)
        assertThat(ContactsUtils.resolveType(" Work ", ContactsUtils.PHONE_TYPES, "mobile")).isEqualTo(Phone.TYPE_WORK)
        assertThat(ContactsUtils.resolveType("HOME", ContactsUtils.EMAIL_TYPES, "home")).isEqualTo(Email.TYPE_HOME)
        assertThat(ContactsUtils.resolveType(null, ContactsUtils.EMAIL_TYPES, "home")).isEqualTo(Email.TYPE_HOME)
        assertThat(ContactsUtils.resolveType("mobile", ContactsUtils.EMAIL_TYPES, "home")).isNull()
        assertThat(ContactsUtils.resolveType("fax", ContactsUtils.PHONE_TYPES, "mobile")).isNull()
    }

    @Test
    fun `advertised enums match the accepted labels`() {
        val tool = CreateContactTool(mockk(relaxed = true))
        assertThat(tool.parameters.single { it.name == "phone_type" }.enumValues).containsExactly("mobile", "home", "work", "other").inOrder()
        assertThat(tool.parameters.single { it.name == "email_type" }.enumValues).containsExactly("home", "work", "other").inOrder()
    }

    @Test
    fun `ids parse from numbers and numeric strings`() {
        assertThat(ContactsUtils.parseId(42)).isEqualTo(42L)
        assertThat(ContactsUtils.parseId(" 7 ")).isEqualTo(7L)
        assertThat(ContactsUtils.parseId(0)).isNull()
        assertThat(ContactsUtils.parseId(-3L)).isNull()
        assertThat(ContactsUtils.parseId("abc")).isNull()
        assertThat(ContactsUtils.parseId(null)).isNull()
    }

    @Test
    fun `phones match by digits regardless of formatting`() {
        assertThat(ContactsUtils.samePhone("+1 (555) 010-0123", "15550100123")).isTrue()
        assertThat(ContactsUtils.samePhone("555 0100", "555-0100")).isTrue()
        assertThat(ContactsUtils.samePhone("5550100", "15550100")).isFalse()
        assertThat(ContactsUtils.samePhone("()", "--")).isFalse()
    }

    private suspend fun update(vararg params: Pair<String, Any>): ToolResult =
        UpdateContactTool(mockk(relaxed = true)).execute(mapOf(*params))

    @Test
    fun `update validates before touching the provider`() = runTest {
        assertThat(update().errorMessage).startsWith("contact_id is required")
        assertThat(update("contact_id" to 0).errorMessage).startsWith("contact_id is required")
        assertThat(update("contact_id" to 5).errorMessage).startsWith("Nothing to update")
        assertThat(update("contact_id" to 5, "name" to "  ").errorMessage).startsWith("Nothing to update")
        assertThat(update("contact_id" to 5, "add_phone" to "abc").errorMessage).startsWith("Invalid phone")
        assertThat(update("contact_id" to 5, "add_email" to "nope").errorMessage).startsWith("Invalid email")
        assertThat(update("contact_id" to 5, "name" to "A", "phone_type" to "fax").errorMessage).startsWith("phone_type must be one of")
    }

    @Test
    fun `update of a contact with no raw contacts is not found`() = runTest {
        // A relaxed resolver returns a null cursor: no raw contacts.
        assertThat(update("contact_id" to 5, "name" to "Ann").errorMessage).isEqualTo("Contact not found with ID: 5")
    }

    @Test
    fun `delete validates the id and reports a missing contact`() = runTest {
        val tool = DeleteContactTool(mockk(relaxed = true))
        assertThat(tool.execute(mapOf("contact_id" to "x")).errorMessage).startsWith("contact_id is required")
        assertThat(tool.execute(mapOf("contact_id" to 9)).errorMessage).isEqualTo("Contact not found with ID: 9")
    }

    private suspend fun create(vararg params: Pair<String, Any>): ToolResult =
        CreateContactTool(mockk(relaxed = true)).execute(mapOf(*params))

    @Test
    fun `input validation errors before touching the provider`() = runTest {
        assertThat(create().errorMessage).isEqualTo("name is required")
        assertThat(create("name" to "  ").errorMessage).isEqualTo("name is required")
        assertThat(create("name" to "Ann", "phone" to "abc").errorMessage).startsWith("Invalid phone")
        assertThat(create("name" to "Ann", "email" to "nope").errorMessage).startsWith("Invalid email")
        assertThat(create("name" to "Ann", "phone_type" to "fax").errorMessage).startsWith("phone_type must be one of")
        assertThat(create("name" to "Ann", "email_type" to "mobile").errorMessage).startsWith("email_type must be one of")
    }
}
