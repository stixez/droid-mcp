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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IsVpnActiveTool(private val context: Context) : McpTool {
    override val name = "is_vpn_active"
    override val description = "Check if a VPN connection is currently active and return the VPN package name if available."
    override val parameters = emptyList<ToolParameter>()

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
                    // Try to get VPN package name via reflection (not officially available in public API)
                    val vpnPackage = try {
                        val underlyingNetworksField = capabilities.javaClass.getDeclaredField("underlyingNetworks")
                        underlyingNetworksField.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val networks = underlyingNetworksField.get(capabilities) as? Array<Network>
                        networks?.firstOrNull()?.toString()
                    } catch (e: Exception) {
                        null
                    }

                    // Alternative: try to get from NetworkCapabilities via reflection
                    val packageName = try {
                        val ownerUidField = capabilities.javaClass.getDeclaredField("ownerUid")
                        ownerUidField.isAccessible = true
                        val uid = ownerUidField.get(capabilities) as? Int
                        if (uid != null && uid > 10000) {
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
