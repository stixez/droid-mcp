package io.droidmcp.qr

import android.content.Context
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Decodes a QR code from a still image file (referenced by `image_uri`) using ML Kit barcode scanning
 * restricted to [Barcode.FORMAT_QR_CODE]. This reads an existing image — it does NOT open the camera,
 * and the module declares no permissions. `image_uri` is validated by [ImageUriValidator]: `file://`
 * URIs (or bare absolute paths) are sandboxed to external storage, `content://` URIs served by the host
 * app itself are refused, other schemes are rejected. Returns the first QR code found, or
 * `found = false` when the image decodes but contains none; a decode/scanner failure is an error.
 *
 * Result keys: `raw_value`, `format` (always `"QR_CODE"` when found), `found`.
 */
class ScanQrCodeTool(private val context: Context) : McpTool {

    override val name = "scan_qr_code"
    override val description = "Scan a QR code from an image file URI"
    override val parameters = listOf(
        ToolParameter("image_uri", "file:// (external storage only) or content:// URI of the image containing the QR code", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val imageUriStr = params["image_uri"]?.toString()
            ?: return@withContext ToolResult.error("image_uri is required")

        val imageUri = ImageUriValidator.validate(context, imageUriStr).getOrElse {
            return@withContext ToolResult.error(it.message ?: "invalid image_uri")
        }
        try {
            val image = InputImage.fromFilePath(context, imageUri)

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            val result = try {
                suspendCancellableCoroutine { cont ->
                    scanner.process(image)
                        .addOnSuccessListener { barcodes -> cont.resume(barcodes) }
                        .addOnFailureListener { e -> cont.resumeWithException(e) }
                        .addOnCanceledListener { cont.resumeWithException(CancellationException("ML Kit task cancelled")) }
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
