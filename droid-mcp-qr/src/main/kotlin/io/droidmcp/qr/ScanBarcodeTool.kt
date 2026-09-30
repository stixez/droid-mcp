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
 * Decodes a 1D/product barcode from a still image file (referenced by `image_uri`) using ML Kit,
 * restricted to EAN-13, UPC-A, CODE-128, CODE-39, EAN-8, and UPC-E. Reads an existing image — does NOT
 * open the camera, and the module declares no permissions. `image_uri` is validated by
 * [ImageUriValidator] (external-storage sandbox for `file://`, host-owned `content://` authorities
 * refused). Returns the first barcode found, or `found = false` when the image decodes but contains
 * none; a decode/scanner failure is an error.
 *
 * Result keys: `raw_value`, `format` (one of the supported format names, or `"UNKNOWN"`), `found`.
 */
class ScanBarcodeTool(private val context: Context) : McpTool {

    override val name = "scan_barcode"
    override val description = "Scan a barcode from an image file URI. Supports EAN-13, UPC-A, CODE-128, etc."
    override val parameters = listOf(
        ToolParameter("image_uri", "file:// (external storage only) or content:// URI of the image containing the barcode", ParameterType.STRING, required = true),
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
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_E,
                )
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
            val formatName = when (barcode.format) {
                Barcode.FORMAT_EAN_13 -> "EAN_13"
                Barcode.FORMAT_UPC_A -> "UPC_A"
                Barcode.FORMAT_CODE_128 -> "CODE_128"
                Barcode.FORMAT_CODE_39 -> "CODE_39"
                Barcode.FORMAT_EAN_8 -> "EAN_8"
                Barcode.FORMAT_UPC_E -> "UPC_E"
                else -> "UNKNOWN"
            }

            ToolResult.success(mapOf(
                "raw_value" to barcode.rawValue,
                "format" to formatName,
                "found" to true,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to scan barcode: ${e.message}")
        }
    }
}
