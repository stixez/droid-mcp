package io.droidmcp.app.data.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.Inet4Address

/** The phone's address on the local network, as clients will reach it. */
data class LocalNetwork(
    /** IPv4 address, or null when the phone has no usable network. */
    val address: String?,
    /** The address belongs to Wi-Fi or Ethernet, i.e. a computer on the same LAN can reach it. */
    val onWifi: Boolean,
    /**
     * The Wi-Fi network's name, or null when not on Wi-Fi or Android hides it (reading it needs
     * location access on Android 10+).
     */
    val ssid: String? = null,
)

/**
 * Finds the address a computer on the same Wi-Fi can reach. The default network isn't enough:
 * with a VPN on, it's the tunnel, whose address nobody on the LAN can reach. The server listens
 * on every interface, so the Wi-Fi (or Ethernet) address is the one to show.
 */
class NetworkAddress(context: Context) {

    private val appContext = context.applicationContext

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    /**
     * The last Wi-Fi name read, and the network it belongs to. Android hides the name from a
     * background app, and network callbacks fire often (signal strength), so without this a
     * routine callback would turn a known network into an unknown one. A reconnect is a new
     * network, so a name never carries over to a different one.
     */
    @Volatile
    private var known: Pair<Long, String>? = null

    fun current(): LocalNetwork {
        val cm = connectivity ?: return LocalNetwork(null, onWifi = false)
        @Suppress("DEPRECATION") // allNetworks is the only way to see non-default networks without a callback.
        val candidates = cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            val address = ipv4(cm.getLinkProperties(network)) ?: return@mapNotNull null
            network to LocalNetwork(address, onWifi = caps.isLan())
        }
        val (network, best) = candidates.firstOrNull { it.second.onWifi }
            ?: candidates.firstOrNull()
            ?: return LocalNetwork(null, onWifi = false)
        if (!best.onWifi) return best
        val handle = network.networkHandle
        val ssid = currentSsid()
        if (ssid != null) known = handle to ssid
        return best.copy(ssid = ssid ?: known?.takeIf { it.first == handle }?.second)
    }

    /** The connected Wi-Fi's name, without quotes; null when unknown or hidden. */
    @Suppress("DEPRECATION") // connectionInfo is still the simplest way to read the SSID once.
    fun currentSsid(): String? {
        val raw = runCatching { wifi?.connectionInfo?.ssid }.getOrNull() ?: return null
        val name = raw.removeSurrounding("\"")
        return name.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }

    /**
     * Emits whenever any network or its address changes, and when Location is switched on or off
     * (Android shows the Wi-Fi name only while it is on).
     */
    fun observe(): Flow<LocalNetwork> = callbackFlow {
        val locationMode = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(current())
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            locationMode,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val cm = connectivity
        if (cm == null) {
            trySend(LocalNetwork(null, onWifi = false))
            awaitClose { appContext.unregisterReceiver(locationMode) }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(current())
            }

            override fun onLost(network: Network) {
                trySend(current())
            }
        }
        trySend(current())
        // A plain request matches every non-VPN internet network, Wi-Fi included even when it isn't default.
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        awaitClose {
            cm.unregisterNetworkCallback(callback)
            appContext.unregisterReceiver(locationMode)
        }
    }.distinctUntilChanged()

    private fun ipv4(links: LinkProperties?): String? = links?.linkAddresses
        ?.map { it.address }
        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress && !it.isAnyLocalAddress }
        ?.hostAddress

    private fun NetworkCapabilities.isLan(): Boolean =
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
