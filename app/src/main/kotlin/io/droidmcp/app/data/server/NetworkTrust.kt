package io.droidmcp.app.data.server

import io.droidmcp.app.data.settings.AppSettings

/** Why the server isn't listening on this network, and whether the user can say "serve here". */
data class TrustVerdict(val reason: String, val canServeHere: Boolean = false)

/**
 * Pure: null when the server may listen on [net], else why not.
 *
 * - USB only listens on loopback, which no network reaches, so the network doesn't matter.
 * - "Trusted Wi-Fi only" serves on [AppSettings.trustedNetworks] and nowhere else.
 * - Otherwise, without HTTPS the token travels in plain text, so a Wi-Fi network the user hasn't
 *   served on before pauses the server until they say "serve here" ([TrustVerdict.canServeHere]).
 *   Known names are remembered in the trusted list. A hidden name can't be remembered, so the
 *   answer holds for [servedHere], the address it was given for, until the network changes.
 *   Off Wi-Fi the address belongs to mobile data or the phone's own hotspot, where nobody else
 *   on a shared network can listen.
 *
 * [saved] gives the network rules, which apply at once; [bound] gives what the server was built
 * with (HTTPS, USB only), which needs a restart to change. [hiddenName] explains a hidden name.
 */
fun trustVerdict(
    net: LocalNetwork,
    saved: AppSettings,
    bound: AppSettings,
    servedHere: String?,
    hiddenName: () -> String,
): TrustVerdict? {
    if (bound.usbOnly) return null
    if (saved.trustedWifiOnly) {
        if (!net.onWifi) return TrustVerdict("Paused: not on Wi-Fi")
        val ssid = net.ssid ?: return TrustVerdict(hiddenName())
        return if (ssid in saved.trustedNetworks) null else TrustVerdict("Paused: \"$ssid\" isn't a trusted network")
    }
    if (bound.tlsEnabled || !net.onWifi) return null
    val ssid = net.ssid
    if (ssid != null) {
        return if (ssid in saved.trustedNetworks) null else TrustVerdict(unencryptedReason(ssid), canServeHere = true)
    }
    if (net.address != null && net.address == servedHere) return null
    return TrustVerdict(hiddenName(), canServeHere = true)
}

/** Shown on Home with a "Serve on this network" button. */
fun unencryptedReason(ssid: String): String = "Paused: \"$ssid\" is new and traffic isn't encrypted"
