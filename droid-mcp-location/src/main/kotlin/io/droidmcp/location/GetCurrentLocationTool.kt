package io.droidmcp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import io.droidmcp.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

/**
 * Returns the device's location from [android.location.LocationManager]: the freshest cached fix,
 * or — when no provider has one (e.g. a fresh device where no app has asked yet) — a fresh fix
 * requested from every enabled provider at once, bounded by [FRESH_FIX_TIMEOUT_MS]. Output key
 * `source` is `"cache"` or `"fresh"`. Requires `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`.
 * Accepts an `accuracy` param (`"fine"` | `"coarse"`, default `"coarse"`) that only reorders
 * the provider preference (GPS/network/fused). Every enabled provider is consulted and the
 * freshest cached fix wins (preference order breaks ties); a `SecurityException` from one
 * provider (e.g. GPS under a coarse-only grant) just skips it. Output: `latitude`, `longitude`,
 * `accuracy_meters`, `altitude`, `speed_mps`, `timestamp`, `provider`. Returns
 * [ToolResult.error] when permission is missing (or every enabled provider refused with a
 * `SecurityException`), the accuracy value is invalid, or no cached fix exists on any enabled
 * provider.
 */
class GetCurrentLocationTool(private val context: Context) : McpTool {

    override val name = "get_current_location"
    override val description = "Get the device's current location: the freshest cached fix, or a fresh one (up to 10 s) when nothing is cached. Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission. Returns latitude, longitude, accuracy, altitude, speed, timestamp, provider and source (cache/fresh)."
    override val parameters = listOf(
        ToolParameter("accuracy", "Location accuracy preference: 'fine' (GPS) or 'coarse' (network). Default: 'coarse'", ParameterType.STRING, enumValues = listOf("fine", "coarse")),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    @SuppressLint("MissingPermission")
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (!LocationTools.hasPermissions(context)) {
            return ToolResult.error("Location permission not granted. Grant ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION first.")
        }

        val accuracy = params["accuracy"]?.toString()?.lowercase() ?: "coarse"

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = when (accuracy) {
            "fine" -> listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.FUSED_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
            )
            "coarse" -> listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.FUSED_PROVIDER,
                LocationManager.GPS_PROVIDER,
            )
            else -> return ToolResult.error("Invalid accuracy '$accuracy'. Use: fine, coarse")
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        // Check every provider and keep the freshest fix: the preference order only breaks ties,
        // so a stale GPS fix from yesterday can't shadow a network fix from a minute ago.
        var best: Location? = null
        var bestProvider: String? = null
        var attempted = 0
        var securityFailures = 0
        for (provider in providers) {
            try {
                if (!locationManager.isProviderEnabled(provider)) continue
                attempted++
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (best == null || location.time > best.time) {
                    best = location
                    bestProvider = provider
                }
            } catch (_: SecurityException) {
                // e.g. coarse-only grant hitting the GPS provider — try the next one.
                securityFailures++
                continue
            } catch (_: Exception) {
                continue
            }
        }

        var source = "cache"
        if (best == null && attempted > securityFailures) {
            val enabled = providers.filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            freshFix(locationManager, enabled)?.let { (provider, location) ->
                best = location
                bestProvider = provider
                source = "fresh"
            }
        }

        val fix = best
        if (fix != null) {
            return ToolResult.success(mapOf(
                "latitude" to fix.latitude,
                "longitude" to fix.longitude,
                "accuracy_meters" to fix.accuracy,
                "altitude" to if (fix.hasAltitude()) fix.altitude else null,
                "speed_mps" to if (fix.hasSpeed()) fix.speed else null,
                "timestamp" to dateFormat.format(Date(fix.time)),
                "provider" to bestProvider,
                "source" to source,
            ))
        }
        if (attempted > 0 && securityFailures == attempted) {
            return ToolResult.error("Location permission denied. Grant ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.")
        }

        return ToolResult.error(
            "No location fix within ${FRESH_FIX_TIMEOUT_MS / 1000}s: nothing cached and no provider " +
                "produced a fix. Check that location is on and the device can see GPS or a network."
        )
    }

    /**
     * Asks every provider in [providers] for one fix at once; the first to answer wins. Null when
     * none answers within [FRESH_FIX_TIMEOUT_MS]. Outstanding requests are cancelled either way.
     */
    @SuppressLint("MissingPermission")
    private suspend fun freshFix(lm: LocationManager, providers: List<String>): Pair<String, Location>? {
        if (providers.isEmpty()) return null
        val first = CompletableDeferred<Pair<String, Location>>()
        val cancels = mutableListOf<() -> Unit>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            for (provider in providers) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val signal = CancellationSignal()
                        lm.getCurrentLocation(provider, signal, executor) { loc -> if (loc != null) first.complete(provider to loc) }
                        cancels += { signal.cancel() }
                    } else {
                        val listener = LocationListener { loc -> first.complete(provider to loc) }
                        @Suppress("DEPRECATION")
                        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                        cancels += { lm.removeUpdates(listener) }
                    }
                } catch (_: SecurityException) {
                    // Not allowed for this provider (e.g. GPS under a coarse-only grant).
                } catch (_: IllegalArgumentException) {
                    // Provider vanished between the check and the request.
                }
            }
            return withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) { first.await() }
        } finally {
            cancels.forEach { runCatching(it) }
            executor.shutdown()
        }
    }

    private companion object {
        /** Upper bound on waiting for a fresh fix when nothing is cached. */
        const val FRESH_FIX_TIMEOUT_MS = 10_000L
    }
}
