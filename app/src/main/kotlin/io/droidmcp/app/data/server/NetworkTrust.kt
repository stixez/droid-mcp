package io.droidmcp.app.data.server

import io.droidmcp.app.data.settings.AppSettings

/** Why the server isn't listening on this network, and whether the user can say "serve here". */
data class TrustVerdict(val reason: String, val canServeHere: Boolean = false)

/**
 * Pure: null when the server may listen on [net], else why not.
 *
 * - USB only listens on loopback, which no network reaches, so the network doesn't matter.
 * - "Trusted Wi-Fi only" serves on [AppSettings.trustedNetworks], by name, and nowhere else.
 * - Otherwise, without HTTPS the token travels in plain text, so a network the user hasn't
 *   served on before pauses the server until they say "serve here" ([TrustVerdict.canServeHere]).
 *   A readable name is checked against the trusted list. While Android hides the name (in the
 *   background after a reboot) or there is none (Ethernet), the network's fingerprint is checked
 *   against [AppSettings.trustedFingerprints] instead. [servedHere] is an address the user said
 *   "serve here" for without a name or fingerprint to remember; it holds until the address changes.
 *   Off Wi-Fi and Ethernet the address belongs to mobile data or the phone's own hotspot, where
 *   nobody else on a shared network can listen.
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
        if (net.wired) return TrustVerdict("Paused: on a wired network, not a trusted Wi-Fi")
        val ssid = net.ssid ?: return TrustVerdict(hiddenName())
        return if (ssid in saved.trustedNetworks) null else TrustVerdict("Paused: \"$ssid\" isn't a trusted network")
    }
    if (bound.tlsEnabled || !net.onWifi) return null
    if (net.address != null && net.address == servedHere) return null
    val known = net.fingerprint?.let { saved.trustedFingerprints[it] }
    val ssid = net.ssid
    if (ssid != null) {
        // "" = trusted while its name was hidden; now that the name shows, it's the same network.
        return if (ssid in saved.trustedNetworks || known == "") null
        else TrustVerdict(unencryptedReason(ssid), canServeHere = true)
    }
    if (known != null) return null
    if (net.wired) return TrustVerdict(WIRED_REASON, canServeHere = true)
    return TrustVerdict(hiddenName(), canServeHere = true)
}

/**
 * Pure: what to save so the current network stays recognised, or null when nothing changes.
 * A trusted name gets its fingerprint recorded (or moved, when the router changed). A network
 * approved while its name was hidden, by fingerprint or by [servedHere], gets its name trusted
 * once the name shows, so readable names and fingerprints agree.
 */
fun learnTrust(net: LocalNetwork, s: AppSettings, servedHere: String?): AppSettings? {
    if (s.trustedWifiOnly || !net.onWifi) return null
    val ssid = net.ssid ?: return null
    val fingerprint = net.fingerprint
    val approvedHidden = (fingerprint != null && s.trustedFingerprints[fingerprint] == "") ||
        (net.address != null && net.address == servedHere)
    if (ssid !in s.trustedNetworks && !approvedHidden) return null
    val names = s.trustedNetworks + ssid
    val prints = if (fingerprint == null) s.trustedFingerprints else s.trustedFingerprints + (fingerprint to ssid)
    return if (names == s.trustedNetworks && prints == s.trustedFingerprints) null
    else s.copy(trustedNetworks = names, trustedFingerprints = prints)
}

/** "Serve on this network": the settings that trust [net] from now on, or null if it can't be saved. */
fun trustNetwork(net: LocalNetwork, s: AppSettings): AppSettings? {
    if (net.ssid == null && net.fingerprint == null) return null
    val names = net.ssid?.let { s.trustedNetworks + it } ?: s.trustedNetworks
    val prints = net.fingerprint?.let { s.trustedFingerprints + (it to net.ssid.orEmpty()) } ?: s.trustedFingerprints
    return s.copy(trustedNetworks = names, trustedFingerprints = prints)
}

/** Shown on Home with a "Serve on this network" button. */
fun unencryptedReason(ssid: String): String = "Paused: \"$ssid\" is new and traffic isn't encrypted"

const val WIRED_REASON = "Paused: this wired network is new and traffic isn't encrypted"
