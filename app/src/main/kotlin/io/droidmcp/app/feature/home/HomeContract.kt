package io.droidmcp.app.feature.home

import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.permissions.Capability
import io.droidmcp.app.data.server.ConnectionInfo
import io.droidmcp.app.data.server.LocalNetwork
import io.droidmcp.app.data.server.ServerStatus
import io.droidmcp.core.ToolCallAudit

/** Which client the connect card shows instructions for. */
enum class ClientKind(val label: String) {
    ClaudeCode("Claude Code"),
    Codex("Codex"),
    Json("JSON"),
}

/** How the computer reaches the phone. */
enum class Route(val label: String) {
    Wifi("Wi-Fi"),
    Usb("USB"),
}

data class HomeState(
    val status: ServerStatus = ServerStatus.Stopped,
    /** Null until the first network reading arrives. */
    val network: LocalNetwork? = null,
    val token: String? = null,
    val port: Int = 8080,
    val tls: Boolean = false,
    val client: ClientKind = ClientKind.ClaudeCode,
    val route: Route = Route.Wifi,
    /** USB was picked for the user because there was no Wi-Fi address; undone when one appears. */
    val autoUsb: Boolean = false,
    val tokenVisible: Boolean = false,
    val restartNeeded: Boolean = false,
    /** Trusted Wi-Fi paused while the app was closed; offer "All the time" location. */
    val pausedWhileClosed: Boolean = false,
    val missingEssentials: List<String> = emptyList(),
    /** The user dismissed the nudge about [missingEssentials]. */
    val essentialsHintDismissed: Boolean = false,
    val lastCall: ToolCallAudit? = null,
    val callsToday: Int = 0,
    /** [callsToday] hit the feed limit, so the real number is higher. */
    val callsTodayCapped: Boolean = false,
    /** A newer release the user hasn't dismissed, or null. */
    val updateAvailable: String? = null,
    /** "Try asking" suggestions for what's allowed right now. */
    val examples: List<String> = emptyList(),
) {
    /** Running and no call has come in since the server started. */
    val waitingForFirstCall: Boolean
        get() {
            val running = status as? ServerStatus.Running ?: return false
            val last = lastCall ?: return true
            return last.timestamp < running.startedAt
        }

    /** Where clients connect, or null until the token and an address are known. */
    val connection: ConnectionInfo?
        get() {
            // While running, show what the live server uses, not settings waiting for a restart.
            val live = status as? ServerStatus.Running
            val t = live?.token ?: token ?: return null
            val p = live?.port ?: port
            val secure = live?.tls ?: tls
            val host = when (route) {
                Route.Usb -> "localhost"
                Route.Wifi -> network?.address ?: return null
            }
            return ConnectionInfo(host = host, port = p, tls = secure, token = t)
        }

    /** The text the big copy button copies. */
    val configText: String?
        get() = connection?.let {
            when (client) {
                ClientKind.ClaudeCode -> it.claudeCodeCommand()
                ClientKind.Codex -> it.codexConfig()
                ClientKind.Json -> it.jsonConfig()
            }
        }

    /** Same text with the token masked, for showing on screen. */
    val configPreview: String?
        get() {
            val text = configText ?: return null
            val t = connection?.token ?: return text
            return if (tokenVisible) text else text.replace(t, mask(t))
        }

    val isRunning: Boolean get() = status is ServerStatus.Running

    /** The port clients reach: the running server's, else the saved one. */
    val activePort: Int get() = connection?.port ?: (status as? ServerStatus.Running)?.port ?: port

    /** Address is a mobile-data or VPN one: computers on the home Wi-Fi likely can't reach it. */
    val offWifi: Boolean get() = route == Route.Wifi && network?.address != null && network.onWifi.not()

    /** True once the token and the network are known, so "no Wi-Fi" isn't shown while loading. */
    val connectionKnown: Boolean get() = token != null && network != null

    companion object {
        fun mask(token: String): String = "•".repeat(8) + token.takeLast(4)
    }
}

/** Pure: fold a network reading into the state, auto-picking USB without Wi-Fi and undoing it after. */
fun HomeState.withNetwork(n: LocalNetwork): HomeState = when {
    n.address == null && route == Route.Wifi -> copy(network = n, route = Route.Usb, autoUsb = true)
    n.address != null && autoUsb -> copy(network = n, route = Route.Wifi, autoUsb = false)
    else -> copy(network = n)
}

sealed interface HomeIntent {
    data object ToggleServer : HomeIntent
    data object Restart : HomeIntent
    data class SelectClient(val client: ClientKind) : HomeIntent
    data class SelectRoute(val route: Route) : HomeIntent
    data object ToggleTokenVisible : HomeIntent
    data object CopyConfig : HomeIntent
    data object CopyUrl : HomeIntent
    data object CopyToken : HomeIntent
    data object CopyAdbForward : HomeIntent
    data object OpenPermissions : HomeIntent
    data object OpenActivity : HomeIntent
    data object DismissEssentialsHint : HomeIntent
    data object OpenUpdate : HomeIntent
    data object DismissUpdate : HomeIntent
    data class CopyExample(val prompt: String) : HomeIntent
    data object OpenComputers : HomeIntent
    data object AllowBackgroundLocation : HomeIntent
    data object DismissPausedWhileClosed : HomeIntent
}

sealed interface HomeEffect {
    /** Put [text] on the clipboard; [what] names it for the confirmation ("Command copied"). */
    data class Copy(val text: String, val what: String, val sensitive: Boolean) : HomeEffect
    data object NavigatePermissions : HomeEffect
    data object NavigateActivity : HomeEffect
    data object NavigateComputers : HomeEffect
    data class OpenUrl(val url: String) : HomeEffect
    data class LaunchGrant(val capability: Capability) : HomeEffect
}

/**
 * Pure: up to three example prompts for what the user has allowed, most telling first. Each one
 * shows a capability the user just turned on, so the value is obvious.
 */
fun examplePrompts(granted: Set<String>, limit: Int = 3): List<String> {
    val byCapability = listOf(
        Capabilities.CALENDAR to "What's on my calendar today?",
        Capabilities.NOTIFICATION_ACCESS to "Which notifications did I miss this morning?",
        Capabilities.SMS to "Summarise my unread texts.",
        Capabilities.USAGE_ACCESS to "Which apps did I use most this week?",
        // Not a system Settings screen: Android hides overlays there, so guide_user would show nothing.
        Capabilities.OPEN_APPS to "Open the Clock app on my phone and point me to where I add an alarm.",
        Capabilities.SCREEN_CONTROL to "Open Settings on my phone and turn on dark mode.",
        Capabilities.CONTACTS to "What's Alex's phone number?",
        Capabilities.PHOTOS to "Find the photos I took last weekend.",
        Capabilities.LOCATION to "Where is my phone right now?",
    )
    val always = listOf(
        "How much battery and storage does my phone have?",
        "Set a 10 minute timer on my phone.",
    )
    return (byCapability.filter { (id, _) -> id in granted }.map { it.second } + always).take(limit)
}
