package io.droidmcp.sms

import android.telephony.PhoneNumberUtils
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Module-local helpers shared by the SMS tools. */
internal object SmsUtils {

    /** Escapes `\`, `%`, `_` so [value] matches literally inside a `LIKE ? ESCAPE '\'` clause. */
    fun escapeLike(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /**
     * Strict `yyyy-MM-dd` parse (device timezone): non-lenient, so `2024-02-30` is rejected rather
     * than rolled into March, and the whole string must match (no trailing text).
     */
    fun parseDate(input: String): Date? {
        val text = input.trim()
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val pos = ParsePosition(0)
        val date = format.parse(text, pos) ?: return null
        return if (pos.errorIndex < 0 && pos.index == text.length) date else null
    }

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
}
