package io.droidmcp.app.feature.computers

import io.droidmcp.app.data.server.ConnectionInfo
import io.droidmcp.app.data.settings.PairedComputer

data class ComputerUi(
    val name: String,
    val createdAt: Long,
    /** Time of this computer's latest tool call, from the activity log; null if none yet. */
    val lastUsed: Long?,
)

data class ComputersState(
    val computers: List<ComputerUi> = emptyList(),
    /** Name field of the "Add a computer" dialog; null when the dialog is closed. */
    val nameInput: String? = null,
    val nameError: String? = null,
    /** Computer whose command is on screen (just added, or opened from the list). */
    val showing: PairedComputer? = null,
    /** Where clients connect; null without an address (the sheet then shows only the token). */
    val host: String? = null,
    val port: Int = 8080,
    val tls: Boolean = false,
    val confirmRevoke: String? = null,
) {
    /** Connection details for [showing], or null. */
    val showingConnection: ConnectionInfo?
        get() {
            val computer = showing ?: return null
            return ConnectionInfo(host = host ?: "PHONE_IP", port = port, tls = tls, token = computer.token)
        }
}

sealed interface ComputersIntent {
    data object AskAdd : ComputersIntent
    data class EditName(val text: String) : ComputersIntent
    data object ConfirmAdd : ComputersIntent
    data object CancelAdd : ComputersIntent
    data class Show(val name: String) : ComputersIntent
    data object HideCommand : ComputersIntent
    data object CopyCommand : ComputersIntent
    data object CopyJson : ComputersIntent
    data class AskRevoke(val name: String) : ComputersIntent
    data object ConfirmRevoke : ComputersIntent
    data object CancelRevoke : ComputersIntent
}

sealed interface ComputersEffect {
    data class Copy(val text: String, val what: String) : ComputersEffect
    data class Message(val text: String) : ComputersEffect
}

/** Pure: null when [name] can be used for a new computer, else why not. */
fun validateComputerName(name: String, existing: List<String>): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Give it a name, like \"Work laptop\""
        trimmed.length > 40 -> "Keep it under 40 characters"
        trimmed.equals("primary", ignoreCase = true) || trimmed.equals("anonymous", ignoreCase = true) -> "That name is reserved"
        trimmed.any { it.isISOControl() } -> "Use letters, numbers and spaces"
        existing.any { it.equals(trimmed, ignoreCase = true) } -> "You already have a computer with that name"
        else -> null
    }
}
