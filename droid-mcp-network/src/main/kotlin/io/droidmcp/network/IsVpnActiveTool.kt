package io.droidmcp.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
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
 * (checked up front). On API 23+ inspects `NetworkCapabilities.TRANSPORT_VPN` and attempts to
 * resolve the owning app's package name via reflection on the hidden `ownerUid` field (often
 * `"unknown"`); below API 23 uses the deprecated `TYPE_VPN` network info (package always null).
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
                    // ownerUid is a hidden NetworkCapabilities field, accessed via reflection
                    // (not officially available in the public API). Note: on API 30+ the
                    // platform strips ownerUid to INVALID_UID for any caller other than the
                    // VPN app itself, so this only ever resolves a real package pre-30.
                    val packageName = try {
                        val ownerUidField = capabilities.javaClass.getDeclaredField("ownerUid")
                        ownerUidField.isAccessible = true
                        val uid = ownerUidField.get(capabilities) as? Int
                        if (uid != null && uid >= android.os.Process.FIRST_APPLICATION_UID) {
                            val packageManager = context.packageManager
                            val packages = packageManager.getPackagesForUid(uid)
                            packages?.firstOrNull()
                        } else {
                            null
                        }
                    } catch (e: Exception) {
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
