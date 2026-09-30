package io.droidmcp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Returns the device's last known cached location from [android.location.LocationManager]
 * (no fresh fix is requested). Requires `ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`.
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
    override val description = "Get the device's current location using the last known cached location. Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission. Returns latitude, longitude, accuracy, altitude, speed, and timestamp. If no cached location is available, suggests opening Google Maps or another location app to warm the cache."
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

        if (best != null) {
            return ToolResult.success(mapOf(
                "latitude" to best.latitude,
                "longitude" to best.longitude,
                "accuracy_meters" to best.accuracy,
                "altitude" to if (best.hasAltitude()) best.altitude else null,
                "speed_mps" to if (best.hasSpeed()) best.speed else null,
                "timestamp" to dateFormat.format(Date(best.time)),
                "provider" to bestProvider,
            ))
        }
        if (attempted > 0 && securityFailures == attempted) {
            return ToolResult.error("Location permission denied. Grant ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.")
        }

        return ToolResult.error(
            "No cached location available. " +
            "Open Google Maps or another location app to warm the location cache, then try again."
        )
    }
}
