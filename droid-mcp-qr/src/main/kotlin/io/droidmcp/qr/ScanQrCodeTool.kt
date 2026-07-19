package io.droidmcp.qr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Decodes a QR code from a still image file (referenced by `image_uri`) using ML Kit barcode scanning
 * restricted to [Barcode.FORMAT_QR_CODE]. This reads an existing image — it does NOT open the camera,
 * so despite the module declaring `CAMERA`, no camera permission is needed for this tool. Returns the
 * first QR code found, or `found = false` when none is present.
 *
 * Result keys: `raw_value`, `format` (always `"QR_CODE"` when found), `found`.
 */
class ScanQrCodeTool(private val context: Context) : McpTool {

    override val name = "scan_qr_code"
    override val description = "Scan a QR code from an image file URI"
    override val parameters = listOf(
        ToolParameter("image_uri", "URI of the image file containing the QR code", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val imageUriStr = params["image_uri"]?.toString()
            ?: return@withContext ToolResult.error("image_uri is required")

        try {
            val imageUri = Uri.parse(imageUriStr)
            val image = InputImage.fromFilePath(context, imageUri)

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val result = try {
                suspendCancellableCoroutine { cont ->
                    scanner.process(image)
                        .addOnSuccessListener { barcodes -> cont.resume(barcodes) }
                        .addOnFailureListener { cont.resume(emptyList()) }
                }
            } finally {
                scanner.close()
            }

            if (result.isEmpty()) {
                return@withContext ToolResult.success(mapOf(
                    "raw_value" to null,
                    "format" to null,
                    "found" to false,
                ))
            }

            val barcode = result[0]
            ToolResult.success(mapOf(
                "raw_value" to barcode.rawValue,
                "format" to "QR_CODE",
                "found" to true,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to scan QR code: ${e.message}")
        }
    }
}
