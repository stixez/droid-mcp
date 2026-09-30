package io.droidmcp.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import io.droidmcp.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import kotlin.coroutines.resume

/**
 * Reverse-geocodes a `latitude`/`longitude` pair to a postal address via the platform
 * [android.location.Geocoder] (needs network access; no location permission). On API 33+
 * uses the async callback API with a 5 s timeout (backend errors reported via
 * `GeocodeListener.onError` resolve immediately); below that the deprecated blocking call.
 * Output: `latitude`, `longitude`, `formatted_address`, `street`, `city`, `district`,
 * `state`, `country`, `country_code`, `postal_code`. Returns [ToolResult.error] for
 * out-of-range coordinates, when no Geocoder backend is present, on timeout, or when no
 * address matches.
 */
class GetLocationAddressTool(private val context: Context) : McpTool {

    override val name = "get_location_address"
    override val description = "Reverse geocode coordinates (latitude/longitude) to a human-readable address. Uses the Android Geocoder which requires network access. Returns street address, city, state, country, and postal code."
    override val parameters = listOf(
        ToolParameter("latitude", "Latitude in decimal degrees (e.g. 37.4219)", ParameterType.NUMBER, required = true, minimum = -90.0, maximum = 90.0),
        ToolParameter("longitude", "Longitude in decimal degrees (e.g. -122.0841)", ParameterType.NUMBER, required = true, minimum = -180.0, maximum = 180.0),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

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
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (!cont.isActive) return
                                if (addresses.isEmpty()) {
                                    cont.resume(ToolResult.error("No address found for coordinates ($latitude, $longitude)"))
                                } else {
                                    cont.resume(ToolResult.success(buildAddressMap(addresses[0], latitude, longitude)))
                                }
                            }

                            // Without this override a backend failure (no network, service error)
                            // never calls back, and the caller waits out the full timeout.
                            override fun onError(errorMessage: String?) {
                                if (!cont.isActive) return
                                cont.resume(ToolResult.error("Geocoder failed: ${errorMessage ?: "unknown error"}"))
                            }
                        })
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

    private fun buildAddressMap(addr: Address, latitude: Double, longitude: Double): Map<String, Any?> {
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
