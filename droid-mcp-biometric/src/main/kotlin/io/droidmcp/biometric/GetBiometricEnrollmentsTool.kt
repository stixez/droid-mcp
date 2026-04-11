package io.droidmcp.biometric

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.hardware.fingerprint.FingerprintManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolResult

class GetBiometricEnrollmentsTool(private val context: Context) : McpTool {

    override val name = "get_biometric_enrollments"
    override val description = "Get information about enrolled biometrics on the device"
    override val parameters = emptyList<io.droidmcp.core.ToolParameter>()

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val hasFingerprint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val fm = context.getSystemService(Context.FINGERPRINT_SERVICE) as? FingerprintManager
                fm?.hasEnrolledFingerprints() == true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }

        val hasFace = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        val enrolledCount = if (hasFingerprint) {
            // Can't get exact count without permissions, return 1 if enrolled
            1
        } else {
            0
        }

        return ToolResult.success(mapOf(
            "enrolled_count" to enrolledCount,
            "has_fingerprint" to hasFingerprint,
            "has_face" to hasFace
        ))
    }
}
