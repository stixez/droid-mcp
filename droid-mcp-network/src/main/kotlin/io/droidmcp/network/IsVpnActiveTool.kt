package io.droidmcp.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import io.droidmcp.core.ParameterType
import io.droidmcp.core.PermissionHelper
import io.droidmcp.core.ToolAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reports whether the active network is carried over a VPN. Requires `ACCESS_NETWORK_STATE`
 * (checked up front). On API 23+ inspects `NetworkCapabilities.TRANSPORT_VPN`; on API 30+ it
 * resolves the owning app's package via the public `NetworkCapabilities.getOwnerUid()`, which the
 * platform only populates for the VPN app itself, so it is usually `"unknown"`. Below API 30 the
 * owner is not exposed publicly and is always `"unknown"`. Below API 23 uses the deprecated
 * `TYPE_VPN` network info (package always null).
 * Output: `is_active`, `vpn_package_name` (nullable / `"unknown"`). Returns [ToolResult.error]
 * when permission is missing or on failure.
 */
class IsVpnActiveTool(private val context: Context) : McpTool {
    override val name = "is_vpn_active"
    override val description = "Check if a VPN connection is currently active and return the VPN package name if available."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasPermissions(context, listOf(Manifest.permission.ACCESS_NETWORK_STATE))) {
            return@withContext ToolResult.error("ACCESS_NETWORK_STATE permission not granted")
        }

        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@withContext ToolResult.error("ConnectivityManager not available")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork: Network = connectivityManager.activeNetwork
                    ?: return@withContext ToolResult.success(mapOf(
                        "is_active" to false,
                        "vpn_package_name" to null
                    ))

                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    ?: return@withContext ToolResult.success(mapOf(
                        "is_active" to false,
                        "vpn_package_name" to null
                    ))

                val hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

                if (hasVpn) {
                    // NetworkCapabilities.getOwnerUid() is public from API 30. The platform only
                    // populates it for the VPN app itself (INVALID_UID for everyone else), so for a
                    // third-party caller this usually stays "unknown". Below API 30 there is no
                    // public way to find the owner.
                    val packageName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val uid = capabilities.ownerUid
                            if (uid >= Process.FIRST_APPLICATION_UID) {
                                context.packageManager.getPackagesForUid(uid)?.firstOrNull()
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }

                    ToolResult.success(mapOf(
                        "is_active" to true,
                        "vpn_package_name" to (packageName ?: "unknown")
                    ))
                } else {
                    ToolResult.success(mapOf(
                        "is_active" to false,
                        "vpn_package_name" to null
                    ))
                }
            } else {
                // Fallback for older Android versions
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_VPN)
                val isActive = networkInfo?.isConnected == true

                ToolResult.success(mapOf(
                    "is_active" to isActive,
                    "vpn_package_name" to null
                ))
            }
        } catch (e: SecurityException) {
            ToolResult.error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ToolResult.error("Failed to check VPN status: ${e.message}")
        }
    }
}
