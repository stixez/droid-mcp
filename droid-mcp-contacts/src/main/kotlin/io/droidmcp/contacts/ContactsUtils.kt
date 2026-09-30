package io.droidmcp.contacts

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone

/** Pure validation and type-mapping helpers for [CreateContactTool]. */
internal object ContactsUtils {

    /** Accepted `phone_type` values, mapped to `Phone.TYPE_*` (same labels `read_contact` reports). */
    val PHONE_TYPES: Map<String, Int> = linkedMapOf(
        "mobile" to Phone.TYPE_MOBILE,
        "home" to Phone.TYPE_HOME,
        "work" to Phone.TYPE_WORK,
        "other" to Phone.TYPE_OTHER,
    )

    /** Accepted `email_type` values, mapped to `Email.TYPE_*` (same labels `read_contact` reports). */
    val EMAIL_TYPES: Map<String, Int> = linkedMapOf(
        "home" to Email.TYPE_HOME,
        "work" to Email.TYPE_WORK,
        "other" to Email.TYPE_OTHER,
    )

    // Leading +, digits and dialler punctuation (spaces, - ( ) . and the * # , ; pause/wait
    // characters). The digit count is checked separately so "((((" is rejected.
    private val phoneCharset = Regex("^\\+?[0-9\\s\\-().*#,;]+$")

    // Deliberately loose: one @, no whitespace, a dot in the domain. The provider stores any string.
    private val emailShape = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** True if [input] looks like a dialable number: allowed characters and 3-20 digits. */
    fun isValidPhone(input: String): Boolean =
        phoneCharset.matches(input) && input.count { it.isDigit() } in 3..20

    /** True if [input] has the basic `local@domain.tld` shape. */
    fun isValidEmail(input: String): Boolean = emailShape.matches(input)

    /** Maps a case-insensitive type label via [types]; [default] when [input] is null, null when unknown. */
    fun resolveType(input: String?, types: Map<String, Int>, default: String): Int? =
        types[(input ?: default).trim().lowercase()]
}
