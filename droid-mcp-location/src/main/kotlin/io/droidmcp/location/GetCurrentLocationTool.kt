package io.droidmcp.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class GetCurrentLocationTool(private val context: Context) : McpTool {

    override val name = "get_current_location"
    override val description = "Get the device's current location using the last known cached location. Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permission. Returns latitude, longitude, accuracy, altitude, speed, and timestamp. If no cached location is available, suggests opening Google Maps or another location app to warm the cache."
    override val parameters = listOf(
        ToolParameter("accuracy", "Location accuracy preference: 'fine' (GPS) or 'coarse' (network). Default: 'coarse'", ParameterType.STRING),
    )

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

        for (provider in providers) {
            try {
                if (!locationManager.isProviderEnabled(provider)) continue
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                return ToolResult.success(mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy_meters" to location.accuracy,
                    "altitude" to if (location.hasAltitude()) location.altitude else null,
                    "speed_mps" to if (location.hasSpeed()) location.speed else null,
                    "timestamp" to dateFormat.format(Date(location.time)),
                    "provider" to provider,
                ))
            } catch (_: SecurityException) {
                return ToolResult.error("Location permission denied. Grant ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.")
            } catch (_: Exception) {
                continue
            }
        }

        return ToolResult.error(
            "No cached location available. " +
            "Open Google Maps or another location app to warm the location cache, then try again."
        )
    }
}
