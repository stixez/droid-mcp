package io.droidmcp.sms

import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import io.droidmcp.core.support.SqlLike

/** Module-local helpers shared by the SMS tools. */
internal object SmsUtils {

    /** Strips everything except digits, keeping a leading `+` (e.g. `+1 (555) 010-9999` → `+15550109999`). */
    fun normalizeNumber(value: String): String {
        val trimmed = value.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /**
     * True if a stored SMS [address] matches the caller's [filter]: a case-insensitive substring
     * match on the raw text (covers alphanumeric senders like "BANK"), a digit-only substring
     * match after normalization (so `555-0109` matches `+1 (555) 010-9...`), or
     * [PhoneNumberUtils.compare] (so `0911234567` matches `+385911234567`).
     */
    @Suppress("DEPRECATION")
    fun addressMatches(address: String?, filter: String): Boolean {
        if (address == null) return false
        if (address.contains(filter.trim(), ignoreCase = true)) return true
        val filterDigits = normalizeNumber(filter).trimStart('+')
        if (filterDigits.isNotEmpty() && normalizeNumber(address).contains(filterDigits)) return true
        return filterDigits.length >= 3 && PhoneNumberUtils.compare(address, filter)
    }

    /** A `selection` fragment and its single `selectionArgs` value. */
    data class SqlFilter(val selection: String, val arg: String)

    /** How many trailing filter digits the [addressPrefilter] pattern pins down. */
    private const val PREFILTER_DIGITS = 7

    /**
     * A SQL pre-filter on `ADDRESS` for an [addressMatches] filter, so the provider returns only
     * candidate rows instead of the whole box; [addressMatches] stays the precise check. The
     * pattern always selects a superset of what [addressMatches] accepts, or it is null:
     *  - 4+ digits: the last 4–7 filter digits *in order*, with `%` between them
     *    (`%0%1%0%9%9%9%9%`). Stored addresses may carry formatting (`+1 (555) 010-9999`) that a
     *    contiguous digit run would miss, and [PhoneNumberUtils.compare] matches on trailing digits.
     *  - No digits (an alphanumeric sender such as `BANK`): the trimmed filter as a literal
     *    substring. Only for ASCII filters, since SQLite's `LIKE` folds case for ASCII only.
     *  - 1–3 digits, or a non-ASCII filter without digits: null (no safe narrowing).
     */
    fun addressPrefilter(filter: String): SqlFilter? {
        val selection = "${Telephony.Sms.ADDRESS} LIKE ? ESCAPE '\\'"
        val digits = normalizeNumber(filter).trimStart('+')
        if (digits.length >= 4) {
            val tail = digits.takeLast(PREFILTER_DIGITS)
            return SqlFilter(selection, tail.toList().joinToString(separator = "%", prefix = "%", postfix = "%"))
        }
        val text = filter.trim()
        if (digits.isEmpty() && text.isNotEmpty() && text.all { it.code < 128 }) {
            return SqlFilter(selection, "%${SqlLike.escape(text)}%")
        }
        return null
    }
}
