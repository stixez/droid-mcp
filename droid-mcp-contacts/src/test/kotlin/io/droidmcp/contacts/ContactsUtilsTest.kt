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
