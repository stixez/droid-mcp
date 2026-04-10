package io.droidmcp.location

import android.content.Context
import android.location.Geocoder
import android.os.Build
import io.droidmcp.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import kotlin.coroutines.resume

class GetLocationAddressTool(private val context: Context) : McpTool {

    override val name = "get_location_address"
    override val description = "Reverse geocode coordinates (latitude/longitude) to a human-readable address. Uses the Android Geocoder which requires network access. Returns street address, city, state, country, and postal code."
    override val parameters = listOf(
        ToolParameter("latitude", "Latitude in decimal degrees (e.g. 37.4219)", ParameterType.NUMBER, required = true),
        ToolParameter("longitude", "Longitude in decimal degrees (e.g. -122.0841)", ParameterType.NUMBER, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val latitude = (params["latitude"] as? Number)?.toDouble()
            ?: return ToolResult.error("latitude is required and must be a number")
        val longitude = (params["longitude"] as? Number)?.toDouble()
            ?: return ToolResult.error("longitude is required and must be a number")

        if (latitude < -90 || latitude > 90) return ToolResult.error("latitude must be between -90 and 90")
        if (longitude < -180 || longitude > 180) return ToolResult.error("longitude must be between -180 and 180")

        if (!Geocoder.isPresent()) {
            return ToolResult.error("Geocoder is not available on this device")
        }

        val geocoder = Geocoder(context, Locale.getDefault())

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(5000) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            if (addresses.isEmpty()) {
                                cont.resume(ToolResult.error("No address found for coordinates ($latitude, $longitude)"))
                            } else {
                                cont.resume(buildAddressMap(addresses[0], latitude, longitude).let { ToolResult.success(it) })
                            }
                        }
                    }
                } ?: ToolResult.error("Geocoding timed out")
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (addresses.isNullOrEmpty()) {
                    ToolResult.error("No address found for coordinates ($latitude, $longitude)")
                } else {
                    ToolResult.success(buildAddressMap(addresses[0], latitude, longitude))
                }
            }
        } catch (e: Exception) {
            ToolResult.error("Geocoder failed: ${e.message}")
        }
    }

    private fun buildAddressMap(addr: android.location.Address, latitude: Double, longitude: Double): Map<String, Any?> {
        val lines = (0..addr.maxAddressLineIndex).map { addr.getAddressLine(it) }
        return mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "formatted_address" to lines.joinToString(", "),
            "street" to listOfNotNull(addr.subThoroughfare, addr.thoroughfare).joinToString(" ").ifEmpty { null },
            "city" to addr.locality,
            "district" to addr.subLocality,
            "state" to addr.adminArea,
            "country" to addr.countryName,
            "country_code" to addr.countryCode,
            "postal_code" to addr.postalCode,
        )
    }
}
